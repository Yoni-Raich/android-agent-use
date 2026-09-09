package dev.androidagent.app

import dev.androidagent.connectors.PermissionMode
import dev.androidagent.core.McpToolApprovalMode
import org.junit.Assert.assertEquals
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
}
