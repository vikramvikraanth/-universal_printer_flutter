package com.universalprinter.escpos

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.universalprinter.model.PrinterStatus

/**
 * Reads live ESC/POS status from a USB printer via raw bulk transfer — DantSu doesn't expose the
 * `DLE EOT` channel, so (like the reference RN package's `UsbPrinterStatusManager`) we open the
 * device directly, write each `DLE EOT n` query to the bulk-OUT endpoint and read the status byte
 * from bulk-IN, then reuse [EscPosStatus.parse]. Returns null if the printer can't be opened or
 * doesn't answer (many low-end USB printers don't) — callers should then proceed, not block.
 */
internal class UsbStatusReader(
    private val usbManager: UsbManager,
    private val device: UsbDevice,
    private val timeoutMs: Int = 2_000,
) {

    fun read(): PrinterStatus? {
        val iface = printerInterface() ?: return null
        val outEp = endpoint(iface, UsbConstants.USB_DIR_OUT) ?: return null
        val inEp = endpoint(iface, UsbConstants.USB_DIR_IN) ?: return null
        val conn = usbManager.openDevice(device) ?: return null
        return try {
            if (!conn.claimInterface(iface, true)) return null
            val printerByte = query(conn, outEp, inEp, EscPosStatus.QUERY_PRINTER) ?: return null
            val offlineByte = query(conn, outEp, inEp, EscPosStatus.QUERY_OFFLINE) ?: return null
            val errorByte = query(conn, outEp, inEp, EscPosStatus.QUERY_ERROR) ?: return null
            val paperByte = query(conn, outEp, inEp, EscPosStatus.QUERY_PAPER) ?: return null
            EscPosStatus.parse(printerByte, offlineByte, errorByte, paperByte)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn.releaseInterface(iface) }
            runCatching { conn.close() }
        }
    }

    /** Send one `DLE EOT n` command and read back the status byte (last byte received). */
    private fun query(
        conn: android.hardware.usb.UsbDeviceConnection,
        outEp: UsbEndpoint,
        inEp: UsbEndpoint,
        command: ByteArray,
    ): Int? {
        val written = conn.bulkTransfer(outEp, command, command.size, timeoutMs)
        if (written < 0) return null
        val buf = ByteArray(16)
        val n = conn.bulkTransfer(inEp, buf, buf.size, timeoutMs)
        return if (n > 0) buf[n - 1].toInt() and 0xFF else null
    }

    private fun printerInterface(): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) return iface
        }
        // No printer-class interface → skip status (best-effort). Don't guess interface 0: on a composite
        // device that's often a non-printer control/HID interface, and a DLE EOT to its endpoint would
        // time out or return garbage that parses as a spurious fault. Printing is unaffected (DantSu
        // selects its own interface); an unread status simply lets preflight proceed.
        return null
    }

    private fun endpoint(iface: UsbInterface, direction: Int): UsbEndpoint? {
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == direction) return ep
        }
        return null
    }
}
