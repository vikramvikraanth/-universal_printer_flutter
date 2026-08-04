package com.universalprinter.html

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintElement
import com.universalprinter.util.Bitmaps
import java.io.ByteArrayOutputStream

/**
 * Android-side encoders for [ReceiptHtmlRenderer]: turns images and barcodes/QR into `data:image/png`
 * URIs to embed in the HTML. Barcodes/QR are generated via [ReceiptCodes] (ZXing) so they stay real
 * and scannable in the rasterized image.
 */
internal class ReceiptImages(private val paper: PaperWidth) {

    /** `<img src>` for a logo/photo — scaled to paper width, PNG data-URI. */
    fun imageEncoder(bitmap: Bitmap): String = dataUri(Bitmaps.scaleToWidth(bitmap, paper.widthPx))

    /** `<img src>` for a barcode/QR — real ZXing bitmap as a PNG data-URI; blank on encode failure. */
    fun codeEncoder(element: PrintElement): String =
        runCatching { dataUri(matrixToBitmap(element)) }.getOrElse { dataUri(blank()) }

    private fun matrixToBitmap(element: PrintElement): Bitmap {
        val matrix = ReceiptCodes.matrix(element, paper.widthPx)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h) { i -> if (matrix.get(i % w, i / w)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun blank(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    private fun dataUri(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
