package dev.androidagent.connectors

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorInfrastructureTest {
    @Test
    fun `github catalog has three-state approval mode and fixed remote MCP values`() {
        val definition = GitHubConnectorCatalog.definition

        assertEquals(PermissionMode.ASK_BEFORE_WRITES, definition.permissionMode)
        assertEquals(AuthStrategy.GITHUB_OAUTH_APP_DEVICE_FLOW, definition.authStrategy)
        assertTrue(GitHubOAuthScopes.supported.size >= 30)
        assertTrue(definition.defaultScopes.all { it in definition.supportedScopes })
        assertEquals(setOf(GitHubOAuthScopes.REPO), definition.defaultScopes)
        // offline_access is not a GitHub OAuth App scope.
        assertFalse(GitHubOAuthScopes.supported.any { it == "offline_access" })
        assertEquals("https://api.githubcopilot.com/mcp/x/all", GitHubEndpoints.REMOTE_MCP)
        assertEquals("GITHUB_PERSONAL_ACCESS_TOKEN", GitHubEndpoints.REMOTE_MCP_TOKEN_ENVIRONMENT)
    }

    @Test
    fun `fixed egress accepts only official github routes`() {
        assertTrue(GitHubEndpoints.isAllowed(GitHubEndpoints.DEVICE_CODE))
        assertTrue(GitHubEndpoints.isAllowed(GitHubEndpoints.ACCESS_TOKEN))
        assertTrue(GitHubEndpoints.isAllowed("https://api.github.com/user"))
        assertTrue(GitHubEndpoints.isAllowed(GitHubEndpoints.REMOTE_MCP))
        assertFalse(GitHubEndpoints.isAllowed("https://api.github.example/user"))
        assertFalse(GitHubEndpoints.isAllowed("http://api.github.com/user"))
        assertFalse(GitHubEndpoints.isAllowed("https://evil.example/redirect"))
    }

    @Test
    fun `in memory stores replace and remove data`() {
        val stateStore = InMemoryConnectorStateStore()
        val snapshot = ConnectorSnapshot("github", ConnectorState.CONNECTED, accountLogin = "octocat")
        stateStore.put(snapshot)
        assertEquals(snapshot, stateStore.get("github"))
        stateStore.put(snapshot.copy(state = ConnectorState.REAUTH_REQUIRED))
        assertEquals(ConnectorState.REAUTH_REQUIRED, stateStore.get("github")!!.state)
        stateStore.remove("github")
        assertEquals(null, stateStore.get("github"))

        val vault = InMemoryCredentialVault()
        val credentials = CredentialBundle("gho_token", refreshToken = "ghr_refresh")
        vault.write("github", credentials)
        assertEquals(credentials, vault.read("github"))
        vault.clear("github")
        assertEquals(null, vault.read("github"))
    }

    @Test
    fun `schema canonicalization ignores object key order but preserves arrays`() {
        val first = Json.parseToJsonElement("{\"b\":2,\"a\":{\"d\":false,\"c\":true}}")
        val second = Json.parseToJsonElement("{\"a\":{\"c\":true,\"d\":false},\"b\":2}")
        val reorderedArray = Json.parseToJsonElement("{\"a\":[2,1]}")
        val originalArray = Json.parseToJsonElement("{\"a\":[1,2]}")

        assertEquals(
            ToolSchemaFingerprint.canonicalize(first),
            ToolSchemaFingerprint.canonicalize(second),
        )
        assertEquals(ToolSchemaFingerprint.sha256(first), ToolSchemaFingerprint.sha256(second))
        assertNotEquals(
            ToolSchemaFingerprint.sha256(originalArray),
            ToolSchemaFingerprint.sha256(reorderedArray),
        )
        assertEquals(64, ToolSchemaFingerprint.sha256(first).length)
    }

    @Test
    fun `tool fingerprint is independent of tool collection order`() {
        val schema = buildJsonObject { put("type", "object") }
        val first = ConnectorToolSchema("b", "B", schema)
        val second = ConnectorToolSchema("a", "A", schema)

        assertEquals(
            ToolSchemaFingerprint.forTools("github", "v1", listOf(first, second)),
            ToolSchemaFingerprint.forTools("github", "v1", listOf(second, first)),
        )
        assertNotEquals(
            ToolSchemaFingerprint.forTools("github", "v1", listOf(first)),
            ToolSchemaFingerprint.forTools("github", "v1", listOf(first.copy(description = "wording changed"))),
        )
    }

    @Test
    fun `redactor removes github tokens bearer values and form secrets`() {
        val text = "Authorization: Bearer gho_abc123456789; refresh_token=ghr_refresh123456; user_code=ABCD-EFGH; extra=private-value"

        val redacted = TokenRedactor.redact(text, listOf("private-value"))

        assertNotNull(redacted)
        assertFalse(redacted!!.contains("gho_abc123456789"))
        assertFalse(redacted.contains("ghr_refresh123456"))
        assertFalse(redacted.contains("ABCD-EFGH"))
        assertFalse(redacted.contains("private-value"))
        assertTrue(redacted.contains("[REDACTED]"))
        assertEquals("[REDACTED]", TokenRedactor.redactToken("gho_abc123456789"))
        assertEquals("<none>", TokenRedactor.redactToken(null))
    }
}
