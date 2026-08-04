package com.universalprinter.star

import android.graphics.Bitmap
import com.universalprinter.model.Align
import com.universalprinter.model.BarcodeSymbology
import com.universalprinter.model.CutType
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement
import com.universalprinter.model.QrErrorLevel
import com.universalprinter.model.TextSize
import com.universalprinter.util.ColumnLayout

/**
 * A normalized Star print op. Star-specific *numbers* (magnification, QR cell size, barcode height)
 * are resolved here; the trivial 1:1 enum passes ([Align]→Alignment, symbology, QR level) are done
 * in the backend executor. Produced purely by [StarRenderer], so the mapping is unit-testable.
 */
internal sealed interface StarOp {
    data class Text(
        val text: String,
        val align: Align,
        val bold: Boolean,
        val underline: Boolean,
        val invert: Boolean,
        val widthMagnification: Int,
        val heightMagnification: Int,
    ) : StarOp

    data class Image(val bitmap: Bitmap, val align: Align, val invert: Boolean, val dither: Boolean, val targetWidthPx: Int) : StarOp
    data class Barcode(val data: String, val symbology: BarcodeSymbology, val heightMm: Double, val align: Align) : StarOp
    data class QrCode(val data: String, val cellSize: Int, val level: QrErrorLevel, val align: Align) : StarOp
    data class Feed(val lines: Int) : StarOp
    data class Cut(val full: Boolean) : StarOp
    // Raw is intentionally absent: StarXpand builds a command document; raw byte passthrough is unsupported.
}

/** Pure element→[StarOp] mapping. Columns/divider are flattened to left-aligned unstyled text. */
internal object StarRenderer {

    fun render(document: PrintDocument): List<StarOp> {
        val ops = mutableListOf<StarOp>()
        for (element in document.elements) {
            when (element) {
                is PrintElement.Text -> {
                    val (w, h) = magnification(element.size)
                    ops += StarOp.Text(element.text + "\n", element.align, element.bold, element.underline, element.invert, w, h)
                }
                is PrintElement.Columns -> ColumnLayout.format(element.cells, document.paper).forEach {
                    ops += StarOp.Text(it + "\n", Align.LEFT, bold = false, underline = false, invert = false, widthMagnification = 1, heightMagnification = 1)
                }
                is PrintElement.Image -> ops += StarOp.Image(element.bitmap, element.align, element.invert, element.dither, document.paper.widthPx)
                is PrintElement.Barcode -> ops += StarOp.Barcode(element.data, element.symbology, element.heightDots / 8.0, element.align)
                is PrintElement.QrCode -> ops += StarOp.QrCode(element.data, (element.sizeDots / 25).coerceIn(1, 8), element.errorLevel, element.align)
                is PrintElement.Feed -> ops += StarOp.Feed(element.lines)
                PrintElement.Divider -> ops += StarOp.Text("-".repeat(document.paper.charsPerLine) + "\n", Align.LEFT, false, false, false, 1, 1)
                is PrintElement.Raw -> {} // unsupported on Star
                is PrintElement.ImageUrl -> {} // resolved to Image before rendering; skip if unresolved
            }
        }
        if (document.cut != CutType.NONE) ops += StarOp.Cut(full = document.cut == CutType.FULL)
        return ops
    }

    /** width × height magnification per size. */
    private fun magnification(size: TextSize): Pair<Int, Int> = when (size) {
        TextSize.NORMAL -> 1 to 1
        TextSize.WIDE -> 2 to 1
        TextSize.TALL -> 1 to 2
        TextSize.LARGE -> 2 to 2
    }
}
