package com.universalprinter

import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintErrorReason
import com.universalprinter.model.PrintResult
import com.universalprinter.model.PrinterWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Callback for a print job, for callers who don't want to handle [PrintResult] themselves. Every
 * method has a no-op default, so implement only what you need. Non-fatal messages (e.g. paper
 * near-end) arrive on [onWarning] — separate from [onSuccess]/[onError].
 *
 * Callbacks run on the [CoroutineScope] passed to [print]; use a main-dispatcher scope for UI work.
 */
interface PrintCallback {
    /** A non-fatal condition (e.g. [PrinterWarning.PAPER_NEAR_END]); fired before [onSuccess], once per warning. */
    fun onWarning(warning: PrinterWarning) {}

    /** The job printed successfully. */
    fun onSuccess() {}

    /** The job failed. */
    fun onError(error: PrintResult.Error) {}
}

/**
 * Fire-and-forget print: runs the job in [scope] and dispatches the outcome to [callback]
 * (warnings first, then success; or error). Returns the [Job] so the caller can cancel/await.
 */
fun Printer.print(document: PrintDocument, scope: CoroutineScope, callback: PrintCallback): Job =
    scope.launch {
        when (val result = print(document)) {
            is PrintResult.Success -> {
                result.warnings.forEach(callback::onWarning)
                callback.onSuccess()
            }
            is PrintResult.Error -> callback.onError(result)
        }
    }

/** Thrown by [printOrThrow] when a job fails. */
class PrintException(
    val reason: PrintErrorReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Exception-style print: returns the (possibly empty) list of non-fatal [PrinterWarning]s on success,
 * or throws [PrintException] on failure. For callers who prefer try/catch over [PrintResult].
 */
suspend fun Printer.printOrThrow(document: PrintDocument): List<PrinterWarning> =
    when (val result = print(document)) {
        is PrintResult.Success -> result.warnings
        is PrintResult.Error -> throw PrintException(result.reason, result.message, result.cause)
    }
