package com.universalprinter.escpos

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
 * Runtime USB permission request. Uses the current Android-14-safe contract: an EXPLICIT intent
 * (setPackage) + FLAG_IMMUTABLE (Android 14 forbids FLAG_MUTABLE with an implicit intent) +
 * RECEIVER_NOT_EXPORTED on API 33+. Suspends until the user responds.
 */
internal class UsbPermissionHelper(context: Context) {

    private val appContext = context.applicationContext

    suspend fun ensurePermission(device: UsbDevice): Boolean = suspendCancellableCoroutine { cont ->
        val manager = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager
        if (manager == null) {
            cont.resume(false); return@suspendCancellableCoroutine
        }
        if (manager.hasPermission(device)) {
            cont.resume(true); return@suspendCancellableCoroutine
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != ACTION) return
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                runCatching { appContext.unregisterReceiver(this) }
                if (cont.isActive) cont.resume(granted)
            }
        }
        cont.invokeOnCancellation { runCatching { appContext.unregisterReceiver(receiver) } }

        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(receiver, filter)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pending = PendingIntent.getBroadcast(
            appContext, 0, Intent(ACTION).setPackage(appContext.packageName), flags,
        )
        manager.requestPermission(device, pending)
    }

    private companion object {
        const val ACTION = "com.universalprinter.USB_PERMISSION"
    }
}
