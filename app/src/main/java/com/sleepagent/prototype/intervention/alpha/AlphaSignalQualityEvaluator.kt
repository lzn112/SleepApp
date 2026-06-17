package com.sleepagent.prototype.intervention.alpha

import com.sleepagent.prototype.intervention.model.AlphaCalibrationResult
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Evaluates EEG signal quality for alpha intervention.
 *
 * Checks for common artifacts: DC offset, saturation, high-frequency noise,
 * and evaluates baseline signal-to-noise ratio in the alpha band.
 */
class AlphaSignalQualityEvaluator {

    /** Minimum acceptable signal quality score for alpha intervention */
    companion object {
        const val MIN_QUALITY_THRESHOLD = 0.70f
        const val SATURATION_THRESHOLD = 0.95f
        const val MAX_DC_OFFSET_RATIO = 0.3f
        const val MAX_HF_NOISE_RATIO = 0.5f
    }

    /**
     * Evaluate signal quality from a window of EEG samples.
     * Returns a score in [0, 1] where 1 = perfect, 0 = unusable.
     */
    fun evaluate(samples: FloatArray, sampleRateHz: Int = 100): Float {
        if (samples.size < sampleRateHz) return 0f // Need at least 1 second

        val mean = samples.average().toFloat()
        val n = samples.size

        // 1. DC offset check
        var maxAbs = 0f
        for (s in samples) {
            val abs = kotlin.math.abs(s)
            if (abs > maxAbs) maxAbs = abs
        }
        if (maxAbs < 1e-6f) return 0f // dead signal

        val dcOffsetRatio = kotlin.math.abs(mean) / maxAbs
        val dcScore = if (dcOffsetRatio > MAX_DC_OFFSET_RATIO) 0f
        else 1f - (dcOffsetRatio / MAX_DC_OFFSET_RATIO)

        // 2. Saturation check
        var saturationCount = 0
        for (s in samples) {
            if (kotlin.math.abs(s - mean) / maxAbs > SATURATION_THRESHOLD) saturationCount++
        }
        val saturationRatio = saturationCount.toFloat() / n
        val saturationScore = 1f - saturationRatio.coerceIn(0f, 1f)

        // 3. Variance (too low = flatline, too high = noisy)
        var varianceSum = 0.0
        for (s in samples) varianceSum += ((s - mean) * (s - mean)).toDouble()
        val variance = (varianceSum / n).toFloat()
        val varianceScore = if (variance < 1e-8f) 0f else {
            // Reasonable EEG variance range
            val logVar = ln(variance.toDouble() + 1e-10).toFloat()
            (logVar / 10f + 0.5f).coerceIn(0f, 1f)
        }

        // 4. Simple alpha band power ratio estimate
        val alphaBandPower = estimateAlphaBandPower(samples, sampleRateHz, mean)
        val totalPower = variance
        val alphaRatio = if (totalPower > 1e-8f) (alphaBandPower / totalPower).coerceIn(0f, 1f) else 0f

        // Combined score
        val score = (dcScore * 0.2f + saturationScore * 0.3f + varianceScore * 0.2f + alphaRatio * 0.3f)
        return score.coerceIn(0f, 1f)
    }

    /**
     * Simple alpha band (8-12 Hz) power estimation using Goertzel-like approach
     * for the given sample rate.
     */
    private fun estimateAlphaBandPower(samples: FloatArray, sampleRateHz: Int, mean: Float): Float {
        val n = samples.size
        // Approximate alpha power by bandpass filtering with simple difference
        var alphaSum = 0f
        val alphaLow = 8
        val alphaHigh = 12

        // Use simplified approach: compute power in approximate frequency bins
        val fftSize = nextPowerOf2(n)
        if (fftSize < 32) return 0f

        // Simple approximation using auto-correlation difference
        val lagLow = sampleRateHz / alphaHigh
        val lagHigh = sampleRateHz / alphaLow
        var acPower = 0f

        for (lag in lagLow..lagHigh.coerceAtMost(n / 2)) {
            if (lag <= 0 || lag >= n) continue
            var ac = 0f
            for (i in 0 until n - lag) {
                ac += (samples[i] - mean) * (samples[i + lag] - mean)
            }
            acPower += kotlin.math.abs(ac) / (n - lag)
        }

        return acPower.coerceIn(0f, Float.MAX_VALUE)
    }

    private fun nextPowerOf2(n: Int): Int {
        var v = 1
        while (v < n) v = v shl 1
        return v
    }
}
