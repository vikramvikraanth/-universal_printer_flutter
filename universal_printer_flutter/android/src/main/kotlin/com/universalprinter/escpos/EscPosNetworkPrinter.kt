package com.universalprinter.escpos

import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.tcp.TcpConnection
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import com.universalprinter.QueuedPrinter
import com.universalprinter.StatusQueryable
import com.universalprinter.model.CutType
import com.universalprinter.model.PreflightResult
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintElement
import com.universalprinter.model.PrintErrorReason
import com.universalprinter.model.PrintResult
import com.universalprinter.model.PrinterStatus
import com.universalprinter.model.RetryPolicy
import com.universalprinter.preflight.Preflight
import com.universalprinter.util.Bitmaps
import com.universalprinter.util.retrying
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Network (TCP/9100) ESC/POS printer, backed by DantSu's [TcpConnection] + [EscPosPrinter].
 * A fresh connection is opened per job (the queue serializes them), rendered from the
 * device-agnostic [PrintDocument] via [EscPosRenderer].
 *
 * Timeouts:
 * - [connectTimeoutMs] (default 4s) is DantSu's `Socket.connect` timeout (verified from its bytecode).
 * - [writeTimeoutMs] (default 3s) caps the blocking write. DantSu's print path is write-only, so
 *   `SO_TIMEOUT` (a read timeout) would not apply; instead a watchdog force-closes the socket at the
 *   deadline, which is the only thing that unblocks a wedged TCP write. Requires a multi-threaded
 *   dispatcher (the default [Dispatchers.IO]) so the watchdog can run while the write blocks.
 *
 * Transient connectivity failures are retried per [retryPolicy] — but only during the connect
 * handshake, never the write (raw 9100 has no ack, so replaying a partial write would duplicate).
 */
