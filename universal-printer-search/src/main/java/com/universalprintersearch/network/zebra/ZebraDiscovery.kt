package com.universalprintersearch.network.zebra

import android.content.Context
import com.universalprintersearch.model.DiscoveredPrinter
import com.universalprintersearch.model.Emulation
import com.universalprintersearch.model.PrinterBrand
import com.universalprintersearch.model.PrinterConnectionType
import com.universalprintersearch.network.snmp.SnmpIdentityProbe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * SDK-free Zebra discovery: UDP-4201 broadcast ([ZebraUdpDiscovery]) for IP /
 * serial / model, then a per-host SNMP probe to fill the MAC address (the UDP
 * reply carries no MAC). MAC/SNMP is best-effort — a Zebra with SNMP disabled
 * still surfaces from the UDP reply, just without a MAC.
 */
class ZebraDiscovery(
    private val udp: ZebraUdpDiscovery = ZebraUdpDiscovery(),
    private val snmp: SnmpIdentityProbe = SnmpIdentityProbe(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun discover(context: Context, timeoutMs: Long = SEARCH_TIMEOUT_MS): List<DiscoveredPrinter> =
        withContext(dispatcher) {
            withTimeoutOrNull(timeoutMs) {
                val devices = udp.discover(context, UDP_WINDOW_MS)
                coroutineScope {
                    devices.map { dev ->
                        async {
                            val mac = runCatching { snmp.probe(dev.ip)?.mac }.getOrNull()
                            buildPrinter(dev, mac)
                        }
                    }.awaitAll()
                }
            }?.distinctBy { it.ipAddress } ?: emptyList()
        }

    private fun buildPrinter(dev: ZebraUdpDiscovery.ZebraDevice, mac: String?) = DiscoveredPrinter(
        name = dev.model.ifEmpty { dev.hostname.ifEmpty { "Zebra Printer" } },
        connectionType = PrinterConnectionType.NETWORK,
        ipAddress = dev.ip,
        port = PORT,
        macAddress = mac,
        serialNumber = dev.serial.ifEmpty { null },
        brand = PrinterBrand.ZEBRA,
        model = dev.model.ifEmpty { null },
        emulation = Emulation.ZPL, // Zebra Link-OS printers speak ZPL (mobile models also CPCL)
    )

    companion object {
        const val SEARCH_TIMEOUT_MS = 10_000L
        private const val UDP_WINDOW_MS = 4_000L
        private const val PORT = 9100
    }
}
