package com.universalprinter.escpos

import com.universalprinter.model.PaperState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosStatusTest {

    @Test
    fun onlineFromPrinterStatusBit3() {
        assertTrue(EscPosStatus.online(0x12))  // bit3 clear -> online
        assertFalse(EscPosStatus.online(0x1A)) // 0x1A has bit3 (0x08) set -> offline
        assertFalse(EscPosStatus.online(0x08))
    }

    @Test
    fun coverAndErrorFromOfflineStatus() {
        assertTrue(EscPosStatus.coverOpen(0x04))
        assertFalse(EscPosStatus.coverOpen(0x00))
        assertTrue(EscPosStatus.error(0x40))
        assertFalse(EscPosStatus.error(0x04)) // cover bit is not the error bit
    }

    @Test
    fun paperStateFromSensorMasks() {
        assertEquals(PaperState.OK, EscPosStatus.paper(0x12))
        assertEquals(PaperState.NEAR_END, EscPosStatus.paper(0x1E))
        assertEquals(PaperState.NOT_PRESENT, EscPosStatus.paper(0x72))
    }

    @Test
    fun paperNotPresentTakesPrecedenceOverNearEnd() {
        // 0x72 satisfies the near-end mask too, but no-paper must win.
        assertEquals(PaperState.NOT_PRESENT, EscPosStatus.paper(0x72))
    }

    @Test
    fun autoCutterErrorFromErrorStatusBit3() {
        assertTrue(EscPosStatus.autoCutterError(0x08))
        assertFalse(EscPosStatus.autoCutterError(0x12)) // no cutter bit
    }

    @Test
    fun parseCombinesAllFourAndComputesReady() {
        val ready = EscPosStatus.parse(printerByte = 0x12, offlineByte = 0x00, errorByte = 0x12, paperByte = 0x12)
        assertTrue(ready.ready)

        val paperOut = EscPosStatus.parse(printerByte = 0x12, offlineByte = 0x00, errorByte = 0x12, paperByte = 0x72)
        assertFalse(paperOut.ready)
        assertEquals(PaperState.NOT_PRESENT, paperOut.paper)

        val coverOpen = EscPosStatus.parse(printerByte = 0x12, offlineByte = 0x04, errorByte = 0x12, paperByte = 0x12)
        assertFalse(coverOpen.ready)
        assertTrue(coverOpen.coverOpen)

        val cutter = EscPosStatus.parse(printerByte = 0x12, offlineByte = 0x00, errorByte = 0x08, paperByte = 0x12)
        assertFalse(cutter.ready)
        assertTrue(cutter.autoCutterError)
    }
}
