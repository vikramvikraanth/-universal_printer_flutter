package com.universalprintersearch.network.sunmi

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.universalprintersearch.model.DiscoveredPrinter
import com.universalprintersearch.model.Emulation
import com.universalprintersearch.model.PrinterBrand
import com.universalprintersearch.model.PrinterConnectionType
import com.universalprintersearch.network.EscPosProbe
import com.universalprintersearch.network.EscPosSerialProbe
import com.universalprintersearch.util.NetworkUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * SDK-free Sunmi cloud-printer discovery via Android Network Service Discovery
 * (DNS-SD / mDNS) — the platform `NsdManager`. No Sunmi SDK, no jar.
 *
 * WHY this is the right mechanism (not the UDP-17899 broadcast some docs mention):
 * the shipping Sunmi SDK (`external-printerlibrary2:1.0.13`) discovers LAN cloud
 * printers exactly this way — verified by decompiling its `LanHelper`:
 *   - service type = `_afpovertcp._tcp.`
 *   - `NsdManager.discoverServices(type, PROTOCOL_DNS_SD, listener)`
 *   - each found service is resolved, then FILTERED to names starting with
 *     `CloudPrint_` (this is what distinguishes a Sunmi cloud printer from any
 *     other `_afpovertcp._tcp` responder such as a Mac)
 *   - printer = { name = serviceName, ip = resolved host, port = resolved port }
 *
 * Resolves are performed one-at-a-time (serialized in the collection loop) because
 * `NsdManager.resolveService` fails with FAILURE_ALREADY_ACTIVE if another resolve
 * is in flight on Android < 12.
 */
class SunmiDiscovery(
    private val serialProbe: EscPosProbe = EscPosSerialProbe(),
) {

    suspend fun discover(context: Context, timeoutMs: Long = DEFAULT_TIMEOUT_MS): List<DiscoveredPrinter> =
        coroutineScope {
            val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
                ?: return@coroutineScope emptyList()

            val results = LinkedHashMap<String, DiscoveredPrinter>()
            val channel = Channel<NsdServiceInfo>(Channel.UNLIMITED)

            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.d(TAG, "start discovery failed: $errorCode")
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    // Cheap prefix pre-filter on the (unresolved) name; the resolve step re-checks.
                    if (serviceInfo.serviceName?.startsWith(SERVICE_NAME_PREFIX) == true) {
                        channel.trySend(serviceInfo)
                    }
                }
            }

            var started = false
            try {
                nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                started = true
                // Collect + resolve until the window elapses. resolveService is serialized
                // here (one awaited resolve per loop turn) to avoid FAILURE_ALREADY_ACTIVE.
                withTimeoutOrNull(timeoutMs) {
                    for (info in channel) {
                        val name = info.serviceName ?: continue
                        if (results.containsKey(name)) continue
                        val printer = resolve(nsd, info) ?: continue
                        results[name] = enrichIdentity(printer)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "discover failed: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                if (started) runCatching { nsd.stopServiceDiscovery(listener) }
                channel.close()
            }
            results.values.toList()
        }

    /**
     * Fill the unique identifier. Sunmi cloud printers answer `GS I 68` over TCP-9100
     * with a serial number (verified: their `customQuerySn` == `GS_I_SERIAL`). MAC is a
     * best-effort ARP lookup that only resolves on Android <= 9 (hidden by the OS above).
     */
    private suspend fun enrichIdentity(printer: DiscoveredPrinter): DiscoveredPrinter {
        val ip = printer.ipAddress ?: return printer
        val serial = serialProbe.querySerial(ip, printer.port)
        val mac = NetworkUtils.macFromArp(ip).ifEmpty { null }
        // Sunmi cloud printers answer ESC/POS GS I (that's how the serial is read).
        return printer.copy(serialNumber = serial, macAddress = mac, emulation = Emulation.ESC_POS)
    }

    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): DiscoveredPrinter? =
        suspendCancellableCoroutine { cont ->
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val name = serviceInfo.serviceName
                    val ip = serviceInfo.host?.hostAddress
                    val printer = if (name != null && name.startsWith(SERVICE_NAME_PREFIX) && ip != null) {
                        DiscoveredPrinter(
                            name = name,
                            connectionType = PrinterConnectionType.NETWORK,
                            ipAddress = ip,
                            port = if (serviceInfo.port > 0) serviceInfo.port else DEFAULT_PRINT_PORT,
                            brand = PrinterBrand.SUNMI,
                        )
                    } else {
                        null
                    }
                    if (cont.isActive) cont.resume(printer)
                }
            })
        }

    companion object {
        private const val TAG = "SunmiDiscovery"
        // Verified from external-printerlibrary2:1.0.13 LanHelper.
        private const val SERVICE_TYPE = "_afpovertcp._tcp."
        private const val SERVICE_NAME_PREFIX = "CloudPrint_"
        private const val DEFAULT_PRINT_PORT = 9100
        const val DEFAULT_TIMEOUT_MS = 8_000L
    }
}
