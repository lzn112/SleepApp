package com.sleepagent.prototype.intervention.strategy

import com.sleepagent.prototype.intervention.model.RealtimeSleepSnapshot

/**
 * Strategy for smart wake within a defined time window.
 * Calculates wake scores and decides when to trigger gentle wake-up.
 */
interface SmartWakeStrategy {

    /** Wake window start time (elapsedRealtimeNanos) */
    val windowStartNanos: Long

    /** Wake window end time (elapsedRealtimeNanos, fallback alarm) */
    val windowEndNanos: Long

    /** Whether REM sleep allows wake */
    val allowRemWake: Boolean

    /** Score threshold for triggering wake */
    val wakeScoreThreshold: Float

    /** Number of consecutive windows above threshold required */
    val consecutiveWindowsRequired: Int

    /** Current consecutive count of high-score windows */
    val consecutiveHighScoreCount: Int

    /**
     * Calculate wake score for the current snapshot.
     * Higher scores indicate more favorable wake conditions.
     */
    fun calculateWakeScore(snapshot: RealtimeSleepSnapshot): Float

    /**
     * Check if the current time is within the smart wake window.
     */
    fun isInWindow(currentNanos: Long): Boolean

    /**
     * Check if the window has expired (fallback alarm time reached).
     */
    fun isWindowExpired(currentNanos: Long): Boolean

    /**
     * Evaluate whether to trigger smart wake based on the current snapshot.
     */
    fun shouldTriggerWake(snapshot: RealtimeSleepSnapshot, currentNanos: Long): Boolean

    /**
     * Reset state for a new session.
     */
    fun reset(windowStartNanos: Long, windowEndNanos: Long)
}
