package com.universalprinter

import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement
import com.universalprinter.model.PrintResult
import com.universalprinter.model.PrintType
import kotlinx.coroutines.test.runTest
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
            PrintType.TEXT, original,
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
            PrintType.IMAGE, original,
            print = { printed = it; PrintResult.Success() },
            toImageDoc = { image },
        )
        assertSame(image, printed)
    }
}
