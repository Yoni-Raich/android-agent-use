package dev.androidagent.app.ui

import dev.androidagent.app.update.AppUpdateInfo
import dev.androidagent.app.update.UpdateStatus
import dev.androidagent.core.AccountStatus
import dev.androidagent.core.AgentModel
import dev.androidagent.core.AgentSkill
import dev.androidagent.core.AdbEndpoint
import dev.androidagent.a11y.A11yStatus
import dev.androidagent.core.AdbStatus
import dev.androidagent.core.ChatMessage
import dev.androidagent.core.ChatSession
import dev.androidagent.core.EngineEvent
import dev.androidagent.core.RunState
import dev.androidagent.core.RuntimeStatus
import dev.androidagent.core.SetupChecklist
import dev.androidagent.core.SetupRow
import dev.androidagent.core.SetupSignals
import dev.androidagent.core.VoiceState
import dev.androidagent.connectors.PermissionMode

data class GitHubConnectorUiState(
    val available: Boolean = true,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val accountLogin: String? = null,
    val permissionMode: PermissionMode = PermissionMode.ASK_BEFORE_WRITES,
    val userCode: String? = null,
    val verificationUrl: String? = null,
    val toolCount: Int = 0,
    val status: String = "Not connected",
)

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

/**
 * Which durable root a listed file came from.
 *
 * The browser used to show only the session workspace, which is the one root
 * whose contents do not survive the chat. What the agent actually remembers
 * lives in the other two, and a user with no way to see them has no way to
 * check, correct or delete what has been recorded about them.
 */
enum class WorkspaceFileRoot(val label: String) {
    SESSION("This chat"),
    MEMORY("Shared memory"),
    SKILLS("Skills"),
}

data class WorkspaceFileItem(
    val path: String,
    val sizeBytes: Long? = null,
    val modifiedAt: Long? = null,
    val isDirectory: Boolean = false,
    val root: WorkspaceFileRoot = WorkspaceFileRoot.SESSION,
)

/**
 * One file opened in the in-app viewer.
 *
 * @param text the content, or null when it is not something this app should
 *   render. Everything the agent writes is Markdown or JSON, and handing those
 *   to an external viewer through a chooser is what made the browser look
 *   broken: most phones have nothing registered for `text/markdown`, so the
 *   chooser came up empty and the tap appeared to do nothing.
 * @param truncated true when the file was longer than the viewer shows.
 */
data class WorkspaceFilePreview(
    val item: WorkspaceFileItem,
    val text: String? = null,
    val truncated: Boolean = false,
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
 * Android grants the app asks for outside the permission dialog flow. They are
 * changed in system Settings, so they are re-read when the app resumes rather
 * than observed.
 */
data class DevicePermissions(
    val overlay: Boolean = false,
    val notifications: Boolean = false,
    val installUnknownApps: Boolean = false,
    val microphone: Boolean = false,
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
    val isFileBrowserOpen: Boolean = false,
    val filePreview: WorkspaceFilePreview? = null,
    val discoveredEndpoints: List<AdbEndpoint> = emptyList(),
    val runState: RunState = RunState(),
    val queuedTurns: List<dev.androidagent.core.QueuedTurn> = emptyList(),
    val queuePaused: Boolean = false,
    val tokenUsage: dev.androidagent.core.TokenUsage? = null,
    val usageLimits: List<dev.androidagent.core.UsageLimit> = emptyList(),
    val adbStatus: AdbStatus = AdbStatus(),
    val a11yStatus: A11yStatus = A11yStatus(declaredEnabled = false, connected = false),
    val permissions: DevicePermissions = DevicePermissions(),
    val runtimeStatus: RuntimeStatus = RuntimeStatus(),
    /**
     * Why the tunnel to OpenAI last failed, in one sentence, or null when it
     * has not. Without this the user sees only a retry counter and a 502.
     */
    val networkDiagnostic: String? = null,
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
    val githubConnector: GitHubConnectorUiState = GitHubConnectorUiState(),
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
    val onOpenAccessibilitySettings: () -> Unit = {},
    val onOpenAppInfo: () -> Unit = {},
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
    val onCloseWorkspaceFiles: () -> Unit = {},
    /** Show the file in the app. Only a viewer, never an editor. */
    val onOpenWorkspaceFile: (WorkspaceFileItem) -> Unit = {},
    val onCloseFilePreview: () -> Unit = {},
    /** Hand the file to another app, for the types this one cannot render. */
    val onOpenFileExternally: (WorkspaceFileItem) -> Unit = {},
    val onApproval: (requestId: String, allow: Boolean) -> Unit = { _, _ -> },
    val onCheckForUpdates: () -> Unit = {},
    val onDownloadUpdate: () -> Unit = {},
    val onInstallUpdate: () -> Unit = {},
    val onDismissUpdateBanner: () -> Unit = {},
    val onOpenInstallPermission: () -> Unit = {},
    val onOpenNotificationSettings: () -> Unit = {},
    /** Open the pairing dialog and read the code from it instead of asking for it. */
    val onCapturePairing: () -> Unit = {},
    val onDismissInfo: () -> Unit = {},
    val onConnectGitHub: () -> Unit = {},
    val onCancelGitHubConnect: () -> Unit = {},
    val onDisconnectGitHub: () -> Unit = {},
    val onGitHubPermissionChanged: (PermissionMode) -> Unit = {},
)

/** The setup checklist for this state, so no screen assembles the signals itself. */
internal fun AgentUiState.setupRows(): List<SetupRow> = SetupChecklist.rows(
    SetupSignals(
        runtimePhase = runtimeStatus.phase,
        signedIn = accountStatus?.signedIn,
        loginPending = accountStatus?.signedIn == false && accountStatus?.loginUrl != null,
        a11yConnected = a11yStatus.connected,
        a11yDeclared = a11yStatus.declaredEnabled,
        overlayGranted = permissions.overlay,
        notificationsGranted = permissions.notifications,
        installUpdatesGranted = permissions.installUnknownApps,
        microphoneGranted = permissions.microphone,
        adbPhase = adbStatus.phase,
        adbPort = adbStatus.port,
    ),
)

internal fun EngineEvent.Approval.detailsText(): String = details.toString().removeSurrounding("{", "}")
