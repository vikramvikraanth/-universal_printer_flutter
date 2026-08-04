package com.universalprintersearch.network.snmp

import android.util.Log
import com.universalprintersearch.model.PrinterBrand
import com.universalprintersearch.util.NetworkUtils

/**
 * Brand-agnostic printer identity over SNMP. Reads standard MIB OIDs and maps the
 * device description to a [PrinterBrand] — no vendor SDK. This is the shared
 * engine behind SNMP discovery for Star / Bixolon / Citizen / Brother / Seiko
 * (and any other printer that answers SNMP).
 *
 * ASSUMED (hardware-gated, per .memory/verified-facts.json): that a printer answers
 * SNMP with community "public" by default, and that its sysDescr / hrDeviceDescr
 * contain the [BRAND_TOKENS] below. The token map, OIDs and community are all
 * overridable so a real-device capture can tune them without structural change.
 */
class SnmpIdentityProbe(
    private val snmp: SnmpTransport = SnmpClient(),
) {

    /** SNMP identity for one host. [brand] is GENERIC when SNMP answered but no token matched. */
    data class SnmpIdentity(
        val brand: PrinterBrand,
        val model: String?,
        val serial: String?,
        val mac: String?,
    )

    /** Returns null if the host did not answer SNMP at all (so callers can skip it). */
    suspend fun probe(ip: String, community: String = SnmpClient.DEFAULT_COMMUNITY): SnmpIdentity? {
        val sysObjId = Snmp.oidOf(snmp.get(ip, OID_SYS_OBJECT_ID, community))
        val sysDescr = Snmp.textOf(snmp.get(ip, OID_SYS_DESCR, community))
        val hrDescr = Snmp.textOf(snmp.get(ip, OID_HR_DEVICE_DESCR, community))
        if (sysObjId == null && sysDescr == null && hrDescr == null) {
            Log.d(TAG, "$ip: no SNMP response (community='$community') -> stays GENERIC IP-only")
            return null
        }

        // sysObjectID enterprise number is the robust discriminator; sysDescr text is the fallback.
        val brand = matchByPen(sysObjId) ?: matchBrand(listOfNotNull(sysDescr, hrDescr).joinToString(" "))
        Log.d(TAG, "$ip: sysObjId=$sysObjId sysDescr=\"$sysDescr\" hrDescr=\"$hrDescr\" -> brand=$brand")
        var serial = Snmp.textOf(snmp.get(ip, OID_SERIAL, community))
        // Brother exposes its serial under a vendor OID, not always the standard Printer-MIB one.
        if (serial == null && brand == PrinterBrand.BROTHER) {
            serial = Snmp.textOf(snmp.get(ip, OID_BROTHER_SERIAL, community))
        }
        val mac = Snmp.macHexOf(snmp.get(ip, OID_IF_PHYS_ADDR, community))
            ?.let { NetworkUtils.normalizeMac(it) }?.ifEmpty { null }
        return SnmpIdentity(brand, model = sysDescr ?: hrDescr, serial = serial, mac = mac)
    }

    /**
     * Match by IANA Private Enterprise Number in sysObjectID (`1.3.6.1.4.1.<PEN>…`).
     * Returns null when the OID is absent, not enterprise-rooted, or the PEN is unknown
     * (so the caller falls back to [matchBrand]).
     */
    internal fun matchByPen(sysObjectId: String?): PrinterBrand? {
        val oid = sysObjectId ?: return null
        if (!oid.startsWith(ENTERPRISE_PREFIX)) return null
        val pen = oid.removePrefix(ENTERPRISE_PREFIX).substringBefore('.').toLongOrNull() ?: return null
        return PEN_BRANDS[pen]
    }

    /** Map a device description to a brand via [BRAND_TOKENS]; GENERIC if none match. */
    internal fun matchBrand(descr: String): PrinterBrand {
        val upper = descr.uppercase()
        for ((brand, tokens) in BRAND_TOKENS) {
            if (tokens.any { upper.contains(it) }) return brand
        }
        return PrinterBrand.GENERIC
    }

    companion object {
        private const val TAG = "SnmpIdentityProbe"

        // Standard MIB-II / Host-Resources / Printer-MIB OIDs (RFC 1213 / 2790 / 3805).
        const val OID_SYS_OBJECT_ID = "1.3.6.1.2.1.1.2.0"          // sysObjectID.0 (vendor OID)
        const val OID_SYS_DESCR = "1.3.6.1.2.1.1.1.0"              // sysDescr.0
        const val OID_HR_DEVICE_DESCR = "1.3.6.1.2.1.25.3.2.1.3.1" // hrDeviceDescr.1
        const val OID_SERIAL = "1.3.6.1.2.1.43.5.1.1.17.1"         // prtGeneralSerialNumber.1
        const val OID_IF_PHYS_ADDR = "1.3.6.1.2.1.2.2.1.6.1"       // ifPhysAddress.1 (MAC)
        const val OID_BROTHER_SERIAL = "1.3.6.1.4.1.2435.2.3.9.4.2.1.5.5.1" // brInfoSerialNumber

        private const val ENTERPRISE_PREFIX = "1.3.6.1.4.1."

        /**
         * IANA Private Enterprise Number -> brand. VERIFIED via IANA registry / oidref /
         * real sysObjectID captures / LibreNMS detection defs: Zebra=10642 (Zebra
         * Technologies) and 683 (ZebraNet/Eltron print servers, per LibreNMS ESI-MIB
         * detection), Brother=2435, Seiko Epson=1248, Seiko Instruments (SII)=263. This is
         * the PRIMARY, robust discriminator (numeric OID prefix). Star / Bixolon / Citizen
         * do NOT appear in the IANA registry (likely no assigned PEN) so they rely solely
         * on the sysDescr token fallback below.
         */
        val PEN_BRANDS: Map<Long, PrinterBrand> = mapOf(
            10642L to PrinterBrand.ZEBRA,
            683L to PrinterBrand.ZEBRA,
            2435L to PrinterBrand.BROTHER,
            1248L to PrinterBrand.EPSON,
            263L to PrinterBrand.SEIKO,
        )

        /**
         * sysDescr/hrDeviceDescr tokens (uppercased) -> brand. FALLBACK when sysObjectID
         * PEN doesn't match. ASSUMED — confirm/expand against real hardware. Order matters:
         * first match wins. ZEBRA token corroborated by a real capture ("Zebra Technologies
         * ZD421… / ZTC …"); the others are still assumed.
         */
        val BRAND_TOKENS: List<Pair<PrinterBrand, List<String>>> = listOf(
            PrinterBrand.EPSON to listOf("EPSON"),
            PrinterBrand.SUNMI to listOf("SUNMI"),
            PrinterBrand.STAR to listOf("STAR"),
            PrinterBrand.ZEBRA to listOf("ZEBRA", "ZTC", "ZBR"),
            PrinterBrand.BIXOLON to listOf("BIXOLON", "BXL"),
            PrinterBrand.CITIZEN to listOf("CITIZEN", "CBM"),
            PrinterBrand.BROTHER to listOf("BROTHER"),
            PrinterBrand.SEIKO to listOf("SEIKO", "SII", "RP-E", "RP-D", "RP-F"),
        )
    }
}
