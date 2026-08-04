package com.universalprinter

import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement
import com.universalprinter.model.PrintErrorReason
import com.universalprinter.model.PrintResult
import com.universalprinter.model.PrinterWarning
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrintCallbackTest {

    private class FakePrinter(private val result: PrintResult) : Printer {
        override val name = "fake"
        override suspend fun connect() = true
        override suspend fun print(document: PrintDocument) = result
        override fun close() {}
    }

    private fun doc() = PrintDocument(listOf(PrintElement.Text("hi")), PaperWidth.MM_80)

    private class Recorder : PrintCallback {
        val warnings = mutableListOf<PrinterWarning>()
        var success = false
        var error: PrintResult.Error? = null
        override fun onWarning(warning: PrinterWarning) { warnings += warning }
        override fun onSuccess() { success = true }
        override fun onError(error: PrintResult.Error) { this.error = error }
    }

    @Test
    fun callbackDeliversWarningsThenSuccess() = runTest {
        val rec = Recorder()
        FakePrinter(PrintResult.Success(listOf(PrinterWarning.PAPER_NEAR_END))).print(doc(), this, rec).join()
        assertEquals(listOf(PrinterWarning.PAPER_NEAR_END), rec.warnings)
        assertTrue(rec.success)
        assertNull(rec.error)
    }

    @Test
    fun callbackDeliversErrorAndNotSuccess() = runTest {
        val rec = Recorder()
        FakePrinter(PrintResult.Error("cover open", reason = PrintErrorReason.COVER_OPEN)).print(doc(), this, rec).join()
        assertEquals(PrintErrorReason.COVER_OPEN, rec.error?.reason)
        assertTrue(!rec.success)
        assertTrue(rec.warnings.isEmpty())
    }

    @Test
    fun printOrThrowReturnsWarningsOnSuccess() = runTest {
        val warnings = FakePrinter(PrintResult.Success(listOf(PrinterWarning.PAPER_NEAR_END))).printOrThrow(doc())
        assertEquals(listOf(PrinterWarning.PAPER_NEAR_END), warnings)
    }

    @Test
    fun printOrThrowThrowsTypedExceptionOnError() = runTest {
        val thrown = runCatching {
            FakePrinter(PrintResult.Error("out of paper", reason = PrintErrorReason.PAPER_OUT)).printOrThrow(doc())
        }.exceptionOrNull()
        assertTrue(thrown is PrintException)
        assertEquals(PrintErrorReason.PAPER_OUT, (thrown as PrintException).reason)
    }
}
