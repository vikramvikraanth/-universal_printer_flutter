package com.universalprinter.imin

import com.universalprinter.model.Align
import com.universalprinter.model.BarcodeSymbology
import com.universalprinter.model.Column
import com.universalprinter.model.CutType
import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement
import com.universalprinter.model.QrErrorLevel
import com.universalprinter.model.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IminRendererTest {

    private fun render(vararg e: PrintElement, cut: CutType = CutType.NONE, drawer: Boolean = false) =
        IminRenderer.render(PrintDocument(e.toList(), PaperWidth.MM_80, cut, drawer))

    @Test
    fun textCarriesAlignAndInvertOnly() {
        val t = render(PrintElement.Text("Hi", Align.CENTER, bold = true, invert = true, size = TextSize.LARGE))
            .single() as IminOp.Text
        assertEquals("Hi\n", t.text)
        assertEquals(1, t.align)
        assertTrue(t.invert) // bold/size are intentionally not represented (iMin can't apply them)
    }

    @Test
    fun ean13SymbologyDiffersFromSunmi() {
        val bc = render(PrintElement.Barcode("1", BarcodeSymbology.EAN13)).single() as IminOp.Barcode
        assertEquals(3, bc.symbology) // iMin EAN13 = 3 (Sunmi = 2)
    }

    @Test
    fun qrCarriesAlignOnly() {
        val qr = render(PrintElement.QrCode("q", sizeDots = 500, errorLevel = QrErrorLevel.H, align = Align.RIGHT))
            .single() as IminOp.QrCode
        assertEquals(2, qr.align) // size/level not representable on iMin's printQrCodeWithAlign
    }

    @Test
    fun cutFullVsPartialAndDrawerOrder() {
        val full = render(PrintElement.Text("x"), cut = CutType.FULL, drawer = true)
        assertTrue((full[full.size - 2] as IminOp.Cut).full)
        assertEquals(IminOp.OpenDrawer, full.last())
        val partial = render(PrintElement.Text("x"), cut = CutType.PARTIAL).last() as IminOp.Cut
        assertTrue(!partial.full)
    }

    @Test
    fun feedThenColumnsFlattenLeft() {
        val ops = render(PrintElement.Feed(3), PrintElement.Columns(listOf(Column("A"), Column("B"))))
        assertEquals(3, (ops[0] as IminOp.Feed).lines)
        assertTrue(ops.drop(1).all { it is IminOp.Text && it.align == 0 })
    }
}
