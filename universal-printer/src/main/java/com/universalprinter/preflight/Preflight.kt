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

    /** Sunmi `updatePrinterState()` code (near-end has no code on this API, so it's never warned here). */
    fun sunmi(state: Int): PreflightResult = when (state) {
        4 -> PreflightResult.Block(PrintErrorReason.PAPER_OUT, "out of paper")
        6 -> PreflightResult.Block(PrintErrorReason.COVER_OPEN, "cover open")
        7 -> PreflightResult.Block(PrintErrorReason.CUTTER_ERROR, "cutter error")
        3, 505 -> PreflightResult.Block(PrintErrorReason.NOT_CONNECTED, "printer not connected")
        else -> PreflightResult.Proceed()
    }

    /** Star `StarPrinterStatus` fields (+ `detail.cutterError`). */
    fun star(
        coverOpen: Boolean,
        paperEmpty: Boolean,
        paperNearEmpty: Boolean,
        cutterError: Boolean,
        hasError: Boolean,
    ): PreflightResult = when {
        coverOpen -> PreflightResult.Block(PrintErrorReason.COVER_OPEN, "cover open")
        paperEmpty -> PreflightResult.Block(PrintErrorReason.PAPER_OUT, "out of paper")
        cutterError -> PreflightResult.Block(PrintErrorReason.CUTTER_ERROR, "auto-cutter error")
        hasError -> PreflightResult.Block(PrintErrorReason.UNKNOWN, "printer error")
        paperNearEmpty -> NEAR_END
        else -> PreflightResult.Proceed()
    }
}
