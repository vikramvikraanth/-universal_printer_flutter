package com.universalprintersearch.network.star

import android.util.Log
import com.universalprintersearch.model.DiscoveredPrinter
import com.universalprintersearch.model.PrinterBrand
import com.universalprintersearch.model.PrinterConnectionType
import com.universalprintersearch.network.HostProbe
import com.universalprintersearch.network.NetworkScanner
import com.universalprintersearch.network.PrinterDiscoverer
import com.universalprintersearch.util.NetworkUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * SDK-free Star discovery via the printer's built-in web-configuration server
 * (port 80) — no StarXpand SDK. Star LAN printers (TSP100IV, TSP650II, mC-Print, …)
 * serve an "<model> Web Configuration" page whose HTML contains the MAC address.
 *
 * Per host: TCP-80 reachable → GET / → if it's a Star web config, scrape MAC + model,
 * then derive emulation via [StarModelEmulation] (the same model→emulation table the
 * StarXpand SDK uses). Yields brand=STAR + IP + MAC + model + emulation, SDK-free.
 *
 * Not obtainable this way (nor from the SDK's discovery): the serial number.
 */
class StarWebDiscoverer(
    private val portCheck: HostProbe = NetworkScanner(port = HTTP_PORT),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val localSubnetPrefix: () -> String? = { NetworkUtils.subnetPrefix(NetworkUtils.localIpv4()) },
) : PrinterDiscoverer {

    override suspend fun discover(): List<DiscoveredPrinter> = withContext(dispatcher) {
        val prefix = localSubnetPrefix() ?: return@withContext emptyList()
        coroutineScope {
            val jobs = mutableListOf<Deferred<DiscoveredPrinter?>>()
            for (host in 2..255) {
                val ip = "$prefix$host"
                jobs += async { if (portCheck.ping(ip)) probe(ip) else null }
                if (host % 10 == 0) delay(50)
            }
            jobs.awaitAll().filterNotNull().distinctBy { it.macAddress ?: it.ipAddress }
        }
    }

    private fun probe(ip: String): DiscoveredPrinter? {
        val body = httpGet(ip) ?: return null
        if (!body.contains("Star Micronics", ignoreCase = true)) return null // not a Star web config
        val model = MODEL_RE.find(body)?.groupValues?.getOrNull(1)?.trim()
        val mac = macFrom(body, ip)
        Log.d(TAG, "$ip: Star web config, model=$model mac=$mac")
        return DiscoveredPrinter(
            name = model ?: "Star Printer",
            connectionType = PrinterConnectionType.NETWORK,
            ipAddress = ip,
            port = PRINT_PORT,
            macAddress = mac,
            brand = PrinterBrand.STAR,
            model = model,
            emulation = StarModelEmulation.emulationFor(model),
        )
    }

    /**
     * The Star page lists every interface (e.g. wired LAN + WLAN), each with its own MAC.
     * Pick the MAC of the interface whose IP is the one we actually reached — an inactive
     * NIC (e.g. unplugged LAN showing 0.0.0.0) has a different MAC we must not return.
     * Falls back to the first Star-OUI (00:11:62) MAC, then any MAC.
     */
    internal fun macFrom(body: String, ip: String): String? {
        val activeBlockMac = body.split(Regex("(?i)Network Status"))
            .firstOrNull { it.contains(ip) }
            ?.let { MAC_RE.find(it)?.value }
        val mac = activeBlockMac
            ?: MAC_RE.findAll(body).map { it.value }.firstOrNull { it.uppercase().startsWith(STAR_OUI) }
            ?: MAC_RE.find(body)?.value
        return mac?.let { NetworkUtils.normalizeMac(it.uppercase()) }?.ifEmpty { null }
    }

    /**
     * Raw-socket HTTP/1.0 GET. Deliberately NOT HttpURLConnection/OkHttp: those enforce
     * Android's cleartext-HTTP policy (blocked by default on targetSdk 28+), which would
     * make this silently fail. A raw socket isn't subject to that policy — and it matches
     * the library's SDK-free, raw-socket approach. "Connection: close" → read to EOF.
     */
    private fun httpGet(ip: String): String? {
        var socket: Socket? = null
        return try {
            socket = Socket().apply {
                connect(InetSocketAddress(ip, HTTP_PORT), CONNECT_TIMEOUT_MS)
                soTimeout = READ_TIMEOUT_MS
            }
            socket.getOutputStream().apply {
                write("GET / HTTP/1.0\r\nHost: $ip\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
            socket.getInputStream().bufferedReader().use { it.readText().take(MAX_BODY) }
        } catch (e: Exception) {
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    companion object {
        private const val TAG = "StarWebDiscoverer"
        private const val HTTP_PORT = 80
        private const val PRINT_PORT = 9100
        private const val STAR_OUI = "00:11:62"
        private const val CONNECT_TIMEOUT_MS = 1500
        private const val READ_TIMEOUT_MS = 2000
        private const val MAX_BODY = 65_536
        private val MODEL_RE = Regex("""([A-Za-z0-9_+\-]+)\s+Web Configuration""")
        private val MAC_RE = Regex("""([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}""")
    }
}
