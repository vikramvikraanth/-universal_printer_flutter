package com.universalprintersearch.network.snmp

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * SDK-free SNMPv1 GET over a raw UDP socket (default port 161). One request →
 * one response per call. Never throws — returns null on any failure (host down,
 * SNMP disabled, wrong community, timeout, malformed reply).
 *
 * SNMP is the vendor-neutral, SDK-free way to read a network printer's identity:
 * make/model/serial live at standard MIB OIDs (see [SnmpIdentityProbe]).
 */
class SnmpClient(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SnmpTransport {

    override suspend fun get(ip: String, oid: String, community: String): Snmp.VarBind? = withContext(dispatcher) {
        if (ip.isEmpty()) return@withContext null
        var socket: DatagramSocket? = null
        try {
            val request = Snmp.encodeGetRequest(oid, community, REQUEST_ID)
            socket = DatagramSocket().apply { soTimeout = DEFAULT_TIMEOUT_MS }
            val addr = InetAddress.getByName(ip)
            socket.send(DatagramPacket(request, request.size, addr, SNMP_PORT))
            val buffer = ByteArray(RESPONSE_BUFFER)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            Snmp.decodeResponse(buffer.copyOf(packet.length))
        } catch (e: Exception) {
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    companion object {
        const val SNMP_PORT = 161
        const val DEFAULT_COMMUNITY = "public"
        const val DEFAULT_TIMEOUT_MS = 1500
        private const val REQUEST_ID = 1
        private const val RESPONSE_BUFFER = 2048
    }
}
