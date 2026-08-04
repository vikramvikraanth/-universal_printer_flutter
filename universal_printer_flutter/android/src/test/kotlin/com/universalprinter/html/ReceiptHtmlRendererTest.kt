package com.universalprinter.html

import com.universalprinter.model.Align
import com.universalprinter.model.BarcodeSymbology
import com.universalprinter.model.Column
import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintDocumentBuilder
import com.universalprinter.model.PrintElement
import com.universalprinter.model.TextSize
import com.universalprinter.model.printDocument
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptHtmlRendererTest {

    // Fake encoders — no Android/ZXing needed; the renderer just splices these strings in.
    private fun render(paper: PaperWidth = PaperWidth.MM_80, block: PrintDocumentBuilder.() -> Unit): String =
        ReceiptHtmlRenderer.render(printDocument(paper, block), imageEncoder = { "IMG" }, codeEncoder = { "CODE" })

    @Test
    fun centerTextIsCenteredAndHtmlEscaped() {
        val html = render { text("A & B <x>", align = Align.CENTER) }
        assertTrue(html.contains("text-align:center"))
        assertTrue(html.contains("A &amp; B &lt;x&gt;"))
    }

    @Test
    fun boldUnderlineInvertAndSizeWrap() {
        val html = render { text("H", bold = true, underline = true, invert = true, size = TextSize.LARGE) }
        assertTrue(html.contains("<b>"))
        assertTrue(html.contains("<u>"))
        assertTrue(html.contains("class=\"inv\""))
        assertTrue(html.contains("scale(2)"))
    }

    @Test
    fun columnsAreFlexWithPerCellWeightAndAlign() {
        val html = render { columns(Column("L", 1, Align.LEFT), Column("R", 2, Align.RIGHT)) }
        assertTrue(html.contains("display:flex"))
        assertTrue(html.contains("flex:1;text-align:left"))
        assertTrue(html.contains("flex:2;text-align:right"))
    }

    @Test
    fun dividerAndFeedRender() {
        val html = render { divider(); feed(2) }
        assertTrue(html.contains("class=\"divider\""))
        assertTrue(html.contains("height:2em"))
    }

    @Test
    fun barcodeAndQrUseTheInjectedCodeEncoder() {
        val html = render {
            barcode("12345", BarcodeSymbology.CODE128)
            qr("https://x/1", align = Align.CENTER)
        }
        assertTrue(html.contains("<img src=\"CODE\">"))
        assertTrue(html.contains("text-align:center"))
    }

    @Test
    fun paperWidthDrivesViewportWidth() {
        assertTrue(render(PaperWidth.MM_58) { text("x") }.contains("content=\"width=384\""))
        assertTrue(render(PaperWidth.MM_80) { text("x") }.contains("content=\"width=576\""))
    }

    @Test
    fun rawElementIsOmittedFromHtml() {
        val doc = PrintDocument(listOf(PrintElement.Raw(byteArrayOf(1, 2, 3))), PaperWidth.MM_80)
        val html = ReceiptHtmlRenderer.render(doc, { "IMG" }, { "CODE" })
        assertTrue(html.contains("<body></body>"))
    }
}
