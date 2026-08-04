package com.universalprinter.model

/** Result of a pre-flight status check run before a job. */
sealed interface PreflightResult {
    /** Printing may proceed; [warnings] (e.g. paper near-end) are attached to the eventual success. */
    data class Proceed(val warnings: List<PrinterWarning> = emptyList()) : PreflightResult

    /** Printing must not start; the job fails fast with [reason]/[message]. */
    data class Block(val reason: PrintErrorReason, val message: String) : PreflightResult
}
