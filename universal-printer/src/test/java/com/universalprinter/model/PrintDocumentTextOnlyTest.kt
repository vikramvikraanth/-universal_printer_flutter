package com.universalprinter.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintDocumentTextOnlyTest {

    @Test
    fun stripsImagesAndRendersCodesAsText() {
        val doc = printDocument(PaperWidth.IMPACT_76) {
            imageUrl("https://logo.example/l.png")
            text("HEADER", align = Align.CENTER)
            row("Coffee", "3.50")
            barcode("123456789012", align = Align.LEFT)
            qr("https://example.com/r/1", align = Align.CENTER)
            feed(2)
        }

        val out = doc.textOnly()

        // No graphics survive.
        assertTrue(out.elements.none {
            it is PrintElement.Image || it is PrintElement.ImageUrl ||
                it is PrintElement.Barcode || it is PrintElement.QrCode
        })
        // Barcode/QR become their data as text (preserving alignment).
        val texts = out.elements.filterIsInstance<PrintElement.Text>().map { it.text }
        assertTrue(texts.contains("123456789012"))
        assertTrue(texts.contains("https://example.com/r/1"))
        // Text / columns / feed are preserved.
        assertTrue(out.elements.any { it is PrintElement.Columns })
        assertTrue(out.elements.any { it is PrintElement.Feed })
    }

    @Test
    fun returnsSameInstanceWhenNoGraphics() {
        val doc = printDocument(PaperWidth.IMPACT_76) {
            text("HELLO")
            row("A", "B")
            feed(1)
        }
        assertSame(doc, doc.textOnly())
    }

    @Test
    fun barcodeAlignmentIsCarriedToText() {
        val doc = printDocument(PaperWidth.IMPACT_76) { barcode("X99", align = Align.RIGHT) }
        val t = doc.textOnly().elements.filterIsInstance<PrintElement.Text>().single()
        assertEquals(Align.RIGHT, t.align)
    }
}
