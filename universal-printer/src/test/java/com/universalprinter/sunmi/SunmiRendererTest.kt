package com.universalprinter.sunmi

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

class SunmiRendererTest {

    private fun render(vararg e: PrintElement, cut: CutType = CutType.NONE, drawer: Boolean = false) =
        SunmiRenderer.render(PrintDocument(e.toList(), PaperWidth.MM_80, cut, drawer))

    @Test
    fun textResolvesAlignStyleAndLargeSizeFlags() {
        val t = render(PrintElement.Text("Hi", Align.RIGHT, bold = true, underline = true, invert = true, size = TextSize.LARGE))
            .single() as SunmiOp.Text
        assertEquals("Hi\n", t.text)
        assertEquals(2, t.align)
        assertTrue(t.bold && t.underline && t.invert)
        assertTrue(t.doubleWidth && t.doubleHeight)
    }

    @Test
    fun wideIsWidthOnlyTallIsHeightOnly() {
        val wide = render(PrintElement.Text("w", size = TextSize.WIDE)).single() as SunmiOp.Text
        val tall = render(PrintElement.Text("t", size = TextSize.TALL)).single() as SunmiOp.Text
        assertTrue(wide.doubleWidth && !wide.doubleHeight)
        assertTrue(!tall.doubleWidth && tall.doubleHeight)
    }

    @Test
    fun columnsFlattenToLeftAlignedUnstyledLines() {
        val ops = render(PrintElement.Columns(listOf(Column("Coffee", 1, Align.LEFT), Column("3.50", 1, Align.RIGHT))))
        assertTrue(ops.isNotEmpty())
        ops.forEach { assertTrue(it is SunmiOp.Text && it.align == 0 && !it.bold && !it.invert && !it.doubleWidth) }
        assertTrue((ops[0] as SunmiOp.Text).text.startsWith("Coffee"))
    }

    @Test
    fun barcodeAndQrResolveSunmiCodes() {
        val bc = render(PrintElement.Barcode("123", BarcodeSymbology.EAN13, heightDots = 80)).single() as SunmiOp.Barcode
        assertEquals(2, bc.symbology) // Sunmi EAN13 = 2
        assertEquals(80, bc.height)
        val qr = render(PrintElement.QrCode("q", sizeDots = 200, errorLevel = QrErrorLevel.H)).single() as SunmiOp.QrCode
        assertEquals(8, qr.moduleSize) // 200/24 = 8
        assertEquals(3, qr.level) // H = 3
    }

    @Test
    fun cutAndDrawerAppendedInOrder() {
        val ops = render(PrintElement.Text("x"), cut = CutType.FULL, drawer = true)
        assertEquals(SunmiOp.Cut, ops[ops.size - 2])
        assertEquals(SunmiOp.OpenDrawer, ops.last())
    }

    @Test
    fun noCutOpWhenCutTypeNone() {
        assertTrue(render(PrintElement.Text("x"), cut = CutType.NONE).none { it is SunmiOp.Cut })
    }
}
