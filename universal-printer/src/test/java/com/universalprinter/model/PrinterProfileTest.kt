package com.universalprinter.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterProfileTest {

    @Test
    fun impactModelsResolveToImpactTextOnlyProfile() {
        for (model in listOf("TM-U220", "TM-U220B", "SP742", "SP700", "SRP-275III", "SRP-270")) {
            val p = PrinterProfiles.forModel(model)
            assertTrue("$model should be impact", p.isImpact)
            assertFalse("$model must not support graphics", p.supportsGraphics)
            assertEquals("$model width", PaperWidth.IMPACT_76, p.paper)
        }
    }

    @Test
    fun thermalModelsResolveToGraphicsProfileWithFallbackWidth() {
        val p = PrinterProfiles.forModel("TM-T88VI")
        assertFalse(p.isImpact)
        assertTrue(p.supportsGraphics)
        assertEquals(PaperWidth.MM_80, p.paper)

        assertEquals(PaperWidth.MM_58, PrinterProfiles.forModel("TM-T20", fallback = PaperWidth.MM_58).paper)
    }

    @Test
    fun nullModelIsTreatedAsThermal() {
        val p = PrinterProfiles.forModel(null)
        assertFalse(p.isImpact)
        assertEquals(PaperWidth.MM_80, p.paper)
    }

    @Test
    fun isImpactModelIsCaseInsensitive() {
        assertTrue(PrinterProfiles.isImpactModel("tm-u220"))
        assertTrue(PrinterProfiles.isImpactModel("Epson TM-U295"))
        assertFalse(PrinterProfiles.isImpactModel("TM-T88"))
    }
}
