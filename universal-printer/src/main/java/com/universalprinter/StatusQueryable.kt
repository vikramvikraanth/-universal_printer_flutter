package com.universalprinter

import com.universalprinter.model.PrinterStatus

/**
 * A [Printer] that can report live hardware status (online / cover / paper / error). Implemented by
 * backends with a real-time status channel (ESC/POS `DLE EOT`). Query with
 * `(printer as? StatusQueryable)?.queryStatus()`.
 */
interface StatusQueryable {
    /** Returns the current [PrinterStatus], or null if the printer is unreachable or doesn't answer
     *  the status query (many low-end printers don't implement it). */
    suspend fun queryStatus(): PrinterStatus?
}
