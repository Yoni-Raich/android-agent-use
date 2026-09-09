package dev.androidagent.app

import android.content.Context
import android.os.PowerManager
import dev.androidagent.core.KeepAwakePolicy
import dev.androidagent.core.RunState
import dev.androidagent.core.VoiceState

/** Small seam that makes the service-owned screen lock lifecycle testable. */
internal interface ScreenWakeLock {
    val isHeld: Boolean
    fun acquire()
    fun release()
}

internal class ConversationKeepAwakeController(
    private val screenWakeLock: ScreenWakeLock,
) {
    fun update(run: RunState, voice: VoiceState) {
        setHeld(KeepAwakePolicy.shouldKeepAwake(run, voice))
    }

    fun release() {
        setHeld(false)
    }

    private fun setHeld(shouldHold: Boolean) {
        if (shouldHold) {
            if (!screenWakeLock.isHeld) screenWakeLock.acquire()
        } else if (screenWakeLock.isHeld) {
            screenWakeLock.release()
        }
    }
}

/** Screen wake lock used only while the foreground service owns an active run. */
internal class AndroidScreenWakeLock(context: Context) : ScreenWakeLock {
    @Suppress("DEPRECATION")
    private val wakeLock = (context.getSystemService(PowerManager::class.java)
        ?: error("Power manager is unavailable"))
        .newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
            "${context.packageName}:Conversation",
        )
        .apply { setReferenceCounted(false) }

    override val isHeld: Boolean
        get() = wakeLock.isHeld

    override fun acquire() {
        if (!wakeLock.isHeld) wakeLock.acquire()
    }

    override fun release() {
        if (wakeLock.isHeld) wakeLock.release()
    }
}
