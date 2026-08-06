package com.universalprinter.model

/**
 * Receipt paper width supplied by the parent app. Values are for 203 dpi (8 dots/mm) thermal
 * printers: [printableWidthMM] feeds the ESC/POS engine, [widthPx] scales full-receipt bitmaps,
 * [charsPerLine] sizes monospace text columns.
 */
enum class PaperWidth(
    val printableWidthMM: Float,
    val widthPx: Int,
    val charsPerLine: Int,
    /** Max columns per row for this width (excess cells are clamped): 58→3, 72→4, 80→5. */
    val maxColumns: Int,
) {
    MM_58(48f, 384, 32, 3),   // 58mm − 10mm = 48mm printable · 48×8 = 384 dots
    MM_72(64f, 512, 42, 4),   // 72mm − 8mm  = 64mm printable · 64×8 = 512 dots
    MM_80(72f, 576, 48, 5),   // 80mm − 8mm  = 72mm printable · 72×8 = 576 dots

    /** 3-inch **impact / dot-matrix** (Epson TM-U220 class, 9-pin): ~63mm printable, 33 chars Font A.
     *  Text-only — impact printers can't raster (no image/QR). `widthPx` is nominal (images not used). */
    IMPACT_76(63f, 200, 33, 2);

    companion object {
        /** Map a raw mm value (58/72/80) from the parent app; defaults to [MM_80]. */
        fun ofMillimeters(mm: Int): PaperWidth = when (mm) {
            58 -> MM_58
            72 -> MM_72
            80 -> MM_80
            else -> MM_80
        }
    }
}
