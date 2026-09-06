package dev.androidagent.enginecodex

import dev.androidagent.core.AgentModel
import dev.androidagent.core.RealtimeAudioChunk
import dev.androidagent.core.ReasoningEffortOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CodexEngineTest {
    @Test fun modelCatalogPreservesAdvertisedReasoningOptions() {
        val response = Json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": "gpt-5.6-luna",
                  "model": "gpt-5.6-luna",
                  "displayName": "GPT-5.6 Luna",
                  "defaultReasoningEffort": "medium",
                  "supportedReasoningEfforts": [
                    {"reasoningEffort": "low", "description": "Fast"},
                    {"reasoningEffort": "medium", "description": "Balanced"},
                    {"reasoningEffort": "high", "description": "Deep"}
                  ]
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        assertEquals(
            listOf(
                AgentModel(
                    id = "gpt-5.6-luna",
                    displayName = "GPT-5.6 Luna",
                    reasoningEfforts = listOf(
                        ReasoningEffortOption("low", "Fast"),
                        ReasoningEffortOption("medium", "Balanced"),
                        ReasoningEffortOption("high", "Deep"),
                    ),
                    defaultReasoningEffort = "medium",
                )
            ),
            CodexEngine.parseModelCatalog(response),
        )
    }

    @Test fun modelCatalogAcceptsLegacyReasoningLevelAliases() {
        val response = Json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "slug": "legacy-model",
                  "supportedReasoningLevels": [
                    {"effort": "low", "description": "Short"},
                    {"effort": "high", "description": "Deep"}
                  ],
                  "defaultReasoningLevel": "low"
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val model = CodexEngine.parseModelCatalog(response).single()
        assertEquals("legacy-model", model.id)
        assertEquals(listOf("low", "high"), model.reasoningEfforts.map { it.value })
        assertEquals("low", model.defaultReasoningEffort)
    }

    @Test fun turnParamsTransmitSelectedEffortAndOmitAuto() {
        val selected = CodexEngine.turnStartParams("thread", "Hello", listOf(File("photo.png")), "high")
        assertEquals("high", selected["effort"]?.jsonPrimitive?.content)
        assertTrue(selected["input"].toString().contains("photo.png"))

        val automatic = CodexEngine.turnStartParams("thread", "Hello", emptyList(), null)
        assertFalse(automatic.containsKey("effort"))
    }

    @Test fun realtimeStartUsesAudioAndLeavesTransportToAppServer() {
        val params = CodexEngine.realtimeStartParams("thread-1", "voice-model")

        assertEquals("thread-1", params["threadId"]?.jsonPrimitive?.content)
        assertEquals("audio", params["outputModality"]?.jsonPrimitive?.content)
        assertEquals("v2", params["version"]?.jsonPrimitive?.content)
        assertEquals("true", params["flushTranscriptTailOnSessionEnd"]?.jsonPrimitive?.content)
        assertEquals("voice-model", params["model"]?.jsonPrimitive?.content)
        assertFalse(params.containsKey("transport"))
    }

    @Test fun realtimeAudioParamsEncodeBytesAndParserRoundTripsThem() {
        val chunk = RealtimeAudioChunk(
            data = byteArrayOf(0x00, 0x01, 0x02, 0x7f, 0xff.toByte()),
            sampleRate = 24_000,
            numChannels = 1,
            samplesPerChannel = 512,
        )
        val params = CodexEngine.realtimeAppendAudioParams("thread-1", chunk)
        val audio = params["audio"]!!.jsonObject

        assertEquals("AAECf/8=", audio["data"]?.jsonPrimitive?.content)
        assertEquals(24_000, audio["sampleRate"]?.jsonPrimitive?.intOrNull)
        assertEquals(1, audio["numChannels"]?.jsonPrimitive?.intOrNull)
        assertEquals(512, audio["samplesPerChannel"]?.jsonPrimitive?.intOrNull)

        val decoded = CodexEngine.parseRealtimeAudio(audio)
        assertArrayEquals(chunk.data, decoded.data)
        assertEquals(chunk.sampleRate, decoded.sampleRate)
        assertEquals(chunk.numChannels, decoded.numChannels)
        assertEquals(chunk.samplesPerChannel, decoded.samplesPerChannel)
    }

    @Test fun realtimeTextAndSpeechParamsMatchPinnedProtocol() {
        val text = CodexEngine.realtimeAppendTextParams("thread-1", "hello", "assistant")
        assertEquals("thread-1", text["threadId"]?.jsonPrimitive?.content)
        assertEquals("hello", text["text"]?.jsonPrimitive?.content)
        assertEquals("assistant", text["role"]?.jsonPrimitive?.content)

        val speech = CodexEngine.realtimeAppendSpeechParams("thread-1", "say this")
        assertEquals("thread-1", speech["threadId"]?.jsonPrimitive?.content)
        assertEquals("say this", speech["text"]?.jsonPrimitive?.content)
        assertEquals("thread-1", CodexEngine.realtimeStopParams("thread-1")["threadId"]?.jsonPrimitive?.content)
    }

    @Test fun realtimeTextRejectsUnknownRole() {
        try {
            CodexEngine.realtimeAppendTextParams("thread-1", "hello", "system")
            throw AssertionError("expected an invalid role to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
