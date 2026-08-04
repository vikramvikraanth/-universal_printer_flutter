package com.universalprintersearch.model

/**
 * Command-emulation / page-description-language names used in [DiscoveredPrinter.emulation].
 * Centralized so the values aren't duplicated as string literals across discovery flows.
 */
object Emulation {
    const val ESC_POS = "ESC/POS"
    const val ZPL = "ZPL"
    const val STAR_PRNT = "StarPRNT"
    const val STAR_LINE = "StarLine"
    const val STAR_GRAPHIC = "StarGraphic"
    const val STAR_DOT = "StarDot"
    const val STAR_CD5 = "StarCD5"
    const val UNKNOWN = "Unknown"
}
