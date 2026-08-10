package com.universalprinter.preflight

import com.universalprinter.model.PaperState
import com.universalprinter.model.PreflightResult
import com.universalprinter.model.PrintErrorReason
import com.universalprinter.model.PrinterStatus
import com.universalprinter.model.PrinterWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflightTest {

    private fun status(
        online: Boolean = true,
        coverOpen: Boolean = false,
        error: Boolean = false,
        autoCutter: Boolean = false,
        paper: PaperState = PaperState.OK,
    ) = PrinterStatus(online, coverOpen, error, paper, autoCutter)

    private fun blockReason(r: PreflightResult) = (r as PreflightResult.Block).reason

    // ---- ESC/POS ----

    @Test
    fun escPosNullOrOkProceeds() {
        assertTrue(Preflight.escPos(null) is PreflightResult.Proceed)
        assertEquals(PreflightResult.Proceed(), Preflight.escPos(status()))
    }

    @Test
    fun escPosBlocksOnEachFaultWithRightReason() {
        assertEquals(PrintErrorReason.NOT_CONNECTED, blockReason(Preflight.escPos(status(online = false))))
        assertEquals(PrintErrorReason.COVER_OPEN, blockReason(Preflight.escPos(status(coverOpen = true))))
        assertEquals(PrintErrorReason.CUTTER_ERROR, blockReason(Preflight.escPos(status(autoCutter = true))))
        assertEquals(PrintErrorReason.PAPER_OUT, blockReason(Preflight.escPos(status(paper = PaperState.NOT_PRESENT))))
    }

    @Test
    fun escPosNearEndProceedsWithWarningNotBlock() {
        val r = Preflight.escPos(status(paper = PaperState.NEAR_END))
        assertTrue(r is PreflightResult.Proceed)
        assertEquals(listOf(PrinterWarning.PAPER_NEAR_END), (r as PreflightResult.Proceed).warnings)
    }

    // ---- Sunmi (updatePrinterState codes) ----

    @Test
    fun sunmiCodesMapToReasons() {
        assertEquals(PrintErrorReason.PAPER_OUT, blockReason(Preflight.sunmi(4)))
        assertEquals(PrintErrorReason.PAPER_OUT, blockReason(Preflight.sunmi(9)))  // no black-mark paper
        assertEquals(PrintErrorReason.COVER_OPEN, blockReason(Preflight.sunmi(6)))
        assertEquals(PrintErrorReason.CUTTER_ERROR, blockReason(Preflight.sunmi(7)))
        assertEquals(PrintErrorReason.UNKNOWN, blockReason(Preflight.sunmi(5)))    // overheating
        assertEquals(PrintErrorReason.UNKNOWN, blockReason(Preflight.sunmi(3)))    // hardware abnormal
        assertEquals(PrintErrorReason.NOT_CONNECTED, blockReason(Preflight.sunmi(505)))
        // states 5 and 9 must NOT silently proceed (the bug this fixes)
        assertTrue(Preflight.sunmi(5) is PreflightResult.Block)
        assertTrue(Preflight.sunmi(9) is PreflightResult.Block)
        assertTrue(Preflight.sunmi(1) is PreflightResult.Proceed) // running
        assertTrue(Preflight.sunmi(2) is PreflightResult.Proceed) // initializing
        assertTrue(Preflight.sunmi(8) is PreflightResult.Proceed) // cutter normal
    }

    // ---- Star ----

    @Test
    fun starBlocksAndWarnsAppropriately() {
        assertEquals(PrintErrorReason.COVER_OPEN, blockReason(Preflight.star(coverOpen = true, paperEmpty = false, paperNearEmpty = false, cutterError = false, hasError = false)))
        assertEquals(PrintErrorReason.PAPER_OUT, blockReason(Preflight.star(false, paperEmpty = true, false, false, false)))
        assertEquals(PrintErrorReason.CUTTER_ERROR, blockReason(Preflight.star(false, false, false, cutterError = true, hasError = false)))
        val near = Preflight.star(false, false, paperNearEmpty = true, false, false)
        assertEquals(listOf(PrinterWarning.PAPER_NEAR_END), (near as PreflightResult.Proceed).warnings)
        assertTrue(Preflight.star(false, false, false, false, false) is PreflightResult.Proceed)
    }
}
