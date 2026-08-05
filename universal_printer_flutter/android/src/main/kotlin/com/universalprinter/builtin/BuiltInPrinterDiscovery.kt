package com.universalprinter.builtin

import android.content.Context
import android.os.Build
import com.imin.printer.PrinterHelper
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import com.universalprintersearch.model.DiscoveredPrinter
import com.universalprintersearch.model.PrinterBrand
import com.universalprintersearch.model.PrinterConnectionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Discovers the **host device's built-in printer** (Sunmi / iMin POS hardware). Detection is by
 * vendor **service-package presence** (per the `<queries>` in the manifest); paper size / model /
 * serial are queried **live** from the vendor SDK. SDK getter names vary across versions, so the
 * queries are reflective and fall back to brand defaults if unavailable. Vendor-hardware only.
 */
class BuiltInPrinterDiscovery(context: Context) {

    private val appContext = context.applicationContext

    suspend fun discover(): List<DiscoveredPrinter> = withContext(Dispatchers.IO) {
        buildList {
            if (isInstalled(SUNMI_SERVICE_PKG)) add(discoverSunmi())
            if (isInstalled(IMIN_SERVICE_PKG)) add(discoverImin())
        }
    }

    private fun isInstalled(pkg: String): Boolean =
        runCatching { appContext.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    // ---- Sunmi ----

    private suspend fun discoverSunmi(): DiscoveredPrinter {
        val service = bindSunmi()
        val model = service?.reflectString("getPrinterModal")
        val serial = service?.reflectString("getPrinterSerialNo")
        val paper = service?.reflectInt("getPrinterPaper") // 1 = 58mm, 2 = 80mm
        runCatching { sunmiCallback?.let { InnerPrinterManager.getInstance().unBindService(appContext, it) } }
        val widths = when (paper) {
            1 -> listOf(58)
            2 -> listOf(80)
            else -> listOf(58) // Sunmi default (most handhelds); query is authoritative when present
        }
        return DiscoveredPrinter(
            name = model?.takeIf { it.isNotBlank() }?.let { "Sunmi $it" } ?: "Sunmi built-in printer",
            connectionType = PrinterConnectionType.BUILT_IN,
            brand = PrinterBrand.SUNMI,
            model = model?.takeIf { it.isNotBlank() } ?: Build.MODEL,
            serialNumber = serial?.takeIf { it.isNotBlank() },
            supportedPaperWidthsMm = widths,
        )
    }

    @Volatile private var sunmiService: SunmiPrinterService? = null
    private var sunmiCallback: InnerPrinterCallback? = null

    private suspend fun bindSunmi(): SunmiPrinterService? {
        val cb = object : InnerPrinterCallback() {
            override fun onConnected(s: SunmiPrinterService) { sunmiService = s }
            override fun onDisconnected() { sunmiService = null }
        }
        sunmiCallback = cb
        val requested = runCatching { InnerPrinterManager.getInstance().bindService(appContext, cb) }.getOrDefault(false)
        if (!requested) return null
        repeat(40) { sunmiService?.let { return it }; delay(50) } // bind is async
        return sunmiService
    }

    // ---- iMin ----

    private fun discoverImin(): DiscoveredPrinter {
        val helper = runCatching { PrinterHelper.getInstance().also { it.initPrinterService(appContext) } }.getOrNull()
        val model = helper?.reflectString("getPrinterModelName") ?: helper?.reflectString("getPrinterModel")
        val paper = helper?.reflectInt("getPrinterPaperType") // best-effort; unknown across versions
        val widths = when (paper) {
            0 -> listOf(58)
            1 -> listOf(80)
            else -> listOf(80) // iMin default (most iMin POS are 80mm)
        }
        return DiscoveredPrinter(
            name = model?.takeIf { it.isNotBlank() }?.let { "iMin $it" } ?: "iMin built-in printer",
            connectionType = PrinterConnectionType.BUILT_IN,
            brand = PrinterBrand.IMIN,
            model = model?.takeIf { it.isNotBlank() } ?: Build.MODEL,
            supportedPaperWidthsMm = widths,
        )
    }

    // ---- reflective vendor getters (names differ across SDK versions) ----

    private fun Any.reflectString(method: String): String? =
        runCatching { javaClass.getMethod(method).invoke(this) as? String }.getOrNull()

    private fun Any.reflectInt(method: String): Int? =
        runCatching { (javaClass.getMethod(method).invoke(this) as? Number)?.toInt() }.getOrNull()

    companion object {
        const val SUNMI_SERVICE_PKG = "woyou.aidlservice.jiuiv5"
        const val IMIN_SERVICE_PKG = "com.imin.printerservice"
    }
}
