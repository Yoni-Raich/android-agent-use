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
    suspend fun openSession(workspace: File, threadId: String?, model: String?, tools: List<ToolDefinition>): String
    suspend fun startTurn(threadId: String, prompt: String, images: List<File> = emptyList()): String
    suspend fun steer(threadId: String, turnId: String, prompt: String)
    suspend fun interrupt(threadId: String, turnId: String)
    suspend fun answerTool(requestId: String, result: ToolResult)
    suspend fun answerApproval(requestId: String, allow: Boolean)
    suspend fun close()
}

enum class RunPhase { IDLE, STARTING, THINKING, TOOL, CONTROLLING, STOPPING, ERROR }
data class RunState(val phase: RunPhase = RunPhase.IDLE, val sessionId: String? = null, val status: String = "Ready", val controlling: Boolean = false, val approval: EngineEvent.Approval? = null) {
    val active: Boolean get() = phase !in setOf(RunPhase.IDLE, RunPhase.ERROR)
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
    /** Move the compact control card away from a planned device coordinate. */
    fun avoidTouch(x: Int, y: Int) {}
    /** Temporarily removes the overlay from screenshots/UI hierarchy capture. */
    suspend fun setCaptureHidden(hidden: Boolean) {}
}
