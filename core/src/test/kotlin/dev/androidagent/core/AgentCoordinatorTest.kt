package dev.androidagent.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AgentCoordinatorTest {
    @Test fun stopRevokesBeforeWaitingForEngineAndBlocksAnActionWaitingForOverlay() = runTest {
        val rig = Rig(this)
        rig.overlay.waitForShow = CompletableDeferred()
        rig.engine.waitForInterrupt = CompletableDeferred()
        rig.coordinator.send("one", "Open settings")
        runCurrent()
        rig.engine.emit(EngineEvent.ToolCall("1", "tap", buildJsonObject {}, "thread", "turn"))
        runCurrent()
        assertEquals(0, rig.tools.executions)
        rig.coordinator.stop()
        assertTrue(rig.tools.revoked)
        assertEquals(RunPhase.STOPPING, rig.coordinator.state.value.phase)
        assertTrue(rig.overlay.states.any { it.phase == OverlayPhase.STOPPING })
        rig.overlay.waitForShow!!.complete(Unit)
        runCurrent()
        assertEquals(0, rig.tools.executions)
        advanceTimeBy(2_001)
        runCurrent()
        assertTrue(rig.engine.closed)
        assertFalse(rig.coordinator.state.value.active)
        assertEquals(OverlayPhase.DONE, rig.overlay.finished.last().phase)
        rig.close()
    }

    @Test fun lateEventsAfterStopCannotRunToolsOrAppendAssistantText() = runTest {
        val rig = Rig(this)
        rig.coordinator.send("one", "Read the screen")
        runCurrent()
        rig.coordinator.stop()
        runCurrent()
        rig.engine.emit(EngineEvent.TextDelta("late output"))
        rig.engine.emit(EngineEvent.ToolCall("late", "tap", buildJsonObject {}))
        runCurrent()
        assertEquals(0, rig.tools.executions)
        assertFalse(rig.store.messages.any { it.text.contains("late output") })
        assertTrue(rig.engine.answers.any { !it.success && it.text.contains("stopped") })
        rig.close()
    }

    @Test fun anotherSessionCannotTakeOverAnActiveDeviceRun() = runTest {
        val rig = Rig(this)
        rig.coordinator.send("one", "First")
        runCurrent()
        rig.coordinator.send("two", "Second")
        runCurrent()
        assertEquals("one", rig.coordinator.state.value.sessionId)
        assertEquals(1, rig.engine.turns)
        assertFalse(rig.store.messages.any { it.sessionId == "two" })
        rig.close()
    }

    @Test fun uiControlWaitsForOverlayAndBackendReadsDoNotShowIt() = runTest {
        val rig = Rig(this)
        rig.coordinator.send("one", "Read and tap")
        runCurrent()
        assertEquals(1, rig.overlay.shown)
        assertTrue(rig.overlay.states.any { it.phase == OverlayPhase.THINKING })
        rig.engine.emit(EngineEvent.ToolCall("read", "read_ui", buildJsonObject {}, "thread", "turn"))
        runCurrent()
        assertTrue(rig.overlay.states.any { it.phase == OverlayPhase.RUNNING && it.detail == "read ui" })
        rig.engine.emit(EngineEvent.ToolCall("tap", "tap", buildJsonObject {}, "thread", "turn"))
        runCurrent()
        assertTrue(rig.overlay.states.any { it.phase == OverlayPhase.CONTROLLING && it.detail == "tap" })
        assertEquals(listOf("read_ui", "tap"), rig.tools.names)
        assertTrue(rig.tools.controlWasVisible)
        rig.close()
    }

    @Test fun completedRunShowsDoneThenReleasesOverlay() = runTest {
        val rig = Rig(this)
        rig.coordinator.send("one", "Finish this")
        runCurrent()
        rig.engine.emit(EngineEvent.TurnFinished("completed", threadId = "thread", turnId = "turn"))
        runCurrent()

        assertTrue(rig.overlay.states.any { it.phase == OverlayPhase.STARTING })
        assertTrue(rig.overlay.states.any { it.phase == OverlayPhase.THINKING })
        assertEquals(OverlayPhase.DONE, rig.overlay.finished.last().phase)
        assertFalse(rig.overlay.visible)
        rig.close()
    }

    @Test fun overlayFailureReturnsToolErrorWithoutExecutingDeviceAction() = runTest {
        val rig = Rig(this)
        rig.overlay.fail = true
        rig.coordinator.send("one", "Tap")
        runCurrent()
        rig.engine.emit(EngineEvent.ToolCall("tap", "tap", buildJsonObject {}, "thread", "turn"))
        runCurrent()
        assertEquals(0, rig.tools.executions)
        assertEquals(RunPhase.ERROR, rig.coordinator.state.value.phase)
        assertEquals(OverlayPhase.ERROR, rig.overlay.finished.last().phase)
        rig.close()
    }

    private class Rig(test: TestScope) {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(test.testScheduler))
        val engine = FakeEngine()
        val store = FakeStore()
        val overlay = FakeOverlay()
        val tools = FakeTools(overlay)
        val coordinator = AgentCoordinator(scope, engine, store, tools, overlay)
        fun close() { scope.cancel() }
    }

    private class FakeEngine : AgentEngine {
        private val stream = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
        override val events = stream.asSharedFlow()
        var turns = 0
        var closed = false
        var waitForInterrupt: CompletableDeferred<Unit>? = null
        val answers = mutableListOf<ToolResult>()
        suspend fun emit(value: EngineEvent) = stream.emit(value)
        override suspend fun connect() = Unit
        override suspend fun account() = AccountStatus(true, "Test")
        override suspend fun login() = account()
        override suspend fun logout() = Unit
        override suspend fun models() = listOf("test")
        override suspend fun openSession(workspace: File, threadId: String?, model: String?, tools: List<ToolDefinition>) = "thread"
        override suspend fun startTurn(threadId: String, prompt: String, images: List<File>): String { turns++; return "turn" }
        override suspend fun steer(threadId: String, turnId: String, prompt: String) = Unit
        override suspend fun interrupt(threadId: String, turnId: String) { waitForInterrupt?.await() }
        override suspend fun answerTool(requestId: String, result: ToolResult) { answers.add(result) }
        override suspend fun answerApproval(requestId: String, allow: Boolean) = Unit
        override suspend fun close() { closed = true }
    }
    private class FakeStore : SessionStore {
        override val sessions = MutableStateFlow(listOf(ChatSession("one", "One", 0, 0), ChatSession("two", "Two", 0, 0)))
        val messages = mutableListOf<ChatMessage>()
        override suspend fun createSession() = sessions.value.first()
        override suspend fun getSession(id: String) = sessions.value.firstOrNull { it.id == id }
        override fun messages(sessionId: String) = flowOf(messages.filter { it.sessionId == sessionId })
        override suspend fun append(message: ChatMessage) { messages.add(message) }
        override suspend fun updateMessage(id: String, text: String, state: String) { val i = messages.indexOfFirst { it.id == id }; if (i >= 0) messages[i] = messages[i].copy(text = text, state = state) }
        override suspend fun setThread(sessionId: String, threadId: String) = Unit
        override suspend fun rename(sessionId: String, title: String) = Unit
        override suspend fun deleteSession(sessionId: String) = Unit
        override fun workspace(sessionId: String) = File("session-$sessionId")
    }
    private class FakeTools(private val overlay: FakeOverlay) : DeviceToolGateway {
        override val definitions = emptyList<ToolDefinition>()
        var revoked = true
        var executions = 0
        var controlWasVisible = false
        val names = mutableListOf<String>()
        override fun beginRun(runId: String, workspace: File) { revoked = false }
        override fun revoke() { revoked = true }
        override fun needsControl(name: String) = name == "tap"
        override suspend fun invoke(name: String, arguments: kotlinx.serialization.json.JsonObject): ToolResult {
            check(!revoked)
            if (needsControl(name)) controlWasVisible = overlay.visible
            executions++; names.add(name)
            return ToolResult("Done")
        }
        override suspend fun cancel() = Unit
    }
    private class FakeOverlay : ControlOverlay {
        var waitForShow: CompletableDeferred<Unit>? = null
        var shown = 0
        var visible = false
        var fail = false
        val states = mutableListOf<OverlayState>()
        val finished = mutableListOf<OverlayState>()
        override suspend fun show(status: String) { if (fail) error("Overlay permission required"); waitForShow?.await(); shown++; visible = true }
        override fun update(status: String) = Unit
        override suspend fun showState(state: OverlayState) { states += state; show(state.label) }
        override fun updateState(state: OverlayState) { states += state; update(state.label) }
        override fun finish(state: OverlayState) { finished += state; updateState(state); hide() }
        override fun hide() { visible = false }
    }
}
