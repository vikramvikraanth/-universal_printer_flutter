package com.universalprintersearch.network.snmp

import java.io.ByteArrayOutputStream

/**
 * Minimal, dependency-free SNMPv1 BER/ASN.1 codec — just enough to build a
 * single-OID GET-Request and pull the value out of the GET-Response. No snmp4j,
 * no vendor SDK; this keeps the AAR clean to publish.
 *
 * SNMPv1 message = SEQUENCE { version INTEGER(0), community OCTET STRING,
 *   PDU }. GET-Request PDU (tag 0xA0) / GET-Response PDU (tag 0xA2) =
 *   { request-id INT, error-status INT, error-index INT,
 *     varbind-list SEQUENCE { SEQUENCE { name OID, value } } }.
 *
 * Encoding/decoding are pure functions (no sockets) so they can be unit-tested
 * against captured bytes without hardware — see SnmpClient for the transport.
 */
object Snmp {

    const val TAG_INTEGER = 0x02
    const val TAG_OCTET_STRING = 0x04
    const val TAG_NULL = 0x05
    const val TAG_OID = 0x06
    const val TAG_SEQUENCE = 0x30
    const val TAG_GET_REQUEST = 0xA0
    const val TAG_GET_RESPONSE = 0xA2

    // SNMPv1 "value" exception tags returned in place of a real value.
    private const val TAG_NO_SUCH_OBJECT = 0x80
    private const val TAG_NO_SUCH_INSTANCE = 0x81
    private const val TAG_END_OF_MIB_VIEW = 0x82

    /** A decoded variable binding: the OID plus its raw value bytes + BER tag. */
    data class VarBind(val oid: String, val type: Int, val value: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is VarBind && oid == other.oid && type == other.type && value.contentEquals(other.value)

        override fun hashCode(): Int = (oid.hashCode() * 31 + type) * 31 + value.contentHashCode()
    }

    // ---- Encoding --------------------------------------------------------

    fun encodeGetRequest(oid: String, community: String, requestId: Int): ByteArray {
        val varbind = tlv(TAG_SEQUENCE, encodeOid(oid) + tlv(TAG_NULL, ByteArray(0)))
        val varbindList = tlv(TAG_SEQUENCE, varbind)
        val pdu = tlv(
            TAG_GET_REQUEST,
            encodeInteger(requestId) + encodeInteger(0) + encodeInteger(0) + varbindList,
        )
        return tlv(
            TAG_SEQUENCE,
            encodeInteger(0) + tlv(TAG_OCTET_STRING, community.toByteArray(Charsets.US_ASCII)) + pdu,
        )
    }

