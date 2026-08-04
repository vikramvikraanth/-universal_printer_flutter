package com.universalprintersearch.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveredPrinterTest {

    private fun net(model: String? = null, name: String = "Network Printer", emulation: String? = null) =
        DiscoveredPrinter(
            name = name,
            connectionType = PrinterConnectionType.NETWORK,
            ipAddress = "192.168.0.10",
            model = model,
            emulation = emulation,
        )

    @Test
    fun effectiveEmulationDefaultsToEscPosWhenUnknown() {
        assertEquals(Emulation.ESC_POS, net(emulation = null).effectiveEmulation)
    }

    @Test
    fun effectiveEmulationKeepsPositivelyIdentifiedValue() {
        assertEquals(Emulation.STAR_PRNT, net(emulation = Emulation.STAR_PRNT).effectiveEmulation)
        assertEquals(Emulation.ZPL, net(emulation = Emulation.ZPL).effectiveEmulation)
    }

    @Test
    fun isImpactMatchesImpactModelsByModelThenName() {
        assertTrue(net(model = "TM-U220II").isImpact)
        assertTrue(net(model = "SP742").isImpact)
        assertTrue(net(model = null, name = "Star SRP-275III").isImpact) // falls back to name
    }

    @Test
    fun isImpactFalseForThermalAndGenericHosts() {
        assertFalse(net(model = "TM-m30III").isImpact)
        assertFalse(net(model = null, name = "Network Printer").isImpact) // bare 9100 host — unknowable
    }
}
