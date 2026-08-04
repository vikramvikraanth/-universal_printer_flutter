package com.universalprinter.escpos

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.usb.UsbConnection
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import com.universalprinter.QueuedPrinter
import com.universalprinter.model.CutType
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintErrorReason
import com.universalprinter.model.PrintResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * USB ESC/POS printer, backed by DantSu's [UsbConnection] + [EscPosPrinter]. Requests runtime USB
 * permission on demand (see [UsbPermissionHelper]); listing/enumerating USB devices is the caller's
 * job (e.g. UsbManager.deviceList / the search SDK).
 */
class EscPosUsbPrinter(
    context: Context,
    private val device: UsbDevice,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : QueuedPrinter(dispatcher) {

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val permission = UsbPermissionHelper(appContext)

    override val name: String = "USB ${device.deviceName}"

    override suspend fun doConnect(): Boolean =
        usbManager.hasPermission(device) || permission.ensurePermission(device)

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
