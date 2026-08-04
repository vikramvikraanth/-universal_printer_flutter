package com.universalprinter.model

/**
 * A printer code page for **native** (non-rasterized) ESC/POS text. [javaCharsetName] is the JVM/ICU
 * charset used to encode the bytes; [codePageId] is the ESC/POS `ESC t n` page the printer selects.
 * Applies only to the ESC/POS backends (network/USB); other backends ignore it.
 *
 * Only the constants below are **manufacturer-portable** — their `ESC t n` numbers are identical on
 * Epson TM and Rongta RP80 (verified from both references). Cyrillic/Greek page numbers **differ per
 * model** (e.g. Rongta puts WPC1253/Greek at page 17, where Epson has PC866/Cyrillic), so supply those
 * via [custom] using the id from YOUR printer's manual.
 *
 * CJK (GBK/Big5/Shift-JIS) is multi-byte and **cannot** be selected this way — print CJK via
 * [RenderMode.AUTO]/[RenderMode.IMAGE] (on-device rasterization), which is script-independent.
 *
 * Charset availability is device-dependent (ICU-backed on API 26+); [WPC1252_WESTERN] is the safest.
 */
data class PrinterCharset(val javaCharsetName: String, val codePageId: Int) {
    companion object {
        val PC437_USA = PrinterCharset("IBM437", 0)
        val PC850_MULTILINGUAL = PrinterCharset("IBM850", 2)
        val PC860_PORTUGUESE = PrinterCharset("IBM860", 3)
        val PC863_CANADIAN_FRENCH = PrinterCharset("IBM863", 4)
        val PC865_NORDIC = PrinterCharset("IBM865", 5)
        val WPC1252_WESTERN = PrinterCharset("windows-1252", 16)
        val PC852_LATIN2 = PrinterCharset("IBM852", 18)
        val PC858_EURO = PrinterCharset("IBM858", 19)

        /**
         * A model-specific code page — pass the `ESC t n` id from your printer's manual.
         * Rongta RP80 examples: Greek `custom("windows-1253", 17)`, Cyrillic `custom("windows-1251", 6)`
         * or `custom("IBM866", 7)`. (Epson differs — check that model's table.)
         */
        fun custom(javaCharsetName: String, codePageId: Int) = PrinterCharset(javaCharsetName, codePageId)
    }
}
