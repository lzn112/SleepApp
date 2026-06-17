package com.sleepagent.prototype.intervention.model

/**
 * Alpha intervention operation mode.
 */
enum class AlphaInterventionMode {
    /** High-precision phase-locked stimulation */
    PHASE_LOCKED,
    /** Alpha-aware (amplitude-based) without phase locking */
    ALPHA_AWARE,
    /** Sham (control) mode */
    SHAM,
    /** Disabled */
    DISABLED
}

/**
 * Alpha intervention configuration.
 */
data class AlphaInterventionConfig(
    val enabled: Boolean = false,
    val targetPhaseDeg: Float = 0f,
    val phaseToleranceDeg: Float = 30f,
    val pulseGain: Float = 0.04f,
    val maxDurationMinutes: Int = 30,
    val minimumAlphaAmplitude: Float = 0.3f,
    val minimumSignalQuality: Float = 0.70f,
    val refractoryPeriodMs: Long = 2000L,
    val mode: AlphaInterventionMode = AlphaInterventionMode.ALPHA_AWARE
)

/**
 * Result of an alpha calibration session.
 */
data class AlphaCalibrationResult(
    val individualAlphaFrequencyHz: Float?,
    val peakPower: Float?,
    val peakProminence: Float?,
    val signalQuality: Float,
    val success: Boolean,
    val failureReason: String? = null
)

/**
 * Alpha intervention live state during a sleep session.
 */
data class AlphaInterventionState(
    val enabled: Boolean = false,
    val mode: AlphaInterventionMode = AlphaInterventionMode.ALPHA_AWARE,
    val isCalibrated: Boolean = false,
    val individualAlphaFrequencyHz: Float? = null,
    val calibrationQuality: Float = 0f,
    val isActive: Boolean = false,
    val pulseCount: Int = 0,
    val skipCount: Int = 0,
    val lastSkipReason: String? = null,
    val elapsedSinceStartMs: Long = 0
)
