package com.universalprinter.preflight

import com.universalprinter.model.PaperState
import com.universalprinter.model.PreflightResult
import com.universalprinter.model.PrintErrorReason
import com.universalprinter.model.PrinterStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflightGateTest {

    private fun ok() = Preflight.StatusProbe.Answered(
        PrinterStatus(online = true, coverOpen = false, error = false, paper = PaperState.OK, autoCutterError = false),
    )
    private fun paperOut() = Preflight.StatusProbe.Answered(
        PrinterStatus(online = true, coverOpen = false, error = false, paper = PaperState.NOT_PRESENT, autoCutterError = false),
    )

    /** A probe that replays a scripted sequence (repeating the last element) and counts its invocations. */
    private class FakeProbe(private vararg val script: Preflight.StatusProbe) {
        var calls = 0; private set
        suspend fun next(): Preflight.StatusProbe = script[calls.coerceAtMost(script.size - 1)].also { calls++ }
    }

    @Test
    fun answeredFaultBlocksAnswerCleanProceeds() = runTest {
        val probe = FakeProbe(paperOut(), ok())
        val gate = PreflightGate(probe::next)
        assertEquals(PrintErrorReason.PAPER_OUT, (gate.evaluate() as PreflightResult.Block).reason)
        assertTrue(gate.evaluate() is PreflightResult.Proceed)
        assertEquals(2, probe.calls) // capable printer is probed every job
    }

    @Test
    fun aSingleSilentDoesNotStopProbing() = runTest {
        // The hardening: one slow/busy first probe must NOT permanently downgrade a capable printer.
        val probe = FakeProbe(Preflight.StatusProbe.Silent, paperOut())
        val gate = PreflightGate(probe::next, silentThreshold = 2)
        assertTrue(gate.evaluate() is PreflightResult.Proceed) // 1st silent → proceed, still probing
        assertEquals(PrintErrorReason.PAPER_OUT, (gate.evaluate() as PreflightResult.Block).reason) // re-probed, caught the fault
        assertEquals(2, probe.calls)
    }

    @Test
    fun twoConsecutiveSilentsStopProbing() = runTest {
        val probe = FakeProbe(Preflight.StatusProbe.Silent)
        val gate = PreflightGate(probe::next, silentThreshold = 2, reprobeInterval = 20)
        repeat(5) { assertTrue(gate.evaluate() is PreflightResult.Proceed) }
        assertEquals(2, probe.calls) // probed twice → marked unsupported → later jobs skip the probe
    }

    @Test
    fun answeredResetsTheSilentStreak() = runTest {
        // silent, answered, silent → the middle answer resets the streak, so the trailing silent is only #1.
        val probe = FakeProbe(Preflight.StatusProbe.Silent, ok(), Preflight.StatusProbe.Silent, ok())
        val gate = PreflightGate(probe::next, silentThreshold = 2)
        repeat(4) { gate.evaluate() }
        assertEquals(4, probe.calls) // never reached 2 *consecutive* silents → still probing every job
    }

    @Test
    fun unreachableNeverCountsAsSilent() = runTest {
        val probe = FakeProbe(Preflight.StatusProbe.Unreachable)
        val gate = PreflightGate(probe::next, silentThreshold = 2)
        repeat(5) { assertTrue(gate.evaluate() is PreflightResult.Proceed) }
        assertEquals(5, probe.calls) // transient failures keep probing — a recovered printer regains faults
    }

    @Test
    fun reprobesPeriodicallyAndRecoversWhenPrinterStartsAnswering() = runTest {
        // Two silents → unsupported; skip until the re-probe interval; then it answers → probing resumes.
        val probe = FakeProbe(
            Preflight.StatusProbe.Silent, Preflight.StatusProbe.Silent, // jobs 1,2 → unsupported
            ok(), // the re-probe answers
        )
        val gate = PreflightGate(probe::next, silentThreshold = 2, reprobeInterval = 3)
        gate.evaluate(); gate.evaluate()      // 2 probes → unsupported
        gate.evaluate(); gate.evaluate()      // jobs 3,4 skipped (jobsSinceProbe 1,2 < 3)
        assertEquals(2, probe.calls)
        gate.evaluate()                       // job 5 → re-probe fires, answers → unsupported cleared
        assertEquals(3, probe.calls)
        gate.evaluate()                       // now probing every job again
        assertEquals(4, probe.calls)
    }
}
