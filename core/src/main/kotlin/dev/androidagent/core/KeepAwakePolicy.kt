package dev.androidagent.core

/**
 * Decides whether the device screen must stay awake for an ongoing conversation.
 *
 * The foreground activity applies `FLAG_KEEP_SCREEN_ON` while this returns
 * true. The foreground service uses a screen wake lock for the same state when
 * the activity is hidden. Neither path writes system settings, so the user's
 * display timeout is preserved.
 *
 * The inputs reuse the existing `active` definitions: [RunState.active] stays
 * true through STOPPING and drops on IDLE/ERROR, and [VoiceState.active] stays
 * true through STOPPING and drops on IDLE/ERROR. That keeps the screen awake
 * for typed runs, realtime voice, and typed text sent while voice is active.
 */
object KeepAwakePolicy {
    fun shouldKeepAwake(run: RunState, voice: VoiceState): Boolean =
        run.active || voice.active
}
