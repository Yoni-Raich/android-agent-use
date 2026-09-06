package dev.androidagent.adb

import dev.androidagent.core.AdbEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdbReconnectPolicyTest {
    @Test fun savedConnectPortWinsOverDiscoveredPairingAndConnectPorts() {
        val endpoints = listOf(
            AdbEndpoint(37123, pairing = true),
            AdbEndpoint(41231, pairing = false),
        )
        assertEquals(49876, AdbReconnectPolicy.preferredConnectPort(49876, endpoints))
        assertEquals(41231, AdbReconnectPolicy.preferredConnectPort(null, endpoints))
    }

    @Test fun pairingEndpointIsNeverSelectedAsTheReconnectTarget() {
        assertNull(AdbReconnectPolicy.preferredConnectPort(null, listOf(AdbEndpoint(37123, pairing = true))))
        assertEquals(41231, AdbReconnectPolicy.preferredConnectPort(80, listOf(AdbEndpoint(41231, pairing = false))))
        assertEquals(41231, AdbReconnectPolicy.fallbackConnectPort(49876, listOf(AdbEndpoint(41231, pairing = false))))
        assertNull(AdbReconnectPolicy.fallbackConnectPort(41231, listOf(AdbEndpoint(41231, pairing = false))))
    }

    @Test fun retryBackoffIsBounded() {
        assertEquals(500L, AdbReconnectPolicy.retryDelayMs(0))
        assertEquals(1_000L, AdbReconnectPolicy.retryDelayMs(1))
        assertEquals(10_000L, AdbReconnectPolicy.retryDelayMs(5))
        assertEquals(10_000L, AdbReconnectPolicy.retryDelayMs(100))
    }

    @Test fun missingServiceExplainsWirelessDebuggingState() {
        assertEquals("Wireless Debugging is off", AdbReconnectPolicy.noServiceMessage(false))
        assertEquals("Wireless Debugging is on; waiting for the ADB service", AdbReconnectPolicy.noServiceMessage(true))
        assertEquals("Wireless Debugging status unavailable; waiting for the ADB service", AdbReconnectPolicy.noServiceMessage(null))
    }
}
