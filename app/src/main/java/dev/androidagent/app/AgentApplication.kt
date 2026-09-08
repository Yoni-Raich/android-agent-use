package dev.androidagent.app

import android.app.Application
import android.content.Intent
import dev.androidagent.a11y.A11yDeviceTools
import dev.androidagent.adb.AndroidAdbTransport
import dev.androidagent.core.AgentCoordinator
import dev.androidagent.core.CompositeDeviceToolGateway
import dev.androidagent.core.KnowledgeStore
import dev.androidagent.core.KnowledgeToolGateway
import dev.androidagent.core.ObservationState
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
    // One counter for every backend, so an observation revision never moves
    // backwards when a call falls through from one gateway to another.
    private val observations = ObservationState()
    val adbTools = AndroidDeviceTools(
        adb,
        BuildConfig.APPLICATION_ID + "/dev.androidagent.app.ime.AgentInputMethodService",
        observations,
        { x, y -> overlay.avoidTouch(x, y) },
    ) { hidden -> overlay.setCaptureHidden(hidden) }
    val a11yTools = A11yDeviceTools(
        app,
        observations,
        avoidTouch = { x, y -> overlay.avoidTouch(x, y) },
        authorizeIntent = { request, dispatch -> runCoordinator.authorizeLocalIntent(request, dispatch) },
    )
    // Under homeDirectory, which is global across chats and is the one place
    // WorkspaceSeeder does not rewrite on every access.
    val knowledge = KnowledgeStore(KnowledgeStore.directoryIn(runtime.homeDirectory))
    val knowledgeTools = KnowledgeToolGateway(knowledge)
    // Accessibility first: it needs no ADB, keeps the phone's own settings
    // untouched, and falls through to ADB for anything it cannot do. The
    // knowledge gateway shares no tool name with either device backend, so its
    // position in the chain only decides where its names appear in the list.
    val tools = CompositeDeviceToolGateway(listOf(knowledgeTools, a11yTools, adbTools))
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