class EscPosNetworkPrinter(
    private val host: String,
    private val port: Int = 9100,
    private val connectTimeoutMs: Int = 4_000,
    private val writeTimeoutMs: Long = 3_000,
    private val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
    private val statusReadTimeoutMs: Int = 1_500,
    private val minWriteDrainMs: Long = 60,
    private val maxWriteDrainMs: Long = 2_500,
    /** Discovered brand (e.g. "EPSON") — carried on the printer for brand-aware behaviour/diagnostics. */
    val brand: String? = null,
    /** Physical paper width in mm (58/72/80) from discovery; available for rendering/validation. */
    val paperWidthMm: Int? = null,
    preflightEnabled: Boolean = true,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : QueuedPrinter(dispatcher, preflightEnabled = preflightEnabled), StatusQueryable {

    override val name: String = (brand?.let { "$it " } ?: "") + "ESC/POS $host:$port"

    /** Pre-flight via a live `DLE EOT` query; unreachable/unsupported printers proceed (best-effort). */
    override suspend fun preflight(document: PrintDocument): PreflightResult = Preflight.escPos(queryStatus())

    /**
     * Live status via `DLE EOT` on a short-lived socket (works when the printer is idle — port 9100
     * refuses a second connection while a job streams). Returns null if the printer is unreachable or
     * doesn't answer (many low-end models don't implement real-time status).
     */
    override suspend fun queryStatus(): PrinterStatus? = withContext(dispatcher) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.soTimeout = statusReadTimeoutMs
                val out = socket.getOutputStream()
                val inp = socket.getInputStream()
                val printer = queryByte(out, inp, EscPosStatus.QUERY_PRINTER)
                val offline = queryByte(out, inp, EscPosStatus.QUERY_OFFLINE)
                val errorByte = queryByte(out, inp, EscPosStatus.QUERY_ERROR)
                val paper = queryByte(out, inp, EscPosStatus.QUERY_PAPER)
                if (printer < 0 || offline < 0 || errorByte < 0 || paper < 0) null
                else EscPosStatus.parse(printer, offline, errorByte, paper)
            }
        }.getOrNull()
    }

    private fun queryByte(out: OutputStream, inp: InputStream, cmd: ByteArray): Int {
        out.write(cmd); out.flush()
        return inp.read() // blocks up to soTimeout; -1 on EOF, SocketTimeoutException if the printer is silent
    }

    override suspend fun doConnect(): Boolean = runCatching {
        retrying(retryPolicy, ::isConnectivityError, onRetry = ::logRetry) {
            val c = TcpConnection(host, port, connectTimeoutMs)
            try {
                c.connect()
                c.isConnected
            } finally {
                runCatching { c.disconnect() }
            }
        }
    }.getOrDefault(false)

    override suspend fun doPrint(document: PrintDocument): PrintResult {
        // A whole-receipt image (e.g. PrintType.IMAGE) → our own paced GS v 0 band sender instead of
        // DantSu, so bands are written + flushed + drained one at a time and the printer never stalls.
        (document.elements.singleOrNull() as? PrintElement.Image)?.let { return printImagePaced(it, document) }
        var connection: TcpConnection? = null
        return try {
            // Retry ONLY the connect/handshake: no bytes are on the wire until the write below,
            // so re-opening the socket can never double-print.
            val printer = retrying(retryPolicy, ::isConnectivityError, onRetry = ::logRetry) {
                connection?.let { prev -> runCatching { prev.disconnect() } }
                val c = TcpConnection(host, port, connectTimeoutMs)
                connection = c
                buildEscPosPrinter(c, document, DPI)
            }
            val text = EscPosRenderer.render(document) { PrinterTextParserImg.bitmapToHexadecimalString(printer, it) }
            writeWithTimeout(connection, printer, document, text)
        } catch (e: Exception) {
            PrintResult.Error(e.message ?: "network print failed", e, escPosReason(e))
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    /**
     * Runs the (blocking) write with a hard [writeTimeoutMs] cap. A watchdog coroutine closes the
     * socket at the deadline; that makes the blocked write throw, which we map to a timeout error.
     */
    private suspend fun writeWithTimeout(
        connection: TcpConnection?,
        printer: EscPosPrinter,
        document: PrintDocument,
        text: String,
    ): PrintResult = coroutineScope {
        val timedOut = AtomicBoolean(false)
        val watchdog = launch {
            delay(writeTimeoutMs)
            timedOut.set(true)
            runCatching { connection?.disconnect() }
        }
        try {
            when {
                document.openDrawer -> printer.printFormattedTextAndOpenCashBox(text, 0)
                document.cut != CutType.NONE -> printer.printFormattedTextAndCut(text)
                else -> printer.printFormattedText(text)
            }
            // Write returned — stop the force-close watchdog, then DRAIN: give the printer time to
            // consume its buffer before we close the socket. Closing immediately after a large write
            // can truncate the receipt/image on network printers ("stuck"/incomplete print). Delay is
            // proportional to the payload (~1ms per 64 chars, floored/capped).
            watchdog.cancel()
            delay(drainMs(text))
            printer.disconnectPrinter()
            PrintResult.Success()
        } catch (e: Exception) {
            if (timedOut.get()) PrintResult.Error("network print timed out after ${writeTimeoutMs}ms", e, PrintErrorReason.TIMEOUT)
            else PrintResult.Error(e.message ?: "network write failed", e, escPosReason(e))
        } finally {
            watchdog.cancel()
        }
    }

    /**
     * Paced whole-image sender: connect, then write `GS v 0` bands one at a time (flush + a short drain
     * per band) so the printer keeps up. Bypasses DantSu — whose single un-paced send can truncate a
     * large raster ("stuck"/incomplete image). Connect is retried per [retryPolicy].
     */
    private suspend fun printImagePaced(image: PrintElement.Image, document: PrintDocument): PrintResult = withContext(dispatcher) {
        var socket: Socket? = null
        try {
            val s = retrying(retryPolicy, ::isConnectivityError, onRetry = ::logRetry) {
                socket?.let { runCatching { it.close() } }
                val sock = Socket()
                socket = sock
                sock.connect(InetSocketAddress(host, port), connectTimeoutMs)
                runCatching { sock.sendBufferSize = 16 * 1024 }
                sock
            }
            val out = s.getOutputStream()
            var bmp = Bitmaps.scaleToWidth(image.bitmap, document.paper.widthPx)
            if (image.dither) bmp = Bitmaps.dither(bmp)
            if (image.invert) bmp = Bitmaps.invert(bmp)
            out.write(EscPosRaster.INIT)
            out.write(EscPosRaster.align(image.align))
            for (band in EscPosRaster.bands(bmp)) {
                out.write(band)
                out.flush()
                delay((band.size / 64L).coerceIn(5, 400)) // pace each band so the printer drains it
            }
            out.write(EscPosRaster.feed(3))
            EscPosRaster.cut(document.cut).takeIf { it.isNotEmpty() }?.let { out.write(it) }
            out.flush()
            delay(minWriteDrainMs)
            PrintResult.Success()
        } catch (e: Exception) {
            PrintResult.Error(e.message ?: "network image print failed", e, escPosReason(e))
        } finally {
            runCatching { socket?.close() }
        }
    }

    /** Post-write drain (ms), proportional to payload so the printer empties its buffer before close. */
    private fun drainMs(text: String): Long = (text.length / 64L).coerceIn(minWriteDrainMs, maxWriteDrainMs)

    private fun logRetry(attempt: Int, error: Throwable, delayMs: Long) {
        android.util.Log.w("EscPosNetworkPrinter", "connect attempt $attempt to $host:$port failed (${error.message}); retrying in ${delayMs}ms")
    }

    private companion object {
        const val DPI = 203

        /**
         * Retry only connectivity failures. DantSu wraps socket connect/send errors as
         * [EscPosConnectionException] (verified: it extends [Exception], not [IOException]); raw
         * socket errors are caught defensively. Content errors (parser/encoding/barcode) are
         * deterministic and must NOT be retried.
         */
        fun isConnectivityError(t: Throwable): Boolean =
            t is EscPosConnectionException || t is IOException
    }
}
