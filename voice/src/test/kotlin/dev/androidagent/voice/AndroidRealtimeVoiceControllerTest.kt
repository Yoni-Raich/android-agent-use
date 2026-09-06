package dev.androidagent.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidRealtimeVoiceControllerTest {
    @Test fun twentyMillisecondPcmFrameReports480Samples() {
        assertEquals(
            480,
            AndroidRealtimeVoiceController.samplesPerChannel(
                AndroidRealtimeVoiceController.FRAME_BYTES,
                AndroidRealtimeVoiceController.CHANNELS,
            ),
        )
    }
}
