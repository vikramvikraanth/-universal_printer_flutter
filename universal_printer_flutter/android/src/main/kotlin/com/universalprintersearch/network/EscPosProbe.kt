package com.universalprintersearch.network

/**
 * ESC/POS `GS I` identity/serial probe over raw TCP-9100. Abstracts
 * [EscPosSerialProbe] so discovery orchestrators can be unit-tested with a fake —
 * Dependency Inversion.
 */
interface EscPosProbe {

    /** ESC/POS self-reported identity; any field may be null if the printer doesn't answer that query. */
    data class EscPosIdentity(val maker: String?, val model: String?, val serial: String?)

    suspend fun querySerial(ip: String, port: Int = 9100, timeoutMs: Int = 3000): String?

    suspend fun queryIdentity(ip: String, port: Int = 9100, timeoutMs: Int = 3000): EscPosIdentity?
}
