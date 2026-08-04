package com.universalprinter.util

/**
 * Floyd–Steinberg error-diffusion dithering to 1-bit black/white. Pure (operates on ARGB int
 * arrays), so it is unit-testable without Android. Thermal printers are 1-bit devices; dithering a
 * photo/logo before printing preserves apparent gradients far better than the plain thresholding
 * most vendor bitmap paths apply.
 */
internal object Dithering {

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /**
     * Returns a new ARGB array where every pixel is [BLACK] or [WHITE], with quantization error
     * diffused to neighbours. [argb] must be [width]*[height] in row-major order.
     */
    fun floydSteinberg(argb: IntArray, width: Int, height: Int): IntArray {
        require(argb.size == width * height) { "argb size ${argb.size} != $width*$height" }
        // Work on a mutable luminance buffer so diffused error accumulates across the pass.
        val lum = FloatArray(argb.size)
        for (i in argb.indices) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        val out = IntArray(argb.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val old = lum[idx]
                val new = if (old < 128f) 0f else 255f
                out[idx] = if (new == 0f) BLACK else WHITE
                val err = old - new
                if (x + 1 < width) lum[idx + 1] += err * 7f / 16f
                if (y + 1 < height) {
                    if (x > 0) lum[idx + width - 1] += err * 3f / 16f
                    lum[idx + width] += err * 5f / 16f
                    if (x + 1 < width) lum[idx + width + 1] += err * 1f / 16f
                }
            }
        }
        return out
    }
}
