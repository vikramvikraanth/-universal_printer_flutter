package com.universalprintersearch.network.star

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the model→emulation table matches the values extracted from StarXpand's printerspec.j.e(). */
class StarModelEmulationTest {

    @Test
    fun emulationFor_matchesStarXpandTable() {
        // StarLine
        assertEquals("StarLine", StarModelEmulation.emulationFor("TSP650II"))
        assertEquals("StarLine", StarModelEmulation.emulationFor("TSP700II"))
        // StarGraphic (TSP100 legacy)
        assertEquals("StarGraphic", StarModelEmulation.emulationFor("TSP100LAN"))
        assertEquals("StarGraphic", StarModelEmulation.emulationFor("TSP100IIILAN"))
        // StarPRNT
        assertEquals("StarPRNT", StarModelEmulation.emulationFor("TSP100IV"))
        assertEquals("StarPRNT", StarModelEmulation.emulationFor("mC-Print3"))
        assertEquals("StarPRNT", StarModelEmulation.emulationFor("SM-S230i"))
        assertEquals("StarPRNT", StarModelEmulation.emulationFor("BSC10II"))
        // StarDot / StarCD5
        assertEquals("StarDot", StarModelEmulation.emulationFor("SP700"))
        assertEquals("StarCD5", StarModelEmulation.emulationFor("CD5"))
    }

    @Test
    fun emulationFor_tsp100ivWinsOverGenericTsp100() {
        // TSP100IV must map to StarPRNT, not the StarGraphic bucket the other TSP100s use.
        assertEquals("StarPRNT", StarModelEmulation.emulationFor("TSP100IV"))
        assertEquals("StarGraphic", StarModelEmulation.emulationFor("TSP100IIIW"))
    }

    @Test
    fun emulationFor_unknownOrBlankIsUnknown() {
        assertEquals(StarModelEmulation.UNKNOWN, StarModelEmulation.emulationFor("SomethingElse"))
        assertEquals(StarModelEmulation.UNKNOWN, StarModelEmulation.emulationFor(null))
        assertEquals(StarModelEmulation.UNKNOWN, StarModelEmulation.emulationFor(""))
    }
}
