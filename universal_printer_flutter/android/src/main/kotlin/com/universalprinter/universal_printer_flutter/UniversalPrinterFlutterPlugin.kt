package com.universalprinter.universal_printer_flutter

import android.app.Activity
import android.content.Context
import android.hardware.usb.UsbManager
import com.universalprinter.Printer
import com.universalprinter.StatusQueryable
import com.universalprinter.UniversalPrinter
import com.universalprinter.builtin.BuiltInPrinterDiscovery
import com.universalprinter.model.PrintType
import com.universalprinter.printReceipt
import com.universalprintersearch.UniversalPrinterSearch
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Native bridge for `universal_printer_flutter`. Exposes the discovery facade
 * ([UniversalPrinterSearch]) and the print facade ([UniversalPrinter]) over a single MethodChannel.
 *
 * Stateful [Printer] objects live natively in a handle registry (a channel can't hold a native
 * object reference), so Dart creates a printer -> gets an id -> prints/closes by id.
 */
class UniversalPrinterFlutterPlugin :
    FlutterPlugin,
    ActivityAware,
    MethodCallHandler {

    private lateinit var channel: MethodChannel
    private lateinit var appContext: Context
    private var activity: Activity? = null

    private lateinit var discovery: UniversalPrinterSearch
    private lateinit var builtIn: BuiltInPrinterDiscovery
    private val printers = ConcurrentHashMap<String, Printer>()
    private val handleSeq = AtomicLong(0)

    // Main-thread scope: MethodChannel results must be delivered on the main thread. Blocking work is
    // pushed to Dispatchers.IO inside each handler.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Prefer the Activity (USB permission dialog / WebView theming); fall back to application context. */
    private fun ctx(): Context = activity ?: appContext

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        appContext = binding.applicationContext
        discovery = UniversalPrinterSearch(appContext)
        builtIn = BuiltInPrinterDiscovery(appContext)
        channel = MethodChannel(binding.binaryMessenger, "universal_printer_flutter")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        printers.values.forEach { runCatching { it.close() } }
        printers.clear()
        scope.cancel()
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) { activity = binding.activity }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) { activity = binding.activity }
    override fun onDetachedFromActivityForConfigChanges() { activity = null }
    override fun onDetachedFromActivity() { activity = null }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getPlatformVersion" -> result.success("Android ${android.os.Build.VERSION.RELEASE}")

            // ---- Discovery (suspend -> IO, reply on Main) ----
            "discoverEpson" -> discover(result) { discovery.discoverEpsonPrinters() }
            "discoverSunmi" -> discover(result) { discovery.discoverSunmiPrinters() }
            "discoverSnmp" -> discover(result) { discovery.discoverSnmpPrinters() }
            "discoverZebra" -> discover(result) { discovery.discoverZebraPrinters() }
            "discoverStar" -> discover(result) { discovery.discoverStarPrinters() }
            "discoverSeiko" -> discover(result) { discovery.discoverSeikoPrinters() }
            "discoverNetwork" -> discover(result) { discovery.discoverNetworkPrinters() }
            // network/USB/Star/... from the search facade, plus the host's built-in printer.
            "discoverAll" -> discover(result) { discovery.discoverAll() + builtIn.discover() }
            // discoverUsbPrinters() isn't suspend, but the helper still runs it on Dispatchers.IO.
            "discoverUsb" -> discover(result) { discovery.discoverUsbPrinters() }
            // host device's own built-in printer (Sunmi/iMin), detected by vendor service package.
            "discoverBuiltIn" -> discover(result) { builtIn.discover() }

            "ping" -> {
                val ip = call.argument<String>("ip").orEmpty()
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { discovery.ping(ip) } }
                        .onSuccess { result.success(it) }
                        .onFailure { result.error("PING", it.message, null) }
                }
            }

            // ---- Printing ----
            "createPrinter" -> createPrinter(call, result)
            "printDocument" -> printDocument(call, result)
            "getStatus" -> getStatus(call, result)
            "receiptHtml" -> receiptHtml(call, result)
            "closePrinter" -> {
                val printer = printers.remove(call.argument<String>("handle").orEmpty())
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { printer?.close() } }
                    result.success(null)
                }
            }

            else -> result.notImplemented()
        }
    }

    private fun discover(
        result: Result,
        block: suspend () -> List<com.universalprintersearch.model.DiscoveredPrinter>,
    ) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess { result.success(Bridge.printersToList(it)) }
                .onFailure { result.error("DISCOVER", it.message, null) }
        }
    }

    private fun createPrinter(call: MethodCall, result: Result) {
        val kind = call.argument<String>("kind").orEmpty()
        val host = call.argument<String>("host").orEmpty()
        val port = call.argument<Int>("port") ?: 9100
        val identifier = call.argument<String>("identifier").orEmpty()
        val vid = call.argument<Int>("vendorId")
        val pid = call.argument<Int>("productId")
        val brand = call.argument<String>("brand")
        val paperWidthMm = call.argument<Int>("paperWidthMm")
        val isImpact = call.argument<Boolean>("isImpact") ?: false

        scope.launch {
            val printer = try {
                withContext(Dispatchers.IO) {
                    when (kind) {
                        "network", "sunmiCloud" -> UniversalPrinter.network(host, port, brand = brand, paperWidthMm = paperWidthMm, isImpact = isImpact)
                        "star" -> UniversalPrinter.star(ctx(), identifier, isImpact = isImpact, paperWidthMm = paperWidthMm)
                        "sunmi" -> UniversalPrinter.sunmi(ctx())
                        "imin" -> UniversalPrinter.imin(ctx())
                        "usb" -> {
                            val usb = ctx().getSystemService(Context.USB_SERVICE) as UsbManager
                            val device = usb.deviceList.values.firstOrNull { it.vendorId == vid && it.productId == pid }
                                ?: throw IllegalStateException("No USB device with vendorId=$vid productId=$pid")
                            UniversalPrinter.usb(ctx(), device, isImpact = isImpact)
                        }
                        else -> throw IllegalArgumentException("Unknown printer kind: $kind")
                    }
                }
            } catch (e: Exception) {
                result.error("CREATE_PRINTER", e.message, null)
                return@launch
            }
            val handle = "p${handleSeq.incrementAndGet()}"
            printers[handle] = printer
            result.success(handle)
        }
    }

    /** Live hardware status for a handle. `{supported:false}` if the backend can't read status; the
     *  fields are null when the printer didn't answer the query. */
    private fun getStatus(call: MethodCall, result: Result) {
        val handle = call.argument<String>("handle").orEmpty()
        val printer = printers[handle]
            ?: return result.error("NO_PRINTER", "No printer for handle $handle", null)
        val queryable = printer as? StatusQueryable
            ?: return result.success(mapOf("supported" to false))
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { queryable.queryStatus() } }
                .onSuccess { result.success(Bridge.statusToMap(it)) }
                .onFailure { result.error("STATUS", it.message, null) }
        }
    }

    private fun printDocument(call: MethodCall, result: Result) {
        val handle = call.argument<String>("handle").orEmpty()
        val printer = printers[handle]
            ?: return result.error("NO_PRINTER", "No printer for handle $handle", null)
        val docMap = call.argument<Map<String, Any?>>("document")
            ?: return result.error("BAD_DOC", "Missing document", null)
        val type = if ((call.argument<String>("type") ?: "TEXT").equals("IMAGE", true)) PrintType.IMAGE else PrintType.TEXT

        scope.launch {
            runCatching {
                // buildDocument decodes image bytes (BitmapFactory) — keep it off the main thread too.
                // printReceipt's IMAGE path self-dispatches its WebView work to the main looper, so
                // running the whole call on IO is correct.
                withContext(Dispatchers.IO) {
                    printer.printReceipt(ctx(), Bridge.buildDocument(docMap), type)
                }
            }
                .onSuccess { result.success(Bridge.resultToMap(it)) }
                .onFailure { result.error("PRINT", it.message, null) }
        }
    }

    private fun receiptHtml(call: MethodCall, result: Result) {
        val docMap = call.argument<Map<String, Any?>>("document")
            ?: return result.error("BAD_DOC", "Missing document", null)
        scope.launch {
            runCatching {
                // renderReceiptHtml resolves URL images (Glide) — off the main thread. It builds only an
                // HTML string (no WebView), so IO is safe.
                withContext(Dispatchers.IO) {
                    UniversalPrinter.receiptHtml(ctx(), Bridge.buildDocument(docMap))
                }
            }
                .onSuccess { result.success(it) }
                .onFailure { result.error("HTML", it.message, null) }
        }
    }
}
