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
    MM_58(48f, 384, 32, 3),
    MM_72(72f, 576, 42, 4),
    MM_80(72f, 576, 48, 5),

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
