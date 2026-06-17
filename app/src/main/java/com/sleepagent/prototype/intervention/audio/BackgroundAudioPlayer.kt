package com.sleepagent.prototype.intervention.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Playback state for background audio.
 */
data class AudioPlaybackState(
    val isPlaying: Boolean = false,
    val currentSoundKey: SleepSoundKey? = null,
    val currentGain: Float = 0f,
    val isPaused: Boolean = false
)

/**
 * Interface for background audio playback used during sleep intervention.
 * Implementations handle ExoPlayer/MediaSession lifecycle.
 */
interface BackgroundAudioPlayer {

    val state: StateFlow<AudioPlaybackState>

    /**
     * Start looping playback of [resourceId] at [gain] (0.0 .. 1.0).
     */
    suspend fun playLoop(resourceId: Int, gain: Float)

    /**
     * Play [resourceId] once at [gain].
     */
    suspend fun playOnce(resourceId: Int, gain: Float)

    /**
     * Switch to a new looping sound with crossfade.
     */
    suspend fun switchLoop(resourceId: Int, gain: Float, crossFadeDurationMs: Long)

    /**
     * Gradually fade volume to [targetGain] over [durationMs].
     */
    suspend fun fadeTo(targetGain: Float, durationMs: Long)

    fun pause()

    fun stop()

    fun release()
}
