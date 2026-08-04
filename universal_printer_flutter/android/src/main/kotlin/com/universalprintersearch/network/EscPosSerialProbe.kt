package com.universalprintersearch.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Brand-agnostic ESC/POS serial-number probe over raw TCP-9100.
 *
 * Sends `GS I 68` (bytes `1D 49 44` — "Transmit printer ID / serial number").
 * The reply is `_<serial>\0`; strip non-printable chars + the leading underscore.
 *
 * VERIFIED to work for BOTH Epson (GS I 68) and Sunmi cloud printers — in the
 * source repo, Sunmi's `customQuerySn()` is literally `GS_I_SERIAL` (`1D 49 44`),
 * the same command. Does NOT apply to Rongta (uses `GS ( H`) or Seiko.
 *
 * Never throws — returns null on any failure (unreachable, refused, timeout,
 * empty/garbage reply).
 */
class EscPosSerialProbe : EscPosProbe {

    /**
     * One-socket identity probe: GS I 66 (maker) + 67 (model) + 68 (serial). The maker
     * string (e.g. "SUNMI", "EPSON") brands a printer that exposes neither SNMP nor mDNS.
     * Returns null if the host isn't an ESC/POS printer (no GS I replies at all).
     */
    override suspend fun queryIdentity(
        ip: String,
        port: Int,
        timeoutMs: Int,
    ): EscPosProbe.EscPosIdentity? = withContext(Dispatchers.IO) {
        if (ip.isEmpty()) return@withContext null
        val portToUse = if (port > 0) port else DEFAULT_PORT
        val timeout = if (timeoutMs > 0) timeoutMs else DEFAULT_TIMEOUT_MS
        var socket: Socket? = null
        try {
            socket = Socket().apply {
                connect(InetSocketAddress(ip, portToUse), CONNECT_TIMEOUT_MS)
                soTimeout = timeout
                try { tcpNoDelay = true } catch (_: Exception) {}
            }
            val out = socket.getOutputStream()
            val input = socket.getInputStream()
            // Maker first: a non-ESC/POS host (gateway, other device on 9100) returns nothing —
            // bail immediately instead of waiting out two more timeouts on model + serial.
            val maker = readField(out, input, GS_I_MAKER, timeout) ?: return@withContext null
            val model = readField(out, input, GS_I_MODEL, timeout)
            val serial = readField(out, input, GS_I_SERIAL, timeout)
            EscPosProbe.EscPosIdentity(maker, model, serial)
        } catch (e: Exception) {
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /** Send one GS I query, read the `_<value>\0` reply, strip the underscore + non-printables. */
    private fun readField(out: OutputStream, input: InputStream, cmd: ByteArray, timeoutMs: Int): String? {
        return try {
            drainRx(input)
            out.write(cmd)
            out.flush()
            val bytes = readReply(input, timeoutMs) { c -> c.toByteArray().any { it.toInt() == 0 } }
            if (bytes.isEmpty()) null else printableAscii(bytes).trimStart('_').trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun querySerial(
        ip: String,
        port: Int,
        timeoutMs: Int,
    ): String? = withContext(Dispatchers.IO) {
        if (ip.isEmpty()) return@withContext null
        val portToUse = if (port > 0) port else DEFAULT_PORT
        val timeout = if (timeoutMs > 0) timeoutMs else DEFAULT_TIMEOUT_MS
        var socket: Socket? = null
        try {
            socket = Socket().apply {
                connect(InetSocketAddress(ip, portToUse), CONNECT_TIMEOUT_MS)
                soTimeout = timeout
                try { tcpNoDelay = true } catch (_: Exception) {}
            }
            val out: OutputStream = socket.getOutputStream()
            val input: InputStream = socket.getInputStream()
            drainRx(input)
            out.write(GS_I_SERIAL)
            out.flush()
            val bytes = readReply(input, timeout) { c ->
                c.size() >= 3 && c.toByteArray().any { it.toInt() == 0 }
            }
            if (bytes.isEmpty()) null else printableAscii(bytes).trimStart('_').trim().ifEmpty { null }
        } catch (e: Exception) {
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun drainRx(input: InputStream) {
        try {
            val avail = input.available()
            if (avail > 0) input.read(ByteArray(avail))
        } catch (_: Exception) {
        }
    }

    private fun readReply(input: InputStream, timeoutMs: Int, stopWhen: (ByteArrayOutputStream) -> Boolean): ByteArray {
        val collected = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val read = try { input.read(buffer) } catch (_: SocketTimeoutException) { -1 }
            if (read <= 0) break
            collected.write(buffer, 0, read)
            if (stopWhen(collected)) break
            try {
                Thread.sleep(POLL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return collected.toByteArray()
    }

    private fun printableAscii(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v in 0x20..0x7E) sb.append(v.toChar())
        }
        return sb.toString()
    }

    companion object {
        const val DEFAULT_PORT = 9100
        const val DEFAULT_TIMEOUT_MS = 3000
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val BUFFER_SIZE = 256
        private const val POLL_MS = 80L

        // GS I n — Transmit printer ID. 66=maker/type, 67=model, 68=serial.
        private val GS_I_MAKER = byteArrayOf(0x1D, 0x49, 0x42)
        private val GS_I_MODEL = byteArrayOf(0x1D, 0x49, 0x43)
        private val GS_I_SERIAL = byteArrayOf(0x1D, 0x49, 0x44)
    }
}
