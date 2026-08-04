package com.universalprintersearch.network.epson

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
import java.util.Locale

/**
 * SDK-free Epson discovery via ENPC (Epson Network Printer Communication) over
 * UDP port 3289 — the mechanism EpsonNet Config and the ePOS SDK use internally.
 * No Epson SDK, no proprietary jar.
 *
 * Only Epson devices answer ENPC, so any responder is definitively an Epson
 * printer. UDP is used for identity (not TCP-9100) because Epson TM printers
 * accept ~one concurrent TCP-9100 connection, so a GS I probe often can't even
 * connect — whereas ENPC has no such limit and returns the real MAC.
 *
 *   fn=0x00 reply -> MAC at byte offset 54 (verified on TM-m30III).
 *   fn=0x02 reply -> IEEE-1284 ID string (MFG/CMD/MDL...) -> model name.
 */
class EpsonUdpDiscovery {

    data class UdpDevice(val ip: String, val mac: String, val model: String)

    /**
     * Broadcast fn=0x00, collect responders for up to [timeoutMs], then enrich
     * each with its model via fn=0x02. De-duped by IP. Never throws — returns
     * whatever was discovered (empty on failure) so the caller can fall back to TCP.
     */
    suspend fun discover(context: Context, timeoutMs: Long = DEFAULT_WINDOW_MS): List<UdpDevice> =
        withContext(Dispatchers.IO) {
            val macByIp = LinkedHashMap<String, String>()
            val ownIp = NetworkUtils.localIpv4()
            var lock: WifiManager.MulticastLock? = null
            var socket: DatagramSocket? = null
            try {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                lock = wifi?.createMulticastLock("epson-enpc")?.apply {
                    setReferenceCounted(true)
                    runCatching { acquire() }
                }
                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = RECEIVE_SLICE_MS
                }

                // --- Phase 1: broadcast fn=0x00, collect ip -> MAC ---
                val query = enpcQuery(0x00)
                val bcast = InetAddress.getByName("255.255.255.255")
                val subnetBcast = NetworkUtils.subnetBroadcast(ownIp)
                    ?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
                val deadline = System.currentTimeMillis() + timeoutMs
                var nextSendAt = 0L
                val buf = ByteArray(1024)
                while (System.currentTimeMillis() < deadline) {
                    if (System.currentTimeMillis() >= nextSendAt) {
                        runCatching { socket.send(DatagramPacket(query, query.size, bcast, ENPC_PORT)) }
                        subnetBcast?.let { runCatching { socket.send(DatagramPacket(query, query.size, it, ENPC_PORT)) } }
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
                    if (ip == ownIp) continue
                    val payload = packet.data.copyOf(packet.length)
                    if (!startsWith(payload, ENPC_HEADER)) continue
                    if (!macByIp.containsKey(ip)) {
                        macByIp[ip] = parseMac(payload)
                    }
                }

                // --- Phase 2: per responder, fn=0x02 for the model name ---
                macByIp.map { (ip, mac) -> UdpDevice(ip, mac, queryModel(socket, ip)) }
            } catch (e: Exception) {
                Log.e(TAG, "discover failed: ${e.javaClass.simpleName}: ${e.message}")
                emptyList()
            } finally {
                runCatching { socket?.close() }
                runCatching { if (lock?.isHeld == true) lock.release() }
            }
        }

    /** ENPC query: "EPSONQ" + function(LE) + fixed length/params. */
    private fun enpcQuery(fn: Int): ByteArray = byteArrayOf(
        0x45, 0x50, 0x53, 0x4F, 0x4E, 0x51, // "EPSONQ"
        fn.toByte(), 0x00, 0x00, 0x00,
        0x10, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    /** Unicast fn=0x02 and parse the MDL (model) token from the IEEE-1284 ID string. "" if unavailable. */
    private fun queryModel(socket: DatagramSocket, ip: String): String = try {
        val q = enpcQuery(0x02)
        socket.send(DatagramPacket(q, q.size, InetAddress.getByName(ip), ENPC_PORT))
        val buf = ByteArray(1024)
        val packet = DatagramPacket(buf, buf.size)
        val deadline = System.currentTimeMillis() + 1000
        var result = ""
        while (System.currentTimeMillis() < deadline) {
            socket.receive(packet)
            if (packet.address?.hostAddress != ip) continue
            val ascii = String(packet.data, 0, packet.length, Charsets.US_ASCII)
            result = idField(ascii, "MDL:")
            break
        }
        result
    } catch (_: Exception) {
        ""
    }

    /** Extract a `KEY:value;` token from an IEEE-1284 ID string. */
    private fun idField(ascii: String, key: String): String {
        val start = ascii.indexOf(key)
        if (start < 0) return ""
        val from = start + key.length
        val end = ascii.indexOf(';', from).let { if (it < 0) ascii.length else it }
        return ascii.substring(from, end).trim()
    }

    /** MAC = 6 bytes at [MAC_OFFSET]. Returns "" if too short or obviously invalid (all-zero / all-FF). */
    private fun parseMac(payload: ByteArray): String {
        if (payload.size < MAC_OFFSET + 6) return ""
        val mac = payload.copyOfRange(MAC_OFFSET, MAC_OFFSET + 6)
        if (mac.all { it.toInt() == 0 } || mac.all { it.toInt() and 0xFF == 0xFF }) return ""
        return mac.joinToString(":") { String.format(Locale.ROOT, "%02X", it.toInt() and 0xFF) }
    }

    private fun startsWith(data: ByteArray, prefix: ByteArray): Boolean {
        if (data.size < prefix.size) return false
        for (i in prefix.indices) if (data[i] != prefix[i]) return false
        return true
    }

    companion object {
        private const val TAG = "EpsonUdpDiscovery"
        private const val ENPC_PORT = 3289
        private const val RECEIVE_SLICE_MS = 700
        private const val MAC_OFFSET = 54 // verified: TM-m30III fn0 reply, 6-byte MAC at offset 54
        const val DEFAULT_WINDOW_MS = 4000L

        // ENPC replies start with the 5-byte ASCII header "EPSON".
        private val ENPC_HEADER = byteArrayOf(0x45, 0x50, 0x53, 0x4F, 0x4E)
    }
}
