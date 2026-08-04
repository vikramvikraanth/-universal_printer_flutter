package com.universalprintersearch.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Requests runtime USB permission for a printer device — needed only to OPEN/print to a
 * device, NOT to list it (enumeration via [UsbPrinterScanner] needs no permission).
 *
 * Follows the current platform contract that trips people up on newer Android:
 *   - The PendingIntent uses an EXPLICIT intent (setPackage) + FLAG_IMMUTABLE. Android 14
 *     (U, API 34) forbids FLAG_MUTABLE with an implicit intent, so the old MUTABLE pattern
 *     crashes; an explicit + immutable PendingIntent is the correct combination and the
 *     system still delivers EXTRA_PERMISSION_GRANTED to the receiver.
 *   - RECEIVER_NOT_EXPORTED on API 33+ (Android 14 rejects an unspecified export flag).
 *   - FLAG_UPDATE_CURRENT so a repeat request refreshes the existing PendingIntent.
 *
 * Suspends until the user responds; resumes false if there is no UsbManager.
 * Safe to cancel — unregisters the receiver on cancellation.
 */
class UsbPermissionHelper(context: Context) {

    private val appContext = context.applicationContext

    suspend fun ensurePermission(device: UsbDevice): Boolean = suspendCancellableCoroutine { cont ->
        val manager = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager
        if (manager == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        if (manager.hasPermission(device)) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != ACTION_USB_PERMISSION) return
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                runCatching { appContext.unregisterReceiver(this) }
                if (cont.isActive) cont.resume(granted)
            }
        }
        cont.invokeOnCancellation { runCatching { appContext.unregisterReceiver(receiver) } }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(receiver, filter)
        }

        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext, 0, Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName), piFlags,
        )
        manager.requestPermission(device, pendingIntent)
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.universalprintersearch.USB_PERMISSION"
    }
}
