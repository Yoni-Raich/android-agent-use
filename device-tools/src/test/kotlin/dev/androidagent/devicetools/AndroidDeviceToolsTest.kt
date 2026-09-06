package dev.androidagent.devicetools

import dev.androidagent.core.AdbEndpoint
import dev.androidagent.core.AdbStatus
import dev.androidagent.core.AdbTransport
import dev.androidagent.core.CommandResult
import dev.androidagent.core.ConnectionPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.util.Base64

class AndroidDeviceToolsTest {

    @Test fun quotingEscapesSingleQuotesAndSpaces() {
        assertEquals("'hello'", AndroidDeviceTools.shellQuote("hello"))
        assertEquals("''", AndroidDeviceTools.shellQuote(""))
        assertEquals("'a'\\''b'", AndroidDeviceTools.shellQuote("a'b"))
        assertEquals("'a b; rm -rf /'", AndroidDeviceTools.shellQuote("a b; rm -rf /"))
        // Quoted payload stays a single shell token: no raw injection.
        assertTrue(AndroidDeviceTools.shellQuote("x\$(whoami)").startsWith("'"))
        assertTrue(AndroidDeviceTools.shellQuote("x\$(whoami)").endsWith("'"))
    }

    @Test fun invokeBeforeBeginRunIsDenied() {
        val adb = FakeAdb()
        val tools = AndroidDeviceTools(adb)
        assertEquals(12, tools.definitions.size)
        try {
            runBlocking { tools.invoke("device_status", buildJsonObject {}) }
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("stopped", ignoreCase = true))
        }
        assertEquals(0, adb.calls)
    }

    @Test fun revokePreventsSubsequentDispatch() {
        val adb = FakeAdb()
        val tools = AndroidDeviceTools(adb)
        val ws = Files.createTempDirectory("ws").toFile()
        tools.beginRun("r1", ws)
        runBlocking { tools.invoke("device_status", buildJsonObject {}) }
        assertEquals(0, adb.calls) // device_status reads status flow, no shell call
        tools.revoke()
        try {
            runBlocking { tools.invoke("tap", buildJsonObject { put("x", 10); put("y", 20) }) }
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("stopped", ignoreCase = true))
        }
        assertEquals(0, adb.calls)
        // A new run re-arms dispatch.
        tools.beginRun("r2", ws)
        runBlocking { tools.invoke("tap", buildJsonObject { put("x", 10); put("y", 20) }) }
        assertEquals(1, adb.calls)
        assertTrue(adb.lastCommand!!.startsWith("input tap 10 20"))
    }

    @Test fun workspaceTraversalDenied() {
        val adb = FakeAdb()
        val tools = AndroidDeviceTools(adb)
        val ws = Files.createTempDirectory("ws").toFile()
        tools.beginRun("r1", ws)
        for (evil in listOf("../evil.txt", "/abs.txt", "a/../../evil.txt", "C:\\evil.txt", "sub\\file.txt")) {
            try {
                runBlocking {
                    tools.invoke(
                        "pull_file",
                        buildJsonObject { put("remotePath", "/sdcard/f.txt"); put("localName", evil) },
                    )
                }
                fail("expected rejection for $evil")
            } catch (e: IllegalArgumentException) {
                // absolute/traversal/backslash rejections surface as require() failures
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("traversal", ignoreCase = true))
            }
        }
    }

    @Test fun unicodeTextFailsHonestly() {
        try {
            AndroidDeviceTools.requireAdbInputText("hello שלום")
            fail("expected Unicode rejection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("IME", ignoreCase = true))
        }
        // ASCII passes.
        AndroidDeviceTools.requireAdbInputText("Meet at 6:00 PM!")
    }

    @Test fun imePayloadPreservesHebrewAndBroadcastDoesNotExposeText() {
        val text = "שלום עולם 😀 O'Reilly"
        val payload = AndroidDeviceTools.encodeImePayload(text)
        assertEquals(text, String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8))

        val command = AndroidDeviceTools.buildImeBroadcastCommand(
            "dev.androidagent.app/dev.androidagent.app.ime.AgentInputMethodService",
            payload,
        )
        assertTrue(command.contains("dev.androidagent.app.INPUT_TEXT"))
        assertTrue(command.contains("payload_base64"))
        assertTrue(command.contains(AndroidDeviceTools.shellQuote(payload)))
        assertFalse(command.contains("--receiver-permission"))
        assertFalse(command.contains(text))
        assertFalse(command.contains("O'Reilly"))
    }

    @Test fun imeSelectionNormalizesAndroidComponentNames() {
        val component = "dev.androidagent.app/dev.androidagent.app.ime.AgentInputMethodService"
        assertTrue(AndroidDeviceTools.imeSelectionMatches(component, component))
        assertTrue(AndroidDeviceTools.imeSelectionMatches(
            "dev.androidagent.app/.ime.AgentInputMethodService",
            component,
        ))
        assertFalse(AndroidDeviceTools.imeSelectionMatches("com.android.inputmethod/.LatinIME", component))
    }

    @Test fun imeBroadcastOnlyConfirmsARealCommitResult() {
        assertTrue(AndroidDeviceTools.imeBroadcastCommitted("Broadcast completed: result=1 data=ok"))
        assertFalse(AndroidDeviceTools.imeBroadcastCommitted("Broadcast completed: result=4 data=error"))
        assertFalse(AndroidDeviceTools.imeBroadcastCommitted("Broadcast completed"))
    }

    @Test fun imeWaitsForTheEditorConnectionBeforeSendingUnicode() = runBlocking {
        val component = "dev.androidagent.app/dev.androidagent.app.ime.AgentInputMethodService"
        val adb = ImeFakeAdb(component, readyAfterDump = 2, commitResult = 1)
        val tools = AndroidDeviceTools(adb, component)
        tools.beginRun("ime", Files.createTempDirectory("ws").toFile())

        val result = tools.invoke("type_text", buildJsonObject { put("text", "שלום") })

        assertTrue(result.success)
        assertTrue(adb.commands.any { it.contains("INPUT_PROBE") })
        assertTrue(adb.commands.any { it.startsWith("am broadcast") })
        assertEquals(1, adb.commands.count { it.contains("INPUT_TEXT") })
        assertTrue(adb.commands.indexOfFirst { it.contains("INPUT_TEXT") } > adb.commands.indexOfFirst { it.contains("INPUT_PROBE") })
    }

    @Test fun imeCommitFailureReturnsGuidanceAndNeverClaimsSuccess() = runBlocking {
        val component = "dev.androidagent.app/dev.androidagent.app.ime.AgentInputMethodService"
        val adb = ImeFakeAdb(component, readyAfterDump = 1, commitResult = 4)
        val tools = AndroidDeviceTools(adb, component)
        tools.beginRun("ime", Files.createTempDirectory("ws").toFile())

        try {
            tools.invoke("type_text", buildJsonObject { put("text", "שלום") })
            fail("expected the IME commit to be rejected")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("No Enter key was sent", ignoreCase = true))
            assertTrue(error.message!!.contains("editor", ignoreCase = true))
        }
        assertTrue(adb.commands.count { it.startsWith("am broadcast") } >= 2)
    }

    @Test fun ambiguousCommitIsNeverRetriedOrSubmitted() = runBlocking {
        val component = "dev.androidagent.app/.ime.AgentInputMethodService"
        val adb = ImeFakeAdb(component, 1, 5)
        val tools = AndroidDeviceTools(adb, component)
        tools.beginRun("ime", Files.createTempDirectory("ws").toFile())
        try {
            tools.invoke("type_text", buildJsonObject { put("text", "שלום"); put("submit", true) })
            fail("expected ambiguous commit failure")
        } catch (_: IllegalStateException) { }
        assertEquals(1, adb.commands.count { it.contains("INPUT_TEXT") })
        assertFalse(adb.commands.any { it == "input keyevent 66" })
        assertTrue(adb.commands.last().startsWith("ime set"))
    }

    @Test fun coordinatesAndKeysValidated() {
        val tools = AndroidDeviceTools(FakeAdb())
        val ws = Files.createTempDirectory("ws").toFile()
        tools.beginRun("r1", ws)
        try {
            runBlocking { tools.invoke("tap", buildJsonObject { put("x", -5); put("y", 10) }) }
            fail("expected coordinate rejection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("x"))
        }
        try {
            runBlocking { tools.invoke("key", buildJsonObject { put("keycode", "NOT_A_KEY") }) }
            fail("expected key rejection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Unknown key"))
        }
        assertEquals(66, AndroidDeviceTools.resolveKeycode("ENTER"))
        assertEquals(4, AndroidDeviceTools.resolveKeycode("keycode_back"))
        assertEquals(3, AndroidDeviceTools.resolveKeycode("3"))
    }

    @Test fun controlMappingKeepsReadsQuietAndShellVisible() {
        val tools = AndroidDeviceTools(FakeAdb())
        assertFalse(tools.needsControl("device_status"))
        assertFalse(tools.needsControl("read_ui"))
        assertFalse(tools.needsControl("screenshot"))
        assertFalse(tools.needsControl("pull_file"))
        for (name in listOf("tap", "swipe", "type_text", "key", "open_app", "shell", "push_file", "install_apk")) {
            assertTrue("$name must need control", tools.needsControl(name))
        }
        assertTrue(tools.needsControl("unknown_future_tool"))
    }

    @Test fun shellInjectionSafelyQuotedInOpenApp() {
        val adb = FakeAdb()
        val tools = AndroidDeviceTools(adb)
        val ws = Files.createTempDirectory("ws").toFile()
        tools.beginRun("r1", ws)
        try {
            runBlocking {
                tools.invoke("open_app", buildJsonObject { put("package", "com.evil; rm -rf /") })
            }
            fail("expected package rejection")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("package", ignoreCase = true))
        }
        assertEquals(0, adb.calls)
    }

    private class FakeAdb : AdbTransport {
        private val state = MutableStateFlow(AdbStatus(ConnectionPhase.CONNECTED, "ok", 1))
        override val status: StateFlow<AdbStatus> = state.asStateFlow()
        var calls = 0
        var lastCommand: String? = null
        override suspend fun discover(): List<AdbEndpoint> = emptyList()
        override suspend fun pair(port: Int, code: String) = Unit
        override suspend fun connect(port: Int) = Unit
        override suspend fun execute(command: String, timeoutMs: Long): CommandResult {
            calls++
            lastCommand = command
            return CommandResult("ok", 0)
        }
        override suspend fun executeBytes(command: String, timeoutMs: Long): ByteArray {
            calls++
            lastCommand = command
            // Minimal valid PNG header so screenshot validation passes.
            return byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01,
            )
        }
        override suspend fun cancelActive() = Unit
        override suspend fun disconnect() = Unit
        override suspend fun forgetPairing() = Unit
    }

    private class ImeFakeAdb(
        private val component: String,
        private val readyAfterDump: Int,
        private val commitResult: Int,
    ) : AdbTransport {
        private val state = MutableStateFlow(AdbStatus(ConnectionPhase.CONNECTED, "ok", 1))
        override val status: StateFlow<AdbStatus> = state.asStateFlow()
        val commands = mutableListOf<String>()
        private var settingsReads = 0
        private var dumpReads = 0

        override suspend fun discover(): List<AdbEndpoint> = emptyList()
        override suspend fun pair(port: Int, code: String) = Unit
        override suspend fun connect(port: Int) = Unit
        override suspend fun execute(command: String, timeoutMs: Long): CommandResult {
            commands += command
            return when {
                command == "settings get secure default_input_method" -> {
                    settingsReads++
                    CommandResult(if (settingsReads == 1) "com.android.inputmethod/.LatinIME" else "$component\n", 0)
                }
                command.contains("INPUT_PROBE") -> {
                    dumpReads++
                    CommandResult("Broadcast completed: result=${if (dumpReads >= readyAfterDump) 1 else 4}", 0)
                }
                command.startsWith("am broadcast") -> CommandResult("Broadcast completed: result=$commitResult data=ok", 0)
                else -> CommandResult("ok", 0)
            }
        }
        override suspend fun executeBytes(command: String, timeoutMs: Long): ByteArray = ByteArray(0)
        override suspend fun cancelActive() = Unit
        override suspend fun disconnect() = Unit
        override suspend fun forgetPairing() = Unit
    }
}
