package dev.androidagent.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class AgentCoordinator(
    private val scope: CoroutineScope,
    private val engine: AgentEngine,
    private val sessions: SessionStore,
    private val tools: DeviceToolGateway,
    private val overlay: ControlOverlay,
) {
    private val mutableState = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = mutableState.asStateFlow()
    private val epoch = AtomicLong()
    private val lifecycleLock = Any()
    private val toolLock = Mutex()
    private val assistantFlushLock = Mutex()
    private var runJob: Job? = null
    private val toolJobs = mutableSetOf<Job>()
    private val controlJobs = mutableSetOf<Job>()
    private var completion: CompletableDeferred<Unit>? = null
    private var thread: String? = null
    private var turn: String? = null
    private var assistantId: String? = null
    private val assistantText = StringBuilder()
    private var assistantOutcome = "complete"
    private var controlTakeover = false
    private var awaitingTurn = false
    private var voiceMode = false
    private val startupEvents = ArrayDeque<EngineEvent>()
    private var textRevision = 0L
    private var textFlushJob: Job? = null

    init { scope.launch { engine.events.collect(::handleEvent) } }

    fun send(
        sessionId: String,
        prompt: String,
        images: List<File> = emptyList(),
        model: String? = null,
        reasoningEffort: String? = null,
        skill: AgentSkill? = null,
    ) {
        if (prompt.isBlank() && images.isEmpty()) return
        synchronized(lifecycleLock) {
            if (state.value.active) {
                if (state.value.sessionId == sessionId && state.value.phase != RunPhase.STOPPING) steer(prompt)
                return
            }
            val token = epoch.incrementAndGet()
            val runCompletion = CompletableDeferred<Unit>()
            mutableState.value = RunState(RunPhase.STARTING, sessionId, "Starting Codex")
            completion = runCompletion
            thread = null
            turn = null
            assistantId = null
            assistantText.clear()
            assistantOutcome = "complete"
            controlTakeover = false
            awaitingTurn = false
            startupEvents.clear()
            textRevision = 0L
            runJob = scope.launch { run(token, runCompletion, sessionId, prompt, images, model, reasoningEffort, skill) }
        }
    }

    /**
     * Attach the device-tool gateway to turns delegated by a realtime voice
     * session. Realtime owns the conversation transport; this coordinator owns
     * only local tool safety and the delegated turn identity.
     */
    fun beginVoice(sessionId: String, threadId: String, workspace: File) {
        require(threadId.isNotBlank()) { "Voice thread ID is required." }
        synchronized(lifecycleLock) {
            check(!state.value.active) { "Another agent run is already active." }
            val token = epoch.incrementAndGet()
            tools.beginRun(token.toString(), workspace)
            voiceMode = true
            thread = threadId
            turn = null
            assistantId = null
            assistantText.clear()
            assistantOutcome = "complete"
            controlTakeover = false
            awaitingTurn = false
            startupEvents.clear()
            completion = null
            runJob = null
            mutableState.value = RunState(RunPhase.THINKING, sessionId, "Voice ready")
        }
    }

    /** Revoke local voice-delegated work without closing the shared app-server. */
    fun endVoice() {
        val context = synchronized(lifecycleLock) {
            if (!voiceMode) return
            val stoppingEpoch = epoch.incrementAndGet()
            voiceMode = false
            val controls = controlJobs.toList().also { jobs ->
                jobs.forEach { it.cancel() }
                controlJobs.clear()
            }
            val toolsInFlight = toolJobs.toList().also { jobs ->
                jobs.forEach { it.cancel() }
                toolJobs.clear()
            }
            val result = VoiceStopContext(
                stoppingEpoch = stoppingEpoch,
                sessionId = state.value.sessionId,
                threadId = thread,
                turnId = turn,
                controls = controls,
                toolsInFlight = toolsInFlight,
            )
            turn = null
            controlTakeover = false
            mutableState.value = state.value.copy(
                phase = RunPhase.STOPPING,
                status = "Ending voice",
                controlling = false,
                approval = null,
            )
            result
        }
        tools.revoke()
        runCatching { overlay.updateState(OverlayState(OverlayPhase.STOPPING, "Voice")) }
        scope.launch {
            withContext(NonCancellable) {
                context.controls.forEach { job -> runCatching { withTimeout(2_000) { job.join() } } }
                val deviceStop = async { runCatching { withTimeout(2_000) { tools.cancel() } } }
                val engineStop = async {
                    runCatching {
                        withTimeout(2_000) {
                            if (context.threadId != null && context.turnId != null) {
                                engine.interrupt(context.threadId, context.turnId)
                            }
                        }
                    }
                }
                deviceStop.await()
                context.toolsInFlight.forEach { job -> runCatching { withTimeout(2_000) { job.join() } } }
                engineStop.await()
                synchronized(lifecycleLock) {
                    if (epoch.get() == context.stoppingEpoch) {
                        thread = null
                        assistantId = null
                        assistantText.clear()
                        completion = null
                        mutableState.value = RunState(sessionId = context.sessionId, status = "Voice ended")
                    }
                }
                runCatching { overlay.finish(OverlayState(OverlayPhase.DONE, "Voice ended")) }
            }
        }
    }

    private suspend fun run(
        token: Long,
        runCompletion: CompletableDeferred<Unit>,
        sessionId: String,
        prompt: String,
        images: List<File>,
        model: String?,
        reasoningEffort: String?,
        skill: AgentSkill?,
    ) {
        try {
            // Keep the control surface visible for the whole active run. This
            // also checks overlay permission before Codex can request a device action.
            overlay.showState(OverlayState(OverlayPhase.STARTING))
            sessions.append(message(sessionId, "user", prompt, attachments = images.map { it.absolutePath }))
            val session = sessions.getSession(sessionId) ?: error("Chat no longer exists")
            if (session.title == "New chat") sessions.rename(sessionId, prompt.take(48).ifBlank { "Image chat" })
            val work = sessions.workspace(sessionId)
            tools.beginRun(token.toString(), work)
            engine.connect()
            check(engine.account().signedIn) { "Sign in to Codex in Settings first." }
            ensureCurrent(token)
            val openedThread = engine.openSession(work, session.engineThreadId, model, tools.definitions)
            synchronized(lifecycleLock) {
                ensureCurrentLocked(token)
                thread = openedThread
            }
            sessions.setThread(sessionId, openedThread)
            ensureCurrent(token)
            val messageId = UUID.randomUUID().toString()
            synchronized(lifecycleLock) {
                ensureCurrentLocked(token)
                assistantText.clear()
                assistantOutcome = "complete"
                textRevision = 0L
                assistantId = messageId
            }
            sessions.append(ChatMessage(messageId, sessionId, "assistant", "", System.currentTimeMillis(), "streaming"))
            synchronized(lifecycleLock) {
                ensureCurrentLocked(token)
                mutableState.value = state.value.copy(phase = RunPhase.THINKING, status = "Thinking")
            }
            overlay.updateState(OverlayState(OverlayPhase.THINKING))
            beginTurn(token)
            val startedTurn = engine.startTurn(openedThread, prompt, images, reasoningEffort, skill)
            if (!activateTurn(token, startedTurn)) return
            ensureCurrent(token)
            runCompletion.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (isCurrent(token)) {
                sessions.append(message(sessionId, "system", error.message ?: "Run failed"))
                synchronized(lifecycleLock) {
                    if (isCurrentLocked(token)) {
                        assistantOutcome = "error"
                        mutableState.value = state.value.copy(phase = RunPhase.ERROR, status = error.message ?: "Run failed", controlling = false)
                    }
                }
            }
        } finally {
            finalizeRun(token, sessionId)
        }
    }

    fun steer(prompt: String) {
        if (prompt.isBlank()) return
        val request = synchronized(lifecycleLock) {
            val current = state.value
            val currentThread = thread
            val currentTurn = turn
            if (!current.active || current.phase == RunPhase.STOPPING || currentThread.isNullOrBlank() || currentTurn.isNullOrBlank()) {
                null
            } else {
                SteerRequest(epoch.get(), current.sessionId, currentThread, currentTurn, prompt)
            }
        } ?: return
        launchControl {
            try {
                if (!isCurrentTurn(request.token, request.threadId, request.turnId)) return@launchControl
                engine.steer(request.threadId, request.turnId, request.prompt)
                if (isCurrentTurn(request.token, request.threadId, request.turnId)) {
                    request.sessionId?.let { sessions.append(message(it, "user", request.prompt)) }
                }
            } catch (error: Exception) {
                if (error !is CancellationException && isCurrent(request.token)) {
                    request.sessionId?.let { sessions.append(message(it, "system", error.message ?: "Could not send instruction")) }
                }
            }
        }
    }

    fun stop() {
        if (synchronized(lifecycleLock) { voiceMode }) {
            endVoice()
            return
        }
        val context = synchronized(lifecycleLock) {
            val snapshot = state.value
            if (!snapshot.active || snapshot.phase == RunPhase.STOPPING || completion?.isCompleted == true) return
            val stoppingEpoch = epoch.incrementAndGet()
            val controls = controlJobs.toList().also { jobs ->
                jobs.forEach { it.cancel() }
                controlJobs.clear()
            }
            val toolsInFlight = toolJobs.toList().also { jobs ->
                jobs.forEach { it.cancel() }
                toolJobs.clear()
            }
            val flush = textFlushJob.also { it?.cancel() }
            textFlushJob = null
            val result = StopContext(
                stoppingEpoch = stoppingEpoch,
                snapshot = snapshot,
                threadId = thread,
                turnId = turn,
                assistantId = assistantId,
                text = assistantText.toString(),
                controls = controls,
                toolsInFlight = toolsInFlight,
                flush = flush,
                runJob = runJob,
            )
            awaitingTurn = false
            startupEvents.clear()
            controlTakeover = false
            mutableState.value = snapshot.copy(phase = RunPhase.STOPPING, status = "Stopping", controlling = false, approval = null)
            result
        }
        tools.revoke()
        runCatching { overlay.updateState(OverlayState(OverlayPhase.STOPPING)) }
        context.runJob?.cancel()
        scope.launch {
            withContext(NonCancellable) {
                context.controls.forEach { job -> runCatching { withTimeout(2_000) { job.join() } } }
                val deviceStop = async { runCatching { withTimeout(2_000) { tools.cancel() } } }
                val engineStop = async {
                    val interrupted = runCatching {
                        withTimeout(2_000) {
                            if (context.threadId != null && context.turnId != null) engine.interrupt(context.threadId, context.turnId)
                            else engine.close()
                        }
                    }.isSuccess
                    if (!interrupted) runCatching { engine.close() }
                }
                deviceStop.await()
                context.toolsInFlight.forEach { job -> runCatching { withTimeout(2_000) { job.join() } } }
                engineStop.await()
                context.flush?.let { job -> runCatching { withTimeout(1_000) { job.join() } } }
                context.assistantId?.let { id ->
                    runCatching {
                        assistantFlushLock.withLock {
                            sessions.updateMessage(id, context.text.ifBlank { "Stopped." }, "interrupted")
                        }
                    }
                }
                runCatching { overlay.setCaptureHidden(false) }
                synchronized(lifecycleLock) {
                    if (epoch.get() == context.stoppingEpoch) {
                        thread = null
                        turn = null
                        assistantId = null
                        assistantText.clear()
                        assistantOutcome = "complete"
                        completion = null
                        mutableState.value = RunState(sessionId = context.snapshot.sessionId, status = "Stopped")
                    }
                }
                runCatching { overlay.finish(OverlayState(OverlayPhase.DONE, "Stopped")) }
            }
        }
    }

    fun approve(allow: Boolean) {
        val request = synchronized(lifecycleLock) {
            val approval = state.value.approval
            if (approval == null || state.value.phase == RunPhase.STOPPING || !approvalMatchesLocked(approval)) {
                null
            } else {
                ApprovalRequest(epoch.get(), approval)
            }
        } ?: return
        launchControl {
            try {
                if (!isCurrentApproval(request)) return@launchControl
                engine.answerApproval(request.approval.requestId, allow)
                synchronized(lifecycleLock) {
                    if (isCurrentApprovalLocked(request)) mutableState.value = state.value.copy(approval = null)
                }
            } catch (error: Exception) {
                if (error !is CancellationException && isCurrent(request.token)) {
                    state.value.sessionId?.let { sessions.append(message(it, "system", error.message ?: "Approval failed")) }
                }
            }
        }
    }

    private fun beginTurn(token: Long) {
        synchronized(lifecycleLock) {
            ensureCurrentLocked(token)
            awaitingTurn = true
            turn = null
            startupEvents.clear()
        }
    }

    private suspend fun activateTurn(token: Long, startedTurn: String): Boolean {
        val replay = synchronized(lifecycleLock) {
            if (!isCurrentLocked(token) || startedTurn.isBlank()) {
                startupEvents.clear()
                awaitingTurn = false
                null
            } else {
                turn = startedTurn
                awaitingTurn = false
                val events = startupEvents.filter { turnIdOf(it) == startedTurn }
                startupEvents.clear()
                events
            }
        } ?: return false
        replay.forEach { handleEvent(it) }
        return true
    }

    private fun bufferStartupEvent(event: EngineEvent): Boolean = synchronized(lifecycleLock) {
        if (!awaitingTurn) return@synchronized false
        val expectedThread = thread
        val eventThread = threadIdOf(event)
        val eventTurn = turnIdOf(event)
        if (expectedThread.isNullOrBlank() || eventThread != expectedThread || eventTurn.isNullOrBlank()) {
            false
        } else {
            startupEvents.addLast(event)
            true
        }
    }

    private fun matches(threadId: String?, turnId: String?): Boolean = synchronized(lifecycleLock) {
        state.value.active && state.value.phase != RunPhase.STOPPING &&
            !threadId.isNullOrBlank() && !turnId.isNullOrBlank() &&
            threadId == thread && turnId == turn
    }

    private fun approvalMatches(approval: EngineEvent.Approval): Boolean = synchronized(lifecycleLock) { approvalMatchesLocked(approval) }

    private fun approvalMatchesLocked(approval: EngineEvent.Approval): Boolean =
        state.value.active && state.value.phase != RunPhase.STOPPING &&
            !approval.threadId.isNullOrBlank() && !approval.turnId.isNullOrBlank() &&
            approval.threadId == thread && approval.turnId == turn

    private fun failureMatches(event: EngineEvent.Failure): Boolean = synchronized(lifecycleLock) {
        if (!state.value.active || state.value.phase == RunPhase.STOPPING) return@synchronized false
        if (event.threadId.isNullOrBlank() && event.turnId.isNullOrBlank()) true
        else event.threadId == thread && event.turnId == turn
    }

    private fun isCurrent(token: Long): Boolean = synchronized(lifecycleLock) { isCurrentLocked(token) }

    private fun isCurrentLocked(token: Long): Boolean =
        epoch.get() == token && state.value.active && state.value.phase != RunPhase.STOPPING

    private fun isCurrentTurn(token: Long, threadId: String, turnId: String): Boolean = synchronized(lifecycleLock) {
        isCurrentTurnLocked(token, threadId, turnId)
    }

    private fun isCurrentTurnLocked(token: Long, threadId: String, turnId: String): Boolean =
        isCurrentLocked(token) && thread == threadId && turn == turnId

    private fun isCurrentApproval(request: ApprovalRequest): Boolean = synchronized(lifecycleLock) { isCurrentApprovalLocked(request) }

    private fun isCurrentApprovalLocked(request: ApprovalRequest): Boolean =
        isCurrentLocked(request.token) && state.value.approval == request.approval && approvalMatchesLocked(request.approval)

    private fun ensureCurrent(token: Long) {
        synchronized(lifecycleLock) { ensureCurrentLocked(token) }
    }

    private fun ensureCurrentLocked(token: Long) {
        if (!isCurrentLocked(token)) throw CancellationException("Run stopped")
    }

    private suspend fun handleEvent(event: EngineEvent) {
        if (bufferStartupEvent(event)) return
        when (event) {
            is EngineEvent.TurnStarted -> synchronized(lifecycleLock) {
                if (voiceMode && event.threadId == thread && event.turnId.isNotBlank()) {
                    turn = event.turnId
                    mutableState.value = state.value.copy(
                        phase = RunPhase.THINKING,
                        status = "Working from voice",
                        controlling = false,
                    )
                }
            }
            is EngineEvent.TextDelta -> if (matches(event.threadId, event.turnId) && !isVoiceMode()) {
                synchronized(lifecycleLock) {
                    assistantText.append(event.text)
                    textRevision++
                }
                scheduleAssistantFlush()
            }
            is EngineEvent.ToolCall -> {
                if (!matches(event.threadId, event.turnId)) {
                    runCatching { engine.answerTool(event.requestId, ToolResult("Run stopped. No device action was performed.", success = false)) }
                    return
                }
                val token = synchronized(lifecycleLock) { epoch.get() }
                val sessionId = synchronized(lifecycleLock) { state.value.sessionId } ?: return
                launchTool {
                    toolLock.withLock {
                        if (!isCurrentTurn(token, event.threadId.orEmpty(), event.turnId.orEmpty())) return@withLock
                        val visible = tools.needsControl(event.name)
                        val capture = event.name == "read_ui" || event.name == "screenshot"
                        val status = event.name.replace('_', ' ')
                        val overlayState = if (visible) {
                            OverlayState(OverlayPhase.CONTROLLING, status)
                        } else {
                            OverlayState(OverlayPhase.RUNNING, status)
                        }
                        var captureHidden = false
                        var result: ToolResult
                        try {
                            if (capture) {
                                overlay.setCaptureHidden(true)
                                captureHidden = true
                            }
                            if (visible) {
                                val takeover = synchronized(lifecycleLock) { controlTakeover }
                                if (takeover) overlay.updateState(overlayState)
                                else {
                                    overlay.showState(overlayState)
                                    synchronized(lifecycleLock) {
                                        if (isCurrentTurnLocked(token, event.threadId.orEmpty(), event.turnId.orEmpty())) controlTakeover = true
                                    }
                                }
                            } else {
                                overlay.updateState(overlayState)
                            }
                            ensureCurrentTurn(token, event.threadId.orEmpty(), event.turnId.orEmpty())
                            synchronized(lifecycleLock) {
                                mutableState.value = state.value.copy(phase = if (visible) RunPhase.CONTROLLING else RunPhase.TOOL, controlling = visible, status = status)
                            }
                            result = tools.invoke(event.name, event.arguments)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            result = ToolResult(error.message ?: "Device action failed", success = false)
                        } finally {
                            if (captureHidden) runCatching { overlay.setCaptureHidden(false) }
                            synchronized(lifecycleLock) {
                                if (isCurrentTurnLocked(token, event.threadId.orEmpty(), event.turnId.orEmpty())) {
                                    mutableState.value = state.value.copy(phase = RunPhase.THINKING, controlling = false, status = "Thinking")
                                    overlay.updateState(OverlayState(OverlayPhase.THINKING))
                                }
                            }
                        }
                        if (isCurrentTurn(token, event.threadId.orEmpty(), event.turnId.orEmpty())) {
                            sessions.append(message(sessionId, "tool", "${event.name}: ${result.text.take(4_000)}"))
                            engine.answerTool(event.requestId, result)
                        }
                    }
                }
            }
            is EngineEvent.Activity -> synchronized(lifecycleLock) {
                if (isCurrentLocked(epoch.get()) && !state.value.controlling) {
                    mutableState.value = state.value.copy(status = event.text)
                    overlay.updateState(OverlayState(OverlayPhase.RUNNING, event.text))
                }
            }
            is EngineEvent.Approval -> {
                if (approvalMatches(event)) {
                    synchronized(lifecycleLock) {
                        mutableState.value = state.value.copy(approval = event, status = "Waiting for your approval")
                        overlay.updateState(OverlayState(OverlayPhase.RUNNING, "Waiting for approval"))
                    }
                } else {
                    runCatching { engine.answerApproval(event.requestId, false) }
                }
            }
            is EngineEvent.TurnFinished -> if (matches(event.threadId, event.turnId)) {
                if (isVoiceMode()) {
                    if (event.status == "failed") {
                        state.value.sessionId?.let { sessions.append(message(it, "system", event.error ?: "Voice task failed")) }
                    }
                    synchronized(lifecycleLock) {
                        if (voiceMode && thread == event.threadId && turn == event.turnId) {
                            turn = null
                            mutableState.value = state.value.copy(
                                phase = RunPhase.THINKING,
                                status = "Listening",
                                controlling = false,
                                approval = null,
                            )
                        }
                    }
                } else when (event.status) {
                    "failed" -> {
                        val error = event.error ?: "Codex could not finish"
                        synchronized(lifecycleLock) {
                            assistantOutcome = "error"
                            mutableState.value = state.value.copy(phase = RunPhase.ERROR, status = error)
                        }
                        state.value.sessionId?.let { sessions.append(message(it, "system", error)) }
                    }
                    "interrupted" -> synchronized(lifecycleLock) {
                        assistantOutcome = "interrupted"
                        mutableState.value = state.value.copy(status = "Interrupted")
                    }
                }
                if (!isVoiceMode()) completion?.complete(Unit)
            }
            is EngineEvent.Failure -> if (failureMatches(event)) {
                state.value.sessionId?.let { sessions.append(message(it, "system", event.message)) }
                if (isVoiceMode()) {
                    synchronized(lifecycleLock) {
                        if (voiceMode) {
                            turn = null
                            mutableState.value = state.value.copy(
                                phase = RunPhase.THINKING,
                                status = "Voice task failed",
                                controlling = false,
                                approval = null,
                            )
                        }
                    }
                } else {
                    synchronized(lifecycleLock) {
                        assistantOutcome = "error"
                        mutableState.value = state.value.copy(phase = RunPhase.ERROR, status = event.message)
                    }
                    completion?.complete(Unit)
                }
            }
            is EngineEvent.AccountChanged, EngineEvent.SkillsChanged -> Unit
        }
    }

    private fun ensureCurrentTurn(token: Long, threadId: String, turnId: String) {
        synchronized(lifecycleLock) {
            if (!isCurrentTurnLocked(token, threadId, turnId)) throw CancellationException("Run stopped")
        }
    }

    private fun isVoiceMode(): Boolean = synchronized(lifecycleLock) { voiceMode }

    private fun scheduleAssistantFlush() {
        synchronized(lifecycleLock) {
            val id = assistantId ?: return
            if (textFlushJob != null) return
            val token = epoch.get()
            val job = scope.launch(start = CoroutineStart.LAZY) {
                while (isActive) {
                    delay(75)
                    val snapshot = synchronized(lifecycleLock) {
                        if (epoch.get() != token || assistantId != id) null
                        else AssistantTextSnapshot(textRevision, assistantText.toString())
                    } ?: return@launch
                    runCatching {
                        assistantFlushLock.withLock { sessions.updateMessage(id, snapshot.text, "streaming") }
                    }
                    val settled = synchronized(lifecycleLock) {
                        epoch.get() == token && assistantId == id && textRevision == snapshot.revision
                    }
                    if (settled) return@launch
                }
            }
            textFlushJob = job
            job.invokeOnCompletion {
                synchronized(lifecycleLock) { if (textFlushJob === job) textFlushJob = null }
            }
            job.start()
        }
    }

    private suspend fun finalizeRun(token: Long, sessionId: String) {
        val final = synchronized(lifecycleLock) {
            if (epoch.get() != token) null
            else {
                val flush = textFlushJob.also { it?.cancel() }
                textFlushJob = null
                awaitingTurn = false
                voiceMode = false
                startupEvents.clear()
                controlTakeover = false
                AssistantFinal(assistantId, assistantText.toString(), assistantOutcome, flush)
            }
        } ?: return
        final.flush?.let { job -> runCatching { withTimeout(1_000) { job.join() } } }
        runCatching { tools.revoke() }
        runCatching { overlay.setCaptureHidden(false) }
        final.id?.let { id ->
            runCatching {
                assistantFlushLock.withLock { sessions.updateMessage(id, final.text, final.outcome) }
            }
        }
        val terminalOverlay = synchronized(lifecycleLock) {
            when {
                state.value.phase == RunPhase.ERROR -> OverlayState(OverlayPhase.ERROR, state.value.status)
                final.outcome == "interrupted" -> OverlayState(OverlayPhase.DONE, "Interrupted")
                else -> OverlayState(OverlayPhase.DONE)
            }
        }
        synchronized(lifecycleLock) {
            if (epoch.get() == token) {
                if (state.value.phase != RunPhase.ERROR) {
                    mutableState.value = RunState(
                        sessionId = sessionId,
                        status = if (final.outcome == "interrupted") "Interrupted" else "Ready",
                    )
                }
                thread = null
                turn = null
                assistantId = null
                assistantText.clear()
                assistantOutcome = "complete"
                awaitingTurn = false
                startupEvents.clear()
                completion = null
            }
        }
        runCatching { overlay.finish(terminalOverlay) }
    }

    private fun launchControl(block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(start = CoroutineStart.LAZY, block = block)
        synchronized(lifecycleLock) { controlJobs.add(job) }
        job.invokeOnCompletion { synchronized(lifecycleLock) { controlJobs.remove(job) } }
        job.start()
        return job
    }

    private fun launchTool(block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(start = CoroutineStart.LAZY, block = block)
        synchronized(lifecycleLock) { toolJobs.add(job) }
        job.invokeOnCompletion { synchronized(lifecycleLock) { toolJobs.remove(job) } }
        job.start()
        return job
    }

    private fun threadIdOf(event: EngineEvent): String? = when (event) {
        is EngineEvent.TurnStarted -> event.threadId
        is EngineEvent.TextDelta -> event.threadId
        is EngineEvent.ToolCall -> event.threadId
        is EngineEvent.TurnFinished -> event.threadId
        is EngineEvent.Approval -> event.threadId
        is EngineEvent.Failure -> event.threadId
        is EngineEvent.Activity, is EngineEvent.AccountChanged, EngineEvent.SkillsChanged -> null
    }

    private fun turnIdOf(event: EngineEvent): String? = when (event) {
        is EngineEvent.TurnStarted -> event.turnId
        is EngineEvent.TextDelta -> event.turnId
        is EngineEvent.ToolCall -> event.turnId
        is EngineEvent.TurnFinished -> event.turnId
        is EngineEvent.Approval -> event.turnId
        is EngineEvent.Failure -> event.turnId
        is EngineEvent.Activity, is EngineEvent.AccountChanged, EngineEvent.SkillsChanged -> null
    }

    private fun message(session: String, role: String, text: String, attachments: List<String> = emptyList()) =
        ChatMessage(UUID.randomUUID().toString(), session, role, text, System.currentTimeMillis(), attachmentPaths = attachments)

    private data class SteerRequest(val token: Long, val sessionId: String?, val threadId: String, val turnId: String, val prompt: String)
    private data class ApprovalRequest(val token: Long, val approval: EngineEvent.Approval)
    private data class AssistantTextSnapshot(val revision: Long, val text: String)
    private data class AssistantFinal(val id: String?, val text: String, val outcome: String, val flush: Job?)
    private data class StopContext(
        val stoppingEpoch: Long,
        val snapshot: RunState,
        val threadId: String?,
        val turnId: String?,
        val assistantId: String?,
        val text: String,
        val controls: List<Job>,
        val toolsInFlight: List<Job>,
        val flush: Job?,
        val runJob: Job?,
    )

    private data class VoiceStopContext(
        val stoppingEpoch: Long,
        val sessionId: String?,
        val threadId: String?,
        val turnId: String?,
        val controls: List<Job>,
        val toolsInFlight: List<Job>,
    )
}
