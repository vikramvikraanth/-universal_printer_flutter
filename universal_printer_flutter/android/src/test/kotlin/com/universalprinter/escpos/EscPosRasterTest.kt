package com.universalprinter.escpos

import com.universalprinter.model.Align
import com.universalprinter.model.CutType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Byte-level check of the raw ESC/POS control commands (the bitmap band packing needs Android). */
class EscPosRasterTest {

    @Test
    fun alignCommandBytes() {
        assertArrayEquals(byteArrayOf(0x1B, 0x61, 0), EscPosRaster.align(Align.LEFT))
        assertArrayEquals(byteArrayOf(0x1B, 0x61, 1), EscPosRaster.align(Align.CENTER))
        assertArrayEquals(byteArrayOf(0x1B, 0x61, 2), EscPosRaster.align(Align.RIGHT))
    }

    @Test
    fun feedAndInitBytes() {
        assertArrayEquals(byteArrayOf(0x1B, 0x40), EscPosRaster.INIT)
        assertArrayEquals(byteArrayOf(0x1B, 0x64, 3), EscPosRaster.feed(3))
    }

    @Test
    fun cutBytesPerType() {
        assertArrayEquals(byteArrayOf(0x1D, 0x56, 0), EscPosRaster.cut(CutType.FULL))
        assertArrayEquals(byteArrayOf(0x1D, 0x56, 1), EscPosRaster.cut(CutType.PARTIAL))
        assertEquals(0, EscPosRaster.cut(CutType.NONE).size)
    }
}
