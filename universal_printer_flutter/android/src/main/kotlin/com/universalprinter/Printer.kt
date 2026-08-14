package com.universalprinter

import com.universalprinter.model.PreflightResult
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintResult
import com.universalprinter.model.PrinterWarning
import com.universalprinter.model.textOnly
import com.universalprinter.queue.PrintQueue
import com.universalprinter.text.ReceiptRasterization
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** A print target. Every implementation serializes jobs through its own [PrintQueue]. */
interface Printer {
    val name: String

    /** Physical paper width (mm) if the printer knows it (e.g. from discovery). When set, it drives the
     *  render width — `printReceipt` re-paginates the document to this paper. Null = use the document's paper. */
    val paperWidthMm: Int? get() = null

    /** True for 9-pin **impact / dot-matrix** printers (Epson TM-U*, Star SP700/SP742, Bixolon SRP-27x).
     *  These can't raster, so every job is forced text-only (images dropped, barcode/QR rendered as their
     *  data string). Set at construction from the discovered `isImpact` flag. Default false = full graphics. */
    val isImpact: Boolean get() = false

    /** Establish/verify the connection. Returns false if the printer can't be reached. */
    suspend fun connect(): Boolean

    /** Enqueue a document; suspends until it has printed. Same-printer jobs run FIFO. */
    suspend fun print(document: PrintDocument): PrintResult

    /** Release resources (stops the queue, closes the connection). */
    fun close()
}

/**
 * Base class that wires the per-printer [PrintQueue]. Backends implement [doConnect]/[doPrint]/
 * [doClose] and never touch the queue directly — [print] always routes through it.
 */
abstract class QueuedPrinter(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    jobTimeoutMs: Long? = DEFAULT_JOB_TIMEOUT_MS,
    private val preflightEnabled: Boolean = true,
) : Printer {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val queue = PrintQueue(scope, jobTimeoutMs) { runJob(it) }

    final override suspend fun print(document: PrintDocument): PrintResult = queue.submit(document)

    final override suspend fun connect(): Boolean = doConnect()

    final override fun close() {
        queue.close()
        runCatching { doClose() }
    }

    private suspend fun runJob(document: PrintDocument): PrintResult {
        if (preflightEnabled) {
            when (val pf = preflight(document)) {
                is PreflightResult.Block -> return PrintResult.Error(pf.message, reason = pf.reason)
                is PreflightResult.Proceed -> return withWarnings(printRendered(document), pf.warnings)
            }
        }
        return printRendered(document)
    }

    // Impact (9-pin) printers can't raster at all → force text-only and skip rasterization (which would
    // re-introduce images from non-Latin text). Everyone else: rasterize non-Latin text to images (per the
    // document's RenderMode) before the backend renders — transport-agnostic, so every backend gets i18n.
    private suspend fun printRendered(document: PrintDocument): PrintResult =
        doPrint(if (isImpact) document.textOnly() else ReceiptRasterization.apply(document))

    private fun withWarnings(result: PrintResult, warnings: List<PrinterWarning>): PrintResult =
        if (warnings.isNotEmpty() && result is PrintResult.Success) PrintResult.Success(result.warnings + warnings) else result

    protected abstract suspend fun doConnect(): Boolean

    /** Perform the actual print. Called serially by the queue worker — never concurrently. */
    protected abstract suspend fun doPrint(document: PrintDocument): PrintResult

    /** Optional pre-flight status check. Default = proceed (no check). Overridden by backends whose
     *  status can be read out-of-band (ESC/POS network, Sunmi). Gated by `preflightEnabled`. */
    protected open suspend fun preflight(document: PrintDocument): PreflightResult = PreflightResult.Proceed()

    protected open fun doClose() {}

    companion object {
        /** Default overall per-job time budget. A job that overruns yields a TIMEOUT error. */
        const val DEFAULT_JOB_TIMEOUT_MS = 30_000L
    }
}
