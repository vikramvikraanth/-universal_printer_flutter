package com.universalprinter

import android.content.Context
import android.hardware.usb.UsbDevice
import com.universalprinter.escpos.EscPosNetworkPrinter
import com.universalprinter.escpos.EscPosUsbPrinter
import com.universalprinter.imin.IminPrinterBackend
import com.universalprinter.star.StarPrinterBackend
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.RetryPolicy
import com.universalprinter.sunmi.SunmiPrinterBackend

/**
 * Entry point / factory for the printing SDK. Each factory returns a [Printer] whose jobs are
 * serialized through its own per-printer coroutine queue. Paper width travels with each
 * `PrintDocument` (set by the parent app), so it isn't specified here.
 *
 * ```
 * val printer = UniversalPrinter.network("192.168.80.27")
 * printer.print(printDocument(PaperWidth.MM_80) { text("Hello", align = Align.CENTER); qr("id:1") })
 * ```
 */
object UniversalPrinter {

    /**
     * Network/TCP ESC/POS printer (raw-print port, default 9100). Transient connect failures are
     * retried per [retryPolicy] (default: 3 attempts with backoff); pass [RetryPolicy.NONE] to disable.
     */
    fun network(
        host: String,
        port: Int = 9100,
        brand: String? = null,
        paperWidthMm: Int? = null,
        retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
        preflightEnabled: Boolean = true,
    ): Printer = EscPosNetworkPrinter(
        host, port, brand = brand, paperWidthMm = paperWidthMm,
        retryPolicy = retryPolicy, preflightEnabled = preflightEnabled,
    )

    /**
     * Sunmi **Cloud Printer** (NT211/NT212 58mm, NT310/NT311 80mm) over LAN. This is a standalone
     * ESC/POS network printer (verified: raw TCP on port 9100) — distinct from the Sunmi built-in
     * printer ([sunmi]). It's an alias for [network]; discover its IP via the search module's Sunmi mDNS.
     */
    fun sunmiCloud(
        host: String,
        port: Int = 9100,
        retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
        preflightEnabled: Boolean = true,
    ): Printer = network(host, port, retryPolicy = retryPolicy, preflightEnabled = preflightEnabled)

    /** USB ESC/POS printer. Requests runtime USB permission on first print. */
    fun usb(context: Context, device: UsbDevice): Printer = EscPosUsbPrinter(context.applicationContext, device)

    /** Star printer via the StarXpand SDK. [identifier] is the MAC/IP from Star discovery. */
    fun star(context: Context, identifier: String, preflightEnabled: Boolean = true): Printer =
        StarPrinterBackend(context.applicationContext, identifier, preflightEnabled = preflightEnabled)

    /** Sunmi built-in printer (Sunmi hardware only). */
    fun sunmi(context: Context, preflightEnabled: Boolean = true): Printer =
        SunmiPrinterBackend(context.applicationContext, preflightEnabled = preflightEnabled)

    /** iMin built-in printer, v1 + v2 (iMin hardware only). */
    fun imin(context: Context): Printer = IminPrinterBackend(context.applicationContext)

    /**
     * Renders a [PrintDocument] to a self-contained HTML string for the app's receipt preview /
     * template view (embedded images + real barcodes/QR). Same HTML the [PrintType.IMAGE] path prints.
     */
    suspend fun receiptHtml(context: Context, document: PrintDocument): String = renderReceiptHtml(context, document)
}
