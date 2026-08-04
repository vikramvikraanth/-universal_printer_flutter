package com.universalprinter.model

import android.graphics.Bitmap

/** Text/paragraph alignment. */
enum class Align { LEFT, CENTER, RIGHT }

/** Relative text size (mapped to each backend's font scaling). */
enum class TextSize { NORMAL, WIDE, TALL, LARGE }

/** Linear barcode symbologies supported across backends. */
enum class BarcodeSymbology { CODE128, CODE39, EAN13, UPCA }

/** QR error-correction level. */
enum class QrErrorLevel { L, M, Q, H }

/** One cell of a [PrintElement.Columns] row. [weight] splits the line proportionally. */
data class Column(val text: String, val weight: Int = 1, val align: Align = Align.LEFT)

/**
 * A device-agnostic receipt element. Each backend (ESC/POS, Star, Sunmi, iMin) renders the same
 * elements with its own API, so one [PrintDocument] prints identically everywhere.
 */
sealed interface PrintElement {
    data class Text(
        val text: String,
        val align: Align = Align.LEFT,
        val bold: Boolean = false,
        val underline: Boolean = false,
        val invert: Boolean = false, // white-on-black (inverted background)
        val size: TextSize = TextSize.NORMAL,
    ) : PrintElement

    /** A row of weighted, individually-aligned cells; word-wrapped and clamped to the paper's maxColumns. */
    data class Columns(val cells: List<Column>) : PrintElement

    data class Image(
        val bitmap: Bitmap,
        val align: Align = Align.CENTER,
        val invert: Boolean = false, // inverted background (flip pixels)
        val dither: Boolean = false, // Floyd–Steinberg 1-bit dithering (for photos/logos with gradients)
    ) : PrintElement

    /**
     * An image referenced by URL (http/https/file/data). Downloaded and cached **offline** (Glide) and
     * replaced with an [Image] before printing; if it can't be fetched it is skipped. Use it for hosted
     * logos and for pre-rendered/hosted QR or barcode images too.
     */
    data class ImageUrl(
        val url: String,
        val align: Align = Align.CENTER,
        val invert: Boolean = false,
        val dither: Boolean = false,
    ) : PrintElement

    data class Barcode(
        val data: String,
        val symbology: BarcodeSymbology = BarcodeSymbology.CODE128,
        val heightDots: Int = 100,
        val align: Align = Align.CENTER,
    ) : PrintElement

    data class QrCode(
        val data: String,
        val sizeDots: Int = 200,
        val errorLevel: QrErrorLevel = QrErrorLevel.M,
        val align: Align = Align.CENTER,
    ) : PrintElement

    data class Feed(val lines: Int = 1) : PrintElement

    data object Divider : PrintElement

    /** Raw ESC/POS bytes written verbatim (escape hatch). Ignored by non-ESC/POS backends that can't send raw. */
    data class Raw(val bytes: ByteArray) : PrintElement {
        override fun equals(other: Any?) = other is Raw && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
    }
}
