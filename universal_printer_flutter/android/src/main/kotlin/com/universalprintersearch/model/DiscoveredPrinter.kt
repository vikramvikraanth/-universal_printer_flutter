package com.universalprintersearch.model

/** How the printer is reached: over the LAN, over USB, or the host device's own built-in printer. */
enum class PrinterConnectionType { NETWORK, USB, BUILT_IN }

/**
 * Identified brand. EPSON (via ENPC / GS I), SUNMI (via mDNS name), ZEBRA (via
 * UDP-4201), and STAR / BIXOLON / CITIZEN / BROTHER / SEIKO (via SNMP sysDescr)
 * are positively identified.
 */
enum class PrinterBrand { EPSON, SUNMI, IMIN, SEIKO, STAR, ZEBRA, BIXOLON, CITIZEN, BROTHER, GENERIC, UNKNOWN }

/**
 * A printer surfaced by discovery. Fields are populated on a best-effort basis
 * depending on the transport:
 *   - NETWORK Epson: [ipAddress], [macAddress] (ENPC UDP), [serialNumber] (GS I 68), [model], [brand]=EPSON
 *   - NETWORK Seiko: [ipAddress], [model]/[serialNumber]/[macAddress] (SNMP MIB), [brand]=SEIKO
 *   - NETWORK generic: [ipAddress] (+ [macAddress] on Android <= 9), [brand]=GENERIC
 *   - USB: [vendorId], [productId], [usbDeviceName]
 */
data class DiscoveredPrinter(
    val name: String,
    val connectionType: PrinterConnectionType,
    val ipAddress: String? = null,
    val port: Int = 9100,
    val macAddress: String? = null,
    val serialNumber: String? = null,
    val brand: PrinterBrand = PrinterBrand.UNKNOWN,
    val model: String? = null,
    /** Command emulation (e.g. StarPRNT, StarLine, StarGraphic, EscPos) — currently derived for Star. */
    val emulation: String? = null,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val usbDeviceName: String? = null,
    /** Paper widths (mm) the printer supports, e.g. [58] or [58, 80]. Populated for built-in printers
     *  (queried live from the vendor SDK); empty when unknown. */
    val supportedPaperWidthsMm: List<Int> = emptyList(),
) {
    /** True for the host device's own **built-in** printer (Sunmi/iMin POS hardware). */
    val isBuiltIn: Boolean get() = connectionType == PrinterConnectionType.BUILT_IN

    /**
     * Print modes this printer supports. Text is universal; **image** (full-receipt bitmap / raster
     * logo / QR / barcode) works on every printer **except 9-pin impact** printers, which are text-only.
     * So a generic/thermal printer reports `["TEXT", "IMAGE"]`; an impact printer reports `["TEXT"]`.
     */
    val supportedPrintTypes: List<String>
        get() = if (isImpact) listOf("TEXT") else listOf("TEXT", "IMAGE")

    /** Convenience: true unless this is an impact (text-only) printer. */
    val supportsImage: Boolean get() = !isImpact

    /**
     * True for 9-pin **impact / dot-matrix** printers — text-only, no raster image/QR. Detected by
     * model/name token (Epson TM-U*, Star SP700/SP742, Bixolon SRP-27x). Matches the RN package's
     * `TM-U` rule; extend [IMPACT_MODEL_TOKENS] as needed.
     */
    val isImpact: Boolean
        get() {
            val id = (model ?: name).uppercase(java.util.Locale.ROOT)
            return IMPACT_MODEL_TOKENS.any { id.contains(it) }
        }

    /**
     * The command language to drive this printer with — [emulation] when positively identified
     * (Star/ZPL/etc.), otherwise the **ESC/POS** default. Every printer this SDK targets over a
     * raw port-9100 / USB-class-printer transport speaks ESC/POS unless discovery proved otherwise.
     */
    val effectiveEmulation: String
        get() = emulation ?: Emulation.ESC_POS

    companion object {
        val IMPACT_MODEL_TOKENS = listOf("TM-U", "SP700", "SP742", "SRP-270", "SRP-275")
    }
}
