package com.universalprintersearch.network.epson

import android.content.Context
import com.universalprintersearch.model.DiscoveredPrinter
import com.universalprintersearch.model.Emulation
import com.universalprintersearch.model.PrinterBrand
import com.universalprintersearch.model.PrinterConnectionType
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
 * Epson network discovery, SDK-free, bounded to [SEARCH_TIMEOUT_MS].
 *
 *   Phase 1 — ENPC UDP broadcast: reliable IP + MAC (+ model), no TCP dependency.
 *   Phase 2 — per UDP hit: best-effort GS I 68 serial over TCP-9100 (often fails
 *             because Epson's single 9100 socket is busy — the UDP MAC is the id).
 *   Phase 3 — TCP fallback (only when UDP is silent): subnet sweep + GS I probe.
 *
 * The UDP MAC is the PRIMARY identifier (version-independent, survives Android
 * 11+ MAC-hiding); the GS I serial is the BACKUP.
 */
class EpsonDiscovery(
    private val udp: EpsonUdpDiscovery = EpsonUdpDiscovery(),
    private val tcp: EpsonTcpProbe = EpsonTcpProbe(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun discover(context: Context, timeoutMs: Long = SEARCH_TIMEOUT_MS): List<DiscoveredPrinter> =
        withContext(dispatcher) {
            val found = CopyOnWriteArrayList<DiscoveredPrinter>()
            val seen = ConcurrentHashMap.newKeySet<String>()

            withTimeoutOrNull(timeoutMs) {
                coroutineScope {
                    val udpDevices = udp.discover(context, UDP_WINDOW_MS)
                    if (udpDevices.isNotEmpty()) {
                        // Phase 2: enrich each UDP hit with a best-effort serial, concurrently.
                        udpDevices.map { dev ->
                            async {
                                if (!seen.add(dev.ip)) return@async
                                val serial = runCatching { tcp.probe(dev.ip, PORT, TCP_PROBE_MS).serial }.getOrNull()
                                found.add(buildPrinter(dev.ip, serial, dev.mac, dev.model))
                            }
                        }.awaitAll()
                    } else {
                        // Phase 3: subnet sweep + GS I probe on each reachable host.
                        val prefix = NetworkUtils.subnetPrefix(NetworkUtils.localIpv4())
                        if (prefix != null) {
                            val jobs = mutableListOf<Deferred<Unit>>()
                            for (host in 2..255) {
                                val ip = "$prefix$host"
                                jobs += async {
                                    val info = runCatching { tcp.probe(ip, PORT, TCP_PROBE_MS) }.getOrNull()
                                    if (info?.isEpson == true && seen.add(ip)) {
                                        found.add(buildPrinter(ip, info.serial, "", ""))
                                    }
                                }
                                if (host % 10 == 0) delay(100) // throttle like the generic scan
                            }
                            jobs.awaitAll()
                        }
                    }
                }
            }
            found.toList()
        }

    private fun buildPrinter(ip: String, serial: String?, mac: String, model: String) = DiscoveredPrinter(
        name = model.ifEmpty { "Epson Printer" },
        connectionType = PrinterConnectionType.NETWORK,
        ipAddress = ip,
        port = PORT,
        macAddress = mac.ifEmpty { null },
        serialNumber = serial?.ifEmpty { null },
        brand = PrinterBrand.EPSON,
        model = model.ifEmpty { null },
        emulation = Emulation.ESC_POS, // Epson TM printers speak ESC/POS (confirmed via GS I maker)
    )

    companion object {
        const val SEARCH_TIMEOUT_MS = 10_000L
        private const val UDP_WINDOW_MS = 4_000L
        private const val TCP_PROBE_MS = 3000
        private const val PORT = 9100
    }
}
