package com.universalprinter.text

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.universalprinter.model.Align
import com.universalprinter.model.Column
import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement
import com.universalprinter.model.TextSize
import com.universalprinter.util.ColumnLayout

/**
 * Renders text to bitmaps using Android's text stack. `StaticLayout` performs full Unicode layout —
 * glyph shaping and bidirectional/RTL reordering — so any script (CJK, Arabic, Hebrew, Thai, …)
 * prints correctly on any raster-capable printer, independent of the printer's font ROM. Context-free
 * (uses `Typeface.DEFAULT`), so it runs off the main thread on the queue.
 */
internal object TextRasterizer {

    private const val BASE_TEXT_PX = 24f

    fun rasterize(spec: RasterSpec): Bitmap {
        val paint = paint(bold = spec.bold, widthScale = widthScale(spec.size), heightScale = heightScale(spec.size))
        val text = spec.lines.joinToString("\n")
        val layout = layout(text, paint, spec.widthPx, alignment(spec.align))
        return draw(spec.widthPx, layout.height) { canvas -> layout.draw(canvas) }
    }

    /**
     * Lays out a columns row in pixel space: each cell gets a pixel-width slice by weight (reusing
     * [ColumnLayout.columnWidths]) and is wrapped/aligned by the text engine at that width — so
     * variable-width glyphs (CJK double-width, shaped Arabic) align, unlike monospace char counting.
     */
    fun rasterizeColumns(cells: List<Column>, paper: PaperWidth): Bitmap {
        val clamped = if (cells.size > paper.maxColumns) cells.take(paper.maxColumns) else cells
        val widths = ColumnLayout.columnWidths(clamped, paper.widthPx)
        val paint = paint(bold = false, widthScale = 1f, heightScale = 1f)
        val layouts = clamped.mapIndexed { i, cell ->
            layout(cell.text, paint, widths[i].coerceAtLeast(1), alignment(cell.align))
        }
        val rowHeight = layouts.maxOfOrNull { it.height } ?: 1
        return draw(paper.widthPx, rowHeight) { canvas ->
            var x = 0
            layouts.forEachIndexed { i, l ->
                canvas.save()
                canvas.translate(x.toFloat(), 0f)
                l.draw(canvas)
                canvas.restore()
                x += widths[i]
            }
        }
    }

    private fun paint(bold: Boolean, widthScale: Float, heightScale: Float) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = BASE_TEXT_PX * heightScale
            textScaleX = widthScale
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    private fun layout(text: String, paint: TextPaint, widthPx: Int, align: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(align)
            .setIncludePad(false)
            .build()

    private inline fun draw(widthPx: Int, heightPx: Int, block: (Canvas) -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        block(canvas)
        return bitmap
    }

    private fun alignment(a: Align): Layout.Alignment = when (a) {
        Align.LEFT -> Layout.Alignment.ALIGN_NORMAL
        Align.CENTER -> Layout.Alignment.ALIGN_CENTER
        Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
    }

    private fun widthScale(size: TextSize) = if (size == TextSize.WIDE || size == TextSize.LARGE) 2f else 1f
    private fun heightScale(size: TextSize) = if (size == TextSize.TALL || size == TextSize.LARGE) 2f else 1f
}

/**
 * Applies the [RenderPlan] to a document, rasterizing the elements the policy selected into full-width
 * [PrintElement.Image] elements (the bitmap already carries alignment, so the image prints full width).
 * Returns the original document unchanged when nothing needs rasterizing (the common Latin/AUTO case).
 */
internal object ReceiptRasterization {

    fun apply(document: PrintDocument): PrintDocument {
        val plan = RenderPlan.plan(document)
        if (plan.all { it is Planned.Native }) return document
        val elements = plan.map { planned ->
            when (planned) {
                is Planned.Native -> planned.element
                is Planned.Raster -> PrintElement.Image(TextRasterizer.rasterize(planned.spec), Align.LEFT, planned.spec.invert)
                is Planned.RasterColumns -> PrintElement.Image(TextRasterizer.rasterizeColumns(planned.cells, planned.paper), Align.LEFT, false)
            }
        }
        return PrintDocument(elements, document.paper, document.cut, document.openDrawer, document.renderMode, document.charset)
    }
}
