package com.universalprinter.util

/** Script detection used by the AUTO render policy. Pure — no Android dependency. */
internal object Scripts {

    // Codepoints above Latin Extended-B (0x024F) that a Western ESC/POS codepage (e.g. CP1252)
    // still renders natively — common typographic punctuation and the euro sign. Kept native so
    // ordinary European receipts don't get needlessly rasterized.
    private val BENIGN = hashSetOf(
        0x02C6, 0x02DC, 0x2013, 0x2014, 0x2018, 0x2019, 0x201A, 0x201C, 0x201D, 0x201E,
        0x2020, 0x2021, 0x2022, 0x2026, 0x2030, 0x2039, 0x203A, 0x20AC, 0x2122,
    )

    /**
     * True if [text] contains any character a Western codepage can't reliably print — i.e. non-Latin
     * script (CJK, Arabic, Hebrew, Thai, Cyrillic, Greek, Devanagari, …). Basic/accented Latin
     * (≤ U+024F) and a few benign symbols return false so they stay on the fast native path.
     */
    fun requiresGraphics(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp > 0x024F && cp !in BENIGN) return true
            i += Character.charCount(cp)
        }
        return false
    }
}
