package com.universalprinter.preflight

import com.universalprinter.model.PreflightResult

/**
 * Adaptive pre-print gate. Runs a status [probe] before a job and blocks on actionable faults (paper-out,
 * cover-open, cutter) via [Preflight.escPos] — but stops probing a printer that repeatedly proves it
 * doesn't answer real-time status, so those printers print straight through (smooth, like the reference
 * package) while capable printers keep fault-blocking.
 *
 * Owns the adaptive *policy* and its state, separately from the transport backend that supplies the
 * probe — so the policy is unit-testable with a fake probe (no sockets), and the thresholds are tunable
 * without touching the backend.
 *
 * Not thread-safe: drive it from a single serialized job loop (the per-printer [com.universalprinter.queue.PrintQueue]).
 *
 * @param probe reads the printer's status (injected — the only IO dependency).
 * @param silentThreshold consecutive silent probes before a printer is treated as not supporting status.
 *        `>1` so a single slow/busy first probe can't permanently downgrade a capable printer.
 * @param reprobeInterval while treated as unsupported, re-probe once per this many jobs to recover if the
 *        printer starts answering (e.g. it was merely busy during the first probes).
 */
internal class PreflightGate(
    private val probe: suspend () -> Preflight.StatusProbe,
    private val silentThreshold: Int = 2,
    private val reprobeInterval: Int = 20,
) {
    private var consecutiveSilent = 0
    private var unsupported = false
    private var jobsSinceProbe = 0

    suspend fun evaluate(): PreflightResult {
        if (unsupported) {
            // Skip the probe for known non-status printers, but re-probe periodically so a printer that
            // recovers (or was only briefly unresponsive) regains fault detection.
            if (++jobsSinceProbe < reprobeInterval) return PreflightResult.Proceed()
            jobsSinceProbe = 0
        }
        return when (val p = probe()) {
            is Preflight.StatusProbe.Answered -> {
                consecutiveSilent = 0
                unsupported = false // it answered → supports status; resume probing every job
                Preflight.escPos(p.status)
            }
            Preflight.StatusProbe.Silent -> {
                if (++consecutiveSilent >= silentThreshold) unsupported = true
                PreflightResult.Proceed()
            }
            // Transient/down — not the printer's fault; the print itself will surface NOT_CONNECTED. Don't
            // count it toward "unsupported", and keep probing.
            Preflight.StatusProbe.Unreachable -> PreflightResult.Proceed()
        }
    }
}
