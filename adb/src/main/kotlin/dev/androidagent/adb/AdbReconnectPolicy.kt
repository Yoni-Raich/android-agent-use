package dev.androidagent.adb

import dev.androidagent.core.AdbEndpoint

/** Pure decisions used by the lifecycle reconnect loop. */
object AdbReconnectPolicy {
    const val MAX_BACKOFF_MS = 10_000L

    /** Prefer the last known connect port, then an advertised connect service. */
    fun preferredConnectPort(savedPort: Int?, endpoints: List<AdbEndpoint>): Int? {
        if (savedPort != null && AdbServiceDiscovery.isValidAdbPort(savedPort)) return savedPort
        return endpoints.firstOrNull { !it.pairing && AdbServiceDiscovery.isValidAdbPort(it.port) }?.port
    }

    /** Pick a newly advertised connect endpoint after a stored port failed. */
    fun fallbackConnectPort(failedPort: Int?, endpoints: List<AdbEndpoint>): Int? =
        endpoints.firstOrNull {
            !it.pairing && it.port != failedPort && AdbServiceDiscovery.isValidAdbPort(it.port)
        }?.port

    fun retryDelayMs(attempt: Int): Long {
        val exponent = attempt.coerceIn(0, 5)
        return (500L shl exponent).coerceAtMost(MAX_BACKOFF_MS)
    }

    fun noServiceMessage(wirelessDebuggingEnabled: Boolean?): String = when (wirelessDebuggingEnabled) {
        false -> "Wireless Debugging is off"
        true -> "Wireless Debugging is on; waiting for the ADB service"
        null -> "Wireless Debugging status unavailable; waiting for the ADB service"
    }
}
