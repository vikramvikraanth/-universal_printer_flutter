package com.universalprinter.escpos

import android.graphics.Bitmap
import com.universalprinter.model.Align
import com.universalprinter.model.CutType

/**
 * Raw ESC/POS bytes for printing a bitmap ourselves (bypassing DantSu) so the network backend can
 * send it in small, paced `GS v 0` bands — the fix for truncated/"stuck" whole-receipt image prints.
 */
internal object EscPosRaster {

    /** ESC @ — initialize printer. */
    val INIT = byteArrayOf(0x1B, 0x40)

    /** ESC a n — alignment (0=left, 1=center, 2=right). */
    fun align(a: Align): ByteArray = byteArrayOf(0x1B, 0x61, when (a) { Align.LEFT -> 0; Align.CENTER -> 1; Align.RIGHT -> 2 })

    /** ESC d n — feed n lines. */
    fun feed(lines: Int): ByteArray = byteArrayOf(0x1B, 0x64, lines.coerceIn(0, 255).toByte())

    /** GS V m — cut (0 full, 1 partial); empty for NONE. */
    fun cut(cut: CutType): ByteArray = when (cut) {
        CutType.FULL -> byteArrayOf(0x1D, 0x56, 0x00)
        CutType.PARTIAL -> byteArrayOf(0x1D, 0x56, 0x01)
        CutType.NONE -> ByteArray(0)
    }

    /**
     * Splits [bitmap] into vertical bands of at most [bandHeight] dots, each a complete `GS v 0`
     * raster command (header + 1-bit packed data, MSB-first, dark pixel = bit set at luminance < 384).
     * Small bands let the sender pace writes so the printer keeps up.
     */
    fun bands(bitmap: Bitmap, bandHeight: Int = 128): List<ByteArray> {
        val w = bitmap.width
        val h = bitmap.height
        val widthBytes = (w + 7) / 8
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val out = ArrayList<ByteArray>((h + bandHeight - 1) / bandHeight)
        var y = 0
        while (y < h) {
            val bh = minOf(bandHeight, h - y)
            val header = byteArrayOf(
                0x1D, 0x76, 0x30, 0x00,
                (widthBytes and 0xFF).toByte(), ((widthBytes shr 8) and 0xFF).toByte(),
                (bh and 0xFF).toByte(), ((bh shr 8) and 0xFF).toByte(),
            )
            val data = ByteArray(widthBytes * bh)
            for (row in 0 until bh) {
                val srcRow = (y + row) * w
                val dstRow = row * widthBytes
                for (bx in 0 until widthBytes) {
                    var b = 0
                    for (bit in 0 until 8) {
                        val x = bx * 8 + bit
                        if (x < w) {
                            val p = pixels[srcRow + x]
                            val lum = ((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)
                            if (lum < 384) b = b or (0x80 shr bit) // dark → print
                        }
                    }
                    data[dstRow + bx] = b.toByte()
                }
            }
            out += header + data
            y += bh
        }
        return out
    }
}
