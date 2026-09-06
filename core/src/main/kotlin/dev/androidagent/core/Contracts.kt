package dev.androidagent.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.io.File

enum class ConnectionPhase { DISCONNECTED, DISCOVERING, PAIRING, CONNECTING, CONNECTED, ERROR }
data class AdbStatus(val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED, val message: String = "Not connected", val port: Int? = null)
data class AdbEndpoint(val port: Int, val pairing: Boolean, val host: String = "127.0.0.1")
data class CommandResult(val output: String, val exitCode: Int)
interface AdbTransport {
    val status: StateFlow<AdbStatus>
    suspend fun discover(): List<AdbEndpoint>
    suspend fun pair(port: Int, code: String)
    suspend fun connect(port: Int)
    suspend fun execute(command: String, timeoutMs: Long = 30_000): CommandResult
    suspend fun executeBytes(command: String, timeoutMs: Long = 30_000): ByteArray
    suspend fun cancelActive()
    suspend fun disconnect()
    suspend fun forgetPairing()
}

@Serializable
data class ChatSession(val id: String, val title: String, val createdAt: Long, val updatedAt: Long, val engineThreadId: String? = null)
@Serializable
data class ChatMessage(val id: String, val sessionId: String, val role: String, val text: String, val createdAt: Long, val state: String = "complete", val attachmentPaths: List<String> = emptyList())
interface SessionStore {
    val sessions: StateFlow<List<ChatSession>>
    suspend fun createSession(): ChatSession
    suspend fun getSession(id: String): ChatSession?
    fun messages(sessionId: String): Flow<List<ChatMessage>>
    suspend fun append(message: ChatMessage)
    suspend fun updateMessage(id: String, text: String, state: String = "complete")
    suspend fun setThread(sessionId: String, threadId: String)
    suspend fun rename(sessionId: String, title: String)
    suspend fun deleteSession(sessionId: String)
    fun workspace(sessionId: String): File
}

enum class RuntimePhase { MISSING, PREPARING, READY, RUNNING, ERROR }
data class RuntimeStatus(val phase: RuntimePhase = RuntimePhase.MISSING, val message: String = "Runtime not ready", val progress: Float? = null)
interface RuntimeHost {
    val status: StateFlow<RuntimeStatus>
    val homeDirectory: File
    suspend fun prepare()
    suspend fun startAppServer(): Process
    suspend fun stop()
}

data class ToolDefinition(val name: String, val description: String, val inputSchema: JsonObject)
data class ToolResult(val text: String, val imageBase64: String? = null, val success: Boolean = true)
data class AccountStatus(val signedIn: Boolean, val label: String, val loginUrl: String? = null, val userCode: String? = null)

/** A reasoning effort advertised by the connected engine for one model. */
data class ReasoningEffortOption(val value: String, val description: String = "")

/** Model metadata returned by the connected engine's model catalog. */
data class AgentModel(
    val id: String,
    val displayName: String = id,
    val reasoningEfforts: List<ReasoningEffortOption> = emptyList(),
    val defaultReasoningEffort: String? = null,
)
sealed interface EngineEvent {
    data class TurnStarted(val threadId: String, val turnId: String) : EngineEvent
    data class TextDelta(val text: String, val threadId: String? = null, val turnId: String? = null) : EngineEvent
    data class ToolCall(val requestId: String, val name: String, val arguments: JsonObject, val threadId: String? = null, val turnId: String? = null) : EngineEvent
    data class Approval(val requestId: String, val method: String, val details: JsonObject, val threadId: String? = null, val turnId: String? = null) : EngineEvent
    data class Activity(val text: String) : EngineEvent
    data class TurnFinished(val status: String, val error: String? = null, val threadId: String? = null, val turnId: String? = null) : EngineEvent
    data class AccountChanged(val status: AccountStatus) : EngineEvent
    data class Failure(val message: String, val threadId: String? = null, val turnId: String? = null) : EngineEvent
}
interface AgentEngine {
    val events: Flow<EngineEvent>
    suspend fun connect()
    suspend fun account(): AccountStatus
    suspend fun login(): AccountStatus
    suspend fun logout()
    suspend fun models(): List<String>
    /**
     * Return model metadata when the engine can provide it. The default keeps
     * older engine implementations usable while exposing a catalog to newer
     * clients.
     */
    suspend fun modelCatalog(): List<AgentModel> = models().map { AgentModel(id = it) }
    suspend fun openSession(workspace: File, threadId: String?, model: String?, tools: List<ToolDefinition>): String
    suspend fun startTurn(threadId: String, prompt: String, images: List<File> = emptyList()): String
    /** Start a turn with an optional model-advertised reasoning effort. */
    suspend fun startTurn(threadId: String, prompt: String, images: List<File> = emptyList(), reasoningEffort: String?): String =
        startTurn(threadId, prompt, images)
    suspend fun steer(threadId: String, turnId: String, prompt: String)
    suspend fun interrupt(threadId: String, turnId: String)
    suspend fun answerTool(requestId: String, result: ToolResult)
    suspend fun answerApproval(requestId: String, allow: Boolean)
    suspend fun close()
}

