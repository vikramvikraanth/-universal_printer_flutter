package com.universalprintersearch.network

/**
 * Host reachability check (a TCP connect to the raw-print port, not ICMP).
 * Abstracts [NetworkScanner] so discovery orchestrators can be unit-tested with a
 * fake that declares which IPs are "up" — Dependency Inversion.
 */
interface HostProbe {
    suspend fun ping(ip: String, maxRetries: Int = 2): Boolean
}
