package com.universalprinter.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptsTest {

    @Test
    fun latinAndBenignSymbolsStayNative() {
        assertFalse(Scripts.requiresGraphics("Coffee 3.50"))
        assertFalse(Scripts.requiresGraphics("Crème brûlée x2")) // accented Latin
        assertFalse(Scripts.requiresGraphics("Total: €14,50"))   // euro is benign
        assertFalse(Scripts.requiresGraphics("naïve — café"))    // em dash + accents benign
    }

    @Test
    fun nonLatinScriptsRequireGraphics() {
        assertTrue(Scripts.requiresGraphics("咖啡"))         // CJK
        assertTrue(Scripts.requiresGraphics("قهوة"))         // Arabic
        assertTrue(Scripts.requiresGraphics("שלום"))         // Hebrew
        assertTrue(Scripts.requiresGraphics("กาแฟ"))         // Thai
        assertTrue(Scripts.requiresGraphics("Кофе"))         // Cyrillic
    }

    @Test
    fun mixedLatinAndNonLatinRequiresGraphics() {
        assertTrue(Scripts.requiresGraphics("Coffee 咖啡"))
    }
}
