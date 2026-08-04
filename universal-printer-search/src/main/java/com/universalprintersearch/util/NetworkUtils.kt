package com.universalprintersearch.util

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * Transport-agnostic network helpers. Unlike the WifiManager.connectionInfo
 * approach (WiFi-only, deprecated), [localIpv4] enumerates every up interface,
 * so it also resolves a subnet on Ethernet-connected POS terminals.
 */
internal object NetworkUtils {

    /** First non-loopback, site-local IPv4 address across all up interfaces (WiFi OR Ethernet). */
    fun localIpv4(): String? = try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }

    /** "192.168.1.42" -> "192.168.1." ; null if malformed. */
    fun subnetPrefix(ip: String?): String? {
        val parts = ip?.split(".") ?: return null
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}." else null
    }

    /** "192.168.1.42" -> "192.168.1.255" ; null if malformed. */
    fun subnetBroadcast(ip: String?): String? {
        val parts = ip?.split(".") ?: return null
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.255" else null
    }

    /** Best-effort ARP lookup via `ip neigh` (only works on Android <= 9). "" if unavailable. */
    fun macFromArp(ip: String): String = try {
        val process = Runtime.getRuntime().exec("ip neigh")
        process.inputStream.bufferedReader().useLines { lines ->
            lines.map { it.trim().split("\\s+".toRegex()) }
                .firstOrNull { it.size >= 5 && it[0] == ip }
                ?.get(4)
                ?.let { normalizeMac(it) }
                ?: ""
        }
    } catch (e: Exception) {
        ""
    }

    /** Normalizes a MAC to AA:BB:CC:DD:EE:FF. Returns the trimmed input if it is not 12 hex digits. */
    fun normalizeMac(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        val hex = trimmed.replace(":", "").replace("-", "").replace(".", "").uppercase(Locale.ROOT)
        if (hex.length != 12 || !hex.all { it.isDigit() || it in 'A'..'F' }) {
            return trimmed.uppercase(Locale.ROOT)
        }
        return hex.chunked(2).joinToString(":")
    }
}
