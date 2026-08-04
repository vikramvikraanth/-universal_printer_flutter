package com.universalprinter

import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PreflightResult
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement
import com.universalprinter.model.PrintErrorReason
import com.universalprinter.model.PrintResult
import com.universalprinter.model.PrinterWarning
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueuedPrinterPreflightTest {

    private class Fake(
        dispatcher: CoroutineDispatcher,
        private val pf: PreflightResult,
        preflightEnabled: Boolean = true,
        val printed: AtomicInteger = AtomicInteger(0),
    ) : QueuedPrinter(dispatcher, preflightEnabled = preflightEnabled) {
        override val name = "fake"
        override suspend fun doConnect() = true
        override suspend fun preflight(document: PrintDocument) = pf
        override suspend fun doPrint(document: PrintDocument): PrintResult {
            printed.incrementAndGet()
            return PrintResult.Success()
        }
    }

    private fun doc() = PrintDocument(listOf(PrintElement.Text("hi")), PaperWidth.MM_80)

    @Test
    fun blockPreflightReturnsErrorAndNeverPrints() = runTest {
        val fake = Fake(StandardTestDispatcher(testScheduler), PreflightResult.Block(PrintErrorReason.COVER_OPEN, "cover open"))
        val result = fake.print(doc())
        assertTrue(result is PrintResult.Error)
        assertEquals(PrintErrorReason.COVER_OPEN, (result as PrintResult.Error).reason)
        assertEquals(0, fake.printed.get())
    }

    @Test
    fun nearEndWarningIsAttachedToSuccess() = runTest {
        val fake = Fake(StandardTestDispatcher(testScheduler), PreflightResult.Proceed(listOf(PrinterWarning.PAPER_NEAR_END)))
        val result = fake.print(doc())
        assertTrue(result is PrintResult.Success)
        assertEquals(listOf(PrinterWarning.PAPER_NEAR_END), (result as PrintResult.Success).warnings)
        assertEquals(1, fake.printed.get())
    }

    @Test
    fun disabledPreflightSkipsTheCheckAndPrints() = runTest {
        val fake = Fake(StandardTestDispatcher(testScheduler), PreflightResult.Block(PrintErrorReason.PAPER_OUT, "out"), preflightEnabled = false)
        val result = fake.print(doc())
        assertTrue(result is PrintResult.Success)
        assertEquals(1, fake.printed.get())
    }
}
