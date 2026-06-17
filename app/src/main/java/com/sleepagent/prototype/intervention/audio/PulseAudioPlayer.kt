package com.sleepagent.prototype.intervention.audio

/**
 * Result of scheduling or playing a pulse.
 */
sealed class PulsePlaybackResult {
    data object Scheduled : PulsePlaybackResult()
    data object Playing : PulsePlaybackResult()
    data class Skipped(val reason: String) : PulsePlaybackResult()
    data class Error(val message: String) : PulsePlaybackResult()
}

/**
 * Low-latency pulse audio player for alpha/N3 intervention.
 * Uses AudioTrack for compatibility; Oboe can be added later.
 */
interface PulseAudioPlayer {

    /** Initialize audio resources. Called once before first use. */
    fun prepare()

    /**
     * Schedule a pulse at a specific time (elapsedRealtimeNanos).
     * Returns [PulsePlaybackResult.Scheduled] if successfully queued.
     */
    fun schedulePulse(targetElapsedRealtimeNanos: Long, gain: Float): PulsePlaybackResult

    /**
     * Play a pulse immediately.
     */
    fun playPulseNow(gain: Float): PulsePlaybackResult

    /**
     * Stop any currently playing or pending pulse.
     */
    fun stop()

    /**
     * Release all audio resources.
     */
    fun release()

    /** Whether a pulse is currently playing or scheduled. */
    val isActive: Boolean
}
