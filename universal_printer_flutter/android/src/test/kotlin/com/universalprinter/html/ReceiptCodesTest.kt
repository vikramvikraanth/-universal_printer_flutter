package com.universalprinter.html

import com.google.zxing.BarcodeFormat
import com.universalprinter.model.BarcodeSymbology
import com.universalprinter.model.PrintElement
import com.universalprinter.model.QrErrorLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure ZXing generation — runs on the JVM (no Android). */
class ReceiptCodesTest {

    @Test
    fun qrEncodesToaSquareMatrix() {
        val m = ReceiptCodes.matrix(PrintElement.QrCode("https://example.com/r/1", sizeDots = 200, errorLevel = QrErrorLevel.M), 576)
        assertTrue(m.width > 0 && m.height > 0)
        assertEquals(m.width, m.height)
    }

    @Test
    fun code128EncodesToaNonEmptyMatrix() {
        val m = ReceiptCodes.matrix(PrintElement.Barcode("HELLO123", BarcodeSymbology.CODE128, heightDots = 80), 576)
        assertTrue(m.width > 0)
        assertEquals(80, m.height)
    }

    @Test
    fun ean13EncodesWithAutoChecksum() {
        // 12 digits — ZXing computes the 13th (checksum).
        val m = ReceiptCodes.matrix(PrintElement.Barcode("400638133393", BarcodeSymbology.EAN13, heightDots = 60), 576)
        assertTrue(m.width > 0 && m.height > 0)
    }

    @Test
    fun symbologyAndLevelMappings() {
        assertEquals(BarcodeFormat.CODE_128, ReceiptCodes.format(BarcodeSymbology.CODE128))
        assertEquals(BarcodeFormat.EAN_13, ReceiptCodes.format(BarcodeSymbology.EAN13))
        assertEquals(BarcodeFormat.UPC_A, ReceiptCodes.format(BarcodeSymbology.UPCA))
    }
}