/** Lifecycle phase for the experimental Codex Realtime voice session. */
enum class VoicePhase { IDLE, STARTING, LISTENING, SPEAKING, STOPPING, ERROR }

/** State exposed to the voice UI without coupling it to the app-server protocol. */
data class VoiceState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val message: String = "Ready",
    val threadId: String? = null,
) {
    val active: Boolean get() = phase !in setOf(VoicePhase.IDLE, VoicePhase.ERROR)
}

/** Transport used by a realtime voice session. WebRTC is the account-auth path. */
enum class RealtimeTransport { WEBRTC, WEBSOCKET }

/** PCM audio chunk used by the voice contract. The engine owns protocol encoding. */
data class RealtimeAudioChunk(
    val data: ByteArray,
    val sampleRate: Int,
    val numChannels: Int,
    val samplesPerChannel: Int? = null,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(numChannels > 0) { "numChannels must be positive" }
        require(samplesPerChannel == null || samplesPerChannel >= 0) {
            "samplesPerChannel must be non-negative"
        }
    }

    /** Return a copy so callers cannot mutate a chunk while it is being sent or played. */
    fun copyData(): ByteArray = data.copyOf()
}

/** Events emitted by a Realtime voice session. */
sealed interface VoiceEvent {
    data class Started(
        val threadId: String,
        val realtimeSessionId: String? = null,
        val version: String? = null,
    ) : VoiceEvent

    /** Remote SDP answer emitted by app-server for a WebRTC session. */
    data class SdpAnswer(val threadId: String, val sdp: String) : VoiceEvent

    data class TranscriptDelta(val threadId: String, val role: String, val delta: String) : VoiceEvent
    data class TranscriptDone(val threadId: String, val role: String, val text: String) : VoiceEvent
    data class OutputAudio(val threadId: String, val audio: RealtimeAudioChunk) : VoiceEvent
    data class Failure(val message: String, val threadId: String? = null) : VoiceEvent
    data class Closed(val threadId: String, val reason: String? = null) : VoiceEvent
}

/** Small boundary around the experimental app-server Realtime Voice API. */
interface RealtimeVoiceEngine {
    val voiceEvents: Flow<VoiceEvent>
    val voiceState: StateFlow<VoiceState>

    suspend fun startVoice(
        threadId: String,
        model: String? = null,
        transport: RealtimeTransport = RealtimeTransport.WEBSOCKET,
        offerSdp: String? = null,
    )
    suspend fun appendAudio(audio: RealtimeAudioChunk)
    suspend fun appendText(text: String, role: String = "user")
    suspend fun appendSpeech(text: String)
    suspend fun stopVoice()
}

enum class RunPhase { IDLE, STARTING, THINKING, TOOL, CONTROLLING, STOPPING, ERROR }
data class RunState(val phase: RunPhase = RunPhase.IDLE, val sessionId: String? = null, val status: String = "Ready", val controlling: Boolean = false, val approval: EngineEvent.Approval? = null) {
    val active: Boolean get() = phase !in setOf(RunPhase.IDLE, RunPhase.ERROR)
}
enum class OverlayPhase { STARTING, THINKING, RUNNING, CONTROLLING, STOPPING, DONE, ERROR }
data class OverlayState(val phase: OverlayPhase, val detail: String? = null) {
    val label: String
        get() = detail?.trim()?.takeIf { it.isNotEmpty() }?.let { "${phase.title} · $it" } ?: phase.title

    private val OverlayPhase.title: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}
interface DeviceToolGateway {
    val definitions: List<ToolDefinition>
    fun beginRun(runId: String, workspace: File)
    fun revoke()
    fun needsControl(name: String): Boolean
    suspend fun invoke(name: String, arguments: JsonObject): ToolResult
    suspend fun cancel()
}
interface ControlOverlay {
    suspend fun show(status: String)
    fun update(status: String)
    fun hide()
    /** Show one explicit lifecycle state. Implementations may reuse an existing card. */
    suspend fun showState(state: OverlayState) { show(state.label) }
    /** Update the lifecycle state without changing which window owns the card. */
    fun updateState(state: OverlayState) { update(state.label) }
    /** Display the terminal state, then release the overlay. */
    fun finish(state: OverlayState) { updateState(state); hide() }
    /** Move the compact control card away from a planned device coordinate. */
    fun avoidTouch(x: Int, y: Int) {}
    /** Temporarily removes the overlay from screenshots/UI hierarchy capture. */
    suspend fun setCaptureHidden(hidden: Boolean) {}
}
