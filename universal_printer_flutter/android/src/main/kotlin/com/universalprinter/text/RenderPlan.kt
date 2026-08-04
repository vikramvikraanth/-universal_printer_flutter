package com.universalprinter.text

import com.universalprinter.model.Align
import com.universalprinter.model.Column
import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement
import com.universalprinter.model.RenderMode
import com.universalprinter.model.TextSize
import com.universalprinter.util.Scripts

/** One block of text to rasterize into a single bitmap (lines are stacked). */
internal data class RasterSpec(
    val lines: List<String>,
    val align: Align,
    val bold: Boolean,
    val size: TextSize,
    val invert: Boolean,
    val widthPx: Int,
)

/** Per-element outcome of the render policy. */
internal sealed interface Planned {
    data class Native(val element: PrintElement) : Planned
    data class Raster(val spec: RasterSpec) : Planned
    /** A columns row to lay out in pixel space (each cell measured by the text engine). */
    data class RasterColumns(val cells: List<Column>, val paper: PaperWidth) : Planned
}

/**
 * Pure render policy: decides, per element and per [RenderMode], whether a textual element prints
 * natively or is rasterized. No Android dependency — fully unit-testable. Barcodes, QR, images,
 * feeds and dividers are always native.
 */
internal object RenderPlan {

    fun plan(document: PrintDocument): List<Planned> =
        document.elements.map { planElement(it, document) }

    private fun planElement(element: PrintElement, doc: PrintDocument): Planned = when (element) {
        is PrintElement.Text ->
            if (shouldRaster(doc.renderMode, listOf(element.text)))
                Planned.Raster(RasterSpec(listOf(element.text), element.align, element.bold, element.size, element.invert, doc.paper.widthPx))
            else Planned.Native(element)

        is PrintElement.Columns ->
            if (shouldRaster(doc.renderMode, element.cells.map { it.text }))
                Planned.RasterColumns(element.cells, doc.paper)
            else Planned.Native(element)

        else -> Planned.Native(element)
    }

    private fun shouldRaster(mode: RenderMode, texts: List<String>): Boolean = when (mode) {
        RenderMode.TEXT -> false
        RenderMode.IMAGE -> true
        RenderMode.AUTO -> texts.any { Scripts.requiresGraphics(it) }
    }
}
