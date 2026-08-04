package com.universalprinter.queue

import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintErrorReason
import com.universalprinter.model.PrintResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Per-printer FIFO print queue. A single worker coroutine drains an unbounded channel, so jobs
 * submitted to the SAME printer run one-at-a-time in order (no interleaved/garbled output), while
 * different printers — each with their own PrintQueue — run concurrently.
 *
 * [submit] suspends until that job has actually printed (its result is delivered via a
 * [CompletableDeferred]). A worker exception is isolated to its own job as [PrintResult.Error].
 *
 * If [jobTimeoutMs] is non-null, each job is bounded by that budget: a backend call that overruns is
 * abandoned, the caller gets a [PrintErrorReason.TIMEOUT] error, and the queue advances to the next
 * job. Note that a *blocking* (non-suspending) backend call can only be truly interrupted when a
 * spare dispatcher thread is available — with the default [kotlinx.coroutines.Dispatchers.IO] this
 * holds; on a single-threaded dispatcher the overrunning call still occupies the one thread.
 */
class PrintQueue(
    scope: CoroutineScope,
    private val jobTimeoutMs: Long? = null,
    private val worker: suspend (PrintDocument) -> PrintResult,
) {
    private class QueuedJob(val document: PrintDocument, val result: CompletableDeferred<PrintResult>)

    private val channel = Channel<QueuedJob>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (job in channel) {
                job.result.complete(runJob(job.document))
            }
        }
    }

    private suspend fun CoroutineScope.runJob(document: PrintDocument): PrintResult {
        // Run the job as a child coroutine. worker() is wrapped so it never throws, which means the
        // async can't cancel the worker loop; on timeout we abandon it and move on.
        val work = async { runCatching { worker(document) }.getOrElse { asError(it) } }
        val timeout = jobTimeoutMs ?: return work.await()
        return withTimeoutOrNull(timeout) { work.await() } ?: run {
            work.cancel()
            PrintResult.Error("print job timed out after ${timeout}ms", reason = PrintErrorReason.TIMEOUT)
        }
    }

    private fun asError(t: Throwable): PrintResult = PrintResult.Error(t.message ?: "print failed", t)

    suspend fun submit(document: PrintDocument): PrintResult {
        val deferred = CompletableDeferred<PrintResult>()
        val queued = QueuedJob(document, deferred)
        val sent = channel.trySend(queued)
        if (sent.isClosed) return PrintResult.Error("printer is closed", reason = PrintErrorReason.NOT_CONNECTED)
        return deferred.await()
    }

    fun close() {
        channel.close()
    }
}
