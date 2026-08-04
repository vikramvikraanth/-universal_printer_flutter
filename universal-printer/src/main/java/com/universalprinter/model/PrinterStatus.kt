package com.universalprinter.model

/** Roll-paper state from a live status query. */
enum class PaperState { OK, NEAR_END, NOT_PRESENT }

/**
 * A live snapshot of a printer's state (from an ESC/POS `DLE EOT` real-time query). Lets a caller
 * know a receipt can actually print — turning "bytes sent" into "printable" — instead of discovering
 * paper-out only after the job.
 */
data class PrinterStatus(
    val online: Boolean,
    val coverOpen: Boolean,
    val error: Boolean,
    val paper: PaperState,
    val autoCutterError: Boolean = false,
) {
    /** True when the printer is ready to print a job right now. */
    val ready: Boolean get() = online && !coverOpen && !error && !autoCutterError && paper != PaperState.NOT_PRESENT
}
