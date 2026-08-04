package com.universalprintersearch.network

import com.universalprintersearch.model.DiscoveredPrinter
import com.universalprintersearch.model.Emulation
import com.universalprintersearch.model.PrinterBrand
import com.universalprintersearch.model.PrinterConnectionType
import com.universalprintersearch.util.NetworkUtils
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
 * Raw-socket reachability + /24 subnet sweep. No vendor SDK.
 *
 * NOTE: "ping" here is a TCP connect to the raw-print port (default 9100), NOT
 * ICMP. Android forbids raw ICMP sockets without root, and any ESC/POS network
 * printer accepts a TCP-9100 connection, so a successful connect is the
 * reachability signal.
 */
class NetworkScanner(
    private val port: Int = DEFAULT_PORT,
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
) : HostProbe {

    /** True iff a TCP connect to [ip]:[port] succeeds within the timeout, retried up to [maxRetries]. */
    override suspend fun ping(ip: String, maxRetries: Int): Boolean = withContext(Dispatchers.IO) {
        repeat(maxRetries) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(ip, port), connectTimeoutMs)
                return@withContext true
            } catch (e: Exception) {
                // fall through and retry
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
        false
    }

    /**
     * Sweep x.x.x.2 .. x.x.x.255 for hosts answering on [port]. Concurrency is
     * throttled to [BATCH_SIZE] connects per [BATCH_DELAY_MS] to avoid exhausting
     * file descriptors / thread pool on large subnets.
     */
    suspend fun scanSubnet(): List<DiscoveredPrinter> = withContext(Dispatchers.IO) {
        val prefix = NetworkUtils.subnetPrefix(NetworkUtils.localIpv4()) ?: return@withContext emptyList()
        coroutineScope {
            val jobs = mutableListOf<Deferred<DiscoveredPrinter?>>()
            for (host in 2..255) {
                val ip = "$prefix$host"
                jobs += async {
                    if (ping(ip)) {
                        DiscoveredPrinter(
                            name = "Network Printer",
                            connectionType = PrinterConnectionType.NETWORK,
                            ipAddress = ip,
                            port = port,
                            macAddress = NetworkUtils.macFromArp(ip).ifEmpty { null },
                            brand = PrinterBrand.GENERIC,
                            // A host accepting a raw port-9100 connection is an ESC/POS receipt printer by
                            // definition (see class doc) — the transport carries no emulation field, so
                            // ESC/POS is the only safe default. Brand discovery sets its own real emulation.
                            emulation = Emulation.ESC_POS,
                        )
                    } else {
                        null
                    }
                }
                if (host % BATCH_SIZE == 0) delay(BATCH_DELAY_MS)
            }
            jobs.awaitAll().filterNotNull()
        }
    }

    companion object {
        const val DEFAULT_PORT = 9100
        const val DEFAULT_TIMEOUT_MS = 500
        const val DEFAULT_RETRIES = 2
        private const val BATCH_SIZE = 10
        private const val BATCH_DELAY_MS = 100L
    }
}
