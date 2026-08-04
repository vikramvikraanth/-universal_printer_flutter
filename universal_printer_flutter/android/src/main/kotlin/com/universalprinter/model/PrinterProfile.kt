package com.universalprinter.model

import java.util.Locale

/**
 * The resolved capabilities of a target printer — its [paper] width profile and whether it's an
 * impact/dot-matrix printer (text-only). Derived from the discovered model via [PrinterProfiles].
 */
data class PrinterProfile(
    val paper: PaperWidth,
    val isImpact: Boolean = false,
) {
    /** Impact/dot-matrix printers can't raster — no image/QR/barcode graphics. */
    val supportsGraphics: Boolean get() = !isImpact
}

/** Model → width/capability profile ("printer width analysis"). Extend the tables as models are verified. */
object PrinterProfiles {

    private val IMPACT_TOKENS = listOf("TM-U", "SP700", "SP742", "SRP-270", "SRP-275")

    /** True if [model] names a 9-pin impact / dot-matrix printer (text-only). */
    fun isImpactModel(model: String?): Boolean {
        val m = model?.uppercase(Locale.ROOT).orEmpty()
        return IMPACT_TOKENS.any { m.contains(it) }
    }

    /**
     * Resolve a [PrinterProfile] for [model]. Impact printers → [PaperWidth.IMPACT_76] (text-only);
     * everything else → a thermal default ([fallback], 80mm by default).
     */
    fun forModel(model: String?, fallback: PaperWidth = PaperWidth.MM_80): PrinterProfile =
        if (isImpactModel(model)) PrinterProfile(PaperWidth.IMPACT_76, isImpact = true)
        else PrinterProfile(fallback, isImpact = false)
}