    private fun tlv(tag: Int, content: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + encodeLength(content.size) + content

    private fun encodeLength(len: Int): ByteArray {
        if (len < 0x80) return byteArrayOf(len.toByte())
        val bytes = ArrayList<Byte>()
        var v = len
        while (v > 0) {
            bytes.add(0, (v and 0xFF).toByte())
            v = v ushr 8
        }
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }

    private fun encodeInteger(value: Int): ByteArray {
        val bytes = ArrayList<Byte>()
        var v = value
        if (v == 0) {
            bytes.add(0)
        } else {
            while (v != 0) {
                bytes.add(0, (v and 0xFF).toByte())
                v = v ushr 8
            }
            // Keep it positive: prepend 0x00 if the top bit of the MSB is set.
            if (bytes[0].toInt() and 0x80 != 0) bytes.add(0, 0)
        }
        return tlv(TAG_INTEGER, bytes.toByteArray())
    }

    private fun encodeOid(oid: String): ByteArray {
        val arcs = oid.split(".").map { it.toLong() }
        require(arcs.size >= 2) { "OID must have at least two arcs: $oid" }
        val body = ByteArrayOutputStream()
        body.write((40 * arcs[0] + arcs[1]).toInt())
        for (i in 2 until arcs.size) body.write(encodeBase128(arcs[i]))
        return tlv(TAG_OID, body.toByteArray())
    }

    private fun encodeBase128(value: Long): ByteArray {
        if (value < 0x80) return byteArrayOf(value.toByte())
        val bytes = ArrayList<Byte>()
        var v = value
        bytes.add(0, (v and 0x7F).toByte()) // last group: no continuation bit
        v = v ushr 7
        while (v > 0) {
            bytes.add(0, ((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        return bytes.toByteArray()
    }

    // ---- Decoding --------------------------------------------------------

    /**
     * Parse a GET-Response and return its first variable binding, or null on any
     * structural error, a non-zero error-status, or an exception value (noSuch*).
     */
    fun decodeResponse(bytes: ByteArray): VarBind? {
        return try {
            decode(Reader(bytes))
        } catch (e: Exception) {
            null
        }
    }

    private fun decode(r: Reader): VarBind? {
        if (r.tag() != TAG_SEQUENCE) return null; r.len()               // message
        if (r.tag() != TAG_INTEGER) return null; r.skip(r.len())        // version
        if (r.tag() != TAG_OCTET_STRING) return null; r.skip(r.len())   // community
        if (r.tag() != TAG_GET_RESPONSE) return null; r.len()           // PDU
        if (r.tag() != TAG_INTEGER) return null; r.skip(r.len())        // request-id
        if (r.tag() != TAG_INTEGER) return null
        val errStatus = r.intValue(r.len())                             // error-status
        if (r.tag() != TAG_INTEGER) return null; r.skip(r.len())        // error-index
        if (errStatus != 0) return null
        if (r.tag() != TAG_SEQUENCE) return null; r.len()               // varbind-list
        if (r.tag() != TAG_SEQUENCE) return null; r.len()               // first varbind
        if (r.tag() != TAG_OID) return null
        val oid = r.oidValue(r.len())                                   // name
        val valueTag = r.tag()                                          // value
        val valueLen = r.len()
        if (valueTag == TAG_NO_SUCH_OBJECT || valueTag == TAG_NO_SUCH_INSTANCE || valueTag == TAG_END_OF_MIB_VIEW) {
            return null
        }
        return VarBind(oid, valueTag, r.take(valueLen))
    }

    /** OCTET STRING → printable UTF-8 text; INTEGER → decimal string. Null otherwise. */
    fun textOf(vb: VarBind?): String? = when (vb?.type) {
        TAG_OCTET_STRING -> String(vb.value, Charsets.UTF_8).filter { it >= ' ' }.trim().ifEmpty { null }
        TAG_INTEGER -> {
            var v = 0
            for (b in vb.value) v = (v shl 8) or (b.toInt() and 0xFF)
            v.toString()
        }
        else -> null
    }

    /** An OBJECT IDENTIFIER value (e.g. sysObjectID) → dotted string. Null otherwise. */
    fun oidOf(vb: VarBind?): String? {
        if (vb?.type != TAG_OID || vb.value.isEmpty()) return null
        val b = vb.value
        val first = b[0].toInt() and 0xFF
        val sb = StringBuilder().append(first / 40).append('.').append(first % 40)
        var value = 0L
        for (i in 1 until b.size) {
            val x = b[i].toInt() and 0xFF
            value = (value shl 7) or (x and 0x7F).toLong()
            if (x and 0x80 == 0) {
                sb.append('.').append(value)
                value = 0
            }
        }
        return sb.toString()
    }

    /** A 6-byte OCTET STRING (ifPhysAddress) → "AA:BB:CC:DD:EE:FF". Null otherwise. */
    fun macHexOf(vb: VarBind?): String? =
        if (vb?.type == TAG_OCTET_STRING && vb.value.size == 6) {
            vb.value.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
        } else {
            null
        }

    private class Reader(val buf: ByteArray, var pos: Int = 0) {
        fun tag(): Int = buf[pos++].toInt() and 0xFF

        fun len(): Int {
            val first = buf[pos++].toInt() and 0xFF
            if (first < 0x80) return first
            var len = 0
            repeat(first and 0x7F) { len = (len shl 8) or (buf[pos++].toInt() and 0xFF) }
            return len
        }

        fun skip(n: Int) { pos += n }

        fun take(n: Int): ByteArray = buf.copyOfRange(pos, pos + n).also { pos += n }

        fun intValue(n: Int): Int {
            var v = 0
            repeat(n) { v = (v shl 8) or (buf[pos++].toInt() and 0xFF) }
            return v
        }

        fun oidValue(n: Int): String {
            if (n == 0) return ""
            val end = pos + n
            val first = buf[pos++].toInt() and 0xFF
            val sb = StringBuilder().append(first / 40).append('.').append(first % 40)
            var value = 0L
            while (pos < end) {
                val b = buf[pos++].toInt() and 0xFF
                value = (value shl 7) or (b and 0x7F).toLong()
                if (b and 0x80 == 0) {
                    sb.append('.').append(value)
                    value = 0
                }
            }
            return sb.toString()
        }
    }
}
