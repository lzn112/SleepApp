package com.sleepagent.prototype.intervention.model

/**
 * Types of sound intervention available.
 */
enum class SoundInterventionType {
    /** Background ambient sound (rain, ocean, noise, etc.) */
    BACKGROUND,
    /** Alpha-wave sleep onset intervention */
    ALPHA,
    /** Deep sleep (N3) enhancement */
    DEEP_SLEEP,
    /** Smart wake up */
    SMART_WAKE,
    /** Fallback alarm */
    FALLBACK_ALARM
}

/**
 * Overall state of the sound intervention controller.
 */
enum class SoundInterventionState {
    IDLE,
    BACKGROUND_PLAYING,
    ALPHA_CALIBRATING,
    ALPHA_INTERVENTION,
    MONITORING,
    WAITING_FOR_STABLE_N3,
    N3_STIMULATING,
    N3_OBSERVING,
    SMART_WAKE_WINDOW,
    SMART_WAKING,
    FALLBACK_ALARMING,
    STOPPED
}
