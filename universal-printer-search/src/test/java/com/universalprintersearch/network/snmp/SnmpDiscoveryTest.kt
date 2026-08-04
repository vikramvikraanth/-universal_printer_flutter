package com.universalprintersearch.network.snmp

import com.universalprintersearch.model.PrinterBrand
import com.universalprintersearch.network.EscPosProbe
import com.universalprintersearch.network.HostProbe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * End-to-end SnmpDiscovery test with fake HostProbe + SnmpTransport + EscPosProbe + injected
 * subnet — only possible because the orchestrator depends on abstractions (the DIP payoff).
 */
class SnmpDiscoveryTest {

    @Test
    fun discover_brandsAnSnmpPrinterAtAReachableHost() = runBlocking {
        val ip = "192.168.50.10"

        val reachable = object : HostProbe {
            override suspend fun ping(ip: String, maxRetries: Int): Boolean = ip == "192.168.50.10"
        }
        val transport = object : SnmpTransport {
            override suspend fun get(ip: String, oid: String, community: String): Snmp.VarBind? = when {
                ip != "192.168.50.10" -> null
                oid == SnmpIdentityProbe.OID_SYS_DESCR -> octet(oid, "Zebra Technologies ZD421-203dpi")
                oid == SnmpIdentityProbe.OID_SERIAL -> octet(oid, "ZBR12345")
                else -> null
            }
        }
        val noEscPos = object : EscPosProbe {
            override suspend fun querySerial(ip: String, port: Int, timeoutMs: Int): String? = null
            override suspend fun queryIdentity(ip: String, port: Int, timeoutMs: Int): EscPosProbe.EscPosIdentity? = null
        }

        val discovery = SnmpDiscovery(
            scanner = reachable,
            probe = SnmpIdentityProbe(transport),
            serialProbe = noEscPos,
            localSubnetPrefix = { "192.168.50." },
        )

        val result = discovery.discover(snmpOnly = true)

        assertEquals(1, result.size)
        assertEquals(PrinterBrand.ZEBRA, result[0].brand)
        assertEquals(ip, result[0].ipAddress)
        assertEquals("ZBR12345", result[0].serialNumber)
    }

    private fun octet(oid: String, text: String) =
        Snmp.VarBind(oid, Snmp.TAG_OCTET_STRING, text.toByteArray(Charsets.US_ASCII))
}
