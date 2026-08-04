package com.universalprintersearch.network

import com.universalprintersearch.model.DiscoveredPrinter

/**
 * A single SDK-free discovery mechanism producing branded [DiscoveredPrinter]s
 * (Epson ENPC, Sunmi mDNS, Zebra UDP-4201, SNMP sweep, …).
 *
 * [com.universalprintersearch.UniversalPrinterSearch.discoverAll] folds over a list
 * of these, so adding a mechanism means registering one more discoverer rather than
 * editing the merge logic (Open/Closed) — and the merge depends on this abstraction,
 * not on the concrete flows (Dependency Inversion).
 */
fun interface PrinterDiscoverer {
    suspend fun discover(): List<DiscoveredPrinter>
}
