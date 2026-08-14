package com.universalprinter.escpos

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.usb.UsbConnection
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import com.universalprinter.QueuedPrinter
import com.universalprinter.StatusQueryable
import com.universalprinter.model.CutType
import com.universalprinter.model.PreflightResult
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintErrorReason
import com.universalprinter.model.PrintResult
import com.universalprinter.model.PrinterStatus
import com.universalprinter.preflight.Preflight
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * USB ESC/POS printer, backed by DantSu's [UsbConnection] + [EscPosPrinter]. Requests runtime USB
 * permission on demand (see [UsbPermissionHelper]); listing/enumerating USB devices is the caller's
 * job (e.g. UsbManager.deviceList / the search SDK).
 */
class EscPosUsbPrinter(
    context: Context,
    private val device: UsbDevice,
    /** True for a 9-pin impact model (e.g. Epson TM-U* over USB) — forces text-only (no raster). */
    override val isImpact: Boolean = false,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : QueuedPrinter(dispatcher), StatusQueryable {

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val permission = UsbPermissionHelper(appContext)

    override val name: String = "USB ${device.deviceName}"

    override suspend fun doConnect(): Boolean =
        usbManager.hasPermission(device) || permission.ensurePermission(device)

    /**
     * Pre-flight via a raw-USB `DLE EOT` status read (mirrors the reference package's USB status
     * manager). Needs USB permission and an idle printer; a printer that doesn't answer proceeds
     * (best-effort) rather than blocking.
     */
    override suspend fun preflight(document: PrintDocument): PreflightResult =
        Preflight.escPos(queryStatus())

    override suspend fun queryStatus(): PrinterStatus? = withContext(dispatcher) {
        if (!usbManager.hasPermission(device)) return@withContext null
        UsbStatusReader(usbManager, device).read()
    }

    override suspend fun doPrint(document: PrintDocument): PrintResult {
        if (!usbManager.hasPermission(device) && !permission.ensurePermission(device)) {
            return PrintResult.Error("USB permission denied", reason = PrintErrorReason.PERMISSION_DENIED)
        }
        val connection = UsbConnection(usbManager, device)
        return try {
            val printer = buildEscPosPrinter(connection, document, DPI)
            val text = EscPosRenderer.render(document) { PrinterTextParserImg.bitmapToHexadecimalString(printer, it) }
            when {
                document.openDrawer -> printer.printFormattedTextAndOpenCashBox(text, 0)
                document.cut != CutType.NONE -> printer.printFormattedTextAndCut(text)
                else -> printer.printFormattedText(text)
            }
            printer.disconnectPrinter()
            PrintResult.Success()
        } catch (e: Exception) {
            PrintResult.Error(e.message ?: "USB print failed", e, escPosReason(e))
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private companion object {
        const val DPI = 203
    }
}
