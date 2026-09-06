package dev.androidagent.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.androidagent.core.AgentModel
import dev.androidagent.core.ChatMessage
import dev.androidagent.core.ReasoningEffortOption
import dev.androidagent.core.RunPhase
import dev.androidagent.core.RunState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ChatUiTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = AgentUiState(
        activeSessionId = "ui-fixture", activeSessionTitle = "תכנון היום",
        selectedModel = "gpt-5.6-luna", availableModels = listOf("gpt-5.6-luna"),
        modelCatalog = listOf(
            AgentModel(
                id = "gpt-5.6-luna",
                reasoningEfforts = listOf(
                    ReasoningEffortOption("low", "Fast"),
                    ReasoningEffortOption("high", "Deep"),
                ),
                defaultReasoningEffort = "low",
            )
        ),
        messages = listOf(
            ChatMessage("user", "ui-fixture", "user", "עזור לי לתכנן את היום שלי", 1),
            ChatMessage("assistant", "ui-fixture", "assistant",
                "בשמחה. נתחיל בדברים החשובים לך היום.\n\nאפשר להכין רשימת משימות, לבחור מה לעשות קודם ולשמור זמן להפסקה. מה תרצה להספיק?", 2)
        )
    )
    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(700)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun hebrewMessageSendsAndClearsDraft() {
        var sent = ""
        compose.setContent { AndroidAgentScreen(fixture, AgentUiActions(onSend = { text, _ -> sent = text })) }
        compose.onNodeWithText("עזור לי לתכנן את היום שלי").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        screenshot("chat-hebrew")
        compose.onNodeWithContentDescription("Message input").performClick().performTextInput("נכין רשימה")
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.runOnIdle { assertEquals("נכין רשימה", sent) }
        compose.onNodeWithContentDescription("Message input").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }
    @Test fun stopStaysReachableWhileSteering() {
        var stopped = 0
        var steered = ""
        compose.setContent { AndroidAgentScreen(fixture.copy(runState = RunState(
            phase = RunPhase.THINKING, sessionId = "ui-fixture", status = "Thinking")),
            AgentUiActions(onStop = { stopped++ }, onSteer = { steered = it })) }
        compose.onNodeWithContentDescription("Message input").performClick().performTextInput("קודם את המשימה החשובה")
        compose.onNodeWithContentDescription("Stop agent").assertIsDisplayed()
        compose.onNodeWithContentDescription("Steer agent").assertIsDisplayed()
        screenshot("chat-steering")
        compose.onNodeWithContentDescription("Steer agent").performClick()
        compose.runOnIdle { assertEquals("קודם את המשימה החשובה", steered) }
        compose.onNodeWithContentDescription("Stop agent").performClick()
        compose.runOnIdle { assertEquals(1, stopped) }
    }
    @Test fun stoppingKeepsDraftAndBlocksSteering() {
        compose.setContent { AndroidAgentScreen(fixture.copy(runState = RunState(
            phase = RunPhase.STOPPING, sessionId = "ui-fixture")), AgentUiActions()) }
        compose.onNodeWithContentDescription("Message input").performTextInput("Keep this draft")
        compose.onNodeWithContentDescription("Steer agent").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Stop agent").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Message input").assertTextContains("Keep this draft")
    }
    @Test fun reasoningSelectorUsesAdvertisedOptions() {
        var selected: String? = null
        compose.setContent {
            AndroidAgentScreen(
                fixture,
                AgentUiActions(onReasoningEffortSelected = { selected = it }),
            )
        }
        compose.onNodeWithContentDescription("Choose reasoning effort").performClick()
        compose.onNodeWithText("high").performClick()
        compose.runOnIdle { assertEquals("high", selected) }
    }
    @Test fun markdownAndCopyControlAreVisible() {
        compose.setContent { AndroidAgentScreen(fixture.copy(messages = listOf(
            ChatMessage("assistant", "ui-fixture", "assistant", "# כותרת\n\n- פריט ראשון", 3)
        )), AgentUiActions()) }
        compose.onNodeWithText("כותרת", substring = true).assertIsDisplayed()
        compose.onNodeWithText("פריט ראשון", substring = true).assertIsDisplayed()
        screenshot("chat-markdown")
    }
    @Test fun diagnosticsAreCollapsedAndRemainAvailable() {
        val details = "[HTTP] Connection failed | diagnostic trace"
        compose.setContent { AndroidAgentScreen(fixture.copy(errorMessage = details), AgentUiActions()) }
        compose.onNodeWithText(details).assertDoesNotExist()
        compose.onNodeWithText("Show details").performClick()
        compose.onNodeWithText(details).assertIsDisplayed()
        compose.onNodeWithText("Hide details").performClick()
        compose.onNodeWithText(details).assertDoesNotExist()
        screenshot("chat-error-collapsed")
    }
}
