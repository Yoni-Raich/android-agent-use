package dev.androidagent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RuntimeConnectorTest {
    @Test
    fun `composite connector merges namespaced environment and hosts`() {
        val composite = CompositeRuntimeConnector(
            listOf(
                connector(mapOf("GITHUB_TOKEN" to "one"), setOf("API.GITHUB.COM.")),
                connector(mapOf("LINEAR_TOKEN" to "two"), setOf("api.linear.app")),
            ),
        )

        assertEquals(
            mapOf("GITHUB_TOKEN" to "one", "LINEAR_TOKEN" to "two"),
            composite.snapshot(),
        )
        assertEquals(setOf("api.github.com", "api.linear.app"), composite.allowedHttpsHosts())
    }

    @Test
    fun `composite connector rejects environment collisions`() {
        val composite = CompositeRuntimeConnector(
            listOf(
                connector(mapOf("SHARED" to "one"), emptySet()),
                connector(mapOf("SHARED" to "two"), emptySet()),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) { composite.snapshot() }
    }

    private fun connector(
        environment: Map<String, String>,
        hosts: Set<String>,
    ): RuntimeConnector = object : RuntimeConnector {
        override fun snapshot(): Map<String, String> = environment
        override fun allowedHttpsHosts(): Set<String> = hosts
    }
}
