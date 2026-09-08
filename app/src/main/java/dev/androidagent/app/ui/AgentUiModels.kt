package dev.androidagent.app.ui

import dev.androidagent.app.update.AppUpdateInfo
import dev.androidagent.app.update.UpdateStatus
import dev.androidagent.core.AccountStatus
import dev.androidagent.core.AgentModel
import dev.androidagent.core.AgentSkill
import dev.androidagent.core.AdbEndpoint
import dev.androidagent.core.AdbStatus
import dev.androidagent.core.ChatMessage
import dev.androidagent.core.ChatSession
import dev.androidagent.core.EngineEvent
import dev.androidagent.core.RunState
import dev.androidagent.core.RuntimeStatus
import dev.androidagent.core.VoiceState

/**
 * A file that is waiting to be sent with the next user message.
 * The root app owns the picker and decides how the path is resolved.
 */
data class PendingAttachment(
    val id: String,
    val name: String,
    val path: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
)

data class WorkspaceFileItem(
    val path: String,
    val sizeBytes: Long? = null,
    val modifiedAt: Long? = null,
    val isDirectory: Boolean = false,
)

enum class AgentCardState { ACTIVE, COMPLETE, ERROR, BLOCKED }

data class ToolStatusCard(
    val id: String,
    val title: String,
    val detail: String = "",
    val state: AgentCardState = AgentCardState.ACTIVE,
)

data class AgentStatusCard(
    val id: String,
    val title: String,
    val detail: String = "",
    val state: AgentCardState = AgentCardState.COMPLETE,
)

/**
 * All data needed by the screen. It is intentionally free of ViewModel or
 * engine references so the root app can map its own flows into this state.
 */
data class AgentUiState(
    val sessions: List<ChatSession> = emptyList(),
    val activeSessionId: String? = null,
    val activeSessionTitle: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val toolCards: List<ToolStatusCard> = emptyList(),
    val statusCards: List<AgentStatusCard> = emptyList(),
    val attachments: List<PendingAttachment> = emptyList(),
    val workspaceFiles: List<WorkspaceFileItem> = emptyList(),
    val discoveredEndpoints: List<AdbEndpoint> = emptyList(),
    val runState: RunState = RunState(),
    val queuedTurns: List<dev.androidagent.core.QueuedTurn> = emptyList(),
    val queuePaused: Boolean = false,
    val tokenUsage: dev.androidagent.core.TokenUsage? = null,
    val usageLimits: List<dev.androidagent.core.UsageLimit> = emptyList(),
    val adbStatus: AdbStatus = AdbStatus(),
    val runtimeStatus: RuntimeStatus = RuntimeStatus(),
    val accountStatus: AccountStatus? = null,
    val availableModels: List<String> = emptyList(),
    val modelCatalog: List<AgentModel> = emptyList(),
    val availableSkills: List<AgentSkill> = emptyList(),
    val selectedModel: String? = null,
    val selectedReasoningEffort: String? = null,
    val voiceState: VoiceState = VoiceState(),
    val voiceTranscript: String = "",
    val voiceTranscriptRole: String? = null,
    val isDrawerOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isLoadingSessions: Boolean = false,
    val isLoadingMessages: Boolean = false,
    val isPreparingRuntime: Boolean = false,
    val isDiscoveringAdb: Boolean = false,
    val isPairing: Boolean = false,
    val isConnecting: Boolean = false,
    val isRefreshingAccount: Boolean = false,
    val isLoadingModels: Boolean = false,
    val isLoadingSkills: Boolean = false,
    val isLoadingWorkspace: Boolean = false,
    val workspaceError: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
    val updateInfo: AppUpdateInfo? = null,
    val isUpdateBannerVisible: Boolean = true,
)

/**
 * UI events are callbacks so the root app can keep all device and engine
 * operations behind its core gateways.
 */
data class AgentUiActions(
    val onDrawerChanged: (Boolean) -> Unit = {},
    val onNewChat: () -> Unit = {},
    val onSelectSession: (String) -> Unit = {},
    val onAttach: () -> Unit = {},
    val onRemoveAttachment: (String) -> Unit = {},
    val onSend: (String, List<PendingAttachment>) -> Unit = { _, _ -> },
    val onSteer: (String) -> Unit = {},
    val onStop: () -> Unit = {},
    val onCancelQueued: (String) -> Unit = {},
    val onResumeQueue: () -> Unit = {},
    val onVoiceToggle: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onCloseSettings: () -> Unit = {},
    val onPrepareRuntime: () -> Unit = {},
    val onLogin: () -> Unit = {},
    val onLogout: () -> Unit = {},
    val onRefreshAccount: () -> Unit = {},
    val onPair: (code: String, port: String) -> Unit = { _, _ -> },
    val onConnect: (port: String) -> Unit = {},
    val onDiscover: () -> Unit = {},
    val onOpenWirelessSettings: () -> Unit = {},
    val onOpenOverlayPermission: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onForgetPairing: () -> Unit = {},
    val onModelSelected: (String) -> Unit = {},
    val onReasoningEffortSelected: (String?) -> Unit = {},
    val onRenameSession: (sessionId: String, title: String) -> Unit = { _, _ -> },
    val onDeleteSession: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onOpenWorkspaceFiles: () -> Unit = {},
    val onOpenWorkspaceFile: (WorkspaceFileItem) -> Unit = {},
    val onApproval: (requestId: String, allow: Boolean) -> Unit = { _, _ -> },
    val onCheckForUpdates: () -> Unit = {},
    val onDownloadUpdate: () -> Unit = {},
    val onInstallUpdate: () -> Unit = {},
    val onDismissUpdateBanner: () -> Unit = {},
    val onOpenInstallPermission: () -> Unit = {},
)

internal fun EngineEvent.Approval.detailsText(): String = details.toString().removeSurrounding("{", "}")
