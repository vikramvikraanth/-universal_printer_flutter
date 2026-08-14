package com.universalprinter.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintDocumentTest {

    // ---- textOnly() (impact / 9-pin: no raster) ----

    @Test
    fun textOnlyDropsImagesAndRendersCodesAsTheirData() {
        val doc = PrintDocument(
            listOf(
                PrintElement.Text("Header"),
                PrintElement.ImageUrl("https://logo.png"),                       // dropped (same when-arm as Image)
                PrintElement.Barcode("012345678905", align = Align.LEFT),        // -> Text(data)
                PrintElement.QrCode("order:42", align = Align.RIGHT),            // -> Text(data)
                PrintElement.Feed(2),
            ),
            PaperWidth.IMPACT_76,
        )

        val out = doc.textOnly().elements

        // image is gone; barcode/qr collapsed to text; text/feed preserved in order.
        assertEquals(4, out.size)
        assertEquals(PrintElement.Text("Header"), out[0])
        assertEquals(PrintElement.Text("012345678905", align = Align.LEFT), out[1])
        assertEquals(PrintElement.Text("order:42", align = Align.RIGHT), out[2])
        assertEquals(PrintElement.Feed(2), out[3])
    }

    @Test
    fun textOnlyKeepsCodeAlignmentWhenCollapsingToText() {
        val out = PrintDocument(listOf(PrintElement.QrCode("id", align = Align.CENTER)), PaperWidth.IMPACT_76)
            .textOnly().elements
        val text = out.single() as PrintElement.Text
        assertEquals("id", text.text)
        assertEquals(Align.CENTER, text.align)
    }

    @Test
    fun textOnlyReturnsSameInstanceWhenNoGraphics() {
        val doc = PrintDocument(
            listOf(PrintElement.Text("A"), PrintElement.Divider, PrintElement.Feed(1)),
            PaperWidth.IMPACT_76,
        )
        assertSame(doc, doc.textOnly()) // fast path — nothing to strip
    }

    @Test
    fun textOnlyPreservesDocumentSettings() {
        val doc = PrintDocument(
            listOf(PrintElement.Barcode("x")),
            PaperWidth.IMPACT_76,
            cut = CutType.FULL,
            openDrawer = true,
        )
        val out = doc.textOnly()
        assertEquals(PaperWidth.IMPACT_76, out.paper)
        assertEquals(CutType.FULL, out.cut)
        assertTrue(out.openDrawer)
    }
}
