package dev.androidagent.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidRealtimeVoiceControllerTest {
    @Test fun wiredAndBluetoothRoutesRankBeforePhoneSpeaker() {
        org.junit.Assert.assertTrue(CommunicationAudioRoute.priority(android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET) < CommunicationAudioRoute.priority(android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        org.junit.Assert.assertTrue(CommunicationAudioRoute.priority(android.media.AudioDeviceInfo.TYPE_BLE_HEADSET) < CommunicationAudioRoute.priority(android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
    }
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
