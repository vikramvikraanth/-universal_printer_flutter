package com.universalprintersearch.network.star

import com.universalprintersearch.model.Emulation

/**
 * Star model → command-emulation lookup.
 *
 * This is a faithful, SDK-free reproduction of how the StarXpand SDK (StarIO10)
 * derives emulation: it does NOT read emulation from the printer — it detects the
 * model and maps it via `printerspec.j.e(StarPrinterModel)`. That mapping was
 * extracted verbatim from stario10 1.12.0 (reflectively invoking j.e for all 33
 * models). We apply the same table to the model scraped from the Star web config.
 */
object StarModelEmulation {

    const val UNKNOWN = Emulation.UNKNOWN

    /** Returns the StarXpand emulation name for a scraped model string, or [UNKNOWN]. */
    fun emulationFor(model: String?): String {
        if (model.isNullOrBlank()) return UNKNOWN
        val m = model.uppercase().replace(Regex("[^A-Z0-9]"), "")
        return when {
            m.contains("TSP650II") || m.contains("TSP700II") || m.contains("TSP800II") -> Emulation.STAR_LINE
            m.contains("TSP100IV") -> Emulation.STAR_PRNT           // TSP100IV, TSP100IV_SK
            m.contains("TSP100") -> Emulation.STAR_GRAPHIC          // ECO / IIU+ / LAN / IIIW / IIILAN / IIIBI / IIIU
            m.contains("MPOP") -> Emulation.STAR_PRNT
            m.contains("MCPRINT") || m.contains("MCLABEL") || m.contains("MCCONNECT") -> Emulation.STAR_PRNT
            m.startsWith("SMS") || m.startsWith("SMT") || m.startsWith("SML") -> Emulation.STAR_PRNT // SM-S/T/L mobile
            m.contains("BSC10II") -> Emulation.STAR_PRNT
            m.contains("SP700") -> Emulation.STAR_DOT
            m.startsWith("SK1") || m.startsWith("SK5") -> Emulation.STAR_PRNT
            m.contains("CD5") -> Emulation.STAR_CD5
            else -> UNKNOWN
        }
    }
}
