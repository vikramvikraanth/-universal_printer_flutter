package com.universalprinter.preflight

import com.universalprinter.model.PaperState
import com.universalprinter.model.PreflightResult
import com.universalprinter.model.PrintErrorReason
import com.universalprinter.model.PrinterStatus
import com.universalprinter.model.PrinterWarning

/**
 * Pure mappers from each backend's native status to a common [PreflightResult]. Blocking conditions
 * (cover open, auto-cutter error, out of paper, not connected) fail fast; paper near-end proceeds with
 * a [PrinterWarning]. No Android/SDK dependency — fully unit-testable.
 */
internal object Preflight {

    private val NEAR_END = PreflightResult.Proceed(listOf(PrinterWarning.PAPER_NEAR_END))

    /** Outcome of a network `DLE EOT` status probe. Consumed by [PreflightGate]. */
    sealed interface StatusProbe {
        /** The printer answered — its real-time status. */
        data class Answered(val status: PrinterStatus) : StatusProbe
        /** Reachable, but it never answered `DLE EOT` — it doesn't implement real-time status. */
        data object Silent : StatusProbe
        /** Couldn't connect (down/transient) — distinct from [Silent] so we don't give up on a capable printer. */
        data object Unreachable : StatusProbe
    }

    /** ESC/POS via `DLE EOT`. Null = status unknown/unsupported → proceed (don't block clones that don't answer). */
    fun escPos(status: PrinterStatus?): PreflightResult = when {
        status == null -> PreflightResult.Proceed()
        !status.online -> PreflightResult.Block(PrintErrorReason.NOT_CONNECTED, "printer offline")
        status.coverOpen -> PreflightResult.Block(PrintErrorReason.COVER_OPEN, "cover open")
        status.autoCutterError -> PreflightResult.Block(PrintErrorReason.CUTTER_ERROR, "auto-cutter error")
        status.paper == PaperState.NOT_PRESENT -> PreflightResult.Block(PrintErrorReason.PAPER_OUT, "out of paper")
        status.paper == PaperState.NEAR_END -> NEAR_END
        else -> PreflightResult.Proceed()
    }

    /**
     * Sunmi `updatePrinterState()` code. Full code set (from the Sunmi SDK diagnostics):
     * 1 running, 2 initializing, 8 cutter-normal → healthy; 3 hardware abnormal, 4 out of paper,
     * 5 overheating, 6 cover open, 7 cutter abnormal, 9 no black-mark paper, 505 not connected.
     * Sunmi's print calls succeed silently on a fault, so this preflight is what actually blocks.
     */
    fun sunmi(state: Int): PreflightResult = when (state) {
        4, 9 -> PreflightResult.Block(PrintErrorReason.PAPER_OUT, "out of paper")
        6 -> PreflightResult.Block(PrintErrorReason.COVER_OPEN, "cover open")
        7 -> PreflightResult.Block(PrintErrorReason.CUTTER_ERROR, "cutter error")
        5 -> PreflightResult.Block(PrintErrorReason.OVERHEATED, "printer overheating")
        3 -> PreflightResult.Block(PrintErrorReason.UNKNOWN, "printer hardware error")
        505 -> PreflightResult.Block(PrintErrorReason.NOT_CONNECTED, "printer not connected")
        else -> PreflightResult.Proceed()
    }

    /**
     * iMin `getPrinterStatus()` code (iMin built-in SDK). Codes per the official iMin printer docs:
     * 0 normal; -1/1 not connected; 3 head/cover open; 4 overheated; 7 out of paper; 8 paper low
     * (near-end → warn, don't block); 99 other error. iMin (like Sunmi) reports success on a fault,
     * so this preflight is what actually blocks. Unknown codes proceed (don't block what we can't read).
     */
    fun imin(status: Int): PreflightResult = when (status) {
        0 -> PreflightResult.Proceed()
        3 -> PreflightResult.Block(PrintErrorReason.COVER_OPEN, "printer cover open")
        4 -> PreflightResult.Block(PrintErrorReason.OVERHEATED, "printer overheating")
        7 -> PreflightResult.Block(PrintErrorReason.PAPER_OUT, "out of paper")
        8 -> NEAR_END
        99 -> PreflightResult.Block(PrintErrorReason.UNKNOWN, "printer error")
        -1, 1 -> PreflightResult.Block(PrintErrorReason.NOT_CONNECTED, "printer not connected")
        else -> PreflightResult.Proceed()
    }

    /** Star `StarPrinterStatus` fields (+ `detail.cutterError`). */
    /**
     * Star `StarPrinterStatus` (+ `.detail`) fields → unified result. Mirrors the reference package's
     * `throwIfStarReportsFault`, mapped to our reasons. Actionable faults get their own reason (the
     * UI shows a specific instruction); technical faults collapse to UNKNOWN (generic message) while
     * the [StarStatus] detail string is carried as the technical `details` for logging.
     */
    fun star(s: StarStatus): PreflightResult = when {
        s.coverOpen -> PreflightResult.Block(PrintErrorReason.COVER_OPEN, "cover open")
        s.paperEmpty -> PreflightResult.Block(PrintErrorReason.PAPER_OUT, "out of paper")
        s.paperPresent -> PreflightResult.Block(PrintErrorReason.HOLDING_PAPER, "receipt still at the outlet")
        s.cutterError -> PreflightResult.Block(PrintErrorReason.CUTTER_ERROR, "auto-cutter error")
        s.paperJamError -> PreflightResult.Block(PrintErrorReason.PAPER_JAM, "paper jam")
        s.overTemperature -> PreflightResult.Block(PrintErrorReason.OVERHEATED, "print head over temperature")
        // technical / not user-fixable → generic printer-error message, real cause in details.
        // printUnitOpen is intentionally NOT mapped to "cover open" — it's a distinct mechanism state
        // and the cover-open wording confuses operators; show the generic printer error instead.
        s.printUnitOpen -> PreflightResult.Block(PrintErrorReason.UNKNOWN, "print unit open")
        s.voltageError -> PreflightResult.Block(PrintErrorReason.UNKNOWN, "voltage error")
        s.receiveBufferOverflow -> PreflightResult.Block(PrintErrorReason.UNKNOWN, "receive buffer overflow")
        s.rollPositionError -> PreflightResult.Block(PrintErrorReason.UNKNOWN, "roll position error")
        s.paperSeparatorError -> PreflightResult.Block(PrintErrorReason.UNKNOWN, "paper separator error")
        s.unrecoverableError -> PreflightResult.Block(PrintErrorReason.UNKNOWN, "unrecoverable error")
        s.hasError -> PreflightResult.Block(PrintErrorReason.UNKNOWN, "printer error")
        s.paperNearEmpty -> NEAR_END
        else -> PreflightResult.Proceed()
    }

    /** Flattened Star status flags (from `StarPrinterStatus` + its `.detail`). */
    data class StarStatus(
        val coverOpen: Boolean = false,
        val printUnitOpen: Boolean = false,
        val paperEmpty: Boolean = false,
        val paperNearEmpty: Boolean = false,
        val paperPresent: Boolean = false,   // previous receipt held at the outlet
        val cutterError: Boolean = false,
        val paperJamError: Boolean = false,
        val overTemperature: Boolean = false, // printHeadOverTemperature || printHeadThermistorError
        val voltageError: Boolean = false,
        val receiveBufferOverflow: Boolean = false,
        val rollPositionError: Boolean = false,
        val paperSeparatorError: Boolean = false,
        val unrecoverableError: Boolean = false,
        val hasError: Boolean = false,
    )
}
