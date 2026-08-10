package com.universalprinter.imin

import android.content.Context
import com.imin.printer.PrinterHelper
import com.universalprinter.QueuedPrinter
import com.universalprinter.StatusQueryable
import com.universalprinter.model.PaperState
import com.universalprinter.model.PreflightResult
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintResult
import com.universalprinter.model.PrinterStatus
import com.universalprinter.preflight.Preflight
import com.universalprinter.util.Bitmaps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * iMin built-in printer backend via iMin's [PrinterHelper] (unifies iMin v1 + v2). Renders the
 * enriched [PrintDocument]. iMin hardware only. Bold/underline/size are best-effort (iMin exposes
 * `setFontAntiWhite` for invert, verified; other text-style setters are not confirmed).
 */
class IminPrinterBackend(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : QueuedPrinter(dispatcher), StatusQueryable {

    private val appContext = context.applicationContext
    private val helper = PrinterHelper.getInstance()

    override val name: String = "iMin built-in printer"

    override suspend fun doConnect(): Boolean =
        runCatching { helper.initPrinterService(appContext) }.getOrDefault(false)

    /**
     * iMin (like Sunmi) reports print success even when out of paper / cover open, so gate every job
     * on a live status read. The status code is read reflectively (the SDK method signature varies by
     * version) — if it can't be read, proceed rather than block a printer we can't query.
     */
    override suspend fun preflight(document: PrintDocument): PreflightResult {
        runCatching { helper.initPrinterService(appContext) }
        val code = readStatusCode() ?: return PreflightResult.Proceed()
        return Preflight.imin(code)
    }

    /** On-demand status (for the app's getStatus). Maps the iMin code to the common [PrinterStatus]. */
    override suspend fun queryStatus(): PrinterStatus? {
        runCatching { helper.initPrinterService(appContext) }
        val code = readStatusCode() ?: return null
        return PrinterStatus(
            online = code != -1 && code != 1,
            coverOpen = code == 3,
            error = code == 4 || code == 99,
            autoCutterError = false,
            paper = if (code == 7) PaperState.NOT_PRESENT else if (code == 8) PaperState.NEAR_END else PaperState.OK,
        )
    }

    /** Read iMin's integer status via reflection; null if the method/signature isn't available. */
    private fun readStatusCode(): Int? = runCatching {
        (helper.javaClass.getMethod("getPrinterStatus").invoke(helper) as? Number)?.toInt()
    }.getOrNull()

    override suspend fun doPrint(document: PrintDocument): PrintResult {
        return try {
            helper.initPrinterService(appContext)
            for (op in IminRenderer.render(document)) {
                when (op) {
                    is IminOp.Text -> {
                        helper.setFontAntiWhite(op.invert)
                        helper.printTextWithAli(op.text, op.align, null)
                        helper.setFontAntiWhite(false)
                    }
                    is IminOp.Image -> {
                        var bmp = Bitmaps.scaleToWidth(op.bitmap, op.targetWidthPx)
                        if (op.dither) bmp = Bitmaps.dither(bmp)
                        if (op.invert) bmp = Bitmaps.invert(bmp)
                        helper.printBitmapWithAlign(bmp, op.align, null)
                    }
                    is IminOp.Barcode -> helper.printBarCodeWithAlign(op.data, op.symbology, op.align, null)
                    is IminOp.QrCode -> helper.printQrCodeWithAlign(op.data, op.align, null)
                    is IminOp.Feed -> repeat(op.lines) { helper.printAndLineFeed() }
                    is IminOp.Raw -> helper.sendRAWData(op.bytes, null)
                    is IminOp.Cut -> if (op.full) helper.fullCut() else helper.partialCut()
                    IminOp.OpenDrawer -> helper.openDrawer()
                }
            }
            PrintResult.Success()
        } catch (e: Exception) {
            PrintResult.Error(e.message ?: "iMin print failed", e)
        }
    }

    override fun doClose() {
        runCatching { helper.deInitPrinterService(appContext) }
    }
}
