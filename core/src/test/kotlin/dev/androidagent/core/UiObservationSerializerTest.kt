package dev.androidagent.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class UiObservationSerializerTest {

    private fun node(
        id: String = "n0",
        text: String? = "Send",
        contentDescription: String? = null,
        resourceId: String? = "com.example:id/send",
        className: String? = "android.widget.Button",
        bounds: List<Int>? = listOf(10, 20, 110, 60),
        password: Boolean = false,
        clickableAncestor: UiNode? = null,
    ) = UiNode(
        nodeId = id,
        text = text,
        contentDescription = contentDescription,
        resourceId = resourceId,
        className = className,
        bounds = bounds,
        enabled = true,
        clickable = true,
        scrollable = false,
        focused = false,
        packageName = "com.example",
        password = password,
        clickableAncestor = clickableAncestor,
    )

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    // ---- envelopes ----

    @Test fun semanticJsonCarriesTheFullEnvelopeAndNodeList() {
        val text = UiObservationSerializer.semanticJson(
            UiObservation("com.example", listOf(node())),
            source = "uiautomator", observationId = "ui-1", revision = 1,
            elapsedMs = 42, truncated = false, stable = true,
        )
        assertEquals(
            """{"ok":true,"observationId":"ui-1","revision":1,"elapsedMs":42,"source":"uiautomator",""" +
                """"stable":true,"activePackage":"com.example","truncated":false,"nodes":""" +
                """[{"nodeId":"n0","text":"Send","resourceId":"com.example:id/send",""" +
                """"class":"android.widget.Button","bounds":[10,20,110,60],"enabled":true,""" +
                """"clickable":true,"scrollable":false,"focused":false}]}""",
            text,
        )
    }

    @Test fun semanticJsonOmitsActivePackageWhenUnknown() {
        val json = parse(
            UiObservationSerializer.semanticJson(
                UiObservation(null, listOf(node())), "uiautomator", "ui-1", 1, 0, false, true,
            )
        )
        assertFalse(json.containsKey("activePackage"))
        for (key in listOf("ok", "observationId", "revision", "elapsedMs", "source", "stable", "truncated", "nodes")) {
            assertTrue("missing $key", json.containsKey(key))
        }
    }

    @Test fun semanticJsonReportsTheBackendThatAnswered() {
        val json = parse(
            UiObservationSerializer.semanticJson(
                UiObservation("com.example", listOf(node())), "accessibility", "ui-1", 1, 0, false, false,
            )
        )
        assertEquals("accessibility", json["source"]!!.jsonPrimitive.content)
        assertFalse(json["stable"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun semanticJsonKeepsTheClickableAncestorAsAPositionOnlyReference() {
        val ancestor = node(id = "n1", bounds = listOf(0, 0, 200, 100)).asClickTarget()
        val json = parse(
            UiObservationSerializer.semanticJson(
                UiObservation("com.example", listOf(node(clickableAncestor = ancestor))),
                "uiautomator", "ui-1", 1, 0, false, true,
            )
        )
        val emitted = json["nodes"]!!.jsonArray[0].jsonObject["clickableAncestor"]!!.jsonObject
        assertEquals("n1", emitted["nodeId"]!!.jsonPrimitive.content)
        assertEquals(listOf(0, 0, 200, 100), emitted["bounds"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() })
        // A parent reference carries position only; labels belong to the child.
        assertFalse(emitted.containsKey("text"))
        assertFalse(emitted.containsKey("resourceId"))
    }

    @Test fun unchangedJsonNamesTheEarlierRevisionAndExplainsWhatToDo() {
        val text = UiObservationSerializer.unchangedJson(
            activePackage = "com.example", nodeCount = 41, source = "uiautomator",
            observationId = "ui-13", revision = 13, elapsedMs = 7, unchangedSinceRevision = 12,
        )
        val json = parse(text)
        assertTrue(json["unchanged"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(12, json["unchangedSinceRevision"]!!.jsonPrimitive.content.toLong())
        assertEquals(41, json["nodeCount"]!!.jsonPrimitive.content.toInt())
        assertTrue(json["hint"]!!.jsonPrimitive.content.startsWith("Screen is identical to revision 12."))
        assertTrue(json["hint"]!!.jsonPrimitive.content.contains("force=true"))
        assertFalse(json.containsKey("nodes"))
    }

    @Test fun failureJsonOmitsTheOptionalFieldsWhenThereIsNothingToSay() {
        val json = parse(
            UiObservationSerializer.failureJson("ui-3", 3, 12, "ui_timeout", "Timed out")
        )
        assertFalse(json["ok"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("none", json["source"]!!.jsonPrimitive.content)
        assertEquals("ui_timeout", json["errorType"]!!.jsonPrimitive.content)
        assertFalse(json.containsKey("remedy"))
        assertFalse(json.containsKey("alternatives"))
        assertFalse(json.containsKey("reasons"))
    }

    @Test fun failureJsonCarriesRemedyAlternativesAndReasonsWhenSupplied() {
        val json = parse(
            UiObservationSerializer.failureJson(
                "-", 0, 0, "backend_unavailable", "No backend",
                remedy = "Enable the accessibility service.",
                alternatives = listOf("screenshot"),
                reasons = listOf("a11y_unavailable: off", "adb_unavailable: disconnected"),
            )
        )
        assertEquals("Enable the accessibility service.", json["remedy"]!!.jsonPrimitive.content)
        assertEquals(listOf("screenshot"), json["alternatives"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(2, json["reasons"]!!.jsonArray.size)
    }

    @Test fun failureMessageIsCappedSoAThrownStackNeverFloodsTheReply() {
        val json = parse(
            UiObservationSerializer.failureJson("-", 0, 0, "ui_dump_failure", "x".repeat(5_000))
        )
        assertEquals(
            UiObservationSerializer.MAX_UI_FIELD_CHARS,
            json["message"]!!.jsonPrimitive.content.length,
        )
    }

    // ---- digest ----

    @Test fun digestIsStableForIdenticalScreensAndMovesWithAnyRealChange() {
        val base = UiObservationSerializer.digest("com.example", listOf(node()))
        assertEquals(base, UiObservationSerializer.digest("com.example", listOf(node())))
        assertNotEquals(base, UiObservationSerializer.digest("com.example", listOf(node(bounds = listOf(10, 20, 110, 61)))))
        assertNotEquals(base, UiObservationSerializer.digest("com.other", listOf(node())))
        assertNotEquals(base, UiObservationSerializer.digest("com.example", emptyList()))
    }

    // ---- render ----

    @Test fun renderSuppressesAnIdenticalScreenAndForceOverridesIt() {
        val observation = UiObservation("com.example", listOf(node()))
        val first = UiObservationSerializer.render(
            observation, "uiautomator", "adb", "ui-1", 1, 0, previous = null, force = false, stable = true,
        )
        assertFalse(first.unchanged)
        val fingerprint = assertNotNull(first.fingerprint)

        val second = UiObservationSerializer.render(
            observation, "uiautomator", "adb", "ui-2", 2, 0, previous = fingerprint, force = false, stable = true,
        )
        assertTrue(second.unchanged)
        // Nothing new to remember: the model still holds revision 1.
        assertNull(second.fingerprint)
        assertTrue(second.text.contains("\"unchangedSinceRevision\":1"))

        val forced = UiObservationSerializer.render(
            observation, "uiautomator", "adb", "ui-3", 3, 0, previous = fingerprint, force = true, stable = true,
        )
        assertFalse(forced.unchanged)
        assertNotNull(forced.fingerprint)
        assertTrue(forced.text.contains("\"nodes\""))
    }

    @Test fun renderNeverSuppressesAcrossBackendsEvenWhenTheDigestMatches() {
        val observation = UiObservation("com.example", listOf(node()))
        val fromAdb = assertNotNull(
            UiObservationSerializer.render(
                observation, "uiautomator", "adb", "ui-1", 1, 0, null, force = false, stable = true,
            ).fingerprint
        )
        val fromA11y = UiObservationSerializer.render(
            observation, "accessibility", "a11y", "ui-2", 2, 0, previous = fromAdb, force = false, stable = true,
        )
        // Same digest, different backend: the node lists are not comparable, so
        // answering "unchanged" would point the model at a list it never saw.
        assertFalse(fromA11y.unchanged)
        assertNotNull(fromA11y.fingerprint)
    }

    @Test fun renderTruncatesAnOversizedScreenAndTerminates() {
        val nodes = (0 until 3_000).map { node(id = "n$it", text = "label ".repeat(20) + it) }
        val rendered = UiObservationSerializer.render(
            UiObservation("com.example", nodes), "uiautomator", "adb", "ui-1", 1, 0,
            previous = null, force = false, stable = true,
        )
        assertTrue(
            "output was ${rendered.text.length} chars",
            rendered.text.length <= UiObservationSerializer.MAX_OUTPUT_CHARS,
        )
        assertTrue(rendered.text.contains("\"truncated\":true"))
        assertFalse(rendered.unchanged)
    }

    @Test fun truncationDoesNotChangeTheDigestSoASuppressedScreenStaysComparable() {
        // The digest covers the whole screen, not the trimmed payload, so a
        // screen that truncates is still recognised as unchanged next time.
        val nodes = (0 until 3_000).map { node(id = "n$it", text = "label ".repeat(20) + it) }
        val observation = UiObservation("com.example", nodes)
        val rendered = UiObservationSerializer.render(
            observation, "uiautomator", "adb", "ui-1", 1, 0, null, force = false, stable = true,
        )
        assertEquals(
            UiObservationSerializer.digest("com.example", nodes),
            assertNotNull(rendered.fingerprint).digest,
        )
    }

    // ---- privacy ----

    @Test fun passwordNodesNeverEmitTheirText() {
        val json = parse(
            UiObservationSerializer.semanticJson(
                UiObservation("com.example", listOf(node(text = "hunter2", password = true))),
                "uiautomator", "ui-1", 1, 0, false, true,
            )
        )
        val emitted = json["nodes"]!!.jsonArray[0].jsonObject
        assertTrue(emitted["password"]!!.jsonPrimitive.content.toBoolean())
        assertFalse(emitted.containsKey("text"))
    }

    @Test fun onScreenCredentialsAreRedactedBeforeTheyLeaveTheDevice() {
        val json = parse(
            UiObservationSerializer.semanticJson(
                UiObservation(
                    "com.example",
                    listOf(node(text = "key sk-abcdefgh1234", contentDescription = "Bearer abcdefgh1234")),
                ),
                "uiautomator", "ui-1", 1, 0, false, true,
            )
        )
        val emitted = json["nodes"]!!.jsonArray[0].jsonObject
        assertEquals("key [REDACTED_API_KEY]", emitted["text"]!!.jsonPrimitive.content)
        assertEquals("Bearer [REDACTED]", emitted["contentDescription"]!!.jsonPrimitive.content)
    }

    @Test fun ordinaryScreenTextWithAQuestionMarkSurvivesRedaction() {
        // SecretRedactor.redact rewrites everything after a "?", which is right
        // for a URL in a log line and wrong for a dialog on screen.
        val json = parse(
            UiObservationSerializer.semanticJson(
                UiObservation("com.example", listOf(node(text = "Delete this chat?Undo is not possible"))),
                "uiautomator", "ui-1", 1, 0, false, true,
            )
        )
        assertEquals(
            "Delete this chat?Undo is not possible",
            json["nodes"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content,
        )
    }

    // ---- field handling ----

    @Test fun compactFieldTrimsDropsBlanksAndCaps() {
        assertEquals("Send", UiObservationSerializer.compactField("  Send  "))
        assertNull(UiObservationSerializer.compactField("   "))
        assertNull(UiObservationSerializer.compactField(null))
        assertEquals(
            UiObservationSerializer.MAX_UI_FIELD_CHARS,
            UiObservationSerializer.compactField("x".repeat(1_000))!!.length,
        )
    }

    // ---- shared state ----

    @Test fun observationStateHandsOutIncreasingRevisionsAndForgetsOnReset() {
        val state = ObservationState()
        val first = state.nextRevision()
        val second = state.nextRevision()
        assertTrue(second > first)
        assertNull(state.last())

        val fingerprint = ObservationFingerprint("digest", second, "adb")
        state.record(fingerprint)
        assertEquals(fingerprint, state.last())

        state.reset()
        assertNull(state.last())
        // Revisions keep climbing across a reset, so an id is never reused.
        assertTrue(state.nextRevision() > second)
    }

    private fun <T> assertNotNull(value: T?): T {
        assertNotNull("expected a value", value)
        return value!!
    }
}
