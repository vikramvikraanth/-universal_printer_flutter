package com.universalprinter.escpos

import com.universalprinter.model.Align
import com.universalprinter.model.BarcodeSymbology
import com.universalprinter.model.PaperWidth
import com.universalprinter.model.printDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class EscPosRendererTest {

    @Test
    fun rendersElementsToDantSuFormattedText() {
        val doc = printDocument(PaperWidth.MM_58) {
            text("Title", align = Align.CENTER, bold = true)
            divider()
            text("Item")
            barcode("12345", BarcodeSymbology.CODE128, heightDots = 80)
            qr("q1") // default sizeDots = 200 -> size 20
            feed(1)
        }

        val expected = buildString {
            append("[C]<b>Title</b>\n")
            append("[L]").append("-".repeat(32)).append('\n')
            append("[L]Item\n")
            append("[C]<barcode type='128' height='10'>12345</barcode>\n")
            append("[C]<qrcode size='20'>q1</qrcode>\n")
            append("\n")
        }

        assertEquals(expected, EscPosRenderer.render(doc) { "HEX" })
    }

    @Test
    fun textSizeAndUnderlineWrapCorrectly() {
        val doc = printDocument(PaperWidth.MM_80) {
            text("big", size = com.universalprinter.model.TextSize.LARGE, underline = true, align = Align.RIGHT)
        }
        assertEquals("[R]<u><font size='big'>big</font></u>\n", EscPosRenderer.render(doc) { "HEX" })
    }

    @Test
    fun invertedTextUsesBgBlack() {
        val doc = printDocument(PaperWidth.MM_80) { text("HDR", align = Align.CENTER, invert = true) }
        assertEquals("[C]<font color='bg-black'>HDR</font>\n", EscPosRenderer.render(doc) { "HEX" })
    }

    @Test
    fun columnsRowRendersAsLeftAlignedMonospaceLines() {
        val doc = printDocument(PaperWidth.MM_80) { row("Coffee", "3.50") }
        val expected = "[L]" + "Coffee".padEnd(24) + "3.50".padStart(24) + "\n"
        assertEquals(expected, EscPosRenderer.render(doc) { "HEX" })
    }
}
