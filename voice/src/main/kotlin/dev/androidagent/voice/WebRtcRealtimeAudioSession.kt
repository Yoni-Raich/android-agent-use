package dev.androidagent.voice

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Internal boundary so the voice controller can keep WebRTC lifecycle testable. */
interface RealtimeMediaSession {
    suspend fun createOffer(): String
    suspend fun setRemoteAnswer(sdp: String)
    suspend fun awaitConnected()
    fun startAudio()
    fun stopAudio()
    fun close()
}

/**
 * Native WebRTC media for Codex realtime.
 *
 * The Java audio device module owns Android microphone capture and speaker
 * playback. The controller still owns permission, foreground-service state,
 * audio focus, and the session stop boundary.
 */
internal class WebRtcRealtimeAudioSession(context: Context) : RealtimeMediaSession {
    private val app = context.applicationContext
    private val closed = AtomicBoolean(false)
    private val iceGathered = CompletableDeferred<Unit>()
    private val connected = CompletableDeferred<Unit>()

    private val audioDeviceModule: AudioDeviceModule
    private val factory: PeerConnectionFactory
    private val audioSource: org.webrtc.AudioSource
    private val localAudioTrack: AudioTrack
    private val dataChannel: DataChannel
    private val peer: PeerConnection

    init {
        initializeWebRtc(app)
        audioDeviceModule = JavaAudioDeviceModule.builder(app)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
        audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("codex-local-audio", audioSource)
        localAudioTrack.setEnabled(false)

        val configuration = PeerConnection.RTCConfiguration(emptyList())
            .also { it.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        peer = checkNotNull(factory.createPeerConnection(configuration, newObserver())) {
            "WebRTC could not create a peer connection."
        }
        checkNotNull(peer.addTrack(localAudioTrack, listOf("codex-audio"))) {
            "WebRTC could not add the microphone track."
        }
        dataChannel = checkNotNull(peer.createDataChannel("oai-events", DataChannel.Init())) {
            "WebRTC could not create the realtime events channel."
        }
    }

    override suspend fun createOffer(): String {
        checkOpen()
        val offer = suspendCancellableCoroutine<SessionDescription> { continuation ->
            peer.createOffer(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) {
                    continuation.resume(description)
                }

                override fun onSetSuccess() = Unit

                override fun onCreateFailure(error: String) {
                    continuation.resumeWithException(
                        IllegalStateException("WebRTC could not create an offer: $error")
                    )
                }

                override fun onSetFailure(error: String) = Unit
            }, MediaConstraints())
        }

        suspendCancellableCoroutine<Unit> { continuation ->
            peer.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) = Unit

                override fun onSetSuccess() {
                    continuation.resume(Unit)
                }

                override fun onCreateFailure(error: String) = Unit

                override fun onSetFailure(error: String) {
                    continuation.resumeWithException(
                        IllegalStateException("WebRTC could not set the local offer: $error")
                    )
                }
            }, offer)
        }

        if (peer.iceGatheringState() != PeerConnection.IceGatheringState.COMPLETE) {
            withTimeout(ICE_GATHERING_TIMEOUT_MS) { iceGathered.await() }
        }
        return checkNotNull(peer.localDescription?.description?.takeIf { it.isNotBlank() }) {
            "WebRTC did not produce a local SDP offer."
        }
    }

    override suspend fun setRemoteAnswer(sdp: String) {
        checkOpen()
        require(sdp.isNotBlank()) { "WebRTC SDP answer is empty." }
        suspendCancellableCoroutine<Unit> { continuation ->
            peer.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) = Unit

                override fun onSetSuccess() {
                    continuation.resume(Unit)
                }

                override fun onCreateFailure(error: String) = Unit

                override fun onSetFailure(error: String) {
                    continuation.resumeWithException(
                        IllegalStateException("WebRTC could not set the remote answer: $error")
                    )
                }
            }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
        }
    }

    override suspend fun awaitConnected() {
        checkOpen()
        withTimeout(CONNECTION_TIMEOUT_MS) { connected.await() }
    }

    override fun startAudio() {
        checkOpen()
        audioDeviceModule.setMicrophoneMute(false)
        audioDeviceModule.setSpeakerMute(false)
        localAudioTrack.setEnabled(true)
    }

    override fun stopAudio() {
        if (closed.get()) return
        audioDeviceModule.setMicrophoneMute(true)
        audioDeviceModule.setSpeakerMute(true)
        localAudioTrack.setEnabled(false)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        connected.cancel()
        iceGathered.cancel()
        runCatching { audioDeviceModule.setMicrophoneMute(true) }
        runCatching { audioDeviceModule.setSpeakerMute(true) }
        runCatching { dataChannel.close() }
        runCatching { localAudioTrack.setEnabled(false) }
        runCatching { peer.close() }
        runCatching { peer.dispose() }
        runCatching { localAudioTrack.dispose() }
        runCatching { audioSource.dispose() }
        runCatching { audioDeviceModule.release() }
        runCatching { factory.dispose() }
    }

    private fun checkOpen() {
        check(!closed.get()) { "WebRTC voice session is closed." }
    }

    private fun newObserver(): PeerConnection.Observer = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            when (newState) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> connected.complete(Unit)
                PeerConnection.IceConnectionState.FAILED -> connected.completeExceptionally(
                    IllegalStateException("WebRTC ICE negotiation failed.")
                )
                else -> Unit
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
            if (newState == PeerConnection.IceGatheringState.COMPLETE) iceGathered.complete(Unit)
        }

        override fun onIceCandidate(candidate: IceCandidate) = Unit

        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit

        override fun onAddStream(stream: MediaStream) {
            stream.audioTracks.forEach { it.setEnabled(true) }
        }

        override fun onRemoveStream(stream: MediaStream) = Unit

        override fun onDataChannel(channel: DataChannel) {
            // The client-created oai-events channel is used by the upstream
            // realtime service. App-server sideband events remain on JSON-RPC.
        }

        override fun onRenegotiationNeeded() = Unit

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
            (receiver.track() as? AudioTrack)?.setEnabled(true)
        }
    }

    private companion object {
        private const val ICE_GATHERING_TIMEOUT_MS = 15_000L
        private const val CONNECTION_TIMEOUT_MS = 30_000L
        private val INITIALIZATION_LOCK = Any()
        private var initialized = false

        private fun initializeWebRtc(context: Context) {
            synchronized(INITIALIZATION_LOCK) {
                if (!initialized) {
                    PeerConnectionFactory.initialize(
                        PeerConnectionFactory.InitializationOptions.builder(context)
                            .createInitializationOptions()
                    )
                    initialized = true
                }
            }
        }
    }
}
