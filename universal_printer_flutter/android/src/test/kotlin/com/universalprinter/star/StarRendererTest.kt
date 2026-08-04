package com.universalprinter.star

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

class StarRendererTest {

    private fun render(vararg e: PrintElement, cut: CutType = CutType.NONE) =
        StarRenderer.render(PrintDocument(e.toList(), PaperWidth.MM_80, cut, false))

    private fun mag(size: TextSize): Pair<Int, Int> {
        val t = render(PrintElement.Text("x", size = size)).single() as StarOp.Text
        return t.widthMagnification to t.heightMagnification
    }

    @Test
    fun magnificationPerSize() {
        assertEquals(1 to 1, mag(TextSize.NORMAL))
        assertEquals(2 to 1, mag(TextSize.WIDE))
        assertEquals(1 to 2, mag(TextSize.TALL))
        assertEquals(2 to 2, mag(TextSize.LARGE))
    }

    @Test
    fun barcodeHeightConvertedToMillimetres() {
        val bc = render(PrintElement.Barcode("1", BarcodeSymbology.CODE128, heightDots = 100)).single() as StarOp.Barcode
        assertEquals(12.5, bc.heightMm, 0.0001) // 100 / 8.0
        assertEquals(BarcodeSymbology.CODE128, bc.symbology)
    }

    @Test
    fun qrCellSizeResolvedAndClampedTo8() {
        val qr = render(PrintElement.QrCode("q", sizeDots = 200, errorLevel = QrErrorLevel.Q)).single() as StarOp.QrCode
        assertEquals(8, qr.cellSize) // 200 / 25 = 8
        assertEquals(QrErrorLevel.Q, qr.level)
        val big = render(PrintElement.QrCode("q", sizeDots = 1000)).single() as StarOp.QrCode
        assertEquals(8, big.cellSize) // 1000 / 25 = 40 -> clamped to 8
    }

    @Test
    fun columnsAreLeftUnstyledMagnificationOne() {
        val ops = render(PrintElement.Columns(listOf(Column("A"), Column("B"))))
        ops.forEach { assertTrue(it is StarOp.Text && it.align == Align.LEFT && it.widthMagnification == 1 && it.heightMagnification == 1) }
    }

    @Test
    fun rawIsDroppedAndCutCarriesFullFlag() {
        val ops = render(PrintElement.Raw(byteArrayOf(1, 2)), PrintElement.Text("x"), cut = CutType.FULL)
        assertEquals(2, ops.size) // Raw produced no op
        assertTrue(ops[0] is StarOp.Text)
        assertTrue((ops[1] as StarOp.Cut).full)
    }
}
