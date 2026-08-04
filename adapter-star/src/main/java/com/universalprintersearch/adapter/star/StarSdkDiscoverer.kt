package com.universalprintersearch.adapter.star

import android.content.Context
import com.starmicronics.stario10.InterfaceType
import com.starmicronics.stario10.StarDeviceDiscoveryManager
import com.starmicronics.stario10.StarDeviceDiscoveryManagerFactory
import com.starmicronics.stario10.StarPrinter
import com.universalprintersearch.model.DiscoveredPrinter
import com.universalprintersearch.model.PrinterBrand
import com.universalprintersearch.model.PrinterConnectionType
import com.universalprintersearch.network.PrinterDiscoverer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Star discovery via the proprietary StarXpand SDK (StarIO10 v2), wrapped as a
 * [PrinterDiscoverer] so it plugs into [com.universalprintersearch.UniversalPrinterSearch]
 * through `additionalDiscoverers` without touching the SDK-free core.
 *
 * This is why Star returns a MAC where the SDK-free flows can't: StarXpand's
 * discovery exposes each printer's MAC as `connectionSettings.identifier`
 * (e.g. "00:11:62:00:00:01"). Star's LAN discovery wire protocol is not public,
 * so the SDK is the only way to get it — hence this optional adapter.
 */
class StarSdkDiscoverer(
    context: Context,
    private val interfaceTypes: List<InterfaceType> = listOf(InterfaceType.Lan),
    private val discoveryTimeMs: Int = DEFAULT_DISCOVERY_MS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PrinterDiscoverer {

    private val appContext = context.applicationContext

    override suspend fun discover(): List<DiscoveredPrinter> = withContext(dispatcher) {
        val found = LinkedHashMap<String, DiscoveredPrinter>()
        var manager: StarDeviceDiscoveryManager? = null
        try {
            suspendCancellableCoroutine { cont ->
                val mgr = StarDeviceDiscoveryManagerFactory.create(interfaceTypes, appContext)
                manager = mgr
                mgr.discoveryTime = discoveryTimeMs
                mgr.callback = object : StarDeviceDiscoveryManager.Callback {
                    override fun onPrinterFound(printer: StarPrinter) {
                        val mac = printer.connectionSettings.identifier
                        val model = printer.information?.model?.name // StarPrinterModel enum -> String
                        found[mac] = DiscoveredPrinter(
                            name = model ?: "Star Printer",
                            connectionType = PrinterConnectionType.NETWORK,
                            // StarXpand keys LAN printers by MAC, not IP — MAC is the identifier.
                            macAddress = mac,
                            brand = PrinterBrand.STAR,
                            model = model,
                        )
                    }

                    override fun onDiscoveryFinished() {
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                cont.invokeOnCancellation { runCatching { mgr.stopDiscovery() } }
                try {
                    mgr.startDiscovery()
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        } catch (e: Exception) {
            // never throw — best-effort discovery
        } finally {
            runCatching { manager?.stopDiscovery() }
        }
        found.values.toList()
    }

    companion object {
        const val DEFAULT_DISCOVERY_MS = 10_000
    }
}
