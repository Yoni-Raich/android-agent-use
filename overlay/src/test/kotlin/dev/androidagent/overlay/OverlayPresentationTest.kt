package dev.androidagent.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPresentationTest {
    @Test fun lifecycleStatusesUseDistinctTones() {
        assertEquals(OverlayTone.ACTIVE, overlayTone("Starting"))
        assertEquals(OverlayTone.ACTIVE, overlayTone("Thinking"))
        assertEquals(OverlayTone.ACTIVE, overlayTone("Running · read ui"))
        assertEquals(OverlayTone.CONTROLLING, overlayTone("Controlling · tap"))
        assertEquals(OverlayTone.STOPPING, overlayTone("Stopping"))
        assertEquals(OverlayTone.DONE, overlayTone("Done · Stopped"))
        assertEquals(OverlayTone.ERROR, overlayTone("Error · Overlay permission missing"))
    }
}
