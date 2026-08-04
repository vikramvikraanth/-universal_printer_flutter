package com.universalprinter.queue

import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement
import com.universalprinter.model.PrintErrorReason
import com.universalprinter.model.PrintResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintQueueTest {

    private fun doc(tag: String) = PrintDocument(listOf(PrintElement.Text(tag)), PaperWidth.MM_80)
    private fun tagOf(d: PrintDocument) = (d.elements[0] as PrintElement.Text).text

    @Test
    fun neverRunsTwoJobsConcurrently() = runTest {
        var running = 0
        var maxConcurrent = 0
        val queue = PrintQueue(backgroundScope) {
            running++; maxConcurrent = maxOf(maxConcurrent, running)
            delay(10)
            running--
            PrintResult.Success()
        }
        (1..10).map { async { queue.submit(doc("$it")) } }.awaitAll()
        assertEquals(1, maxConcurrent)
    }

    @Test
    fun processesJobsInSubmissionOrder() = runTest {
        val order = mutableListOf<String>()
        val queue = PrintQueue(backgroundScope) { order.add(tagOf(it)); delay(5); PrintResult.Success() }
        (1..5).map { i -> async { queue.submit(doc("$i")) } }.awaitAll()
        assertEquals(listOf("1", "2", "3", "4", "5"), order)
    }

    @Test
    fun aFailedJobIsIsolatedAndTheQueueKeepsGoing() = runTest {
        val queue = PrintQueue(backgroundScope) {
            if (tagOf(it) == "boom") throw RuntimeException("kaboom") else PrintResult.Success()
        }
        val bad = queue.submit(doc("boom"))
        val good = queue.submit(doc("ok"))
        assertTrue(bad is PrintResult.Error)
        assertTrue(good is PrintResult.Success)
    }

    @Test
    fun submitAfterCloseReturnsError() = runTest {
        val queue = PrintQueue(backgroundScope) { PrintResult.Success() }
        queue.close()
        assertTrue(queue.submit(doc("x")) is PrintResult.Error)
    }

    @Test
    fun anOverrunningJobTimesOutAndTheQueueKeepsGoing() = runTest {
        val queue = PrintQueue(backgroundScope, jobTimeoutMs = 1_000) {
            if (tagOf(it) == "slow") { delay(10_000); PrintResult.Success() } else PrintResult.Success()
        }
        val slow = queue.submit(doc("slow"))
        val fast = queue.submit(doc("fast"))
        assertTrue(slow is PrintResult.Error)
        assertEquals(PrintErrorReason.TIMEOUT, (slow as PrintResult.Error).reason)
        assertTrue(fast is PrintResult.Success)
    }
}
