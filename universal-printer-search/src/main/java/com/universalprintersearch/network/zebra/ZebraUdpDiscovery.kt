package com.universalprintersearch.network.zebra

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.universalprintersearch.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * SDK-free Zebra discovery via Zebra's UDP broadcast protocol on port 4201 — the
 * mechanism the Link-OS "Network Discoverer" uses internally. No Zebra SDK / jar.
 *
 * Request : broadcast [ZEBRA_QUERY] (`2E 2C 3A 01 00 00`) to UDP 4201.
 * Response: unicast, starts with [ZEBRA_HEADER] (`3A 2C 2E`) + a 0x03 byte, then
 *           fixed-offset NUL-terminated ASCII fields (reverse-engineered):
 *             0x0C product name (model), 0x38 serial, 0x54 hostname.
 *           The reply carries IP but NO MAC — MAC is filled later via SNMP.
 *
 * Only Zebra devices answer on 4201, so any responder is definitively a Zebra.
 * Never throws — returns whatever was discovered (empty on failure).
 *
 * Offsets/bytes are ASSUMED from public reverse-engineering (see .memory D8), not
 * verified on hardware; all constants are centralized here for easy tuning.
 */
class ZebraUdpDiscovery {

    data class ZebraDevice(val ip: String, val serial: String, val model: String, val hostname: String)

    suspend fun discover(context: Context, timeoutMs: Long = DEFAULT_WINDOW_MS): List<ZebraDevice> =
        withContext(Dispatchers.IO) {
            val byIp = LinkedHashMap<String, ZebraDevice>()
            val ownIp = NetworkUtils.localIpv4()
            var lock: WifiManager.MulticastLock? = null
            var socket: DatagramSocket? = null
            try {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                lock = wifi?.createMulticastLock("zebra-disco")?.apply {
                    setReferenceCounted(true)
                    runCatching { acquire() }
                }
                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = RECEIVE_SLICE_MS
                }

                val bcast = InetAddress.getByName("255.255.255.255")
                val subnetBcast = NetworkUtils.subnetBroadcast(ownIp)
                    ?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
                val deadline = System.currentTimeMillis() + timeoutMs
                var nextSendAt = 0L
                val buf = ByteArray(1024)
                while (System.currentTimeMillis() < deadline) {
                    if (System.currentTimeMillis() >= nextSendAt) {
                        runCatching { socket.send(DatagramPacket(ZEBRA_QUERY, ZEBRA_QUERY.size, bcast, ZEBRA_PORT)) }
                        subnetBcast?.let { runCatching { socket.send(DatagramPacket(ZEBRA_QUERY, ZEBRA_QUERY.size, it, ZEBRA_PORT)) } }
                        nextSendAt = System.currentTimeMillis() + 1000
                    }
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    } catch (e: Exception) {
                        Log.d(TAG, "receive failed: ${e.message}"); continue
                    }
                    val ip = packet.address?.hostAddress ?: continue
                    if (ip == ownIp || byIp.containsKey(ip)) continue
                    val payload = packet.data.copyOf(packet.length)
                    byIp[ip] = parseReply(payload, ip) ?: continue
                }
                byIp.values.toList()
            } catch (e: Exception) {
                Log.e(TAG, "discover failed: ${e.javaClass.simpleName}: ${e.message}")
                emptyList()
            } finally {
                runCatching { socket?.close() }
                runCatching { if (lock?.isHeld == true) lock.release() }
            }
        }

    /** Parse a discovery reply for [ip]; null if it isn't a Zebra reply (bad header). */
    internal fun parseReply(payload: ByteArray, ip: String): ZebraDevice? {
        if (!startsWith(payload, ZEBRA_HEADER)) return null
        return ZebraDevice(
            ip = ip,
            serial = stringAt(payload, OFF_SERIAL),
            model = stringAt(payload, OFF_MODEL),
            hostname = stringAt(payload, OFF_HOSTNAME),
        )
    }

    /** NUL-terminated printable-ASCII field at [offset]. "" if out of range / empty. */
    private fun stringAt(payload: ByteArray, offset: Int, maxLen: Int = 32): String {
        if (offset >= payload.size) return ""
        val end = minOf(payload.size, offset + maxLen)
        val sb = StringBuilder()
        for (i in offset until end) {
            val v = payload[i].toInt() and 0xFF
            if (v == 0) break
            if (v in 0x20..0x7E) sb.append(v.toChar())
        }
        return sb.toString().trim()
    }

    private fun startsWith(data: ByteArray, prefix: ByteArray): Boolean {
        if (data.size < prefix.size) return false
        for (i in prefix.indices) if (data[i] != prefix[i]) return false
        return true
    }

    companion object {
        private const val TAG = "ZebraUdpDiscovery"
        private const val ZEBRA_PORT = 4201
        private const val RECEIVE_SLICE_MS = 700
        const val DEFAULT_WINDOW_MS = 4000L

        // Reverse-engineered field offsets in the discovery reply.
        private const val OFF_MODEL = 0x0C
        private const val OFF_SERIAL = 0x38
        private const val OFF_HOSTNAME = 0x54

        // Discovery request payload.
        private val ZEBRA_QUERY = byteArrayOf(0x2E, 0x2C, 0x3A, 0x01, 0x00, 0x00)
        // Replies start with ":,." (3A 2C 2E).
        private val ZEBRA_HEADER = byteArrayOf(0x3A, 0x2C, 0x2E)
    }
}
