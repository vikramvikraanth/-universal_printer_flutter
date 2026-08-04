package com.universalprintersearch.network.snmp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Hardware-free codec tests for the SNMPv1 BER encoder/decoder. Expected bytes
 * are computed by hand / built with an independent local TLV helper, so these
 * are genuine assertions rather than a round-trip of the production code.
 */
class SnmpTest {

    /** GET-Request for sysDescr.0, community "public", request-id 1 — hand-computed. */
    @Test
    fun encodeGetRequest_sysDescr_matchesHandComputedBytes() {
        val actual = Snmp.encodeGetRequest("1.3.6.1.2.1.1.1.0", "public", 1)
        val expected = bytes(
            0x30, 0x26,                                     // message SEQUENCE, len 38
            0x02, 0x01, 0x00,                               // version = 0
            0x04, 0x06, 0x70, 0x75, 0x62, 0x6C, 0x69, 0x63, // community "public"
            0xA0, 0x19,                                     // GET-Request PDU, len 25
            0x02, 0x01, 0x01,                               // request-id = 1
            0x02, 0x01, 0x00,                               // error-status = 0
            0x02, 0x01, 0x00,                               // error-index = 0
            0x30, 0x0E,                                     // varbind-list, len 14
            0x30, 0x0C,                                     // varbind, len 12
            0x06, 0x08, 0x2B, 0x06, 0x01, 0x02, 0x01, 0x01, 0x01, 0x00, // OID 1.3.6.1.2.1.1.1.0
            0x05, 0x00,                                     // value = NULL
        )
        assertArrayEquals(expected, actual)
    }

    @Test
    fun decodeResponse_octetString_extractsSerialAndOid() {
        val oidBody = bytes(0x2B, 0x06, 0x01, 0x02, 0x01, 0x2B, 0x05, 0x01, 0x01, 0x11, 0x01) // 1.3.6.1.2.1.43.5.1.1.17.1
        val response = getResponse(0xA2, tlv(0x06, oidBody) + octet("ABC12345"))

        val vb = Snmp.decodeResponse(response)
        assertEquals("1.3.6.1.2.1.43.5.1.1.17.1", vb?.oid)
        assertEquals("ABC12345", Snmp.textOf(vb))
    }

    @Test
    fun decodeResponse_sixByteOctetString_formatsMac() {
        val oidBody = bytes(0x2B, 0x06, 0x01, 0x02, 0x01, 0x02, 0x02, 0x01, 0x06, 0x01) // 1.3.6.1.2.1.2.2.1.6.1
        val macValue = tlv(0x04, bytes(0x00, 0x11, 0x22, 0xAB, 0xCD, 0xEF))
        val response = getResponse(0xA2, tlv(0x06, oidBody) + macValue)

        assertEquals("00:11:22:AB:CD:EF", Snmp.macHexOf(Snmp.decodeResponse(response)))
    }

    @Test
    fun decodeResponse_objectIdentifierValue_decodesToDottedString() {
        // sysObjectID reply carrying Zebra's vendor OID 1.3.6.1.4.1.10642.1.1
        // OID body: 2B 06 01 04 01 (1.3.6.1.4.1) + D3 12 (10642, base-128) + 01 01
        val oidValue = tlv(0x06, bytes(0x2B, 0x06, 0x01, 0x04, 0x01, 0xD3, 0x12, 0x01, 0x01))
        val oidName = bytes(0x2B, 0x06, 0x01, 0x02, 0x01, 0x01, 0x02, 0x00) // sysObjectID.0
        val response = getResponse(0xA2, tlv(0x06, oidName) + oidValue)

        assertEquals("1.3.6.1.4.1.10642.1.1", Snmp.oidOf(Snmp.decodeResponse(response)))
    }

    @Test
    fun decodeResponse_nonZeroErrorStatus_returnsNull() {
        val oidBody = bytes(0x2B, 0x06, 0x01, 0x02, 0x01, 0x01, 0x01, 0x00)
        // error-status = 2 (noSuchName)
        val pduBody = int(1) + int(2) + int(0) + tlv(0x30, tlv(0x30, tlv(0x06, oidBody) + octet("x")))
        val response = tlv(0x30, int(0) + octet("public") + tlv(0xA2, pduBody))

        assertNull(Snmp.decodeResponse(response))
    }

    @Test
    fun decodeResponse_noSuchInstanceExceptionValue_returnsNull() {
        val oidBody = bytes(0x2B, 0x06, 0x01, 0x02, 0x01, 0x01, 0x01, 0x00)
        // value replaced by the noSuchInstance exception tag (0x81)
        val response = getResponse(0xA2, tlv(0x06, oidBody) + tlv(0x81, ByteArray(0)))

        assertNull(Snmp.decodeResponse(response))
    }

    // ---- independent local TLV builders (all lengths < 128, values single-byte) ----

    private fun getResponse(pduTag: Int, varbindPair: ByteArray): ByteArray {
        val pduBody = int(1) + int(0) + int(0) + tlv(0x30, tlv(0x30, varbindPair))
        return tlv(0x30, int(0) + octet("public") + tlv(pduTag, pduBody))
    }

    private fun tlv(tag: Int, content: ByteArray): ByteArray =
        bytes(tag, content.size) + content

    private fun int(v: Int): ByteArray = tlv(0x02, bytes(v))

    private fun octet(s: String): ByteArray = tlv(0x04, s.toByteArray(Charsets.US_ASCII))

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
