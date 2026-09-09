package dev.androidagent.app

import dev.androidagent.connectors.PermissionMode
import dev.androidagent.core.McpRuntimePhase
import dev.androidagent.core.McpToolApprovalMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubConnectorPolicyTest {
    @Test
    fun `read only prompts every tool while other modes keep their approval policy`() {
        assertEquals(
            McpToolApprovalMode.PROMPT,
            GitHubConnectorController.approvalModeFor(PermissionMode.READ_ONLY),
        )
        assertEquals(
            McpToolApprovalMode.WRITES,
            GitHubConnectorController.approvalModeFor(PermissionMode.ASK_BEFORE_WRITES),
        )
        assertEquals(
            McpToolApprovalMode.APPROVE,
            GitHubConnectorController.approvalModeFor(PermissionMode.FULL_CONTROL),
        )
    }

    @Test
    fun `a server still coming up is transient, not a failure`() {
        // reloadMcpServers returns before the remote handshake completes, so
        // these phases must never strand a valid credential in ERROR.
        assertTrue(McpRuntimePhase.STARTING in GitHubConnectorController.TRANSIENT_MCP_PHASES)
        assertTrue(McpRuntimePhase.NOT_STARTED in GitHubConnectorController.TRANSIENT_MCP_PHASES)
        assertTrue(McpRuntimePhase.UNKNOWN in GitHubConnectorController.TRANSIENT_MCP_PHASES)
    }

    @Test
    fun `a genuinely failed server is not treated as transient`() {
        assertFalse(McpRuntimePhase.FAILED in GitHubConnectorController.TRANSIENT_MCP_PHASES)
        assertFalse(McpRuntimePhase.CONNECTED in GitHubConnectorController.TRANSIENT_MCP_PHASES)
        assertFalse(
            McpRuntimePhase.AUTHENTICATION_REQUIRED in GitHubConnectorController.TRANSIENT_MCP_PHASES,
        )
    }
}
