package com.universalprintersearch.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.universalprintersearch.model.DiscoveredPrinter
import com.universalprintersearch.model.PrinterConnectionType

/**
 * USB printer enumeration via the platform UsbManager. A device is treated as a
 * printer if any of its interfaces reports USB_CLASS_PRINTER (0x07). No SDK.
 *
 * This only lists devices; it does NOT request permission. Call
 * [UsbPermissionHelper.ensurePermission] before opening a connection to print.
 */
class UsbPrinterScanner(context: Context) {

    private val appContext = context.applicationContext

    fun listConnectedPrinters(): List<DiscoveredPrinter> {
        val manager = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        return manager.deviceList.values
            .filter { isPrinter(it) }
            .map { device ->
                DiscoveredPrinter(
                    name = runCatching { device.productName }.getOrNull() ?: "USB Printer",
                    connectionType = PrinterConnectionType.USB,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    usbDeviceName = device.deviceName,
                )
            }
    }

    /** True iff any interface of [device] is a USB printer-class interface. */
    fun isPrinter(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                return true
            }
        }
        return false
    }
}
