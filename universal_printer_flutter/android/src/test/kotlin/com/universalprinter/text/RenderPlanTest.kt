package com.universalprinter.text

import com.universalprinter.model.Align
import com.universalprinter.model.BarcodeSymbology
import com.universalprinter.model.Column
import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement
import com.universalprinter.model.RenderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderPlanTest {

    private fun plan(vararg e: PrintElement, mode: RenderMode = RenderMode.AUTO) =
        RenderPlan.plan(PrintDocument(e.toList(), PaperWidth.MM_80, renderMode = mode))

    @Test
    fun autoKeepsLatinTextNative() {
        val p = plan(PrintElement.Text("Coffee")).single()
        assertTrue(p is Planned.Native)
    }

    @Test
    fun autoRastersNonLatinTextAtPaperWidth() {
        val p = plan(PrintElement.Text("咖啡", align = Align.CENTER)).single()
        assertTrue(p is Planned.Raster)
        val spec = (p as Planned.Raster).spec
        assertEquals(listOf("咖啡"), spec.lines)
        assertEquals(Align.CENTER, spec.align)
        assertEquals(PaperWidth.MM_80.widthPx, spec.widthPx)
    }

    @Test
    fun textModeForcesNativeEvenForNonLatin() {
        assertTrue(plan(PrintElement.Text("咖啡"), mode = RenderMode.TEXT).single() is Planned.Native)
    }

    @Test
    fun imageModeForcesRasterEvenForLatin() {
        assertTrue(plan(PrintElement.Text("Coffee"), mode = RenderMode.IMAGE).single() is Planned.Raster)
    }

    @Test
    fun columnsWithNonLatinCellBecomePixelMeasuredRasterColumns() {
        val cells = listOf(Column("咖啡", 1, Align.LEFT), Column("3.50", 1, Align.RIGHT))
        val p = plan(PrintElement.Columns(cells)).single()
        assertTrue(p is Planned.RasterColumns)
        assertEquals(cells, (p as Planned.RasterColumns).cells) // cells passed through for pixel-space layout
    }

    @Test
    fun latinColumnsStayNative() {
        val p = plan(PrintElement.Columns(listOf(Column("Item", 3, Align.LEFT), Column("5.00", 1, Align.RIGHT)))).single()
        assertTrue(p is Planned.Native)
    }

    @Test
    fun barcodeAndQrAlwaysNativeEvenInImageMode() {
        val p = plan(
            PrintElement.Barcode("123", BarcodeSymbology.CODE128),
            PrintElement.QrCode("q"),
            mode = RenderMode.IMAGE,
        )
        assertTrue(p.all { it is Planned.Native })
    }
}
