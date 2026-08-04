package com.universalprintersearch.network.epson

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * SDK-free Epson identity over a fresh raw TCP-9100 socket.
 *
 *   GS I 66 (1D 49 42) -> maker name; an "EPSON" substring in the printable
 *   reply positively identifies the brand.
 *   GS I 68 (1D 49 44) -> serial number ("_<serial>\0"). The serial is the only
 *   stable unique id on Android 11+ where MAC addresses are hidden.
 *
 * On a fresh socket the RX queue is empty, so responses land within
 * milliseconds even when the printer is offline (cover open / paper out).
 * Never throws — returns EpsonInfo(false, null) on any failure.
 */
class EpsonTcpProbe {

    data class EpsonInfo(val isEpson: Boolean, val serial: String?)

    suspend fun probe(
        ip: String,
        port: Int = DEFAULT_PORT,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): EpsonInfo = withContext(Dispatchers.IO) {
        if (ip.isEmpty()) return@withContext EpsonInfo(false, null)
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
            val isEpson = isProbablyEpson(out, input, timeout)
            val serial = if (isEpson) probeSerial(out, input, timeout) else null
            EpsonInfo(isEpson, serial)
        } catch (e: Exception) {
            EpsonInfo(false, null)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /** GS I 66 (maker). True iff the printable response contains "EPSON". */
    private fun isProbablyEpson(out: OutputStream, input: InputStream, timeoutMs: Int): Boolean = try {
        drainRx(input)
        out.write(GS_I_MAKER)
        out.flush()
        val bytes = readReply(input, timeoutMs, MAKER_POLL_MS) { it.size() >= 6 }
        bytes.isNotEmpty() && printableAscii(bytes).uppercase().contains("EPSON")
    } catch (_: Exception) {
        false
    }

    /** GS I 68 (serial). Reply is "_<serial>\0"; strip non-printable + leading underscore. */
    private fun probeSerial(out: OutputStream, input: InputStream, timeoutMs: Int): String? = try {
        drainRx(input)
        out.write(GS_I_SERIAL)
        out.flush()
        val bytes = readReply(input, timeoutMs, SERIAL_POLL_MS) { c ->
            c.size() >= 3 && c.toByteArray().any { it.toInt() == 0 }
        }
        if (bytes.isEmpty()) null else printableAscii(bytes).trimStart('_').trim().ifEmpty { null }
    } catch (_: Exception) {
        null
    }

    /** Drain bytes already sitting in the RX buffer so a stale response can't desync the parser. */
    private fun drainRx(input: InputStream) {
        try {
            val avail = input.available()
            if (avail > 0) input.read(ByteArray(avail))
        } catch (_: Exception) {
        }
    }

    private fun readReply(
        input: InputStream,
        timeoutMs: Int,
        pollMs: Long,
        stopWhen: (ByteArrayOutputStream) -> Boolean,
    ): ByteArray {
        val collected = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val read = try { input.read(buffer) } catch (_: SocketTimeoutException) { -1 }
            if (read <= 0) break
            collected.write(buffer, 0, read)
            if (stopWhen(collected)) break
            try {
                Thread.sleep(pollMs)
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
        private const val SERIAL_POLL_MS = 80L
        private const val MAKER_POLL_MS = 100L

        // GS I n — n selects the info type. 66=maker, 68=serial.
        private val GS_I_MAKER = byteArrayOf(0x1D, 0x49, 0x42)
        private val GS_I_SERIAL = byteArrayOf(0x1D, 0x49, 0x44)
    }
}
