package com.universalprinter.escpos

import com.universalprinter.model.PaperState
import com.universalprinter.model.PrinterStatus

/**
 * ESC/POS `DLE EOT n` real-time status: command bytes + pure parsing of the 1-byte replies.
 *
 * This is the STANDARD ESC/POS status protocol, shared verbatim by every ESC/POS printer we target —
 * **Epson, Seiko (RP-D/RP-E, "industry-standard ESC/POS"), and Rongta (RP80)** — so one parser serves
 * all three (no brand-specific variants; Sunmi and Star use their own SDK status APIs instead).
 *
 * Bit meanings verified against multiple sources:
 * - online (n=1, bit 3) and paper (n=4, masks 0x72/0x1E) cross-checked against python-escpos
 *   (`RT_MASK_ONLINE=8`, `RT_MASK_NOPAPER=114`, `RT_MASK_LOWPAPER=30`, `RT_MASK_PAPER=18`);
 * - **auto-cutter (n=3, bit 3) cross-checked against the Rongta RP80 command manual** ("No autocutter
 *   error"/"Autocutter error occurred");
 * - cover/error (n=2, bits 2 and 6) per the Epson TM `DLE EOT` offline-cause spec.
 * Pure — unit-testable without hardware.
 */
internal object EscPosStatus {

    // DLE EOT n
    val QUERY_PRINTER: ByteArray = byteArrayOf(0x10, 0x04, 1) // n=1 printer status
    val QUERY_OFFLINE: ByteArray = byteArrayOf(0x10, 0x04, 2) // n=2 offline cause
    val QUERY_ERROR: ByteArray = byteArrayOf(0x10, 0x04, 3)   // n=3 error status
    val QUERY_PAPER: ByteArray = byteArrayOf(0x10, 0x04, 4)   // n=4 paper roll sensor

    /** n=1 bit 3 (0x08): 1 = offline. */
    fun online(printerByte: Int): Boolean = (printerByte and 0x08) == 0

    /** n=2 bit 2 (0x04): cover open. */
    fun coverOpen(offlineByte: Int): Boolean = (offlineByte and 0x04) != 0

    /** n=2 bit 6 (0x40): an error has occurred. */
    fun error(offlineByte: Int): Boolean = (offlineByte and 0x40) != 0

    /** n=3 bit 3 (0x08): auto-cutter error (canonical Epson error-status bit). */
    fun autoCutterError(errorByte: Int): Boolean = (errorByte and 0x08) != 0

    /** n=4 paper roll sensor (masks per python-escpos: no-paper 0x72, near-end 0x1E). */
    fun paper(paperByte: Int): PaperState = when {
        paperByte and 0x72 == 0x72 -> PaperState.NOT_PRESENT
        paperByte and 0x1E == 0x1E -> PaperState.NEAR_END
        else -> PaperState.OK
    }

    fun parse(printerByte: Int, offlineByte: Int, errorByte: Int, paperByte: Int): PrinterStatus = PrinterStatus(
        online = online(printerByte),
        coverOpen = coverOpen(offlineByte),
        error = error(offlineByte),
        paper = paper(paperByte),
        autoCutterError = autoCutterError(errorByte),
    )
}
