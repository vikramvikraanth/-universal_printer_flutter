package com.universalprintersearch.network.snmp

/**
 * SNMP GET transport abstraction. Lets [SnmpIdentityProbe] depend on an interface
 * rather than the concrete [SnmpClient] (Dependency Inversion), so the probe's
 * identity logic is unit-testable with a fake transport — no real UDP socket.
 */
interface SnmpTransport {
    /** SNMP GET for one OID. Returns null on any failure (never throws). */
    suspend fun get(ip: String, oid: String, community: String = SnmpClient.DEFAULT_COMMUNITY): Snmp.VarBind?
}
