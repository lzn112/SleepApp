package com.sleepagent.prototype.intervention.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * AudioTrack-based pulse player for 50 ms pink noise pulses.
 * Uses a continuous low-latency audio stream mode.
 */
class AudioTrackPulseAudioPlayer(
    private val sampleRate: Int = 48000,
    private val generator: PinkNoiseGenerator = PinkNoiseGenerator()
) : PulseAudioPlayer {

    private val _isActive = MutableStateFlow(false)
    override val isActive: Boolean
        get() = _isActive.value

    private var audioTrack: AudioTrack? = null
    private var preparedBuffer: ShortArray? = null
    private var lastPulseTimeNanos: Long = 0L
    private val minIntervalNanos: Long = 500_000_000L

    override fun prepare() {
        if (audioTrack != null) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val pulseSamples = generator.getSampleCount()
        val totalBufferSize = maxOf(
            if (minBufferSize > 0) minBufferSize else 0,
            pulseSamples * 2
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(totalBufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            preparedBuffer = null
            return
        }

        audioTrack = track

        val floatBuffer = generator.generatePulse()
        preparedBuffer = ShortArray(floatBuffer.size) { index ->
            (floatBuffer[index] * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    override fun schedulePulse(targetElapsedRealtimeNanos: Long, gain: Float): PulsePlaybackResult {
        val now = SystemClock.elapsedRealtimeNanos()
        if (now - lastPulseTimeNanos < minIntervalNanos) {
            return PulsePlaybackResult.Skipped("Refractory period; last pulse too recent")
        }
        val delayNanos = targetElapsedRealtimeNanos - now
        if (delayNanos < 0) {
            return PulsePlaybackResult.Skipped("Target time already passed")
        }
        return playPulseNow(gain)
    }

    override fun playPulseNow(gain: Float): PulsePlaybackResult {
        val track = audioTrack ?: return PulsePlaybackResult.Error("AudioTrack not initialized")
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            return PulsePlaybackResult.Error("AudioTrack is not ready")
        }
        val buffer = preparedBuffer ?: return PulsePlaybackResult.Error("Buffer not prepared")

        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            return PulsePlaybackResult.Skipped("Previous pulse still playing")
        }

        val effectiveGain = gain.coerceIn(0f, 0.20f)
        val gainBuffer = ShortArray(buffer.size) { index ->
            (buffer[index] * effectiveGain)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }

        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.reloadStaticData()
        }
        track.write(gainBuffer, 0, gainBuffer.size)
        track.play()
        lastPulseTimeNanos = SystemClock.elapsedRealtimeNanos()
        _isActive.value = true

        return PulsePlaybackResult.Playing
    }

    override fun stop() {
        val track = audioTrack ?: return
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            _isActive.value = false
            return
        }

        runCatching {
            when (track.playState) {
                AudioTrack.PLAYSTATE_PLAYING -> {
                    track.pause()
                    track.flush()
                }
                AudioTrack.PLAYSTATE_PAUSED -> track.flush()
            }
        }
        _isActive.value = false
    }

    override fun release() {
        audioTrack?.let { track ->
            runCatching {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    when (track.playState) {
                        AudioTrack.PLAYSTATE_PLAYING -> track.stop()
                        AudioTrack.PLAYSTATE_PAUSED -> track.flush()
                    }
                }
            }
            runCatching { track.release() }
        }
        audioTrack = null
        preparedBuffer = null
        _isActive.value = false
    }
}
