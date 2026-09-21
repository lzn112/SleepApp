package com.sleepagent.prototype.intervention.alpha

import com.sleepagent.prototype.data.SleepStage
import com.sleepagent.prototype.intervention.model.AlphaInterventionConfig
import com.sleepagent.prototype.intervention.model.AlphaInterventionMode
import com.sleepagent.prototype.intervention.model.AlphaInterventionState
import com.sleepagent.prototype.intervention.model.RealtimeSleepSnapshot
import android.os.SystemClock

/**
 * Scheduler for alpha intervention pulse triggering.
 *
 * Evaluates all trigger conditions:
 * - alphaInterventionEnabled = true
 * - Within first 30 minutes of sleep onset
 * - Sleep stage: AWAKE, closed-eye awake, or LIGHT (N1)
 * - Not in stable N2
 * - EEG quality >= 0.70
 * - Alpha amplitude >= individual threshold
 * - No significant motion
 * - Device connected
 * - Not in N3 intervention or smart wake
 * - Previous pulse completed
 * - Refractory period satisfied
 */
class AlphaTriggerScheduler(
    private var config: AlphaInterventionConfig = AlphaInterventionConfig(),
    private val phaseEstimator: AlphaPhaseEstimator = AlphaPhaseEstimator()
) {

    /** Current alpha intervention state */
    val state: AlphaInterventionState
        get() = AlphaInterventionState(
            enabled = config.enabled,
            mode = config.mode,
            isCalibrated = isCalibrated,
            individualAlphaFrequencyHz = phaseEstimator.iafHz,
            isActive = isActive,
            pulseCount = pulseCount,
            skipCount = skipCount,
            lastSkipReason = lastSkipReason,
            elapsedSinceStartMs = if (startNanos > 0)
                (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L else 0
        )

    var isCalibrated: Boolean = false
        private set
    var isActive: Boolean = false
        private set

    var pulseCount: Int = 0
        private set
    var skipCount: Int = 0
        private set
    var lastSkipReason: String? = null
        private set

    var targetPhaseDeg: Float = 0f

    private var startNanos: Long = 0L
    private var lastPulseNanos: Long = 0L

    /**
     * Feed a new EEG sample to update phase estimation.
     */
    fun ingestSample(sample: Float) {
        phaseEstimator.ingestSample(sample)
    }

    fun updateConfig(config: AlphaInterventionConfig) {
        this.config = config
    }

    /**
     * Set IAF from calibration result.
     */
    fun setCalibration(hz: Float) {
        phaseEstimator.setIaf(hz)
        isCalibrated = true
    }

    /**
     * Start alpha intervention (called when sleep recording begins).
     */
    fun start() {
        startNanos = SystemClock.elapsedRealtimeNanos()
        pulseCount = 0
        skipCount = 0
        lastSkipReason = null
        isActive = true
        phaseEstimator.reset()
    }

    /**
     * Stop alpha intervention.
     */
    fun stop(reason: String) {
        isActive = false
        lastSkipReason = reason
    }

    /**
     * Evaluate whether an alpha pulse should be triggered.
     *
     * @param snapshot Latest real-time sleep snapshot
     * @param stableStageMs Milliseconds the current stage has been stable
     * @return Recommended pulse gain (Float) if pulse should trigger, null otherwise
     */
    fun evaluateTrigger(
        snapshot: RealtimeSleepSnapshot,
        stableStageMs: Long
    ): Float? {
        if (!isActive) {
            lastSkipReason = "Not active"
            skipCount++
            return null
        }

        if (!config.enabled) {
            lastSkipReason = "Disabled"
            skipCount++
            return null
        }

        if (config.mode == AlphaInterventionMode.DISABLED ||
            config.mode == AlphaInterventionMode.SHAM) {
            lastSkipReason = "Mode: ${config.mode.name}"
            skipCount++
            return null
        }

        // 1. Sleep stage check
        if (snapshot.sleepStage != SleepStage.AWAKE && snapshot.sleepStage != SleepStage.LIGHT) {
            lastSkipReason = "Stage: ${snapshot.sleepStage.name}"
            skipCount++
            return null
        }

        // 2. EEG quality check
        if (snapshot.eegQuality < config.minimumSignalQuality) {
            lastSkipReason = "EEG quality: ${"%.2f".format(snapshot.eegQuality)}"
            skipCount++
            return null
        }

        // 3. Motion check
        if (snapshot.motionLevel != null && snapshot.motionLevel > 0.3f) {
            lastSkipReason = "Motion: ${"%.2f".format(snapshot.motionLevel)}"
            skipCount++
            return null
        }

        // 4. Device connection check
        if (!snapshot.isDeviceConnected) {
            lastSkipReason = "Device disconnected"
            skipCount++
            return null
        }

        // 5. Refractory period check
        val now = SystemClock.elapsedRealtimeNanos()
        val sinceLastPulse = (now - lastPulseNanos) / 1_000_000L
        if (sinceLastPulse < config.refractoryPeriodMs && lastPulseNanos > 0) {
            lastSkipReason = "Refractory: ${sinceLastPulse}ms"
            skipCount++
            return null
        }

        // 6. Alpha amplitude check (for ALPHA_AWARE mode)
        if (config.mode == AlphaInterventionMode.ALPHA_AWARE ||
            config.mode == AlphaInterventionMode.PHASE_LOCKED) {
            if (phaseEstimator.currentAmplitude < config.minimumAlphaAmplitude) {
                lastSkipReason = "Alpha amplitude: ${"%.3f".format(phaseEstimator.currentAmplitude)}"
                skipCount++
                return null
            }
        }

        // 7. Max duration check
        val elapsedMs = (now - startNanos) / 1_000_000L
        if (elapsedMs > config.maxDurationMinutes * 60_000L) {
            lastSkipReason = "Max duration exceeded"
            skipCount++
            return null
        }

        // All checks passed
        return config.pulseGain
    }

    /**
     * Record that a pulse was triggered.
     */
    fun onPulseTriggered(gain: Float) {
        pulseCount++
        lastPulseNanos = SystemClock.elapsedRealtimeNanos()
        lastSkipReason = null
    }

    /**
     * Record that a pulse was skipped externally.
     */
    fun onPulseSkipped(reason: String) {
        skipCount++
        lastSkipReason = reason
    }

    /**
     * Reset scheduler state for a new session.
     */
    fun reset() {
        pulseCount = 0
        skipCount = 0
        lastSkipReason = null
        startNanos = 0L
        lastPulseNanos = 0L
        isActive = false
        isCalibrated = false
        phaseEstimator.reset()
    }

    /**
     * Get current phase info for debugging.
     */
    fun debugInfo(): Map<String, Any> = mapOf(
        "phase_rad" to phaseEstimator.currentPhase,
        "phase_deg" to (phaseEstimator.currentPhase * 180f / Math.PI.toFloat()),
        "amplitude" to phaseEstimator.currentAmplitude,
        "iaf_hz" to phaseEstimator.iafHz,
        "signal_quality" to phaseEstimator.signalQuality,
        "pulse_count" to pulseCount,
        "skip_count" to skipCount,
        "last_skip_reason" to (lastSkipReason ?: "none"),
        "active" to isActive
    )
}
