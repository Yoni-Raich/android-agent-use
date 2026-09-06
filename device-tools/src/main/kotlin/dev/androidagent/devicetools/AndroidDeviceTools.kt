package dev.androidagent.devicetools

import dev.androidagent.adb.AdbFileTransport
import dev.androidagent.core.AdbTransport
import dev.androidagent.core.CommandResult
import dev.androidagent.core.DeviceToolGateway
import dev.androidagent.core.ToolDefinition
import dev.androidagent.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.util.Base64

/**
 * Sole agent-facing device gateway. Every device operation goes through [adb].
 *
 * Run scoping: [beginRun] arms the gateway for one run id plus workspace.
 * [revoke] is synchronous and flips a volatile flag; any [invoke] dispatched
 * afterwards fails without touching the device. In-flight calls are stopped
 * via [cancel], which the coordinator calls after [revoke].
 *
 * Mutating tools (taps, text, keys, app launch, shell, push, install) report
 * [needsControl] true so the overlay glow is shown. Read-only tools
 * (device_status, read_ui, screenshot, pull_file) return false.
 */
class AndroidDeviceTools(
    private val adb: AdbTransport,
    /** Full IME component, for example `com.example/.AgentInputMethodService`. */
    private val inputMethodComponent: String? = null,
) : DeviceToolGateway {

    private val lock = Any()
    @Volatile private var revoked = true
    @Volatile private var workspace: File? = null
    @Volatile private var runId: String? = null

    override val definitions: List<ToolDefinition> = TOOL_DEFINITIONS

    override fun beginRun(runId: String, workspace: File) {
        require(runId.isNotBlank()) { "runId cannot be blank" }
        synchronized(lock) {
            this.runId = runId
            this.workspace = workspace.absoluteFile
            workspace.absoluteFile.mkdirs()
            revoked = false
        }
    }

    override fun revoke() {
        synchronized(lock) { revoked = true }
    }

    override fun needsControl(name: String): Boolean =
        when (name) {
            "device_status", "read_ui", "screenshot", "pull_file" -> false
            "tap", "swipe", "type_text", "key", "open_app", "shell",
            "push_file", "install_apk" -> true
            else -> true
        }

    override suspend fun invoke(name: String, arguments: JsonObject): ToolResult {
        val ws = workspace
        if (revoked || ws == null) {
            throw IllegalStateException("Run stopped. No device action was performed.")
        }
        checkActive()
        val result = when (name) {
            "device_status" -> deviceStatus()
            "read_ui" -> readUi(arguments)
            "screenshot" -> screenshot(arguments)
            "tap" -> tap(arguments)
            "swipe" -> swipe(arguments)
            "type_text" -> typeText(arguments)
            "key" -> pressKey(arguments)
            "open_app" -> openApp(arguments)
            "shell" -> shell(arguments)
            "pull_file" -> pullFile(arguments, ws)
            "push_file" -> pushFile(arguments, ws)
            "install_apk" -> installApk(arguments, ws)
            else -> ToolResult("Unknown tool: $name", success = false)
        }
        checkActive()
        return result
    }

    override suspend fun cancel() {
        runCatching { adb.cancelActive() }
    }

    // ---- tools ----

    private fun deviceStatus(): ToolResult {
        val s = adb.status.value
        val port = s.port?.toString() ?: "-"
        return ToolResult("phase=${s.phase} port=$port ${s.message}".take(MAX_OUTPUT_CHARS))
    }

    private suspend fun readUi(arguments: JsonObject): ToolResult {
        val timeout = arguments.timeoutMsOrDefault()
        // /dev/tty prints the hierarchy to stdout without staging a file.
        val direct = runCatching { userExecute("uiautomator dump --compressed /dev/tty", timeout) }
            .getOrNull()?.output.orEmpty()
        if (direct.contains("<hierarchy")) return ToolResult(bound(direct))
        val fallback = userExecute(
            "uiautomator dump --compressed ${quotedRemote(UI_DUMP_PATH)} && cat ${quotedRemote(UI_DUMP_PATH)}",
            timeout,
        )
        return ToolResult(
            text = bound(fallback.output),
            success = fallback.exitCode == 0,
        )
    }

    private suspend fun screenshot(arguments: JsonObject): ToolResult {
        val timeout = arguments.timeoutMsOrDefault().coerceIn(1L, MAX_TIMEOUT_MS)
        val bytes = runCatching { userExecuteBytes("screencap -p", timeout) }.getOrNull()
        val png = if (bytes != null && isPng(bytes)) {
            bytes
        } else {
            // Text-only transports: recover binary through a base64 round-trip.
            val textResult = userExecute("screencap -p | base64 | tr -d '\\r\\n'", timeout)
            check(textResult.exitCode == 0) { "Screenshot command failed" }
            val out = textResult.output
            val clean = out.filterNot { it.isWhitespace() }
            if (clean.isEmpty()) throw java.io.IOException("Screenshot returned no data")
            try {
                Base64.getMimeDecoder().decode(clean)
            } catch (e: IllegalArgumentException) {
                throw java.io.IOException("Screenshot decode failed", e)
            }.also {
                check(isPng(it)) { "Screenshot did not return PNG data" }
            }
        }
        check(png.size <= MAX_SCREENSHOT_BYTES) { "Screenshot exceeds size limit" }
        val encoded = Base64.getEncoder().encodeToString(png)
        return ToolResult("Screenshot captured (${png.size} bytes, PNG)", imageBase64 = encoded)
    }

    private suspend fun tap(arguments: JsonObject): ToolResult {
        val x = arguments.requireCoordinate("x")
        val y = arguments.requireCoordinate("y")
        val timeout = arguments.timeoutMsOrDefault()
        val out = userExecute("input tap $x $y", timeout)
        return ToolResult(
            text = bound("Tapped $x,$y${out.output.ifBlank { "" }.prefix(" :: ")}"),
            success = out.exitCode == 0,
        )
    }

    private suspend fun swipe(arguments: JsonObject): ToolResult {
        val x1 = arguments.requireCoordinate("x1")
        val y1 = arguments.requireCoordinate("y1")
        val x2 = arguments.requireCoordinate("x2")
        val y2 = arguments.requireCoordinate("y2")
        val duration = arguments.get("durationMs")?.jsonPrimitive?.intOrNull ?: 300
        require(duration in 0..5_000) { "durationMs must be between 0 and 5000" }
        val timeout = arguments.timeoutMsOrDefault()
        val out = userExecute("input swipe $x1 $y1 $x2 $y2 $duration", timeout)
        return ToolResult(
            text = bound("Swiped ($x1,$y1)->($x2,$y2) ${duration}ms${out.output.ifBlank { "" }.prefix(" :: ")}"),
            success = out.exitCode == 0,
        )
    }

    private suspend fun typeText(arguments: JsonObject): ToolResult {
        val text = arguments.get("text")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("text is required")
        val submit = arguments.get("submit")?.jsonPrimitive?.booleanOrNull ?: false
        val timeout = arguments.timeoutMsOrDefault()
        val component = inputMethodComponent
        if (component != null) {
            return typeTextThroughIme(text, submit, timeout, component)
        }

        requireAdbInputText(text)
        // adb input text interprets %s as a space. Keep the whole token quoted
        // and reject literal percent signs rather than sending altered text.
        require(!text.contains('%')) {
            "Literal percent input requires the configured Unicode IME bridge; text was not sent."
        }
        val encoded = text.replace(" ", "%s")
        checkActive()
        val result = userExecute("input text ${shellQuote(encoded)}", timeout)
        check(result.exitCode == 0) { "Text input command failed; text was not confirmed" }
        if (submit) {
            checkActive()
            val submitResult = userExecute("input keyevent 66", timeout)
            check(submitResult.exitCode == 0) { "Enter key command failed after text input" }
        }
        return ToolResult(bound("Typed ${text.length} chars" + if (submit) " + Enter" else ""))
    }

    /**
     * Commits UTF-8 text through the app IME. Every command up to the commit is
     * a new user action and checks revocation. Restoring the previous IME is a
     * cleanup action and is allowed from NonCancellable finally code after a
     * stop request.
     */
    private suspend fun typeTextThroughIme(
        text: String,
        submit: Boolean,
        timeout: Long,
        component: String,
    ): ToolResult {
        requireValidImeComponent(component)
        validateImeText(text)
        val previous = queryDefaultIme(timeout)
        var switched = false
        try {
            checkActive()
            val enabled = userExecute("ime enable ${shellQuote(component)}", timeout)
            check(enabled.exitCode == 0) {
                "Unicode input IME could not be enabled; text was not sent"
            }

            checkActive()
            val selected = userExecute("ime set ${shellQuote(component)}", timeout)
            check(selected.exitCode == 0) {
                "Unicode input IME could not be selected; text was not sent"
            }
            switched = true

            checkActive()
            awaitImeReady(component, timeout)
            val payload = encodeImePayload(text)
            var committed = false
            repeat(IME_COMMIT_ATTEMPTS) { attempt ->
                checkActive()
                val broadcast = userExecute(buildImeBroadcastCommand(component, payload), timeout)
                if (broadcast.exitCode == 0 && broadcastCommitted(broadcast.output)) {
                    committed = true
                    return@repeat
                }
                if (attempt + 1 < IME_COMMIT_ATTEMPTS) delay(IME_COMMIT_RETRY_MS)
            }
            check(committed) {
                "Unicode text was not committed; the target text field or Unicode IME is unavailable. " +
                    "Focus a text field, keep the keyboard visible, and select Android Agent as the active keyboard in system settings, then retry. " +
                    "Text was not sent."
            }

            if (submit) {
                checkActive()
                val submitResult = userExecute("input keyevent 66", timeout)
                check(submitResult.exitCode == 0) { "Enter key command failed after text input" }
            }
            return ToolResult(bound("Typed ${text.length} chars" + if (submit) " + Enter" else ""))
        } finally {
            val restore = previous?.takeIf { switched && it != component }
            if (restore != null) {
                withContext(NonCancellable) {
                    // This is cleanup of the temporary IME selection, not a new
                    // user-requested device action. Do not mask the input result.
                    runCatching {
                        adb.execute("ime set ${shellQuote(restore)}", timeout)
                    }
                }
            }
        }
    }

    /**
     * Selecting an IME is asynchronous on Android. The input service can be
     * alive while its currentInputConnection is still null, so wait for both
     * the selected component and an editor connection before broadcasting.
     */
    private suspend fun awaitImeReady(component: String, timeout: Long) {
        var selected = false
        var connection: Boolean? = null
        try {
            withTimeout(timeout.coerceAtMost(IME_READY_WAIT_MS).coerceAtLeast(IME_READY_POLL_MS)) {
                while (true) {
                    checkActive()
                    selected = imeSelectionMatches(queryDefaultIme(IME_STATUS_TIMEOUT_MS), component)
                    val dump = try {
                        userExecute("dumpsys input_method", IME_STATUS_TIMEOUT_MS)
                            .takeIf { it.exitCode == 0 }?.output
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                    connection = dump?.let { imeDumpConnectionReady(it, component) }
                    if (selected && connection == true) return@withTimeout
                    delay(IME_READY_POLL_MS)
                }
            }
        } catch (error: TimeoutCancellationException) {
            val reason = when {
                !selected -> "the Unicode IME did not become active"
                connection == false -> "the target text field is not focused"
                else -> "Android did not expose an active input connection"
            }
            throw IllegalStateException(
                "Unicode text was not committed because $reason. " +
                    "Focus a text field, keep the keyboard visible, and select Android Agent as the active keyboard in system settings, then retry. " +
                    "Text was not sent.",
                error,
            )
        }
    }

    private suspend fun queryDefaultIme(timeout: Long): String? {
        checkActive()
        val result = userExecute("settings get secure default_input_method", timeout)
        check(result.exitCode == 0) {
            "Could not read the current input method; text was not sent"
        }
        return result.output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it != "null" && it != "none" }
            ?.also { requireValidImeComponent(it) }
    }

    private fun broadcastCommitted(output: String): Boolean {
        return imeBroadcastCommitted(output)
    }

    private suspend fun pressKey(arguments: JsonObject): ToolResult {
        val raw = arguments.get("keycode")?.jsonPrimitive?.content
            ?: arguments.get("key")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("keycode is required")
        val code = resolveKeycode(raw)
        val timeout = arguments.timeoutMsOrDefault()
        val out = userExecute("input keyevent $code", timeout)
        return ToolResult(
            text = bound("Key $code sent${out.output.ifBlank { "" }.prefix(" :: ")}"),
            success = out.exitCode == 0,
        )
    }

    private suspend fun openApp(arguments: JsonObject): ToolResult {
        val pkg = arguments.get("package")?.jsonPrimitive?.content?.trim()
            ?: throw IllegalArgumentException("package is required")
        requireValidPackage(pkg)
        val activity = arguments.get("activity")?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
        val timeout = arguments.timeoutMsOrDefault()
        val result = if (activity == null) {
            userExecute("monkey -p ${shellQuote(pkg)} -c android.intent.category.LAUNCHER 1", timeout)
        } else {
            val component = if (activity.startsWith(".")) pkg + activity else activity
            requireValidComponent(component)
            userExecute("am start --user current -n ${shellQuote(component)}", timeout)
        }
        return ToolResult(
            text = bound("Opened $pkg${result.output.ifBlank { "" }.prefix(" :: ")}"),
            success = result.exitCode == 0,
        )
    }

    private suspend fun shell(arguments: JsonObject): ToolResult {
        val command = arguments.get("command")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("command is required")
        require(command.isNotBlank()) { "command cannot be empty" }
        require(!command.contains('\u0000')) { "command contains NUL" }
        require(command.length <= MAX_SHELL_CHARS) { "command exceeds length limit" }
        val timeout = arguments.timeoutMsOrDefault()
        val out = userExecute(command, timeout)
        val text = bound(out.output)
        return ToolResult(if (out.exitCode == 0) text.ifBlank { "OK" } else "exit ${out.exitCode}: $text", success = out.exitCode == 0)
    }

    private suspend fun pullFile(arguments: JsonObject, ws: File): ToolResult {
        val remote = arguments.get("remotePath")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("remotePath is required")
        requireValidRemotePath(remote)
        val localName = arguments.get("localName")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("localName is required")
        val target = ws.resolveSafe(localName)
        val timeout = arguments.timeoutMsOrDefault()
        val native = adb as? AdbFileTransport
        if (native != null) {
            withContext(Dispatchers.IO) { target.parentFile?.mkdirs() }
            val res = native.pullFile(target, remote, timeout)
            return ToolResult(
                text = bound("Pulled $remote -> ${ws.relativize(target)} (${target.length()} bytes) ${res.output}".trim()),
                success = res.exitCode == 0,
            )
        }
        // Fallback for text-only transports: base64 round-trip, bounded.
        val out = userExecute("base64 ${shellQuote(remote)} | tr -d '\\r\\n'", timeout).output
        val clean = out.filterNot { it.isWhitespace() }
        if (clean.isEmpty()) throw java.io.IOException("Remote file is empty or unreadable: $remote")
        val bytes = try {
            Base64.getMimeDecoder().decode(clean)
        } catch (e: IllegalArgumentException) {
            throw java.io.IOException("Pull decode failed", e)
        }
        check(bytes.size <= MAX_PULL_BYTES) { "Remote file exceeds size limit" }
        withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
        return ToolResult("Pulled $remote -> ${ws.relativize(target)} (${bytes.size} bytes)")
    }

    private suspend fun pushFile(arguments: JsonObject, ws: File): ToolResult {
        val localName = arguments.get("localName")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("localName is required")
        val source = ws.resolveSafe(localName)
        check(source.isFile) { "Local file does not exist: $localName" }
        check(source.length() <= MAX_PUSH_BYTES) { "Local file exceeds size limit" }
        val remote = arguments.get("remotePath")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("remotePath is required")
        requireValidRemotePath(remote)
        val timeout = arguments.timeoutMsOrDefault()
        val native = adb as? AdbFileTransport
            ?: throw java.io.IOException("Push requires a native file transport")
        val res = native.pushFile(source, remote, timeout)
        return ToolResult(
            text = bound("Pushed ${ws.relativize(source)} -> $remote ${res.output}".trim()),
            success = res.exitCode == 0,
        )
    }

    private suspend fun installApk(arguments: JsonObject, ws: File): ToolResult {
        val localName = arguments.get("localName")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("localName is required")
        require(localName.endsWith(".apk", ignoreCase = true)) { "Not an APK: $localName" }
        val source = ws.resolveSafe(localName)
        check(source.isFile) { "APK does not exist: $localName" }
        check(source.length() <= MAX_APK_BYTES) { "APK exceeds size limit" }
        val replace = arguments.get("replace")?.jsonPrimitive?.booleanOrNull ?: true
        val timeout = arguments.timeoutMsOrDefault().coerceAtLeast(60_000L)
        val native = adb as? AdbFileTransport
            ?: throw java.io.IOException("Install requires a native file transport")
        val res = native.installApk(source, replace, timeout)
        return ToolResult(
            text = bound("Installed ${source.name} ${res.output}".trim()),
            success = res.exitCode == 0,
        )
    }

    // ---- helpers ----

    /** Dispatch a new agent action only while the current run is armed. */
    private suspend fun userExecute(command: String, timeoutMs: Long): CommandResult {
        checkActive()
        return adb.execute(command, timeoutMs)
    }

    /** Binary equivalent of [userExecute], preserving transport bytes. */
    private suspend fun userExecuteBytes(command: String, timeoutMs: Long): ByteArray {
        checkActive()
        return adb.executeBytes(command, timeoutMs)
    }

    private fun checkActive() {
        if (revoked) throw IllegalStateException("Run stopped. No device action was performed.")
    }

    private fun bound(text: String): String =
        if (text.length <= MAX_OUTPUT_CHARS) text
        else text.take(MAX_OUTPUT_CHARS) + "\n[output truncated]"

    private fun String.prefix(p: String): String = if (isEmpty()) "" else "$p$this"

    private fun JsonObject.timeoutMsOrDefault(default: Long = DEFAULT_TIMEOUT_MS): Long {
        val raw = get("timeoutMs")?.jsonPrimitive?.longOrNull ?: return default
        return raw.coerceIn(1L, MAX_TIMEOUT_MS)
    }

    private fun JsonObject.requireCoordinate(name: String): Int {
        val v = get(name)?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("$name is required (integer 0..$MAX_COORDINATE)")
        require(v in 0..MAX_COORDINATE) { "$name must be between 0 and $MAX_COORDINATE" }
        return v
    }

    private fun File.resolveSafe(relative: String): File {
        require(relative.isNotBlank()) { "Path cannot be blank" }
        require(!relative.contains('\u0000')) { "Path contains NUL" }
        // Reject absolute paths (posix and windows) and parent traversal.
        require(!relative.startsWith("/")) { "Absolute paths are not allowed: $relative" }
        require(!relative.matches(Regex("^[A-Za-z]:.*"))) { "Absolute paths are not allowed: $relative" }
        require(!relative.contains("\\")) { "Backslashes are not allowed: $relative" }
        val base = canonicalFile
        val target = File(base, relative).canonicalFile
        val prefix = base.path + File.separator
        check(target.path == base.path || target.path.startsWith(prefix)) {
            "Path traversal denied: $relative"
        }
        check(target.path != base.path) { "Path must name a file inside the workspace" }
        return target
    }

    private fun File.relativize(child: File): String =
        runCatching { relativeTo(canonicalFile).path }.getOrDefault(child.name)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val MAX_TIMEOUT_MS = 120_000L
        const val MAX_OUTPUT_CHARS = 20_000
        const val MAX_SHELL_CHARS = 8_000
        const val MAX_COORDINATE = 10_000
        const val MAX_SCREENSHOT_BYTES = 16 * 1024 * 1024
        const val MAX_PULL_BYTES = 32 * 1024 * 1024
        const val MAX_PUSH_BYTES = 64 * 1024 * 1024
        const val MAX_APK_BYTES = 256 * 1024 * 1024
        const val UI_DUMP_PATH = "/sdcard/window_dump.xml"

        private const val IME_ACTION_SUFFIX = ".INPUT_TEXT"
        private const val IME_EXTRA_PAYLOAD = "payload_base64"
        private const val IME_RESULT_SUCCESS = 1
        private const val MAX_IME_TEXT_BYTES = 16 * 1024
        private const val IME_READY_WAIT_MS = 2_500L
        private const val IME_READY_POLL_MS = 100L
        private const val IME_STATUS_TIMEOUT_MS = 2_000L
        private const val IME_COMMIT_ATTEMPTS = 4
        private const val IME_COMMIT_RETRY_MS = 150L

        /** POSIX single-quote escaping. Public for unit tests. */
        fun shellQuote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

        fun quotedRemote(path: String): String = shellQuote(path)

        private val PACKAGE_RE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
        private val COMPONENT_RE = Regex("^[A-Za-z][A-Za-z0-9_.]*(/[A-Za-z0-9_.\$]+)+$")

        fun requireValidPackage(pkg: String) {
            require(pkg.length in 3..255) { "Invalid package name" }
            require(pkg.matches(PACKAGE_RE)) { "Invalid package name: $pkg" }
        }

        fun requireValidComponent(component: String) {
            require(component.length in 3..512) { "Invalid component name" }
            require(component.matches(COMPONENT_RE)) { "Invalid component name: $component" }
        }

        fun requireValidRemotePath(path: String) {
            require(path.startsWith("/")) { "remotePath must be absolute: $path" }
            require(!path.contains('\u0000')) { "remotePath contains NUL" }
            require(path.length <= 1024) { "remotePath too long" }
        }

        /**
         * `adb shell input text` only supports ASCII. Fail honestly instead of
         * corrupting text when the app was not given its Unicode IME component.
         */
        fun requireAdbInputText(text: String) {
            require(text.isNotEmpty()) { "text cannot be empty" }
            require(text.length <= 4096) { "text exceeds 4096 chars" }
            require(!text.contains('\u0000')) { "text contains NUL" }
            require(!text.contains('\n') && !text.contains('\r')) { "text cannot contain line breaks; use submit for Enter" }
            val bad = text.firstOrNull { it.code !in 32..126 }
            require(bad == null) {
                "Unicode input is not supported by `adb input text` (found U+%04X). ".format(bad!!.code) +
                "Root follow-up: add an IME bridge for Unicode input; text was not sent."
            }
        }

        /** Validate text before making any IME selection or broadcast call. */
        fun validateImeText(text: String) {
            require(text.isNotEmpty()) { "text cannot be empty" }
            require(!text.contains('\u0000')) { "text contains NUL" }
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_IME_TEXT_BYTES) {
                "text exceeds the Unicode input size limit"
            }
        }

        /** Base64 UTF-8 payload shared with AgentInputMethodService. */
        fun encodeImePayload(text: String): String {
            validateImeText(text)
            return Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        }

        /** The secure setting should exactly match the component we selected. */
        internal fun imeSelectionMatches(current: String?, requested: String): Boolean =
            current?.trim()?.let { normalizedComponent(it) } == normalizedComponent(requested)

        /**
         * Parse the stable fields printed by `dumpsys input_method` without
         * exposing the dump (which can include unrelated package details).
         * Null means that this Android build uses an unknown dump format.
         */
        internal fun imeDumpConnectionReady(output: String, requested: String): Boolean? {
            if (output.isBlank()) return null
            val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            val current = lines.asSequence()
                .mapNotNull { line ->
                    Regex("\\b(?:mCurId|mCurMethodId|mSelectedMethodId)\\s*=\\s*([^,\\s}]+)").find(line)
                        ?.groupValues?.getOrNull(1)
                }
                .firstOrNull()
            val selected = current?.let { imeSelectionMatches(it, requested) }
            if (selected == false) return false

            val connectionLine = lines.firstOrNull { line ->
                line.contains("mServedInputConnection", ignoreCase = true) ||
                    line.contains("mCurrentInputConnection", ignoreCase = true)
            }
            val editorLine = lines.firstOrNull { line ->
                line.contains("mCurAttribute", ignoreCase = true) ||
                    line.contains("mServedInputContext", ignoreCase = true)
            }
            val connection = when {
                connectionLine?.substringAfter('=')?.trim()?.equals("null", ignoreCase = true) == true -> false
                connectionLine != null -> true
                editorLine?.substringAfter('=')?.trim()?.equals("null", ignoreCase = true) == true -> false
                editorLine != null -> true
                else -> null
            }
            return when {
                connection == false -> false
                selected == true && connection == true -> true
                else -> null
            }
        }

        /** Only result=1 means the IME actually called commitText successfully. */
        internal fun imeBroadcastCommitted(output: String): Boolean =
            Regex("\\bresult=(-?\\d+)").find(output)
                ?.groupValues?.getOrNull(1)?.toIntOrNull() == IME_RESULT_SUCCESS

        private fun normalizedComponent(value: String): String {
            val clean = value.trim().trimEnd(',', ';')
            val slash = clean.indexOf('/')
            if (slash <= 0 || slash == clean.lastIndex) return clean
            val pkg = clean.substring(0, slash)
            val cls = clean.substring(slash + 1)
            return "$pkg/${if (cls.startsWith('.')) pkg + cls else cls}"
        }

        /**
         * Build the only broadcast accepted by the input service. The action is
         * package-scoped and the payload is one shell-safe Base64 token.
         */
        fun buildImeBroadcastCommand(component: String, payload: String): String {
            requireValidImeComponent(component)
            require(payload.isNotEmpty() && payload.length <= ((MAX_IME_TEXT_BYTES + 2) / 3) * 4 + 4) {
                "IME payload is invalid"
            }
            val packageName = component.substringBefore('/')
            val action = packageName + IME_ACTION_SUFFIX
            return "am broadcast --user current --receiver-foreground " +
                "-p ${shellQuote(packageName)} " +
                "--receiver-permission android.permission.DUMP " +
                "-a ${shellQuote(action)} " +
                "--es ${shellQuote(IME_EXTRA_PAYLOAD)} ${shellQuote(payload)}"
        }

        fun requireValidImeComponent(component: String) {
            require(component.length in 3..512) { "Invalid input method component" }
            val slash = component.indexOf('/')
            require(slash > 0 && slash == component.lastIndexOf('/')) {
                "Invalid input method component"
            }
            val pkg = component.substring(0, slash)
            val cls = component.substring(slash + 1)
            requireValidPackage(pkg)
            require(cls.matches(Regex("^\\.?[A-Za-z][A-Za-z0-9_.\$]*$"))) {
                "Invalid input method component"
            }
        }

        private val KEYCODES = mapOf(
            "BACK" to 4, "HOME" to 3, "MENU" to 82, "APP_SWITCH" to 187,
            "ENTER" to 66, "TAB" to 61, "ESCAPE" to 111, "DELETE" to 67,
            "FORWARD_DEL" to 112, "DPAD_UP" to 19, "DPAD_DOWN" to 20,
            "DPAD_LEFT" to 21, "DPAD_RIGHT" to 22, "DPAD_CENTER" to 23,
            "VOLUME_UP" to 24, "VOLUME_DOWN" to 25, "POWER" to 26,
            "CAMERA" to 27, "WAKEUP" to 224, "SLEEP" to 223,
        )

        fun resolveKeycode(raw: String): Int {
            val trimmed = raw.trim()
            trimmed.toIntOrNull()?.let {
                require(it in 0..260) { "keycode must be between 0 and 260" }
                return it
            }
            val normalized = trimmed.uppercase().removePrefix("KEYCODE_")
            return KEYCODES[normalized]
                ?: throw IllegalArgumentException("Unknown key: $raw")
        }

        fun isPng(bytes: ByteArray): Boolean =
            bytes.size > 8 &&
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
                bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
                bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()

        val TOOL_DEFINITIONS: List<ToolDefinition> = listOf(
            tool("device_status", "Read ADB connection state. Read-only.", emptyMap(), emptyList()),
            tool("read_ui", "Dump the current UI hierarchy as XML. Read-only.", emptyMap(), emptyList()),
            tool("screenshot", "Capture a PNG screenshot. Returns imageBase64. Read-only.", emptyMap(), emptyList()),
            tool("tap", "Tap the screen at pixel coordinates.", mapOf("x" to "integer", "y" to "integer"), listOf("x", "y")),
            tool("swipe", "Swipe from one point to another.", mapOf("x1" to "integer", "y1" to "integer", "x2" to "integer", "y2" to "integer", "durationMs" to "integer"), listOf("x1", "y1", "x2", "y2")),
            tool("type_text", "Type text. Uses the configured IME for full Unicode; ASCII falls back to adb input when no IME is configured.", mapOf("text" to "string", "submit" to "boolean"), listOf("text")),
            tool("key", "Send a keyevent by name or numeric code.", mapOf("keycode" to "string"), listOf("keycode")),
            tool("open_app", "Launch an app by package, optionally with activity.", mapOf("package" to "string", "activity" to "string"), listOf("package")),
            tool("shell", "Run an arbitrary shell command. Visible device control.", mapOf("command" to "string", "timeoutMs" to "integer"), listOf("command")),
            tool("pull_file", "Copy a file from the device into the run workspace. Read-only.", mapOf("remotePath" to "string", "localName" to "string"), listOf("remotePath", "localName")),
            tool("push_file", "Push a workspace file to the device.", mapOf("localName" to "string", "remotePath" to "string"), listOf("localName", "remotePath")),
            tool("install_apk", "Install a workspace APK on the device.", mapOf("localName" to "string", "replace" to "boolean"), listOf("localName")),
        )

        private fun tool(
            name: String,
            description: String,
            properties: Map<String, String>,
            required: List<String>,
        ): ToolDefinition {
            val props = buildJsonObject {
                for ((k, t) in properties) putJsonType(k, t)
            }
            val schema = buildJsonObject {
                put("type", "object")
                put("properties", props)
                put("description", description)
                put("required", JsonArray(required.map { JsonPrimitive(it) }))
            }
            return ToolDefinition(name, description, schema)
        }

        private fun kotlinx.serialization.json.JsonObjectBuilder.putJsonType(key: String, type: String) {
            put(key, buildJsonObject { put("type", type) })
        }
    }
}
