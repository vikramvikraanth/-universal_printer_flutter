package com.universalprinter.image

import com.universalprinter.model.Align
import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintElement
import com.universalprinter.model.printDocument
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageResolverTest {

    @Test
    fun failedUrlIsDroppedAndTheRestIsKept() = runTest {
        val doc = printDocument(PaperWidth.MM_80) {
            text("HEADER")
            imageUrl("https://x/logo.png")
            text("FOOTER")
        }
        val out = ImageResolver.resolve(doc) { null } // loader always fails
        assertEquals(2, out.elements.size)
        assertTrue(out.elements.all { it is PrintElement.Text })
        assertEquals("HEADER", (out.elements[0] as PrintElement.Text).text)
        assertEquals("FOOTER", (out.elements[1] as PrintElement.Text).text)
    }

    @Test
    fun documentWithNoImageUrlIsReturnedUnchanged() = runTest {
        val doc = printDocument(PaperWidth.MM_80) { text("x") }
        assertSame(doc, ImageResolver.resolve(doc) { null })
    }

    @Test
    fun urlsListsAllImageUrlTargets() {
        val doc = printDocument(PaperWidth.MM_80) {
            imageUrl("https://a/1.png")
            text("mid")
            imageUrl("https://b/2.png", align = Align.RIGHT)
        }
        assertEquals(listOf("https://a/1.png", "https://b/2.png"), ImageResolver.urls(doc))
    }

    @Test
    fun dslImageUrlCarriesFields() {
        val doc = printDocument(PaperWidth.MM_80) { imageUrl("u", align = Align.RIGHT, invert = true, dither = true) }
        val e = doc.elements.single() as PrintElement.ImageUrl
        assertEquals("u", e.url)
        assertEquals(Align.RIGHT, e.align)
        assertTrue(e.invert && e.dither)
    }
}
