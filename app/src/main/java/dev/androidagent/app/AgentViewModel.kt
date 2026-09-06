package dev.androidagent.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.androidagent.app.ui.*
import dev.androidagent.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    val graph = (application as AgentApplication).graph
    private val preferences = application.getSharedPreferences("ui", 0)
    private val current = MutableStateFlow<String?>(null)
    private val mutable = MutableStateFlow(AgentUiState(selectedModel = preferences.getString("model", null)))
    val ui: StateFlow<AgentUiState> = mutable.asStateFlow()
    private var setupJob: Job? = null

    init {
        viewModelScope.launch {
            graph.sessions.sessions.collect { list ->
                mutable.update { it.copy(sessions = list) }
                if (current.value == null || list.none { it.id == current.value }) {
                    val saved = preferences.getString("session", null)
                    current.value = list.firstOrNull { it.id == saved }?.id ?: list.firstOrNull()?.id
                }
                if (list.isEmpty()) current.value = graph.sessions.createSession().id
                updateTitle()
            }
        }
        viewModelScope.launch { current.filterNotNull().collectLatest { id ->
            preferences.edit().putString("session", id).apply()
            mutable.update { it.copy(activeSessionId = id, messages = emptyList(), attachments = emptyList(), isDrawerOpen = false) }
            updateTitle()
            graph.sessions.messages(id).collect { items -> mutable.update { it.copy(messages = items) } }
        } }
        viewModelScope.launch { graph.coordinator.state.collect { state -> mutable.update { it.copy(runState = state) } } }
        viewModelScope.launch { graph.adb.status.collect { state -> mutable.update { it.copy(adbStatus = state) } } }
        viewModelScope.launch { graph.runtime.status.collect { state -> mutable.update { it.copy(runtimeStatus = state) } } }
        viewModelScope.launch { graph.engine.events.collect { event ->
            when (event) {
                is EngineEvent.AccountChanged -> { mutable.update { it.copy(accountStatus = event.status, infoMessage = if (event.status.signedIn) "Signed in. You can start chatting." else null) }; if (event.status.signedIn) loadModels() }
                is EngineEvent.Failure -> if (!graph.coordinator.state.value.active) error(event.message)
                else -> Unit
            }
        } }
    }
    private fun updateTitle() { mutable.update { state -> state.copy(activeSessionTitle = state.sessions.firstOrNull { it.id == current.value }?.title) } }
    fun editUi(change: (AgentUiState) -> AgentUiState) = mutable.update(change)
    fun newChat() = task { current.value = graph.sessions.createSession().id }
    fun select(id: String) { current.value = id }
    fun rename(id: String, title: String) = task { graph.sessions.rename(id, title) }
    fun delete(id: String) {
        if (graph.coordinator.state.value.sessionId == id && graph.coordinator.state.value.active) { error("Stop this chat before deleting it."); return }
        task { graph.sessions.deleteSession(id) }
    }
    fun send(text: String, attachments: List<PendingAttachment>) {
        val id = current.value ?: return
        val active = graph.coordinator.state.value
        if (active.active && active.sessionId != id) { error("Another chat is working. Stop it before starting this one."); return }
        val paths = attachments.mapNotNull { it.path?.let(::File) }
        val images = attachments.filter { it.mimeType?.startsWith("image/") == true }.mapNotNull { it.path?.let(::File) }
        val otherFiles = paths.filter { it !in images }
        val prompt = if (otherFiles.isEmpty()) text else text + "\n\nAttached files in this session:\n" + otherFiles.joinToString("\n") { it.absolutePath }
        graph.coordinator.send(id, prompt, images, mutable.value.selectedModel)
        mutable.update { it.copy(attachments = emptyList(), errorMessage = null) }
    }
    fun stop() = graph.coordinator.stop()
    fun prepare() {
        if (setupJob?.isActive == true) return
        setupJob = task {
            mutable.update { it.copy(isPreparingRuntime = true, errorMessage = null) }
            try {
                graph.runtime.prepare(); graph.engine.connect()
                val account = graph.engine.account()
                mutable.update { it.copy(accountStatus = account) }
                loadModels()
            } finally { mutable.update { it.copy(isPreparingRuntime = false) } }
        }
    }
    fun login() = task {
        val status = graph.engine.login()
        mutable.update { it.copy(accountStatus = status, isSettingsOpen = true, errorMessage = null) }
    }
    fun logout() = task { check(!graph.coordinator.state.value.active) { "Stop the current run before signing out." }; graph.engine.logout(); mutable.update { it.copy(accountStatus = AccountStatus(false, "Sign in to Codex")) } }
    fun refreshAccount() = task { if (graph.runtime.status.value.phase in setOf(RuntimePhase.READY, RuntimePhase.RUNNING)) { val account = graph.engine.account(); mutable.update { it.copy(accountStatus = account) } } }
    private suspend fun loadModels() {
        mutable.update { it.copy(isLoadingModels = true) }
        try { val models = graph.engine.models(); mutable.update { it.copy(availableModels = models) } }
        finally { mutable.update { it.copy(isLoadingModels = false) } }
    }
    fun model(value: String) { preferences.edit().putString("model", value).apply(); mutable.update { it.copy(selectedModel = value) } }
    fun discover() = task {
        mutable.update { it.copy(isDiscoveringAdb = true) }
        try {
            val values = graph.adb.discover()
            mutable.update {
                it.copy(
                    discoveredEndpoints = values,
                    infoMessage = if (values.isEmpty()) graph.adb.status.value.message else null,
                )
            }
        }
        finally { mutable.update { it.copy(isDiscoveringAdb = false) } }
    }
    fun pair(code: String, port: String) = task {
        check(!graph.coordinator.state.value.active) { "Stop the current run before changing the connection." }
        mutable.update { it.copy(isPairing = true) }
        try {
            graph.adb.pair(parsePort(port), code.trim())
            mutable.update { it.copy(infoMessage = "Paired. Looking for the Wireless Debugging connect port…") }
            discover()
        }
        finally { mutable.update { it.copy(isPairing = false) } }
    }
    fun connect(port: String) = task {
        check(!graph.coordinator.state.value.active) { "Stop the current run before changing the connection." }
        mutable.update { it.copy(isConnecting = true) }
        try { graph.adb.connect(parsePort(port)); mutable.update { it.copy(infoMessage = "Connected to this phone.") } }
        finally { mutable.update { it.copy(isConnecting = false) } }
    }
    fun disconnect() = task {
        check(!graph.coordinator.state.value.active) { "Stop the current run before disconnecting." }
        graph.adb.disconnect()
    }
    fun forgetPairing() = task {
        check(!graph.coordinator.state.value.active) { "Stop the current run before removing this pairing." }
        graph.adb.forgetPairing()
        mutable.update { it.copy(infoMessage = "Pairing removed. Pair again to connect.") }
    }
    fun addAttachment(uri: Uri) = task {
        val id = current.value ?: return@task
        val resolver = getApplication<Application>().contentResolver
        val type = resolver.getType(uri) ?: "application/octet-stream"
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "attachment"
        val safeName = name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(100).ifBlank { "attachment" }
        val target = File(File(graph.sessions.workspace(id), "attachments").apply { mkdirs() }, "${UUID.randomUUID()}-$safeName")
        withContext(Dispatchers.IO) {
            try {
                resolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(8192); var total = 0L
                        while (true) { val count = input.read(buffer); if (count < 0) break; total += count; check(total <= 50L * 1024 * 1024) { "Attachments must be under 50 MB." }; output.write(buffer, 0, count) }
                    }
                } ?: kotlin.error("Could not open this file.")
            } catch (error: Exception) { target.delete(); throw error }
        }
        mutable.update { it.copy(attachments = it.attachments + PendingAttachment(UUID.randomUUID().toString(), name, target.absolutePath, type, target.length())) }
    }
    fun removeAttachment(id: String) { mutable.update { it.copy(attachments = it.attachments.filterNot { item -> item.id == id }) } }
    fun listFiles() = task {
        mutable.update { it.copy(isLoadingWorkspace = true, workspaceError = null) }
        try {
            val root = current.value?.let { graph.sessions.workspace(it) } ?: return@task
            val files = withContext(Dispatchers.IO) { root.walkTopDown().filter { it != root }.take(500).map { WorkspaceFileItem(it.relativeTo(root).path, it.length(), it.lastModified(), it.isDirectory) }.toList() }
            mutable.update { it.copy(workspaceFiles = files) }
        } finally { mutable.update { it.copy(isLoadingWorkspace = false) } }
    }
    fun resolveWorkspaceFile(item: WorkspaceFileItem): File {
        val root = graph.sessions.workspace(current.value ?: kotlin.error("No chat selected")).canonicalFile
        val file = File(root, item.path).canonicalFile
        require(file.toPath().startsWith(root.toPath())) { "File is outside this session." }
        return file
    }
    fun error(message: String) { mutable.update { it.copy(errorMessage = message) } }
    private fun parsePort(value: String): Int = value.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: kotlin.error("Enter a port from 1 to 65535.")
    private fun task(block: suspend () -> Unit): Job = viewModelScope.launch {
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error(failure.message ?: "Something went wrong.") }
    }
}
