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
