package com.universalprinter.sunmi

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
 * A normalized Sunmi print op with all Sunmi-specific parameters already resolved to the primitives
 * the `SunmiPrinterService` expects. Produced purely by [SunmiRenderer] (no Sunmi SDK / Android
 * dependency beyond the passthrough [Image] bitmap), so the element→command mapping is unit-testable.
 */
internal sealed interface SunmiOp {
    /** Text (incl. flattened columns/divider). [align]: 0=left,1=center,2=right. */
    data class Text(
        val text: String,
        val align: Int,
        val bold: Boolean,
        val underline: Boolean,
        val invert: Boolean,
        val doubleWidth: Boolean,
        val doubleHeight: Boolean,
    ) : SunmiOp

    data class Image(val bitmap: Bitmap, val align: Int, val invert: Boolean, val dither: Boolean, val targetWidthPx: Int) : SunmiOp
    data class Barcode(val data: String, val symbology: Int, val height: Int) : SunmiOp
    data class QrCode(val data: String, val moduleSize: Int, val level: Int) : SunmiOp
    data class Feed(val lines: Int) : SunmiOp
    data class Raw(val bytes: ByteArray) : SunmiOp {
        override fun equals(other: Any?) = other is Raw && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
    }
    data object Cut : SunmiOp
    data object OpenDrawer : SunmiOp
}

/** Pure element→[SunmiOp] mapping. Columns/divider are flattened to left-aligned [SunmiOp.Text]. */
internal object SunmiRenderer {

    fun render(document: PrintDocument): List<SunmiOp> {
        val ops = mutableListOf<SunmiOp>()
        for (element in document.elements) {
            when (element) {
                is PrintElement.Text -> ops += SunmiOp.Text(
                    element.text + "\n", align(element.align), element.bold, element.underline, element.invert,
                    doubleWidth = element.size == TextSize.WIDE || element.size == TextSize.LARGE,
                    doubleHeight = element.size == TextSize.TALL || element.size == TextSize.LARGE,
                )
                is PrintElement.Columns -> ColumnLayout.format(element.cells, document.paper).forEach {
                    ops += SunmiOp.Text(it + "\n", 0, bold = false, underline = false, invert = false, doubleWidth = false, doubleHeight = false)
                }
                is PrintElement.Image -> ops += SunmiOp.Image(element.bitmap, align(element.align), element.invert, element.dither, document.paper.widthPx)
                is PrintElement.Barcode -> ops += SunmiOp.Barcode(element.data, symbology(element.symbology), element.heightDots)
                is PrintElement.QrCode -> ops += SunmiOp.QrCode(element.data, (element.sizeDots / 24).coerceIn(1, 16), qrLevel(element.errorLevel))
                is PrintElement.Feed -> ops += SunmiOp.Feed(element.lines)
                PrintElement.Divider -> ops += SunmiOp.Text("-".repeat(document.paper.charsPerLine) + "\n", 0, false, false, false, false, false)
                is PrintElement.Raw -> ops += SunmiOp.Raw(element.bytes)
                is PrintElement.ImageUrl -> {} // resolved to Image before rendering; skip if unresolved
            }
        }
        if (document.cut != CutType.NONE) ops += SunmiOp.Cut
        if (document.openDrawer) ops += SunmiOp.OpenDrawer
        return ops
    }

    private fun align(a: Align): Int = when (a) { Align.LEFT -> 0; Align.CENTER -> 1; Align.RIGHT -> 2 }
    private fun qrLevel(l: QrErrorLevel): Int = when (l) { QrErrorLevel.L -> 0; QrErrorLevel.M -> 1; QrErrorLevel.Q -> 2; QrErrorLevel.H -> 3 }
    private fun symbology(s: BarcodeSymbology): Int = when (s) {
        BarcodeSymbology.CODE128 -> 8; BarcodeSymbology.CODE39 -> 4; BarcodeSymbology.EAN13 -> 2; BarcodeSymbology.UPCA -> 0
    }
}
