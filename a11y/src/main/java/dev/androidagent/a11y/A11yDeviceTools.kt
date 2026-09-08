package dev.androidagent.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import dev.androidagent.core.DeviceToolGateway
import dev.androidagent.core.ObservationState
import dev.androidagent.core.ToolDefinition
import dev.androidagent.core.ToolNotServiceable
import dev.androidagent.core.ToolResult
import dev.androidagent.core.UiObservationSerializer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Device gateway backed by the accessibility service, so observation and
 * action work with no ADB connection at all.
 *
 * It implements the existing tool names rather than introducing new ones.
 * Codex binds the tool list at thread start and never re-sends it, so every
 * chat opened before this shipped still asks for `read_ui` and `tap` — naming
 * the accessibility versions differently would leave those chats dead the
 * moment Wireless Debugging is off.
 *
 * Anything this backend genuinely cannot do raises [ToolNotServiceable] before
 * touching the device, which lets the composite fall through to ADB.
 */
class A11yDeviceTools(
    private val context: Context,
    private val observations: ObservationState,
    /** Moves the floating card away from a coordinate before a gesture lands on it. */
    private val avoidTouch: (Int, Int) -> Unit = { _, _ -> },
) : DeviceToolGateway {

    private val lock = Any()

    @Volatile private var revoked = true
    @Volatile private var workspace: File? = null

    /**
     * Node handles from the most recent observation only. An action addressed
     * to an older observation is refused rather than guessed at, and the map
     * is dropped on revoke so a stopped run leaves nothing behind.
     */
    @Volatile private var handles: Map<String, A11yNodeView> = emptyMap()
    @Volatile private var handlesObservationId: String? = null

    override val definitions: List<ToolDefinition> = TOOL_DEFINITIONS

    override fun beginRun(runId: String, workspace: File) {
        require(runId.isNotBlank()) { "runId cannot be blank" }
        synchronized(lock) {
            this.workspace = workspace.absoluteFile
            workspace.absoluteFile.mkdirs()
            clearHandles()
            revoked = false
        }
        // Re-pushed on every run start, so an instance that reconnected after
        // a crash or a self-update is not left permanently idle-gated.
        A11yServiceHandle.service.value?.runActive = true
    }

    override fun revoke() {
        synchronized(lock) {
            revoked = true
            clearHandles()
        }
        A11yServiceHandle.service.value?.runActive = false
    }

    override fun needsControl(name: String): Boolean =
        when (name) {
            "read_ui", "screenshot" -> false
            else -> true
        }

    override fun hidesOverlayDuringCapture(name: String): Boolean =
        // Only the screenshot composites the display. read_ui filters our own
        // windows out of the tree instead, so the card can stay put.
        name == "screenshot"

    override fun statusLine(): String {
        val connected = A11yServiceHandle.connected
        val declared = A11yAvailability.isDeclaredEnabled(context)
        return "Accessibility: " + when {
            connected -> "connected"
            declared -> "enabled but not connected (restricted setting?)"
            else -> "off"
        }
    }

    override suspend fun cancel() {
        // Nothing to tear down: every wait here is bounded by withTimeoutOrNull
        // and unwinds with the cancelled coroutine.
    }

    override suspend fun invoke(name: String, arguments: JsonObject): ToolResult {
        if (revoked || workspace == null) {
            throw IllegalStateException("Run stopped. No device action was performed.")
        }
        checkActive()
        val result = when (name) {
            "read_ui" -> readUi(arguments)
            "tap" -> tap(arguments)
            "swipe" -> swipe(arguments)
            "type_text" -> typeText(arguments)
            "key" -> pressKey(arguments)
            "open_app" -> openApp(arguments)
            else -> throw ToolNotServiceable(
                "a11y_unsupported",
                "The accessibility backend does not implement \"$name\".",
            )
        }
        checkActive()
        return result
    }

    // ---- observation ----

    private suspend fun readUi(arguments: JsonObject): ToolResult {
        if (arguments["raw"]?.jsonPrimitive?.booleanOrNull == true) {
            // There is no XML behind this backend. Nothing was read, so ADB
            // may still serve the debug path.
            throw ToolNotServiceable(
                "ui_raw_unavailable",
                "raw=true returns the uiautomator XML dump, which only the ADB backend produces.",
            )
        }
        val service = requireService()
        val force = arguments["force"]?.jsonPrimitive?.booleanOrNull ?: false
        val revision = observations.nextRevision()
        val observationId = "ui-$revision"
        val startedAt = System.nanoTime()

        // Reading a screen that is still animating produces nodes that have
        // already moved. A timeout is not fatal, it just means "not settled".
        val stable = awaitQuiescence(service)
        checkActive()

        val result = traverse(service.visibleWindows(), context.packageName)
        val rendered = UiObservationSerializer.render(
            observation = result.observation,
            source = SOURCE,
            backend = BACKEND,
            observationId = observationId,
            revision = revision,
            elapsedMs = elapsedMs(startedAt),
            previous = observations.last(),
            force = force,
            stable = stable,
        )
        synchronized(lock) {
            if (!revoked) {
                handles = result.handles
                handlesObservationId = observationId
            }
        }
        rendered.fingerprint?.let { observations.record(it) }
        return ToolResult(rendered.text)
    }

    /** True when the screen stopped changing before the budget ran out. */
    private suspend fun awaitQuiescence(service: AgentAccessibilityService): Boolean =
        withTimeoutOrNull(QUIESCENCE_TIMEOUT_MS) {
            while (service.idleMs < QUIESCENCE_IDLE_MS) {
                delay(QUIESCENCE_POLL_MS)
            }
            true
        } ?: false

    // ---- actions ----

    private suspend fun tap(arguments: JsonObject): ToolResult {
        val x = arguments.requireCoordinate("x")
        val y = arguments.requireCoordinate("y")
        requireNotOurOwnUi(x, y)
        val service = requireService()
        val landed = service.dispatchTap(x, y)
        return ToolResult("Tapped $x,$y", success = landed)
    }

    private suspend fun swipe(arguments: JsonObject): ToolResult {
        val x1 = arguments.requireCoordinate("x1")
        val y1 = arguments.requireCoordinate("y1")
        val x2 = arguments.requireCoordinate("x2")
        val y2 = arguments.requireCoordinate("y2")
        val duration = arguments["durationMs"]?.jsonPrimitive?.intOrNull ?: 300
        require(duration in 0..5_000) { "durationMs must be between 0 and 5000" }
        requireNotOurOwnUi(x1, y1)
        val service = requireService()
        val landed = service.dispatchSwipe(x1, y1, x2, y2, duration.toLong())
        return ToolResult("Swiped ($x1,$y1)->($x2,$y2) ${duration}ms", success = landed)
    }

    private suspend fun typeText(arguments: JsonObject): ToolResult {
        val text = arguments["text"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("text is required")
        require(text.length <= MAX_TEXT_CHARS) { "text must be at most $MAX_TEXT_CHARS characters" }
        val submit = arguments["submit"]?.jsonPrimitive?.booleanOrNull ?: false
        val service = requireService()
        val target = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: throw ToolNotServiceable(
                "no_text_focus",
                "No editable field has input focus. Tap the centre of the text field first, then retry.",
            )
        val arguments1 = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val committed = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments1)
        if (!committed) {
            return ToolResult("Text was rejected by the field; nothing was typed.", success = false)
        }
        // ACTION_SET_TEXT replaces the whole field, and some Compose and chat
        // composers do not propagate it. Report what the field actually holds
        // rather than assuming the write took.
        val verified = runCatching { service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.text?.toString() }
            .getOrNull() == text
        var submitted = false
        if (submit) {
            submitted = target.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id,
            )
        }
        return ToolResult(
            buildJsonObject {
                put("typed", text.length)
                put("verified", verified)
                if (submit) put("submitted", submitted)
            }.toString(),
        )
    }

    private suspend fun pressKey(arguments: JsonObject): ToolResult {
        val raw = arguments["keycode"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("keycode is required")
        val normalized = raw.trim().uppercase().removePrefix("KEYCODE_")
        val action = GLOBAL_ACTIONS[normalized]
            ?: throw ToolNotServiceable(
                "key_unsupported",
                "Accessibility can only send ${GLOBAL_ACTIONS.keys.sorted().joinToString(", ")}. " +
                    "\"$raw\" needs the ADB backend.",
            )
        val service = requireService()
        val sent = service.performGlobalAction(action)
        return ToolResult("Sent $normalized", success = sent)
    }

    private suspend fun openApp(arguments: JsonObject): ToolResult {
        val pkg = arguments["package"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw IllegalArgumentException("package is required")
        require(PACKAGE_RE.matches(pkg)) { "package is not a valid Android package name" }
        if (arguments["activity"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true) {
            // An explicit component is often not exported, and startActivity
            // cannot reach those. `am start` can, so let ADB have it.
            throw ToolNotServiceable(
                "explicit_activity_unsupported",
                "Launching a named activity needs the ADB backend.",
            )
        }
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: throw ToolNotServiceable(
                "no_launch_intent",
                "No launcher activity resolved for $pkg. It may not be installed or not visible to this app.",
            )
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolResult("Opened $pkg")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ToolResult("Could not open $pkg: ${error.message}", success = false)
        }
    }

    // ---- helpers ----

    private suspend fun requireService(): AgentAccessibilityService =
        A11yServiceHandle.service.value
            ?: A11yServiceHandle.await(CONNECT_GRACE_MS)
            ?: throw ToolNotServiceable(
                "a11y_unavailable",
                "The Android Agent accessibility service is not running. Ask the user to " +
                    "enable it in Settings > Accessibility > Android Agent.",
            )

    /**
     * A coordinate gesture hits whatever is topmost, so filtering our windows
     * out of the tree does nothing for it. Move the card, then refuse if the
     * point is still ours.
     */
    private fun requireNotOurOwnUi(x: Int, y: Int) {
        avoidTouch(x, y)
        val service = A11yServiceHandle.service.value ?: return
        if (service.ownWindowContains(context.packageName, x, y)) {
            throw IllegalStateException(
                "($x,$y) is inside Android Agent's own window. Nothing was tapped; " +
                    "read_ui again and pick a target in the app you are driving.",
            )
        }
    }

    private fun clearHandles() {
        handles = emptyMap()
        handlesObservationId = null
    }

    private suspend fun checkActive() {
        currentCoroutineContext().ensureActive()
        if (revoked) throw IllegalStateException("Run stopped. No device action was performed.")
    }

    private fun elapsedMs(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)

    private fun JsonObject.requireCoordinate(key: String): Int {
        val value = this[key]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("$key is required and must be an integer")
        require(value in 0..MAX_COORDINATE) { "$key must be between 0 and $MAX_COORDINATE" }
        return value
    }

    companion object {
        private const val SOURCE = "accessibility"

        /** Scopes unchanged-suppression to this backend. */
        private const val BACKEND = "a11y"

        private const val CONNECT_GRACE_MS = 750L
        private const val QUIESCENCE_IDLE_MS = 350L
        private const val QUIESCENCE_TIMEOUT_MS = 3_000L
        private const val QUIESCENCE_POLL_MS = 50L
        private const val MAX_COORDINATE = 20_000
        private const val MAX_TEXT_CHARS = 4_000

        private val PACKAGE_RE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

        /** Everything accessibility can send. Anything else belongs to ADB. */
        private val GLOBAL_ACTIONS = mapOf(
            "BACK" to AccessibilityService.GLOBAL_ACTION_BACK,
            "HOME" to AccessibilityService.GLOBAL_ACTION_HOME,
            "APP_SWITCH" to AccessibilityService.GLOBAL_ACTION_RECENTS,
            "RECENTS" to AccessibilityService.GLOBAL_ACTION_RECENTS,
            "NOTIFICATIONS" to AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
            "QUICK_SETTINGS" to AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
            "POWER" to AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
            "LOCK" to AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
        )

        private val TOOL_DEFINITIONS: List<ToolDefinition> = listOf(
            tool(
                "read_ui",
                "Read a bounded compact semantic UI observation. Returns labeled/actionable nodes " +
                    "by default; use raw=true only for debug XML. When the screen is identical to the " +
                    "previous observation the reply is \"unchanged\":true with \"unchangedSinceRevision\" " +
                    "instead of the node list — reuse the nodes from that revision, or pass force=true to " +
                    "resend them. Timeout or idle failures are typed and do not trigger a second dump.",
                mapOf("timeoutMs" to "integer", "raw" to "boolean", "force" to "boolean"),
                emptyList(),
            ),
            tool("tap", "Tap the screen at pixel coordinates.", mapOf("x" to "integer", "y" to "integer"), listOf("x", "y")),
            tool(
                "swipe",
                "Swipe from one point to another.",
                mapOf("x1" to "integer", "y1" to "integer", "x2" to "integer", "y2" to "integer", "durationMs" to "integer"),
                listOf("x1", "y1", "x2", "y2"),
            ),
            tool(
                "type_text",
                "Type text into the focused field. Tap the field first so it holds input focus.",
                mapOf("text" to "string", "submit" to "boolean"),
                listOf("text"),
            ),
            tool("key", "Send a keyevent by name or numeric code.", mapOf("keycode" to "string"), listOf("keycode")),
            tool("open_app", "Launch an app by package, optionally with activity.", mapOf("package" to "string", "activity" to "string"), listOf("package")),
        )

        private fun tool(
            name: String,
            description: String,
            properties: Map<String, String>,
            required: List<String>,
        ): ToolDefinition {
            val props = buildJsonObject {
                for ((key, type) in properties) put(key, buildJsonObject { put("type", type) })
            }
            val schema = buildJsonObject {
                put("type", "object")
                put("properties", props)
                put("description", description)
                put("required", JsonArray(required.map { JsonPrimitive(it) }))
            }
            return ToolDefinition(name, description, schema)
        }
    }
}

/** Front-to-back windows, with the active one flagged. */
internal fun AgentAccessibilityService.visibleWindows(): List<A11yWindow> =
    runCatching {
        windows.map { window ->
            A11yWindow(
                root = runCatching { window.root }.getOrNull()?.let(::RealNodeView),
                active = window.isActive,
            )
        }
    }.getOrElse {
        // Some devices refuse the window list; the focused window still works.
        listOfNotNull(rootInActiveWindow?.let { A11yWindow(RealNodeView(it), active = true) })
    }

/** True when (x, y) falls inside a window this app owns. */
internal fun AgentAccessibilityService.ownWindowContains(ownPackage: String, x: Int, y: Int): Boolean =
    runCatching {
        windows.any { window ->
            val root = runCatching { window.root }.getOrNull() ?: return@any false
            if (root.packageName?.toString() != ownPackage) return@any false
            val rect = android.graphics.Rect()
            window.getBoundsInScreen(rect)
            rect.contains(x, y)
        }
    }.getOrDefault(false)

internal suspend fun AgentAccessibilityService.dispatchTap(x: Int, y: Int): Boolean {
    val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
    val stroke = GestureDescription.StrokeDescription(path, 0, 50)
    return dispatchAndAwait(GestureDescription.Builder().addStroke(stroke).build())
}

internal suspend fun AgentAccessibilityService.dispatchSwipe(
    x1: Int,
    y1: Int,
    x2: Int,
    y2: Int,
    durationMs: Long,
): Boolean {
    val path = Path().apply {
        moveTo(x1.toFloat(), y1.toFloat())
        lineTo(x2.toFloat(), y2.toFloat())
    }
    val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
    return dispatchAndAwait(GestureDescription.Builder().addStroke(stroke).build())
}

private suspend fun AccessibilityService.dispatchAndAwait(gesture: GestureDescription): Boolean {
    val outcome = kotlinx.coroutines.CompletableDeferred<Boolean>()
    val callback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(description: GestureDescription?) { outcome.complete(true) }
        override fun onCancelled(description: GestureDescription?) { outcome.complete(false) }
    }
    if (!dispatchGesture(gesture, callback, null)) return false
    return withTimeoutOrNull(GESTURE_TIMEOUT_MS) { outcome.await() } ?: false
}

private const val GESTURE_TIMEOUT_MS = 5_000L
