package dev.androidagent.enginecodex

import dev.androidagent.core.AgentModel
import dev.androidagent.core.ReasoningEffortOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
}
