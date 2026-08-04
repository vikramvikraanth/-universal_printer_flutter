package com.universalprintersearch.network.snmp

import com.universalprintersearch.model.PrinterBrand
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Brand-mapping tests for the SNMP sysDescr token map (no socket / hardware needed). */
class SnmpIdentityProbeTest {

    private val probe = SnmpIdentityProbe()

    @Test
    fun matchBrand_mapsEachVendorTokenToItsBrand() {
        assertEquals(PrinterBrand.EPSON, probe.matchBrand("EPSON TM-m30III"))
        assertEquals(PrinterBrand.STAR, probe.matchBrand("Star Micronics TSP143"))
        assertEquals(PrinterBrand.ZEBRA, probe.matchBrand("Zebra Technologies ZD421"))
        assertEquals(PrinterBrand.BIXOLON, probe.matchBrand("BIXOLON SRP-350plusIII"))
        assertEquals(PrinterBrand.CITIZEN, probe.matchBrand("CITIZEN CT-E351"))
        assertEquals(PrinterBrand.BROTHER, probe.matchBrand("Brother QL-820NWB"))
        assertEquals(PrinterBrand.SEIKO, probe.matchBrand("SEIKO RP-E11"))
    }

    @Test
    fun matchBrand_isCaseInsensitive() {
        assertEquals(PrinterBrand.STAR, probe.matchBrand("star micronics mc-print3"))
    }

    @Test
    fun matchBrand_unknownDescriptionIsGeneric() {
        assertEquals(PrinterBrand.GENERIC, probe.matchBrand("Some Other Vendor XYZ-100"))
        assertEquals(PrinterBrand.GENERIC, probe.matchBrand(""))
    }

    @Test
    fun matchByPen_mapsKnownEnterpriseNumbers() {
        // Real Zebra sysObjectID: 1.3.6.1.4.1.10642.1.1
        assertEquals(PrinterBrand.ZEBRA, probe.matchByPen("1.3.6.1.4.1.10642.1.1"))
        assertEquals(PrinterBrand.ZEBRA, probe.matchByPen("1.3.6.1.4.1.683.6.2.3.2.1.8")) // ZebraNet print server
        assertEquals(PrinterBrand.BROTHER, probe.matchByPen("1.3.6.1.4.1.2435.2.3.9"))
        assertEquals(PrinterBrand.EPSON, probe.matchByPen("1.3.6.1.4.1.1248.1.2.1"))
        assertEquals(PrinterBrand.SEIKO, probe.matchByPen("1.3.6.1.4.1.263.1")) // Seiko Instruments (SII)
    }

    @Test
    fun matchByPen_unknownOrNonEnterpriseReturnsNull() {
        assertNull(probe.matchByPen("1.3.6.1.4.1.99999.1")) // unknown PEN
        assertNull(probe.matchByPen("1.3.6.1.2.1.1.2.0"))   // not enterprise-rooted
        assertNull(probe.matchByPen(null))
    }

    // These exercise the full probe() against a FAKE SnmpTransport — only possible because
    // the probe depends on the SnmpTransport interface, not the concrete SnmpClient (DIP).

    @Test
    fun probe_identifiesBrandFromSysDescr() = runBlocking {
        val fake = FakeSnmpTransport(
            mapOf(SnmpIdentityProbe.OID_SYS_DESCR to octet(SnmpIdentityProbe.OID_SYS_DESCR, "Star Micronics TSP143")),
        )
        val id = SnmpIdentityProbe(fake).probe("10.0.0.5")
        assertEquals(PrinterBrand.STAR, id?.brand)
        assertEquals("Star Micronics TSP143", id?.model)
    }

    @Test
    fun probe_prefersSysObjectIdPenOverSysDescrText() = runBlocking {
        val fake = FakeSnmpTransport(
            mapOf(
                // vendor OID 1.3.6.1.4.1.10642.1.1 (Zebra) vs a non-branded sysDescr
                SnmpIdentityProbe.OID_SYS_OBJECT_ID to oid("1.3.6.1.2.1.1.2.0", bytes(0x2B, 0x06, 0x01, 0x04, 0x01, 0xD3, 0x12, 0x01, 0x01)),
                SnmpIdentityProbe.OID_SYS_DESCR to octet(SnmpIdentityProbe.OID_SYS_DESCR, "Generic Label Printer"),
            ),
        )
        assertEquals(PrinterBrand.ZEBRA, SnmpIdentityProbe(fake).probe("10.0.0.6")?.brand)
    }

    @Test
    fun probe_noSnmpResponseReturnsNull() = runBlocking {
        assertNull(SnmpIdentityProbe(FakeSnmpTransport(emptyMap())).probe("10.0.0.7"))
    }

    private class FakeSnmpTransport(private val responses: Map<String, Snmp.VarBind?>) : SnmpTransport {
        override suspend fun get(ip: String, oid: String, community: String): Snmp.VarBind? = responses[oid]
    }

    private fun octet(oid: String, text: String) =
        Snmp.VarBind(oid, Snmp.TAG_OCTET_STRING, text.toByteArray(Charsets.US_ASCII))

    private fun oid(oidName: String, valueBody: ByteArray) = Snmp.VarBind(oidName, Snmp.TAG_OID, valueBody)

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
