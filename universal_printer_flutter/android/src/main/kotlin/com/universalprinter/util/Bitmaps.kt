package com.universalprinter.util

import android.graphics.Bitmap

import android.graphics.Color

internal object Bitmaps {
    /** Scale a bitmap down to at most [maxWidthPx] wide, keeping aspect ratio. */
    fun scaleToWidth(bitmap: Bitmap, maxWidthPx: Int): Bitmap {
        if (bitmap.width <= maxWidthPx) return bitmap
        val ratio = maxWidthPx.toFloat() / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, maxWidthPx, (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
    }

    /** Floyd–Steinberg dither to 1-bit black/white (better gradients on thermal hardware). */
    fun dither(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return Bitmap.createBitmap(Dithering.floydSteinberg(pixels, w, h), w, h, Bitmap.Config.ARGB_8888)
    }

    /** Invert RGB (keep alpha) — prints as an inverted/white-on-black image. */
    fun invert(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            pixels[i] = Color.argb(Color.alpha(p), 255 - Color.red(p), 255 - Color.green(p), 255 - Color.blue(p))
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
