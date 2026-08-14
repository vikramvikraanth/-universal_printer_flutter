package com.universalprinter.sunmi

import android.content.Context
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import com.sunmi.peripheral.printer.WoyouConsts
import com.universalprinter.QueuedPrinter
import com.universalprinter.StatusQueryable
import com.universalprinter.model.PaperState
import com.universalprinter.model.PreflightResult
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintErrorReason
import com.universalprinter.model.PrintResult
import com.universalprinter.model.PrinterStatus
import com.universalprinter.preflight.Preflight
import com.universalprinter.util.Bitmaps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

/**
 * Sunmi built-in printer backend via the inner-printer AIDL service. Renders the enriched
 * device-agnostic [PrintDocument] through [SunmiPrinterService]. Sunmi hardware only.
 */
class SunmiPrinterBackend(
    context: Context,
    preflightEnabled: Boolean = true,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : QueuedPrinter(dispatcher, preflightEnabled = preflightEnabled), StatusQueryable {

    private val appContext = context.applicationContext

    @Volatile private var service: SunmiPrinterService? = null

    private val callback = object : InnerPrinterCallback() {
        override fun onConnected(printerService: SunmiPrinterService) { service = printerService }
        override fun onDisconnected() { service = null }
    }

    override val name: String = "Sunmi built-in printer"

    override suspend fun doConnect(): Boolean = bind()

    override suspend fun preflight(document: PrintDocument): PreflightResult {
        val s = service ?: run { if (!bind()) return PreflightResult.Block(PrintErrorReason.NOT_CONNECTED, "Sunmi printer service unavailable"); service }
            ?: return PreflightResult.Block(PrintErrorReason.NOT_CONNECTED, "Sunmi printer service unavailable")
        return Preflight.sunmi(runCatching { s.updatePrinterState() }.getOrDefault(1))
    }

    /**
     * On-demand status (for the app's getStatus). Maps Sunmi's `updatePrinterState()` code to the
     * common [PrinterStatus]. Returns null if the service can't be bound. (Sunmi's state reporting is
     * unreliable per model — some always return 1/running — so treat a healthy result cautiously.)
     */
    override suspend fun queryStatus(): PrinterStatus? {
        val s = service ?: run { if (!bind()) return null; service } ?: return null
        val state = runCatching { s.updatePrinterState() }.getOrNull() ?: return null
        return PrinterStatus(
            online = state != 505,
            coverOpen = state == 6,
            error = state == 3 || state == 5, // hardware abnormal / overheating
            autoCutterError = state == 7,
            paper = if (state == 4 || state == 9) PaperState.NOT_PRESENT else PaperState.OK,
        )
    }

    private suspend fun bind(): Boolean {
        service?.let { return true }
        val requested = runCatching { InnerPrinterManager.getInstance().bindService(appContext, callback) }
            .getOrDefault(false)
        if (!requested) return false
        repeat(50) { service?.let { return true }; delay(60) } // bind is async; poll for the callback
        return service != null
    }

    override suspend fun doPrint(document: PrintDocument): PrintResult {
        // Blocking status conditions are handled by preflight(); here we just need the bound service.
        val s = service ?: run { if (!bind()) return err("Sunmi printer service unavailable", reason = PrintErrorReason.NOT_CONNECTED); service }
            ?: return err("Sunmi printer service unavailable", reason = PrintErrorReason.NOT_CONNECTED)
        return try {
            for (op in SunmiRenderer.render(document)) {
                when (op) {
                    is SunmiOp.Text -> {
                        applyTextStyle(s, op)
                        s.setAlignment(op.align, null)
                        s.printText(op.text, null)
                        resetStyle(s)
                    }
                    is SunmiOp.Image -> {
                        s.setAlignment(op.align, null)
                        var bmp = Bitmaps.scaleToWidth(op.bitmap, op.targetWidthPx)
                        if (op.dither) bmp = Bitmaps.dither(bmp)
                        if (op.invert) bmp = Bitmaps.invert(bmp)
                        s.printBitmap(bmp, null)
                    }
                    is SunmiOp.Barcode -> s.printBarCode(op.data, op.symbology, op.height, 2, 2, null)
                    is SunmiOp.QrCode -> s.printQRCode(op.data, op.moduleSize, op.level, null)
                    is SunmiOp.Feed -> s.lineWrap(op.lines, null)
                    is SunmiOp.Raw -> s.sendRAWData(op.bytes, null)
                    SunmiOp.Cut -> s.cutPaper(null)
                    SunmiOp.OpenDrawer -> s.openDrawer(null)
                }
            }
            ok()
        } catch (e: Exception) {
            err(e.message ?: "Sunmi print failed", e)
        }
    }

    override fun doClose() {
        runCatching { InnerPrinterManager.getInstance().unBindService(appContext, callback) }
        service = null
    }

    private fun applyTextStyle(s: SunmiPrinterService, t: SunmiOp.Text) {
        s.setPrinterStyle(WoyouConsts.ENABLE_BOLD, on(t.bold))
        s.setPrinterStyle(WoyouConsts.ENABLE_UNDERLINE, on(t.underline))
        s.setPrinterStyle(WoyouConsts.ENABLE_ANTI_WHITE, on(t.invert))
        s.setPrinterStyle(WoyouConsts.ENABLE_DOUBLE_WIDTH, on(t.doubleWidth))
        s.setPrinterStyle(WoyouConsts.ENABLE_DOUBLE_HEIGHT, on(t.doubleHeight))
    }

    private fun resetStyle(s: SunmiPrinterService) {
        intArrayOf(
            WoyouConsts.ENABLE_BOLD, WoyouConsts.ENABLE_UNDERLINE, WoyouConsts.ENABLE_ANTI_WHITE,
            WoyouConsts.ENABLE_DOUBLE_WIDTH, WoyouConsts.ENABLE_DOUBLE_HEIGHT,
        ).forEach { s.setPrinterStyle(it, WoyouConsts.DISABLE) }
    }

    private fun ok(): PrintResult = PrintResult.Success()
    private fun err(message: String, cause: Throwable? = null, reason: PrintErrorReason = PrintErrorReason.UNKNOWN): PrintResult =
        PrintResult.Error(message, cause, reason)

    private fun on(b: Boolean) = if (b) WoyouConsts.ENABLE else WoyouConsts.DISABLE
}
