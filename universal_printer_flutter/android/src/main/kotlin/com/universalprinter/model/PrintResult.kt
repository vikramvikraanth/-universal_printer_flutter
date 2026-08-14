package com.universalprinter.model

/** Outcome of a print job. */
sealed interface PrintResult {
    /** The job printed. [warnings] are non-fatal conditions surfaced to the caller (e.g. paper near-end,
     *  where printing continues but the operator should be alerted). */
    data class Success(val warnings: List<PrinterWarning> = emptyList()) : PrintResult

    /**
     * A failed job. [reason] categorizes the failure so callers can react (retry vs prompt the
     * operator vs reload paper); [message]/[cause] carry the detail.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val reason: PrintErrorReason = PrintErrorReason.UNKNOWN,
    ) : PrintResult
}

/** Category of a [PrintResult.Error], for programmatic handling by the caller. */
enum class PrintErrorReason {
    /** The job exceeded its time budget (connect, write, or the overall job timeout). */
    TIMEOUT,

    /** The printer could not be reached — connection refused, dropped, or service unavailable. */
    NOT_CONNECTED,

    /** The printer is out of paper. */
    PAPER_OUT,

    /** The printer cover / roll door is open. */
    COVER_OPEN,

    /** The auto-cutter reported an error / jam. */
    CUTTER_ERROR,

    /** Paper is jammed in the mechanism. */
    PAPER_JAM,

    /** The print head / printer is over-temperature. */
    OVERHEATED,

    /** A previous receipt is still held at the outlet and must be removed before printing. */
    HOLDING_PAPER,

    /** Runtime permission for the device was denied (e.g. USB). */
    PERMISSION_DENIED,

    /** The document contains content the printer/renderer can't encode. */
    CONTENT_INVALID,

    /** The operation isn't supported on this backend. */
    UNSUPPORTED,

    /** Lower-level I/O failure. */
    IO,

    /** Unclassified failure. */
    UNKNOWN,
}

/** A non-fatal condition reported alongside a successful print. */
enum class PrinterWarning {
    /** Paper is near the end of the roll — printing continued; alert the operator to reload soon. */
    PAPER_NEAR_END,
}

/**
 * Maps a [PrintErrorReason]/[PrinterWarning] to a clear, **user-facing** message the app can show as-is.
 * Actionable faults (paper/cover/cutter/connection/permission) get a specific instruction; internal or
 * technical failures (content/unsupported/io/unknown) get the [GENERIC] message — the app should still
 * log the accompanying technical `details` from [PrintResult.Error.message]. Pure, unit-testable.
 */
object PrinterMessages {

    /** Shown when the failure isn't something the operator can directly act on. */
    const val GENERIC = "Printing failed. Please try again. If the problem continues, contact support."

    fun userMessage(reason: PrintErrorReason): String = when (reason) {
        PrintErrorReason.PAPER_OUT -> "The printer is out of paper. Load paper and try again."
        PrintErrorReason.COVER_OPEN -> "The printer cover is open. Close it and try again."
        PrintErrorReason.CUTTER_ERROR -> "The paper cutter is jammed. Clear the jam and try again."
        PrintErrorReason.PAPER_JAM -> "Paper is jammed. Clear the jam and try again."
        PrintErrorReason.OVERHEATED -> "The printer is overheating. Let it cool for a minute and try again."
        PrintErrorReason.HOLDING_PAPER -> "Please remove the printed receipt, then try again."
        PrintErrorReason.NOT_CONNECTED -> "Can't reach the printer. Check it's powered on and connected."
        PrintErrorReason.TIMEOUT -> "The printer isn't responding. Please try again."
        PrintErrorReason.PERMISSION_DENIED -> "Permission to use the printer was denied. Grant access and try again."
        // Technical / internal — not user-understandable → generic (details still sent to the app).
        PrintErrorReason.CONTENT_INVALID,
        PrintErrorReason.UNSUPPORTED,
        PrintErrorReason.IO,
        PrintErrorReason.UNKNOWN -> GENERIC
    }

    fun warningMessage(warning: PrinterWarning): String = when (warning) {
        PrinterWarning.PAPER_NEAR_END -> "Paper is running low — please replace the roll soon."
    }
}
