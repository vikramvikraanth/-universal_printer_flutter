package com.universalprinter.escpos

import android.graphics.Bitmap
import com.universalprinter.model.Align
import com.universalprinter.model.BarcodeSymbology
import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement
import com.universalprinter.model.TextSize
import com.universalprinter.util.Bitmaps
import com.universalprinter.util.ColumnLayout

/**
 * Renders a device-agnostic [PrintDocument] into a DantSu formatted-text string. Image→hex
 * conversion is injected ([imageToHex]) so the mapping is pure/unit-testable. Columns come from the
 * shared [ColumnLayout] (word-wrapped monospace lines). [PrintElement.Raw] is skipped here (ESC/POS
 * raw bytes can't be embedded in DantSu formatted text).
 */
internal object EscPosRenderer {

    private const val MAX_IMG_BAND_HEIGHT = 256

    fun render(document: PrintDocument, imageToHex: (Bitmap) -> String): String {
        val paper = document.paper
        val sb = StringBuilder()
        for (element in document.elements) {
            when (element) {
                is PrintElement.Text -> sb.append(align(element.align)).append(styled(element)).append('\n')
                is PrintElement.Columns ->
                    ColumnLayout.format(element.cells, paper).forEach { sb.append("[L]").append(it).append('\n') }
                is PrintElement.Image -> sb.append(renderImage(element, paper, imageToHex))
                is PrintElement.Barcode -> sb.append(align(element.align))
                    .append("<barcode type='${barcodeType(element.symbology)}' height='${(element.heightDots / 8).coerceAtLeast(1)}'>")
                    .append(element.data).append("</barcode>\n")
                is PrintElement.QrCode -> sb.append(align(element.align))
                    .append("<qrcode size='${(element.sizeDots / 10).coerceIn(1, 60)}'>")
                    .append(element.data).append("</qrcode>\n")
                is PrintElement.Feed -> repeat(element.lines) { sb.append('\n') }
                PrintElement.Divider -> sb.append("[L]").append("-".repeat(paper.charsPerLine)).append('\n')
                is PrintElement.Raw -> {} // raw bytes handled by the backend, not the formatted-text path
                is PrintElement.ImageUrl -> {} // resolved to Image before rendering; skip if unresolved
            }
        }
        return sb.toString()
    }

    private fun align(a: Align): String = when (a) {
        Align.LEFT -> "[L]"
        Align.CENTER -> "[C]"
        Align.RIGHT -> "[R]"
    }

    private fun styled(t: PrintElement.Text): String {
        val attrs = buildString {
            when (t.size) {
                TextSize.NORMAL -> {}
                TextSize.WIDE -> append(" size='wide'")
                TextSize.TALL -> append(" size='tall'")
                TextSize.LARGE -> append(" size='big'")
            }
            if (t.invert) append(" color='bg-black'") // white-on-black inverted background
        }
        var s = if (attrs.isNotEmpty()) "<font$attrs>${t.text}</font>" else t.text
        if (t.bold) s = "<b>$s</b>"
        if (t.underline) s = "<u>$s</u>"
        return s
    }

    private fun barcodeType(s: BarcodeSymbology): String = when (s) {
        BarcodeSymbology.CODE128 -> "128"
        BarcodeSymbology.CODE39 -> "39"
        BarcodeSymbology.EAN13 -> "ean13"
        BarcodeSymbology.UPCA -> "upca"
    }

    private fun renderImage(element: PrintElement.Image, paper: PaperWidth, imageToHex: (Bitmap) -> String): String {
        var scaled = Bitmaps.scaleToWidth(element.bitmap, paper.widthPx)
        if (element.dither) scaled = Bitmaps.dither(scaled)
        if (element.invert) scaled = Bitmaps.invert(scaled)
        val tag = align(element.align)
        val sb = StringBuilder()
        var y = 0
        while (y < scaled.height) {
            val h = minOf(MAX_IMG_BAND_HEIGHT, scaled.height - y)
            val band = Bitmap.createBitmap(scaled, 0, y, scaled.width, h)
            sb.append(tag).append("<img>").append(imageToHex(band)).append("</img>\n")
            y += h
        }
        return sb.toString()
    }
}
