package com.universalprinter

import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement
import com.universalprinter.model.PrintResult
import com.universalprinter.model.PrintType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptPrintingTest {

    private fun doc(tag: String) = PrintDocument(listOf(PrintElement.Text(tag)), PaperWidth.MM_80)

    @Test
    fun textTypePrintsTheOriginalDocumentAndNeverRasterizes() = runTest {
        val original = doc("orig")
        var printed: PrintDocument? = null
        var rasterCalled = false
        routePrint(
            PrintType.TEXT, isImpact = false, original,
            print = { printed = it; PrintResult.Success() },
            toImageDoc = { rasterCalled = true; doc("image") },
        )
        assertSame(original, printed)
        assertTrue(!rasterCalled)
    }

    @Test
    fun imageTypePrintsTheRasterizedDocument() = runTest {
        val original = doc("orig")
        val image = doc("image")
        var printed: PrintDocument? = null
        routePrint(
            PrintType.IMAGE, isImpact = false, original,
            print = { printed = it; PrintResult.Success() },
            toImageDoc = { image },
        )
        assertSame(image, printed)
    }

    @Test
    fun impactAwarePaperUsesImpactWidthRegardlessOfMillimeters() {
        // Impact is always the 33-char IMPACT_76 class — even when discovery reports 76 or 80 mm
        // (ofMillimeters has no impact case and would otherwise give the 48-char MM_80).
        assertEquals(PaperWidth.IMPACT_76, impactAwarePaper(isImpact = true, paperWidthMm = 76))
        assertEquals(PaperWidth.IMPACT_76, impactAwarePaper(isImpact = true, paperWidthMm = 80))
        assertEquals(PaperWidth.IMPACT_76, impactAwarePaper(isImpact = true, paperWidthMm = null))
        // Non-impact: honor the discovered width, or leave the document's paper (null) when unknown.
        assertEquals(PaperWidth.MM_58, impactAwarePaper(isImpact = false, paperWidthMm = 58))
        assertEquals(PaperWidth.MM_80, impactAwarePaper(isImpact = false, paperWidthMm = 80))
        assertNull(impactAwarePaper(isImpact = false, paperWidthMm = null))
    }

    @Test
    fun impactCoercesImageTypeToTextAndNeverRasterizes() = runTest {
        // Regression: impact + IMAGE used to rasterize into a single-image doc that the backend's
        // text-only pass then stripped to nothing (blank receipt). Impact must stay on the text path.
        val original = doc("orig")
        var printed: PrintDocument? = null
        var rasterCalled = false
        routePrint(
            PrintType.IMAGE, isImpact = true, original,
            print = { printed = it; PrintResult.Success() },
            toImageDoc = { rasterCalled = true; doc("image") },
        )
        assertSame(original, printed)
        assertTrue(!rasterCalled)
    }
}
