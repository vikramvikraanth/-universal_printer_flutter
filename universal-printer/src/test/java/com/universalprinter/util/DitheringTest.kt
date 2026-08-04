package com.universalprinter.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DitheringTest {

    private val BLACK = 0xFF000000.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()

    private fun solid(value: Int, w: Int, h: Int): IntArray {
        val p = (0xFF shl 24) or (value shl 16) or (value shl 8) or value
        return IntArray(w * h) { p }
    }

    @Test
    fun outputIsStrictlyBlackOrWhite() {
        val out = Dithering.floydSteinberg(solid(128, 16, 16), 16, 16)
        assertTrue(out.all { it == BLACK || it == WHITE })
    }

    @Test
    fun allWhiteStaysWhiteAllBlackStaysBlack() {
        assertTrue(Dithering.floydSteinberg(solid(255, 8, 8), 8, 8).all { it == WHITE })
        assertTrue(Dithering.floydSteinberg(solid(0, 8, 8), 8, 8).all { it == BLACK })
    }

    @Test
    fun midGrayDiffusesToRoughlyHalfBlack() {
        val out = Dithering.floydSteinberg(solid(128, 20, 20), 20, 20)
        val black = out.count { it == BLACK }
        assertTrue("expected ~half black, got $black/400", black in 120..280) // 30%..70%
    }

    @Test
    fun sizeIsPreserved() {
        assertEquals(12 * 7, Dithering.floydSteinberg(solid(200, 12, 7), 12, 7).size)
    }

    @Test
    fun mismatchedDimensionsThrow() {
        val threw = runCatching { Dithering.floydSteinberg(IntArray(10), 4, 4) }.isFailure
        assertTrue(threw)
    }
}
