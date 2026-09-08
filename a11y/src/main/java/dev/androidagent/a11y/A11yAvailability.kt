package dev.androidagent.a11y

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager

/** What the UI needs to tell the user about the accessibility backend. */
data class A11yStatus(
    /** The user switched it on in Settings. */
    val declaredEnabled: Boolean,
    /** The service is actually running and reachable. */
    val connected: Boolean,
) {
    /**
     * Switched on but never connected. On Android 13 and later this is what a
     * sideloaded build looks like before the user allows restricted settings,
     * and it is the only signal available — there is no API to ask whether a
     * setting is restricted.
     */
    val blockedByRestrictedSetting: Boolean get() = declaredEnabled && !connected

    val message: String
        get() = when {
            connected -> "Accessibility control is on"
            blockedByRestrictedSetting -> "Allow restricted settings to finish enabling it"
            else -> "Accessibility control is off"
        }
}

object A11yAvailability {

    fun status(context: Context): A11yStatus = A11yStatus(
        declaredEnabled = isDeclaredEnabled(context),
        connected = A11yServiceHandle.connected,
    )

    /**
     * Whether the user has switched the service on, regardless of whether it
     * managed to start. Compared against the runtime package name, because the
     * dev flavor carries a `.dev` suffix that a compile-time constant misses.
     */
    fun isDeclaredEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val expected = ComponentName(context.packageName, AgentAccessibilityService::class.java.name)
        return runCatching {
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo?.serviceInfo?.let { info -> ComponentName(info.packageName, info.name) } == expected }
        }.getOrDefault(false)
    }
}
