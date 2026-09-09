package dev.androidagent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAwakePolicyTest {
    @Test fun idleConversationDoesNotKeepAwake() {
        assertFalse(KeepAwakePolicy.shouldKeepAwake(RunState(), VoiceState()))
    }

    @Test fun everyNonTerminalRunPhaseKeepsAwake() {
        val activePhases = listOf(
            RunPhase.STARTING,
            RunPhase.THINKING,
            RunPhase.TOOL,
            RunPhase.CONTROLLING,
            RunPhase.STOPPING,
        )
        for (phase in activePhases) {
            assertTrue(
                "phase $phase should keep the screen awake",
                KeepAwakePolicy.shouldKeepAwake(RunState(phase = phase), VoiceState()),
            )
        }
    }

    @Test fun erroredRunReleasesAwake() {
        assertFalse(
            KeepAwakePolicy.shouldKeepAwake(
                RunState(phase = RunPhase.ERROR, status = "Run failed"),
                VoiceState(),
            ),
        )
    }

    @Test fun everyNonTerminalVoicePhaseKeepsAwake() {
        val activePhases = listOf(
            VoicePhase.STARTING,
            VoicePhase.LISTENING,
            VoicePhase.SPEAKING,
            VoicePhase.STOPPING,
        )
        for (phase in activePhases) {
            assertTrue(
                "voice $phase should keep the screen awake",
                KeepAwakePolicy.shouldKeepAwake(RunState(), VoiceState(phase = phase)),
            )
        }
    }

    @Test fun idleOrErroredVoiceReleasesAwake() {
        assertFalse(KeepAwakePolicy.shouldKeepAwake(RunState(), VoiceState(phase = VoicePhase.IDLE)))
        assertFalse(KeepAwakePolicy.shouldKeepAwake(RunState(), VoiceState(phase = VoicePhase.ERROR)))
    }

    @Test fun typedTextWhileVoiceIsActiveKeepsAwake() {
        assertTrue(
            KeepAwakePolicy.shouldKeepAwake(
                RunState(),
                VoiceState(phase = VoicePhase.LISTENING),
            ),
        )
    }

    @Test fun stoppingStillHoldsAwakeUntilTerminal() {
        assertTrue(
            KeepAwakePolicy.shouldKeepAwake(
                RunState(phase = RunPhase.STOPPING, status = "Stopping"),
                VoiceState(),
            ),
        )
        assertTrue(
            KeepAwakePolicy.shouldKeepAwake(
                RunState(),
                VoiceState(phase = VoicePhase.STOPPING),
            ),
        )
    }
}
