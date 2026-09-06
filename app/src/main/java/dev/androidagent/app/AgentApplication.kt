package dev.androidagent.app

import android.app.Application
import android.content.Intent
import dev.androidagent.adb.AndroidAdbTransport
import dev.androidagent.core.AgentCoordinator
import dev.androidagent.devicetools.AndroidDeviceTools
import dev.androidagent.enginecodex.CodexEngine
import dev.androidagent.overlay.FloatingControlOverlay
import dev.androidagent.runtime.AndroidRuntimeHost
import dev.androidagent.workspace.LocalSessionStore
import dev.androidagent.voice.AndroidRealtimeVoiceController
import kotlinx.coroutines.*

class AgentApplication : Application() {
    lateinit var graph: AgentGraph
        private set
    override fun onCreate() { super.onCreate(); graph = AgentGraph(this) }
}

class AgentGraph(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val sessions = LocalSessionStore(app)
    val runtime = AndroidRuntimeHost(app)
    val engine = CodexEngine(runtime)
    val adb = AndroidAdbTransport(app)
    val tools = AndroidDeviceTools(adb, BuildConfig.APPLICATION_ID + "/dev.androidagent.app.ime.AgentInputMethodService")
    val voice = AndroidRealtimeVoiceController(app, engine, scope)
    private lateinit var runCoordinator: AgentCoordinator
    val overlay = FloatingControlOverlay(
        app,
        onStop = { runCoordinator.stop() },
        onSend = { text -> runCoordinator.steer(text) },
        onOpenApp = { app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) },
    )
    val coordinator: AgentCoordinator
        get() = runCoordinator
    init { runCoordinator = AgentCoordinator(scope, engine, sessions, tools, overlay) }
}
