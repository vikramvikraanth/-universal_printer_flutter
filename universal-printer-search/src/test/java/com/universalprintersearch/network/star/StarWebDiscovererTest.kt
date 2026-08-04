package com.universalprintersearch.network.star

import org.junit.Assert.assertEquals
import org.junit.Test

/** MAC-selection tests mirroring the real Star TSP100IV web-config page structure. */
class StarWebDiscovererTest {

    private val discoverer = StarWebDiscoverer()

    // Real TSP100IV layout: an inactive wired LAN (0.0.0.0) + the active WLAN carrying the IP.
    private val page = """
        <span>Network Status(LAN)
        <dt>MAC Address :</dt><dd>00:11:62:5A:DC:F7</dd>
        <dt>IP Address:</dt><dd>0.0.0.0 (Didn't obtain)</dd>
        <span>Network Status(WLAN)
        <dt>MAC Address :</dt><dd>00:11:62:57:A1:4E</dd>
        <dt>IP Address:</dt><dd>192.168.80.35</dd>
    """.trimIndent()

    @Test
    fun macFrom_picksTheInterfaceCarryingTheConnectedIp() {
        // Must return the WLAN MAC (active, IP-bearing), NOT the inactive LAN MAC.
        assertEquals("00:11:62:57:A1:4E", discoverer.macFrom(page, "192.168.80.35"))
    }

    @Test
    fun macFrom_fallsBackToStarOuiWhenNoIpMatch() {
        assertEquals("00:11:62:5A:DC:F7", discoverer.macFrom(page, "10.0.0.99"))
    }
}
