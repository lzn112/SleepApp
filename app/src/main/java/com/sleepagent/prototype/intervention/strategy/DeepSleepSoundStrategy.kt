package com.sleepagent.prototype.intervention.strategy

import com.sleepagent.prototype.intervention.model.RealtimeSleepSnapshot

/**
 * Strategy for N3 deep sleep sound intervention.
 * Manages group/pulse logic, entry/exit conditions.
 */
interface DeepSleepSoundStrategy {

    /** Max pulses per group */
    val maxPulsesPerGroup: Int

    /** Minutes of stable N3 required before intervention starts */
    val stableN3RequiredMs: Long

    /** Pause between groups (ms) */
    val groupPauseMs: Long

    /** Max groups per session */
    val maxGroups: Int

    /** Current group index (0-based) */
    val currentGroup: Int

    /** Current pulse count in this group */
    val currentPulseInGroup: Int

    /**
     * Check if conditions are met for starting a new N3 group.
     */
    fun shouldStartGroup(snapshot: RealtimeSleepSnapshot, stableN3Ms: Long): Boolean

    /**
     * Start a new group. Returns the recommended gain for the first pulse.
     */
    fun startGroup(snapshot: RealtimeSleepSnapshot): Float

    /**
     * Check if next pulse should be triggered. Returns recommended gain or null.
     */
    fun shouldTriggerPulse(snapshot: RealtimeSleepSnapshot, groupPauseElapsedMs: Long): Float?

    /**
     * Record that a pulse was triggered.
     */
    fun onPulseTriggered(snapshot: RealtimeSleepSnapshot, gain: Float)

    /**
     * Record that a pulse was skipped.
     */
    fun onPulseSkipped(reason: String)

    /**
     * Check if the current group should be finished.
     */
    fun shouldFinishGroup(snapshot: RealtimeSleepSnapshot): Boolean

    /**
     * Check if N3 intervention should be stopped entirely.
     */
    fun shouldStop(snapshot: RealtimeSleepSnapshot): String?

    /**
     * Reset state for a new session.
     */
    fun reset()
}
