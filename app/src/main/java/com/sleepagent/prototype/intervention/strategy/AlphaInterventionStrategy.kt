package com.sleepagent.prototype.intervention.strategy

import com.sleepagent.prototype.intervention.model.AlphaInterventionConfig
import com.sleepagent.prototype.intervention.model.AlphaInterventionMode
import com.sleepagent.prototype.intervention.model.AlphaInterventionState
import com.sleepagent.prototype.intervention.model.RealtimeSleepSnapshot

/**
 * Strategy for alpha (8-12 Hz) sleep onset intervention.
 * Manages trigger conditions, refractory periods, and stop conditions.
 */
interface AlphaInterventionStrategy {

    val config: AlphaInterventionConfig
    val currentState: AlphaInterventionState

    /**
     * Set the individual alpha frequency from calibration.
     */
    fun setIndividualAlphaFrequency(hz: Float)

    /**
     * Evaluate whether an alpha pulse should be triggered based on [snapshot].
     * Returns the recommended pulse gain, or null if no pulse should be triggered.
     */
    fun evaluateTrigger(snapshot: RealtimeSleepSnapshot, stableStageMs: Long): Float?

    /**
     * Record that a pulse was triggered.
     */
    fun onPulseTriggered(snapshot: RealtimeSleepSnapshot, gain: Float, phaseDeg: Float?)

    /**
     * Record that a pulse was skipped.
     */
    fun onPulseSkipped(reason: String)

    /**
     * Check if alpha intervention should be stopped.
     */
    fun shouldStop(snapshot: RealtimeSleepSnapshot, elapsedSinceStartMs: Long): String?

    /**
     * Reset state for a new session.
     */
    fun reset()
}
