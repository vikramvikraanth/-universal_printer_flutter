package com.universalprintersearch

import android.content.Context
import com.universalprintersearch.model.DiscoveredPrinter
import com.universalprintersearch.model.PrinterBrand
import com.universalprintersearch.network.NetworkScanner
import com.universalprintersearch.network.PrinterDiscoverer
import com.universalprintersearch.network.epson.EpsonDiscovery
import com.universalprintersearch.network.epson.EpsonTcpProbe
import com.universalprintersearch.network.snmp.SnmpDiscovery
import com.universalprintersearch.network.snmp.SnmpIdentityProbe
import com.universalprintersearch.network.star.StarWebDiscoverer
import com.universalprintersearch.network.sunmi.SunmiDiscovery
import com.universalprintersearch.network.zebra.ZebraDiscovery
import com.universalprintersearch.usb.UsbPrinterScanner
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Single entry point for SDK-free printer discovery. Every path uses only raw
 * sockets or platform APIs — no vendor jar/aar:
 *   - Epson (network) — ENPC UDP broadcast + GS I TCP.
 *   - Sunmi cloud (network) — mDNS/DNS-SD via NsdManager.
 *   - Zebra (network) — UDP-4201 broadcast.
 *   - SNMP brands (network) — TCP-9100 sweep + SNMP identity: Star, Bixolon,
 *     Citizen, Brother, Seiko (RP-series). Read [DiscoveredPrinter.brand] to tell
 *     them apart; any other SNMP responder comes back GENERIC-with-identity.
 *   - Generic IP (network) — TCP-9100 sweep, SNMP-enriched where available.
 *   - USB — UsbManager enumeration.
 *
 * Still OUT of scope: Star/Zebra/etc. PRINTING (needs each vendor SDK), and the
 * Seiko SLP720/721RT label printers. This library only DISCOVERS.
 *
 * All `suspend` functions are main-safe (they switch to Dispatchers.IO
 * internally), so they can be called from a coroutine on the main thread.
 */
class UniversalPrinterSearch(
    context: Context,
    private val networkScanner: NetworkScanner = NetworkScanner(),
    private val epsonDiscovery: EpsonDiscovery = EpsonDiscovery(),
    private val epsonProbe: EpsonTcpProbe = EpsonTcpProbe(),
    private val sunmiDiscovery: SunmiDiscovery = SunmiDiscovery(),
    private val snmpDiscovery: SnmpDiscovery = SnmpDiscovery(),
    private val snmpProbe: SnmpIdentityProbe = SnmpIdentityProbe(),
    private val zebraDiscovery: ZebraDiscovery = ZebraDiscovery(),
    private val starDiscovery: StarWebDiscoverer = StarWebDiscoverer(),
    usbScanner: UsbPrinterScanner? = null,
    /** Extra network discoverers merged into [discoverAll] — extend without editing this class. */
    private val additionalDiscoverers: List<PrinterDiscoverer> = emptyList(),
) {
    private val appContext = context.applicationContext
    private val usbScanner: UsbPrinterScanner = usbScanner ?: UsbPrinterScanner(appContext)

    // Ordered branded-first so distinctBy(ip) in discoverAll keeps the richer entry
    // (Epson ENPC first — its verified MAC wins over an SNMP hit at the same IP).
    private val networkDiscoverers: List<PrinterDiscoverer> = listOf(
        PrinterDiscoverer { discoverEpsonPrinters() },
        PrinterDiscoverer { discoverSunmiPrinters() },
        PrinterDiscoverer { discoverZebraPrinters() },
        PrinterDiscoverer { discoverStarPrinters() },
        PrinterDiscoverer { discoverNetworkPrinters() },
    ) + additionalDiscoverers

    /** Discover Epson printers on the LAN (ENPC UDP broadcast + TCP GS I fallback). */
    suspend fun discoverEpsonPrinters(): List<DiscoveredPrinter> = epsonDiscovery.discover(appContext)

    /** Discover Sunmi cloud printers on the LAN via mDNS/DNS-SD (`NsdManager`). */
    suspend fun discoverSunmiPrinters(): List<DiscoveredPrinter> = sunmiDiscovery.discover(appContext)

    /**
     * Discover all SNMP-identified printers on the LAN in one TCP-9100 sweep:
     * Star, Bixolon, Citizen, Brother, Seiko (positively branded), plus any other
     * SNMP responder as GENERIC-with-identity. Read [DiscoveredPrinter.brand].
     */
    suspend fun discoverSnmpPrinters(): List<DiscoveredPrinter> = snmpDiscovery.discover(snmpOnly = true)

    /** Discover Zebra printers on the LAN (UDP-4201 broadcast + SNMP MAC enrichment). */
    suspend fun discoverZebraPrinters(): List<DiscoveredPrinter> = zebraDiscovery.discover(appContext)

    /** Discover Star printers on the LAN, SDK-free (web-config scrape → IP + MAC + model + emulation). */
    suspend fun discoverStarPrinters(): List<DiscoveredPrinter> = starDiscovery.discover()

    /** Discover Seiko RP-series printers on the LAN. Thin filter over [discoverSnmpPrinters]. */
    suspend fun discoverSeikoPrinters(): List<DiscoveredPrinter> =
        discoverSnmpPrinters().filter { it.brand == PrinterBrand.SEIKO }

    /**
     * Discover any host answering on the raw-print port across the /24 subnet,
     * SNMP-enriched: each hit gets brand/model/serial/MAC when it answers SNMP,
     * otherwise it comes back GENERIC with IP only.
     */
    suspend fun discoverNetworkPrinters(): List<DiscoveredPrinter> = snmpDiscovery.discover(snmpOnly = false)

    /** Enumerate connected USB printers. Does NOT prompt for permission. */
    fun discoverUsbPrinters(): List<DiscoveredPrinter> = usbScanner.listConnectedPrinters()

    /** TCP-9100 reachability check for a single IP (see [NetworkScanner.ping]). */
    suspend fun ping(ip: String): Boolean = networkScanner.ping(ip)

    /** Probe a single known IP for Epson identity + serial — the manual "add custom printer" path. */
    suspend fun probeEpson(ip: String, port: Int = 9100): EpsonTcpProbe.EpsonInfo = epsonProbe.probe(ip, port)

    /** Probe a single known IP for brand/model/serial/MAC over SNMP — the manual-add path. */
    suspend fun probeSnmp(ip: String): SnmpIdentityProbe.SnmpIdentity? = snmpProbe.probe(ip)

    /**
     * Run every registered network discoverer plus USB concurrently. Network results
     * are de-duped by IP in registration order (branded entries win over a bare generic
     * hit at the same IP). Register more via [additionalDiscoverers] — no edit here.
     */
    suspend fun discoverAll(): List<DiscoveredPrinter> = coroutineScope {
        val networkJobs = networkDiscoverers.map { async { it.discover() } }
        val usb = discoverUsbPrinters()
        // Dedup on IP when present, else MAC (Star is keyed by MAC, no IP), else serial.
        val networkMerged = networkJobs.awaitAll().flatten()
            .distinctBy { it.ipAddress ?: it.macAddress ?: it.serialNumber ?: "${it.brand}:${it.name}" }
        networkMerged + usb
    }
}
