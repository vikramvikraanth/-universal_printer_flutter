package com.universalprinter.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PaperWidthTest {

    @Test
    fun dimensionsAt203Dpi() {
        assertEquals(384, PaperWidth.MM_58.widthPx)
        assertEquals(32, PaperWidth.MM_58.charsPerLine)
        assertEquals(576, PaperWidth.MM_80.widthPx)
        assertEquals(48, PaperWidth.MM_80.charsPerLine)
        assertEquals(512, PaperWidth.MM_72.widthPx) // 72mm − 8mm = 64mm printable · 64×8 = 512 dots
    }

    @Test
    fun ofMillimetersMapsKnownWidthsAndDefaults() {
        assertEquals(PaperWidth.MM_58, PaperWidth.ofMillimeters(58))
        assertEquals(PaperWidth.MM_72, PaperWidth.ofMillimeters(72))
        assertEquals(PaperWidth.MM_80, PaperWidth.ofMillimeters(80))
        assertEquals(PaperWidth.MM_80, PaperWidth.ofMillimeters(999)) // unknown -> default
    }
}
