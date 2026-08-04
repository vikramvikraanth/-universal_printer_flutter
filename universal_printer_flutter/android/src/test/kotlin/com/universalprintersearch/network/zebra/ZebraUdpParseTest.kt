package com.universalprintersearch.network.zebra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Offset-parsing tests for the Zebra UDP-4201 discovery reply (no socket needed). */
class ZebraUdpParseTest {

    private val udp = ZebraUdpDiscovery()

    @Test
    fun parseReply_extractsFieldsAtDocumentedOffsets() {
        val payload = ByteArray(128)
        // header ":,." + 0x03
        byteArrayOf(0x3A, 0x2C, 0x2E, 0x03).copyInto(payload)
        putAscii(payload, 0x0C, "ZebraNet Wired PS") // model
        putAscii(payload, 0x38, "4262077")           // serial
        putAscii(payload, 0x54, "ZBR4262077")         // hostname

        val dev = udp.parseReply(payload, "192.168.1.50")
        assertEquals("192.168.1.50", dev?.ip)
        assertEquals("ZebraNet Wired PS", dev?.model)
        assertEquals("4262077", dev?.serial)
        assertEquals("ZBR4262077", dev?.hostname)
    }

    @Test
    fun parseReply_wrongHeaderReturnsNull() {
        val payload = ByteArray(128)
        byteArrayOf(0x45, 0x50, 0x53, 0x4F).copyInto(payload) // "EPSO", not a Zebra reply
        assertNull(udp.parseReply(payload, "192.168.1.50"))
    }

    private fun putAscii(buf: ByteArray, offset: Int, text: String) {
        text.toByteArray(Charsets.US_ASCII).copyInto(buf, offset)
    }
}
