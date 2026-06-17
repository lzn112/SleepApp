package com.sleepagent.prototype.intervention.alpha

import com.sleepagent.prototype.intervention.model.AlphaCalibrationResult
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Alpha calibration engine for finding Individual Alpha Frequency (IAF).
 *
 * Guides user through a 2-3 minute closed-eye resting calibration:
 * 1. Check headband connection
 * 2. Check EEG signal quality
 * 3. Collect closed-eye EEG
 * 4. Remove DC offset
 * 5. Screen artifacts
 * 6. Compute 8-12 Hz PSD
 * 7. Search for individual alpha peak → IAF
 *
 * Calibration success requires:
 * - EEG quality >= threshold
 * - Clear local peak in 8-12 Hz band
 * - Peak prominence >= threshold
 * - Sufficient valid data duration
 */
class AlphaCalibrationEngine(
    private val signalQualityEvaluator: AlphaSignalQualityEvaluator = AlphaSignalQualityEvaluator()
) {

    companion object {
        const val MIN_DATA_DURATION_SECONDS = 60
        const val TARGET_DATA_DURATION_SECONDS = 120
        const val MIN_PEAK_PROMINENCE = 0.15f
        const val MIN_SIGNAL_QUALITY = 0.70f
        const val ALPHA_LOW_HZ = 8.0f
        const val ALPHA_HIGH_HZ = 12.0f
        const val ALGORITHM_VERSION = "1.0"
    }

    private val collectedSamples = mutableListOf<Float>()
    private var sampleRateHz: Int = 100
    private var totalDurationMs: Long = 0L

    /**
     * Feed a downsampled EEG sample into the calibration collector.
     * Returns true if calibration data collection is complete.
     */
    fun ingestSample(sample: Float): Boolean {
        collectedSamples.add(sample)
        totalDurationMs = (collectedSamples.size * 1000L) / sampleRateHz
        return totalDurationMs >= TARGET_DATA_DURATION_SECONDS * 1000L
    }

    /**
     * Run calibration on collected data.
     * Returns [AlphaCalibrationResult] with IAF if successful.
     */
    fun calibrate(): AlphaCalibrationResult {
        if (collectedSamples.size < MIN_DATA_DURATION_SECONDS * sampleRateHz) {
            return AlphaCalibrationResult(
                individualAlphaFrequencyHz = null,
                peakPower = null,
                peakProminence = null,
                signalQuality = 0f,
                success = false,
                failureReason = "数据不足: 需要至少 ${MIN_DATA_DURATION_SECONDS}秒，当前 ${collectedSamples.size / sampleRateHz}秒"
            )
        }

        val samples = collectedSamples.toFloatArray()
        val signalQuality = signalQualityEvaluator.evaluate(samples, sampleRateHz)

        if (signalQuality < MIN_SIGNAL_QUALITY) {
            return AlphaCalibrationResult(
                individualAlphaFrequencyHz = null,
                peakPower = null,
                peakProminence = null,
                signalQuality = signalQuality,
                success = false,
                failureReason = "信号质量不足: ${"%.2f".format(signalQuality)} < ${MIN_SIGNAL_QUALITY}"
            )
        }

        // Separate into artifact-free windows
        val cleanWindows = extractCleanWindows(samples, sampleRateHz)
        if (cleanWindows.isEmpty()) {
            return AlphaCalibrationResult(
                individualAlphaFrequencyHz = null,
                peakPower = null,
                peakProminence = null,
                signalQuality = signalQuality,
                success = false,
                failureReason = "无有效数据窗口，伪迹过多"
            )
        }

        // Compute average PSD across windows
        val psd = computeAveragePsd(cleanWindows, sampleRateHz)

        // Search for alpha peak in 8-12 Hz
        val peak = findAlphaPeak(psd, sampleRateHz)

        return if (peak != null && peak.prominence >= MIN_PEAK_PROMINENCE) {
            AlphaCalibrationResult(
                individualAlphaFrequencyHz = peak.frequencyHz,
                peakPower = peak.power,
                peakProminence = peak.prominence,
                signalQuality = signalQuality,
                success = true,
                failureReason = null
            )
        } else {
            AlphaCalibrationResult(
                individualAlphaFrequencyHz = peak?.frequencyHz,
                peakPower = peak?.power,
                peakProminence = peak?.prominence ?: 0f,
                signalQuality = signalQuality,
                success = false,
                failureReason = if (peak == null) {
                    "8-12 Hz 频段未检测到 α 峰值"
                } else {
                    "α 峰值突出度不足: ${"%.3f".format(peak.prominence)} < ${MIN_PEAK_PROMINENCE}"
                }
            )
        }
    }

    /**
     * Reset calibration data collector.
     */
    fun reset(sampleRateHz: Int = 100) {
        collectedSamples.clear()
        this.sampleRateHz = sampleRateHz
        totalDurationMs = 0L
    }

    /**
     * Get current calibration progress [0, 1].
     */
    val progress: Float
        get() = (totalDurationMs.toFloat() / (TARGET_DATA_DURATION_SECONDS * 1000f)).coerceIn(0f, 1f)

    /**
     * Extract clean (artifact-free) 2-second windows from the data.
     */
    private fun extractCleanWindows(samples: FloatArray, sampleRateHz: Int): List<FloatArray> {
        val windowSize = sampleRateHz * 2 // 2-second windows
        val windows = mutableListOf<FloatArray>()

        var i = 0
        while (i + windowSize <= samples.size) {
            val window = samples.copyOfRange(i, i + windowSize)
            val quality = signalQualityEvaluator.evaluate(window, sampleRateHz)
            if (quality >= MIN_SIGNAL_QUALITY) {
                windows.add(window)
            }
            i += windowSize / 2 // 50% overlap
        }
        return windows
    }

    /**
     * Compute average PSD using Welch-like method (simple FFT averaging).
     */
    private fun computeAveragePsd(windows: List<FloatArray>, sampleRateHz: Int): FloatArray {
        if (windows.isEmpty()) return FloatArray(0)

        val n = nextPowerOf2(windows[0].size)
        val halfN = n / 2
        val avgPsd = FloatArray(halfN)

        for (window in windows) {
            // Apply Hann window
            val windowed = FloatArray(n)
            for (j in window.indices) {
                val hann = 0.5f * (1f - cos(2.0 * PI * j / (window.size - 1)).toFloat())
                windowed[j] = window[j] * hann
            }

            // Simple DFT (non-FFT for simplicity)
            val psd = computeDftMagnitude(windowed)
            for (j in avgPsd.indices) {
                avgPsd[j] += psd[j]
            }
        }

        // Average
        for (j in avgPsd.indices) {
            avgPsd[j] /= windows.size.toFloat()
        }
        return avgPsd
    }

    /**
     * Compute DFT magnitude squared for a real signal.
     * Returns power for each frequency bin up to Nyquist.
     */
    private fun computeDftMagnitude(signal: FloatArray): FloatArray {
        val n = signal.size
        val halfN = n / 2
        val magnitude = FloatArray(halfN)

        for (k in 0 until halfN) {
            var realSum = 0.0
            var imagSum = 0.0
            for (t in signal.indices) {
                val angle = 2.0 * PI * k * t / n
                realSum += signal[t] * cos(angle)
                imagSum -= signal[t] * kotlin.math.sin(angle)
            }
            magnitude[k] = ((realSum * realSum + imagSum * imagSum) / (n * n)).toFloat()
        }
        return magnitude
    }

    /**
     * Find the alpha peak (8-12 Hz) in the PSD.
     */
    private fun findAlphaPeak(psd: FloatArray, sampleRateHz: Int): PeakInfo? {
        if (psd.isEmpty()) return null

        val freqResolution = sampleRateHz.toFloat() / (psd.size * 2)
        val lowBin = (ALPHA_LOW_HZ / freqResolution).toInt().coerceIn(0, psd.size - 1)
        val highBin = (ALPHA_HIGH_HZ / freqResolution).toInt().coerceIn(lowBin, psd.size - 1)

        if (highBin <= lowBin) return null

        // Find maximum in alpha band
        var maxIdx = lowBin
        var maxVal = psd[lowBin]
        for (i in lowBin..highBin) {
            if (psd[i] > maxVal) {
                maxVal = psd[i]
                maxIdx = i
            }
        }

        if (maxVal <= 0f) return null

        // Calculate prominence (difference from surrounding baseline)
        val baseline = estimateBaseline(psd, lowBin, highBin)
        val prominence = if (baseline > 0f) (maxVal - baseline) / maxVal else 0f

        return PeakInfo(
            frequencyHz = maxIdx * freqResolution,
            power = maxVal,
            prominence = prominence.coerceIn(0f, 1f)
        )
    }

    /**
     * Estimate PSD baseline in alpha band using left/right edge average.
     */
    private fun estimateBaseline(psd: FloatArray, lowBin: Int, highBin: Int): Float {
        val leftEdge = if (lowBin > 0) psd[lowBin] else psd[0]
        val rightEdge = if (highBin < psd.size) psd[highBin] else psd[psd.size - 1]
        return (leftEdge + rightEdge) / 2f
    }

    private fun nextPowerOf2(n: Int): Int {
        var v = 1
        while (v < n) v = v shl 1
        return v
    }

    data class PeakInfo(
        val frequencyHz: Float,
        val power: Float,
        val prominence: Float
    )
}
