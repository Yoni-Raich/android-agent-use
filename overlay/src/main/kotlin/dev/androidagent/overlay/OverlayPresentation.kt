package dev.androidagent.overlay

/** Small, platform-free presentation mapping used by the floating card. */
internal enum class OverlayTone { ACTIVE, CONTROLLING, STOPPING, DONE, ERROR }

internal fun overlayTone(status: String): OverlayTone = when (status.substringBefore('·').trim().lowercase()) {
    "stopping" -> OverlayTone.STOPPING
    "done" -> OverlayTone.DONE
    "error" -> OverlayTone.ERROR
    "controlling" -> OverlayTone.CONTROLLING
    else -> OverlayTone.ACTIVE
}
