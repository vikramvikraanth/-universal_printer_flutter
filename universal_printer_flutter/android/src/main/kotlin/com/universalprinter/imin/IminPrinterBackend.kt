package com.universalprinter.imin

import android.content.Context
import com.imin.printer.PrinterHelper
import com.universalprinter.QueuedPrinter
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintResult
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
) : QueuedPrinter(dispatcher) {

    private val appContext = context.applicationContext
    private val helper = PrinterHelper.getInstance()

    override val name: String = "iMin built-in printer"

    override suspend fun doConnect(): Boolean =
        runCatching { helper.initPrinterService(appContext) }.getOrDefault(false)

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
