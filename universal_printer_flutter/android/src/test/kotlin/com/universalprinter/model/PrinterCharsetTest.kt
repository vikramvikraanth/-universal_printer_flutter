package com.universalprinter.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the manufacturer-portable `ESC t n` code-page ids (verified identical on Epson TM and the
 * Rongta RP80 command manual). A regression here means a code page would select the wrong glyphs.
 */
class PrinterCharsetTest {

    @Test
    fun portableCodePageIdsMatchTheVerifiedEscTTable() {
        assertEquals(0, PrinterCharset.PC437_USA.codePageId)
        assertEquals(2, PrinterCharset.PC850_MULTILINGUAL.codePageId)
        assertEquals(3, PrinterCharset.PC860_PORTUGUESE.codePageId)
        assertEquals(4, PrinterCharset.PC863_CANADIAN_FRENCH.codePageId)
        assertEquals(5, PrinterCharset.PC865_NORDIC.codePageId)
        assertEquals(16, PrinterCharset.WPC1252_WESTERN.codePageId)
        assertEquals(18, PrinterCharset.PC852_LATIN2.codePageId)
        assertEquals(19, PrinterCharset.PC858_EURO.codePageId)
    }

    @Test
    fun customCarriesModelSpecificNameAndId() {
        val greekRongta = PrinterCharset.custom("windows-1253", 17)
        assertEquals("windows-1253", greekRongta.javaCharsetName)
        assertEquals(17, greekRongta.codePageId)
    }

    @Test
    fun documentCarriesCharsetThroughTheBuilder() {
        val doc = printDocument(PaperWidth.MM_80) {
            charset = PrinterCharset.PC852_LATIN2
            text("Zażółć gęślą jaźń")
        }
        assertEquals(PrinterCharset.PC852_LATIN2, doc.charset)
    }
}
