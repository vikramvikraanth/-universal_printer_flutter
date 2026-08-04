package com.universalprinter.imin

import android.graphics.Bitmap
import com.universalprinter.model.Align
import com.universalprinter.model.BarcodeSymbology
import com.universalprinter.model.CutType
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement
import com.universalprinter.util.ColumnLayout

/**
 * A normalized iMin print op with iMin-resolved primitives (align/symbology ints). iMin's
 * `PrinterHelper` exposes only `setFontAntiWhite` for styling, so bold/underline/size are
 * intentionally dropped (not representable). Produced purely by [IminRenderer] for unit-testing.
 */
internal sealed interface IminOp {
    /** Text (incl. flattened columns/divider). [align]: 0=left,1=center,2=right. */
    data class Text(val text: String, val align: Int, val invert: Boolean) : IminOp
    data class Image(val bitmap: Bitmap, val align: Int, val invert: Boolean, val dither: Boolean, val targetWidthPx: Int) : IminOp
    data class Barcode(val data: String, val symbology: Int, val align: Int) : IminOp
    data class QrCode(val data: String, val align: Int) : IminOp
    data class Feed(val lines: Int) : IminOp
    data class Raw(val bytes: ByteArray) : IminOp {
        override fun equals(other: Any?) = other is Raw && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
    }
    data class Cut(val full: Boolean) : IminOp
    data object OpenDrawer : IminOp
}

/** Pure element→[IminOp] mapping. Columns/divider are flattened to left-aligned text. */
internal object IminRenderer {

    fun render(document: PrintDocument): List<IminOp> {
        val ops = mutableListOf<IminOp>()
        for (element in document.elements) {
            when (element) {
                is PrintElement.Text -> ops += IminOp.Text(element.text + "\n", align(element.align), element.invert)
                is PrintElement.Columns -> ColumnLayout.format(element.cells, document.paper).forEach {
                    ops += IminOp.Text(it + "\n", 0, invert = false)
                }
                is PrintElement.Image -> ops += IminOp.Image(element.bitmap, align(element.align), element.invert, element.dither, document.paper.widthPx)
                is PrintElement.Barcode -> ops += IminOp.Barcode(element.data, symbology(element.symbology), align(element.align))
                is PrintElement.QrCode -> ops += IminOp.QrCode(element.data, align(element.align))
                is PrintElement.Feed -> ops += IminOp.Feed(element.lines)
                PrintElement.Divider -> ops += IminOp.Text("-".repeat(document.paper.charsPerLine) + "\n", 0, invert = false)
                is PrintElement.Raw -> ops += IminOp.Raw(element.bytes)
                is PrintElement.ImageUrl -> {} // resolved to Image before rendering; skip if unresolved
            }
        }
        if (document.cut != CutType.NONE) ops += IminOp.Cut(full = document.cut == CutType.FULL)
        if (document.openDrawer) ops += IminOp.OpenDrawer
        return ops
    }

    private fun align(a: Align): Int = when (a) { Align.LEFT -> 0; Align.CENTER -> 1; Align.RIGHT -> 2 }
    private fun symbology(s: BarcodeSymbology): Int = when (s) {
        BarcodeSymbology.CODE128 -> 8; BarcodeSymbology.CODE39 -> 4; BarcodeSymbology.EAN13 -> 3; BarcodeSymbology.UPCA -> 0
    }
}
