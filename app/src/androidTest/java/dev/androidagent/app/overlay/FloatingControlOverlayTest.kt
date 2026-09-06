package dev.androidagent.app.overlay

import android.content.Intent
import android.provider.Settings
import android.os.SystemClock
import android.app.Instrumentation
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import dev.androidagent.core.OverlayPhase
import dev.androidagent.core.OverlayState
import dev.androidagent.overlay.FloatingControlOverlay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class FloatingControlOverlayTest {
    @Test
    fun pillHidesForOwnAppRestoresDuringRunAndCapturesSyntheticSurface() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        // Overlay permission is intentionally managed by the host test setup;
        // do not change app-ops or device state from this test.
        assumeTrue(Settings.canDrawOverlays(targetContext))

        val fixture = instrumentation.startActivitySync(
            Intent(targetContext, OverlayFixtureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val device = UiDevice.getInstance(instrumentation)
        var opened = 0
        val overlay = FloatingControlOverlay(
            fixture,
            onStop = {},
            onSend = {},
            onOpenApp = { opened++ },
        )
        try {
            runBlocking { overlay.show("Thinking") }
            waitForIdle(instrumentation)
            assertTrue(device.hasObject(By.desc("Stop run")))
            assertTrue(device.hasObject(By.text("Thinking")))

            saveScreenshot(device, targetContext, "overlay-glass-pill-visible")

            overlay.setAppForeground(true)
            waitForIdle(instrumentation)
            assertFalse(device.hasObject(By.desc("Stop run")))
            assertTrue(device.hasObject(By.desc("Synthetic overlay test surface")))

            overlay.setAppForeground(false)
            waitForIdle(instrumentation)
            assertTrue(device.hasObject(By.desc("Stop run")))
            assertTrue(device.hasObject(By.text("Thinking")))

            overlay.finish(OverlayState(OverlayPhase.DONE))
            SystemClock.sleep(500L)
            waitForIdle(instrumentation)
            assertFalse(device.hasObject(By.desc("Stop run")))
            assertEquals(1, opened)
        } finally {
            overlay.hide()
            fixture.finish()
        }
    }

    private fun waitForIdle(instrumentation: Instrumentation) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(120L)
    }

    private fun saveScreenshot(device: UiDevice, context: android.content.Context, name: String) {
        val directory = File(context.getExternalFilesDir(null), "overlay-review").apply { mkdirs() }
        val file = File(directory, "$name.png")
        assertTrue(device.takeScreenshot(file))
        assertTrue(file.isFile && file.length() > 0L)
    }
}
