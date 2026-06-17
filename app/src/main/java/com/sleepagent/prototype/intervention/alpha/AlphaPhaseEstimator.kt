package com.sleepagent.prototype.intervention.alpha

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time alpha phase estimator using causal bandpass + endpoint-corrected
 * Hilbert Transform.
 *
 * Pipeline:
 * 1. Continuous EEG at sampleRate Hz
 * 2. Causal bandpass around IAF ± 2 Hz (IIR filter)
 * 3. Endpoint-corrected Hilbert Transform
 * 4. → Instantaneous alpha phase (0 to 2π)
 * 5. → Alpha amplitude
 * 6. → Predict target phase arrival time accounting for delays
 *
 * IMPORTANT: This is an ALPHA_AWARE mode estimator. True phase-locking
 * requires verified low-latency BLE timestamps and continuous EEG.
 *
 * Current implementation provides:
 * - Alpha amplitude (for threshold-based triggering)
 * - Estimated phase (for debugging/monitoring)
 * - Target phase prediction (best-effort, no hardware precision guarantee)
 */
class AlphaPhaseEstimator(
    private val sampleRateHz: Int = 100,
    individualAlphaFrequencyHz: Float = 10.0f
) {
    /** Individual Alpha Frequency (from calibration) */
    var iafHz: Float = individualAlphaFrequencyHz
        private set

    /** Filter group delay in samples */
    val groupDelaySamples: Int get() = filterOrder / 2

    /** Filter group delay in seconds */
    val groupDelaySeconds: Float get() = groupDelaySamples.toFloat() / sampleRateHz

    /** Estimated audio output latency (seconds) */
    var audioOutputLatencySeconds: Float = 0.03f

    /** Current estimated phase [0, 2π) */
    var currentPhase: Float = 0f
        private set

    /** Current alpha amplitude (normalized) */
    var currentAmplitude: Float = 0f
        private set

    /** Current signal quality estimate */
    var signalQuality: Float = 0f
        private set

    // Bandpass filter parameters (causal 2nd-order IIR)
    private val filterOrder = 4
    private var iirDelayLine = FloatArray(filterOrder * 2) // x and y delay

    // Hilbert Transform: FIR approximation
    private val hilbertOrder = 32
    private val hilbertDelayLine = FloatArray(hilbertOrder)
    private var hilbertWriteIdx = 0

    // Analytic signal components
    private var analyticReal = 0f
    private var analyticImag = 0f

    // Amplitude smoothing
    private var amplitudeSmoothed = 0f
    private val amplitudeAlpha = 0.05f // EMA smoothing factor

    // Phase tracking (unwrap friendly)
    private var previousPhase = 0f

    /**
     * Update IAF (e.g., after recalibration).
     */
    fun setIaf(hz: Float) {
        iafHz = hz.coerceIn(8f, 12f)
    }

    /**
     * Feed a new EEG sample and update phase/amplitude estimates.
     */
    fun ingestSample(sample: Float) {
        // 1. Apply causal bandpass filter (IAF ± 2 Hz)
        val filtered = applyBandpass(sample)

        // 2. Update Hilbert FIR delay line
        hilbertDelayLine[hilbertWriteIdx] = filtered
        hilbertWriteIdx = (hilbertWriteIdx + 1) % hilbertOrder

        // 3. Compute analytic signal via Hilbert FIR
        val analytic = computeAnalyticSignal()

        analyticReal = analytic.first
        analyticImag = analytic.second

        // 4. Phase (atan2)
        if (kotlin.math.abs(analyticReal) > 1e-10f || kotlin.math.abs(analyticImag) > 1e-10f) {
            val rawPhase = atan2(analyticImag, analyticReal)
            // Unwrap for continuity
            var diff = rawPhase - previousPhase
            while (diff > PI.toFloat()) diff -= (2f * PI).toFloat()
            while (diff < -PI.toFloat()) diff += (2f * PI).toFloat()
            currentPhase = (previousPhase + diff)
            // Normalize to [0, 2π)
            while (currentPhase < 0f) currentPhase += (2f * PI).toFloat()
            while (currentPhase >= (2f * PI).toFloat()) currentPhase -= (2f * PI).toFloat()
            previousPhase = currentPhase
        }

        // 5. Amplitude (EMA smoothed)
        val amplitude = sqrt(analyticReal * analyticReal + analyticImag * analyticImag)
        amplitudeSmoothed = amplitudeAlpha * amplitude + (1f - amplitudeAlpha) * amplitudeSmoothed
        currentAmplitude = amplitudeSmoothed

        // 6. Simple signal quality from amplitude stability
        val ampQuality = if (amplitudeSmoothed > 1e-6f) {
            (amplitudeSmoothed / (amplitudeSmoothed + 0.5f)).coerceIn(0f, 1f)
        } else 0f
        signalQuality = ampQuality
    }

    /**
     * Predict when the target phase will occur (nanoseconds from now).
     * Accounts for filter group delay + audio output latency.
     *
     * @param targetPhaseRad Target phase in radians [0, 2π)
     * @return Predicted elapsedRealtimeNanos when phase will be at target,
     *         or null if prediction is unreliable.
     */
    fun predictTargetPhaseTime(targetPhaseRad: Float): Long? {
        if (currentAmplitude < 1e-6f) return null

        val angularVelocity = 2f * PI.toFloat() * iafHz // radians/second

        // Phase difference to target (shortest path)
        var phaseDiff = targetPhaseRad - currentPhase
        while (phaseDiff > PI.toFloat()) phaseDiff -= (2f * PI).toFloat()
        while (phaseDiff < -PI.toFloat()) phaseDiff += (2f * PI).toFloat()

        // Time to target (accounting for filter + audio delays)
        val timeToTargetSeconds = phaseDiff / angularVelocity
        val totalDelaySeconds = groupDelaySeconds + audioOutputLatencySeconds
        val adjustedTimeSeconds = timeToTargetSeconds - totalDelaySeconds

        if (adjustedTimeSeconds <= 0f || adjustedTimeSeconds > 0.5f) return null

        val adjustedTimeNanos = (adjustedTimeSeconds * 1_000_000_000L).toLong()

        // Current time from the caller's perspective
        val nowNanos = android.os.SystemClock.elapsedRealtimeNanos()
        return nowNanos + adjustedTimeNanos
    }

    /**
     * Reset estimator state for a new session.
     */
    fun reset() {
        currentPhase = 0f
        currentAmplitude = 0f
        signalQuality = 0f
        analyticReal = 0f
        analyticImag = 0f
        amplitudeSmoothed = 0f
        previousPhase = 0f
        iirDelayLine.fill(0f)
        hilbertDelayLine.fill(0f)
        hilbertWriteIdx = 0
    }

    // ── Private: Bandpass filter ──

    /**
     * Apply causal 2nd-order IIR bandpass (Butterworth-like) around IAF ± 2 Hz.
     */
    private fun applyBandpass(sample: Float): Float {
        val lowHz = (iafHz - 2f).coerceAtLeast(1f)
        val highHz = (iafHz + 2f).coerceAtMost(sampleRateHz / 2.5f)

        // Design a simple 2nd-order bandpass using normalized frequencies
        val w0 = 2f * PI.toFloat() * iafHz / sampleRateHz
        val bw = 2f * PI.toFloat() * (highHz - lowHz) / sampleRateHz
        val cosW0 = cos(w0)
        val alpha = sin(bw) / 2f

        // Biquad coefficients (peaking EQ → bandpass)
        val a0 = 1f + alpha
        val b0 = alpha / a0
        val b1 = 0f
        val b2 = -alpha / a0
        val a1 = (-2f * cosW0) / a0
        val a2 = (1f - alpha) / a0

        // Shift delay line
        for (i in filterOrder - 1 downTo 1) {
            iirDelayLine[i] = iirDelayLine[i - 1]
        }
        iirDelayLine[0] = sample

        // Apply filter
        val y = b0 * iirDelayLine[0] +
                b1 * (if (filterOrder > 1) iirDelayLine[1] else 0f) +
                b2 * (if (filterOrder > 2) iirDelayLine[2] else 0f) -
                a1 * (if (filterOrder > 0) iirDelayLine[filterOrder] else 0f) -
                a2 * (if (filterOrder > 1) iirDelayLine[filterOrder + 1] else 0f)

        // Shift output delay
        for (i in filterOrder * 2 - 1 downTo filterOrder + 1) {
            if (i < iirDelayLine.size) iirDelayLine[i] = iirDelayLine[i - 1]
        }
        if (filterOrder < iirDelayLine.size) iirDelayLine[filterOrder] = y

        return y
    }

    // ── Private: Hilbert Transform ──

    /**
     * Compute analytic signal using FIR Hilbert filter.
     * Returns (real, imaginary) pair.
     */
    private fun computeAnalyticSignal(): Pair<Float, Float> {
        val centerIdx = hilbertOrder / 2
        var imag = 0f

        for (i in 0 until hilbertOrder) {
            val idx = (hilbertWriteIdx + i) % hilbertOrder
            val coeff = hilbertCoeff(i)
            imag += hilbertDelayLine[idx] * coeff
        }

        // Real part is delayed version (center of FIR)
        val realIdx = (hilbertWriteIdx + centerIdx) % hilbertOrder
        val real = hilbertDelayLine[realIdx]

        return real to imag
    }

    /**
     * Ideal discrete Hilbert transform FIR coefficients.
     * H(e^(jω)) = -j*sign(ω) for -π < ω < π
     * h[n] = 0 for n = center, else 2/(π*n) * sin²(π*n/2)
     */
    private fun hilbertCoeff(index: Int): Float {
        val n = index - hilbertOrder / 2
        if (n == 0) return 0f
        if (n % 2 == 0) return 0f // sin²(π*n/2) = 0 for even n

        // Apply Hamming window for reduced side lobes
        val window = 0.54f - 0.46f * cos(2f * PI.toFloat() * index / (hilbertOrder - 1))
        return 2f / (PI.toFloat() * n) * window
    }
}
