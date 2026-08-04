package com.universalprinter.model

import android.graphics.Bitmap

/**
 * A receipt to print: ordered [elements] at [paper] width, [cut] behaviour, optional cash-drawer
 * kick, and the [renderMode] governing how text is put on paper (see [RenderMode]).
 */
class PrintDocument(
    val elements: List<PrintElement>,
    val paper: PaperWidth,
    val cut: CutType = CutType.PARTIAL,
    val openDrawer: Boolean = false,
    val renderMode: RenderMode = RenderMode.AUTO,
    val charset: PrinterCharset? = null,
) {
    companion object {
        /** Convenience for the "full receipt image" path: print a single pre-rendered bitmap. */
        fun image(bitmap: Bitmap, paper: PaperWidth, cut: CutType = CutType.PARTIAL): PrintDocument =
            PrintDocument(listOf(PrintElement.Image(bitmap)), paper, cut)
    }
}

/** DSL builder — `printDocument(PaperWidth.MM_80) { text("Hi"); row("Item", "5.00"); qr("id") }`. */
class PrintDocumentBuilder(private val paper: PaperWidth) {
    private val elements = mutableListOf<PrintElement>()
    var cut: CutType = CutType.PARTIAL
    var openDrawer: Boolean = false
    var renderMode: RenderMode = RenderMode.AUTO

    /** Native ESC/POS code page (see [PrinterCharset]); null = printer/library default. ESC/POS backends only. */
    var charset: PrinterCharset? = null

    fun text(
        text: String,
        align: Align = Align.LEFT,
        bold: Boolean = false,
        underline: Boolean = false,
        invert: Boolean = false,
        size: TextSize = TextSize.NORMAL,
    ) = apply { elements += PrintElement.Text(text, align, bold, underline, invert, size) }

    /** Multi-column row. */
    fun columns(vararg cells: Column) = apply { elements += PrintElement.Columns(cells.toList()) }

    /** Common 2-column row: left-aligned [left], right-aligned [right]. */
    fun row(left: String, right: String, leftWeight: Int = 1, rightWeight: Int = 1) = apply {
        elements += PrintElement.Columns(
            listOf(Column(left, leftWeight, Align.LEFT), Column(right, rightWeight, Align.RIGHT)),
        )
    }

    fun image(bitmap: Bitmap, align: Align = Align.CENTER, invert: Boolean = false, dither: Boolean = false) =
        apply { elements += PrintElement.Image(bitmap, align, invert, dither) }

    /** Image by URL — downloaded + cached offline (Glide) before printing. Also for hosted QR/barcode images. */
    fun imageUrl(url: String, align: Align = Align.CENTER, invert: Boolean = false, dither: Boolean = false) =
        apply { elements += PrintElement.ImageUrl(url, align, invert, dither) }

    fun barcode(
        data: String,
        symbology: BarcodeSymbology = BarcodeSymbology.CODE128,
        heightDots: Int = 100,
        align: Align = Align.CENTER,
    ) = apply { elements += PrintElement.Barcode(data, symbology, heightDots, align) }

    fun qr(data: String, sizeDots: Int = 200, errorLevel: QrErrorLevel = QrErrorLevel.M, align: Align = Align.CENTER) =
        apply { elements += PrintElement.QrCode(data, sizeDots, errorLevel, align) }

    fun feed(lines: Int = 1) = apply { elements += PrintElement.Feed(lines) }

    fun divider() = apply { elements += PrintElement.Divider }

    fun raw(bytes: ByteArray) = apply { elements += PrintElement.Raw(bytes) }

    fun build(): PrintDocument = PrintDocument(elements.toList(), paper, cut, openDrawer, renderMode, charset)
}

fun printDocument(paper: PaperWidth, block: PrintDocumentBuilder.() -> Unit): PrintDocument =
    PrintDocumentBuilder(paper).apply(block).build()

/**
 * A text-only copy for **impact / dot-matrix** printers (which can't raster): drops image elements and
 * renders barcodes/QR as their data string. Returns the same instance when there are no graphics.
 */
fun PrintDocument.textOnly(): PrintDocument {
    if (elements.none {
            it is PrintElement.Image || it is PrintElement.ImageUrl ||
                it is PrintElement.Barcode || it is PrintElement.QrCode
        }
    ) {
        return this
    }
    val stripped = elements.mapNotNull { e ->
        when (e) {
            is PrintElement.Image, is PrintElement.ImageUrl -> null
            is PrintElement.Barcode -> PrintElement.Text(e.data, e.align)
            is PrintElement.QrCode -> PrintElement.Text(e.data, e.align)
            else -> e
        }
    }
    return PrintDocument(stripped, paper, cut, openDrawer, renderMode, charset)
}
