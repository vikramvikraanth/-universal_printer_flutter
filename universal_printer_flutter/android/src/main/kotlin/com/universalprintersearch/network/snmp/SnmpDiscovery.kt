package com.universalprintersearch.network.snmp

import com.universalprintersearch.model.DiscoveredPrinter
import com.universalprintersearch.model.Emulation
import com.universalprintersearch.model.PrinterBrand
import com.universalprintersearch.model.PrinterConnectionType
import com.universalprintersearch.network.EscPosProbe
import com.universalprintersearch.network.EscPosSerialProbe
import com.universalprintersearch.network.HostProbe
import com.universalprintersearch.network.NetworkScanner
import com.universalprintersearch.util.NetworkUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * SDK-free network discovery via a TCP-9100 /24 sweep + a per-host SNMP identity
 * probe. Covers every printer that answers SNMP — Star / Bixolon / Citizen /
 * Brother / Seiko are positively branded via [SnmpIdentityProbe.BRAND_TOKENS];
 * anything else that answers SNMP is GENERIC-with-identity.
 *
 *   Phase 1 — TCP-9100 sweep (reuses [NetworkScanner.ping]) finds live printers.
 *   Phase 2 — SNMP identity probe fills brand / model / serial / MAC.
 *   Phase 3 — serial fallback: when SNMP doesn't return a serial, probe ESC/POS
 *             `GS I 68` over TCP-9100 ([EscPosSerialProbe]). MAC is frequently
 *             unavailable SDK-free (ARP is blocked on Android 11+, and many
 *             printers don't expose ifPhysAddress), so the ESC/POS serial — proven
 *             to work on Sunmi/Epson hardware — is the unique identifier of last
 *             resort for a MAC-less printer.
 *
 * [snmpOnly] = true returns only hosts that answered SNMP (the discoverSnmpPrinters
 * contract); false also returns non-SNMP reachable hosts as GENERIC IP-only (the
 * enriched discoverNetworkPrinters contract).
 */
class SnmpDiscovery(
    private val scanner: HostProbe = NetworkScanner(),
    private val probe: SnmpIdentityProbe = SnmpIdentityProbe(),
    private val serialProbe: EscPosProbe = EscPosSerialProbe(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val localSubnetPrefix: () -> String? = { NetworkUtils.subnetPrefix(NetworkUtils.localIpv4()) },
) {

    suspend fun discover(
        snmpOnly: Boolean = true,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): List<DiscoveredPrinter> = withContext(dispatcher) {
        val found = CopyOnWriteArrayList<DiscoveredPrinter>()
        val seen = ConcurrentHashMap.newKeySet<String>()

        withTimeoutOrNull(timeoutMs) {
            val prefix = localSubnetPrefix() ?: return@withTimeoutOrNull
            coroutineScope {
                val jobs = mutableListOf<Deferred<Unit>>()
                for (host in 2..255) {
                    val ip = "$prefix$host"
                    jobs += async {
                        if (!scanner.ping(ip)) return@async
                        if (!seen.add(ip)) return@async
                        val identity = probe.probe(ip)
                        when {
                            identity != null -> found.add(buildPrinter(ip, identity))
                            !snmpOnly -> found.add(buildPrinter(ip, identity = null))
                        }
                    }
                    if (host % 10 == 0) delay(100) // throttle like the generic scan
                }
                jobs.awaitAll()
            }
        }
        found.distinctBy { it.ipAddress }
    }

    private suspend fun buildPrinter(ip: String, identity: SnmpIdentityProbe.SnmpIdentity?): DiscoveredPrinter {
        val snmpBrand = identity?.brand ?: PrinterBrand.GENERIC
        // When SNMP didn't positively brand the host (disabled / non-matching), fall back to the
        // printer's ESC/POS self-report: GS I 66 maker brands it, 67 model names it, 68 serial IDs it.
        // This is how a Sunmi/Epson/etc. that answers neither SNMP nor mDNS still gets branded.
        val escpos = if (snmpBrand == PrinterBrand.GENERIC || identity?.serial == null) {
            serialProbe.queryIdentity(ip, PORT)
        } else {
            null
        }
        val escBrand = escpos?.maker?.let { probe.matchBrand(it) }?.takeIf { it != PrinterBrand.GENERIC }
        val brand = if (snmpBrand != PrinterBrand.GENERIC) snmpBrand else escBrand ?: PrinterBrand.GENERIC
        val model = identity?.model ?: escpos?.model
        val serial = identity?.serial ?: escpos?.serial
        val mac = identity?.mac ?: NetworkUtils.macFromArp(ip).ifEmpty { null }
        // A host that answered ESC/POS GS I is demonstrably speaking ESC/POS; SNMP-only hosts
        // (no GS I reply) leave emulation null — the command set can't be proven from SNMP alone.
        val emulation = if (escpos != null) Emulation.ESC_POS else null
        return DiscoveredPrinter(
            name = model ?: "${brandLabel(brand)} Printer",
            connectionType = PrinterConnectionType.NETWORK,
            ipAddress = ip,
            port = PORT,
            macAddress = mac,
            serialNumber = serial,
            brand = brand,
            model = model,
            emulation = emulation,
        )
    }

    private fun brandLabel(brand: PrinterBrand): String =
        if (brand == PrinterBrand.GENERIC) "Network" else brand.name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 14_000L
        private const val PORT = 9100
    }
}
