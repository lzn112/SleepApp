package com.sleepagent.prototype.intervention.audio

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Runtime pink noise generator using the Voss-McCartney algorithm.
 * Generates 50 ms PCM pulse at 48000 Hz mono, with fade-in/out envelope.
 *
 * Parameters:
 * - sampleRate: 48000 Hz
 * - channels: 1 (mono)
 * - duration: 50 ms (2400 samples)
 * - fadeIn: 5 ms
 * - fadeOut: 5 ms
 * - defaultGain: 0.04
 * - maxGain: 0.20
 */
class PinkNoiseGenerator(
    private val sampleRate: Int = 48000,
    private val pulseDurationMs: Int = 50,
    private val fadeInMs: Int = 5,
    private val fadeOutMs: Int = 5,
    private val defaultGain: Float = 0.04f,
    private val maxGain: Float = 0.20f
) {

    private val numSamples: Int = sampleRate * pulseDurationMs / 1000
    private val fadeInSamples: Int = sampleRate * fadeInMs / 1000
    private val fadeOutSamples: Int = sampleRate * fadeOutMs / 1000

    // Pre-generated pink noise buffer
    private val pinkNoiseBuffer: FloatArray by lazy {
        generatePinkNoise(numSamples)
    }

    /**
     * Generate a pink noise buffer normalized to [-1, 1] with cosine envelope.
     */
    fun generatePulse(gain: Float = defaultGain): FloatArray {
        val effectiveGain = gain.coerceIn(0f, maxGain)
        val result = FloatArray(numSamples)

        for (i in result.indices) {
            var sample = pinkNoiseBuffer[i]

            // Apply fade-in
            if (i < fadeInSamples) {
                val fadeInFactor = 0.5f * (1f - cos(Math.PI * i / fadeInSamples).toFloat())
                sample *= fadeInFactor
            }

            // Apply fade-out
            if (i >= numSamples - fadeOutSamples) {
                val fadeOutIdx = i - (numSamples - fadeOutSamples)
                val fadeOutFactor = 0.5f * (1f + cos(Math.PI * fadeOutIdx / fadeOutSamples).toFloat())
                sample *= fadeOutFactor
            }

            result[i] = (sample * effectiveGain).coerceIn(-1f, 1f)
        }

        return result
    }

    fun getSampleCount(): Int = numSamples
    fun getDurationMs(): Int = pulseDurationMs

    /**
     * Generate pink noise using a simplified Voss-McCartney approach.
     * Uses a fixed seed for reproducibility.
     */
    private fun generatePinkNoise(n: Int): FloatArray {
        val result = FloatArray(n)
        val rng = Random(42) // Fixed seed for reproducibility
        val numOctaves = 7
        val runningSum = FloatArray(numOctaves)

        for (i in result.indices) {
            // Determine which octaves to update based on binary representation
            for (j in 0 until numOctaves) {
                if (i % (1 shl j) == 0) {
                    runningSum[j] = (rng.nextFloat() * 2f - 1f)
                }
            }

            // Sum all octaves
            result[i] = runningSum.sum()
        }

        // Normalize to [-1, 1]
        val maxAbs = result.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        if (maxAbs > 0f) {
            for (i in result.indices) {
                result[i] /= maxAbs
            }
        }

        return result
    }
}
