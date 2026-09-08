package dev.androidagent.app

import android.app.Application
import android.content.Intent
import dev.androidagent.adb.AndroidAdbTransport
import dev.androidagent.core.AgentCoordinator
import dev.androidagent.core.SessionRunQueue
import dev.androidagent.devicetools.AndroidDeviceTools
import dev.androidagent.enginecodex.CodexEngine
import dev.androidagent.overlay.FloatingControlOverlay
import dev.androidagent.runtime.AndroidRuntimeHost
import dev.androidagent.workspace.LocalSessionStore
import dev.androidagent.workspace.WorkspaceSeeder
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
    private lateinit var runCoordinator: AgentCoordinator
    // Declared before the gateways: they take `overlay` as a constructor argument,
    // so it must already be initialised rather than captured through a lambda.
    val overlay = FloatingControlOverlay(
        app,
        onStop = { queue.pause(); runCoordinator.stop(); if (voice.state.value.active) scope.launch { voice.stop() } },
        onSend = { text -> runCoordinator.steer(text) },
        onOpenApp = { app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) },
    )
    val tools = AndroidDeviceTools(adb, BuildConfig.APPLICATION_ID + "/dev.androidagent.app.ime.AgentInputMethodService") { hidden -> overlay.setCaptureHidden(hidden) }
    val voice = AndroidRealtimeVoiceController(app, engine, scope)
    val coordinator: AgentCoordinator
        get() = runCoordinator
    val queue: SessionRunQueue
    init {
        runCoordinator = AgentCoordinator(scope, engine, sessions, tools, overlay) { adb.status.value }
        queue = SessionRunQueue(scope, coordinator, sessions)
        runCatching {
            WorkspaceSeeder.installDefaultSkills(runtime.homeDirectory, app)
        }
    }
}
