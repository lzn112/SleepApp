package com.sleepagent.prototype.intervention.audio

import kotlinx.coroutines.delay

/**
 * Utility for volume ramp calculations.
 */
object VolumeRamp {

    /**
     * Calculate linear volume ramp values from [startGain] to [endGain] over [durationMs].
     * Returns a list of (elapsedMs, gain) pairs.
     */
    fun linearRamp(
        startGain: Float,
        endGain: Float,
        durationMs: Long,
        steps: Int = 20
    ): List<Pair<Long, Float>> {
        if (durationMs <= 0 || steps <= 0) return listOf(durationMs to endGain)
        val stepMs = durationMs / steps
        return (1..steps).map { step ->
            val elapsed = step * stepMs
            val fraction = step.toFloat() / steps
            val gain = startGain + (endGain - startGain) * fraction
            elapsed to gain
        }
    }

    /**
     * Execute a volume ramp by calling [setVolume] at each step.
     */
    suspend fun executeRamp(
        startGain: Float,
        endGain: Float,
        durationMs: Long,
        setVolume: suspend (Float) -> Unit
    ) {
        if (durationMs <= 0) {
            setVolume(endGain)
            return
        }
        val steps = 20
        val stepMs = durationMs / steps
        for (i in 1..steps) {
            val fraction = i.toFloat() / steps
            val gain = startGain + (endGain - startGain) * fraction
            setVolume(gain.coerceIn(0f, 1f))
            delay(stepMs)
        }
        setVolume(endGain.coerceIn(0f, 1f))
    }
}

/**
 * Default background audio configuration.
 */
data class BackgroundAudioDefaults(
    val defaultSoundKey: SleepSoundKey = SleepSoundKey.RAIN_GENTLE,
    val defaultGain: Float = 0.12f,
    val maxPlayDurationMinutes: Int = 30,
    val fadeOutAfterSleepMinutes: Int = 60,
    val fadeOutDurationSeconds: Int = 60
)
