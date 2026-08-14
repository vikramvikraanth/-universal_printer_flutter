package com.universalprinter.star

import android.content.Context
import com.starmicronics.stario10.InterfaceType
import com.starmicronics.stario10.StarConnectionSettings
import com.starmicronics.stario10.StarPrinter
import com.starmicronics.stario10.starxpandcommand.DocumentBuilder
import com.starmicronics.stario10.starxpandcommand.DrawerBuilder
import com.starmicronics.stario10.starxpandcommand.MagnificationParameter
import com.starmicronics.stario10.starxpandcommand.PrinterBuilder
import com.starmicronics.stario10.starxpandcommand.StarXpandCommandBuilder
import com.starmicronics.stario10.starxpandcommand.drawer.OpenParameter
import com.starmicronics.stario10.starxpandcommand.printer.Alignment
import com.starmicronics.stario10.starxpandcommand.printer.BarcodeParameter
import com.starmicronics.stario10.starxpandcommand.printer.ImageParameter
import com.starmicronics.stario10.starxpandcommand.printer.QRCodeParameter
import com.starmicronics.stario10.starxpandcommand.printer.QRCodeLevel
import com.starmicronics.stario10.starxpandcommand.printer.TextParameter
import com.starmicronics.stario10.starxpandcommand.printer.BarcodeSymbology as StarBarcodeSymbology
import com.starmicronics.stario10.starxpandcommand.printer.CutType as StarCutType
import com.starmicronics.stario10.StarPrinterStatus
import com.universalprinter.QueuedPrinter
import com.universalprinter.StatusQueryable
import com.universalprinter.model.Align
import com.universalprinter.model.BarcodeSymbology
import com.universalprinter.model.PaperState
import com.universalprinter.model.PreflightResult
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintResult
import com.universalprinter.model.PrinterStatus
import com.universalprinter.model.PrinterWarning
import com.universalprinter.model.QrErrorLevel
import com.universalprinter.preflight.Preflight
import com.universalprinter.util.Bitmaps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Star printer backend using the StarXpand SDK (StarIO10). Renders the enriched [PrintDocument]. */
class StarPrinterBackend(
    context: Context,
    private val identifier: String,
    private val interfaceType: InterfaceType = InterfaceType.Lan,
    /** True for a 9-pin impact Star model (SP700/SP742) — forces text-only (no raster). From discovery. */
    override val isImpact: Boolean = false,
    /** Physical paper width in mm from discovery; drives the render width via [printReceipt]. */
    override val paperWidthMm: Int? = null,
    private val preflightEnabled: Boolean = true,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : QueuedPrinter(dispatcher, preflightEnabled = preflightEnabled), StatusQueryable {

    private val appContext = context.applicationContext

    override val name: String = "Star ($identifier)"

    private fun settings() = StarConnectionSettings(interfaceType, identifier)

    override suspend fun doConnect(): Boolean = runCatching {
        val printer = StarPrinter(settings(), appContext)
        printer.openAsync().await(); printer.closeAsync().await(); true
    }.getOrDefault(false)

    override suspend fun doPrint(document: PrintDocument): PrintResult {
        val printer = StarPrinter(settings(), appContext)
        return try {
            printer.openAsync().await()
            // Star status needs an open port, so preflight here (reusing this connection).
            val warnings: List<PrinterWarning>
            if (preflightEnabled) {
                val st = printer.getStatusAsync().await()
                when (val pf = Preflight.star(starStatus(st))) {
                    is PreflightResult.Block -> return PrintResult.Error(pf.message, reason = pf.reason)
                    is PreflightResult.Proceed -> warnings = pf.warnings
                }
            } else {
                warnings = emptyList()
            }
            printer.printAsync(buildCommands(document)).await()
            PrintResult.Success(warnings)
        } catch (e: Exception) {
            PrintResult.Error(e.message ?: "Star print failed", e, starReason(e))
        } finally {
            runCatching { printer.closeAsync().await() }
        }
    }

    /** On-demand status (for the app's getStatus). Opens the port, reads, closes. */
    override suspend fun queryStatus(): PrinterStatus? = runCatching {
        val printer = StarPrinter(settings(), appContext)
        try {
            printer.openAsync().await()
            val st = printer.getStatusAsync().await()
            val s = starStatus(st)
            PrinterStatus(
                online = !s.unrecoverableError,
                coverOpen = s.coverOpen, // printUnitOpen is a distinct state, surfaced via `error`
                error = s.hasError || s.printUnitOpen || s.voltageError || s.overTemperature ||
                    s.rollPositionError || s.paperSeparatorError || s.unrecoverableError,
                autoCutterError = s.cutterError,
                paper = when {
                    s.paperEmpty -> PaperState.NOT_PRESENT
                    s.paperNearEmpty -> PaperState.NEAR_END
                    else -> PaperState.OK
                },
            )
        } finally {
            runCatching { printer.closeAsync().await() }
        }
    }.getOrNull()

    /** Flatten a [StarPrinterStatus] (+ its non-null `.detail`) into our [Preflight.StarStatus]. Detail
     *  fault flags are `Boolean?` (verified against stario10:1.12.0 via javap); `== true` treats an
     *  absent flag as "not faulted". Direct (compile-checked) access — a future SDK rename now fails the
     *  build instead of silently dropping a fault, which is what reflective lookups risked. */
    private fun starStatus(st: StarPrinterStatus): Preflight.StarStatus {
        val d = st.detail
        return Preflight.StarStatus(
            coverOpen = st.coverOpen,
            printUnitOpen = d.printUnitOpen == true,
            paperEmpty = st.paperEmpty,
            paperNearEmpty = st.paperNearEmpty,
            paperPresent = d.paperPresent == true,
            cutterError = d.cutterError == true,
            paperJamError = d.paperJamError == true,
            overTemperature = d.printHeadOverTemperature == true || d.printHeadThermistorError == true,
            voltageError = d.voltageError == true,
            receiveBufferOverflow = d.receiveBufferOverflow == true,
            rollPositionError = d.rollPositionError == true,
            paperSeparatorError = d.paperSeparatorError == true,
            unrecoverableError = d.unrecoverableError == true,
            hasError = st.hasError,
        )
    }

    private fun buildCommands(document: PrintDocument): String {
        val pb = PrinterBuilder()
        for (op in StarRenderer.render(document)) {
            when (op) {
                is StarOp.Text -> {
                    pb.styleAlignment(align(op.align))
                        .styleBold(op.bold).styleUnderLine(op.underline).styleInvert(op.invert)
                        .styleMagnification(MagnificationParameter(op.widthMagnification, op.heightMagnification))
                        .actionPrintText(op.text, TextParameter())
                    resetStyle(pb)
                }
                is StarOp.Image -> {
                    var bmp = Bitmaps.scaleToWidth(op.bitmap, op.targetWidthPx)
                    if (op.dither) bmp = Bitmaps.dither(bmp)
                    if (op.invert) bmp = Bitmaps.invert(bmp)
                    pb.styleAlignment(align(op.align)).actionPrintImage(ImageParameter(bmp, op.targetWidthPx))
                }
                is StarOp.Barcode -> pb.styleAlignment(align(op.align))
                    .actionPrintBarcode(BarcodeParameter(op.data, symbology(op.symbology)).setHeight(op.heightMm).setPrintHri(true))
                is StarOp.QrCode -> pb.styleAlignment(align(op.align))
                    .actionPrintQRCode(QRCodeParameter(op.data).setLevel(qrLevel(op.level)).setCellSize(op.cellSize))
                is StarOp.Feed -> pb.actionFeedLine(op.lines)
                is StarOp.Cut -> pb.actionCut(if (op.full) StarCutType.Full else StarCutType.Partial)
            }
        }
        val docBuilder = DocumentBuilder().addPrinter(pb)
        if (document.openDrawer) docBuilder.addDrawer(DrawerBuilder().actionOpen(OpenParameter()))
        return StarXpandCommandBuilder().addDocument(docBuilder).getCommands()
    }

    private fun resetStyle(pb: PrinterBuilder) {
        pb.styleBold(false).styleUnderLine(false).styleInvert(false).styleMagnification(MagnificationParameter(1, 1))
    }

    private fun align(a: Align): Alignment = when (a) {
        Align.LEFT -> Alignment.Left; Align.CENTER -> Alignment.Center; Align.RIGHT -> Alignment.Right
    }

    private fun qrLevel(l: QrErrorLevel): QRCodeLevel = when (l) {
        QrErrorLevel.L -> QRCodeLevel.L; QrErrorLevel.M -> QRCodeLevel.M; QrErrorLevel.Q -> QRCodeLevel.Q; QrErrorLevel.H -> QRCodeLevel.H
    }

    private fun symbology(s: BarcodeSymbology): StarBarcodeSymbology = when (s) {
        BarcodeSymbology.CODE128 -> StarBarcodeSymbology.Code128
        BarcodeSymbology.CODE39 -> StarBarcodeSymbology.Code39
        BarcodeSymbology.EAN13 -> StarBarcodeSymbology.Ean13
        BarcodeSymbology.UPCA -> StarBarcodeSymbology.UpcA
    }
}
