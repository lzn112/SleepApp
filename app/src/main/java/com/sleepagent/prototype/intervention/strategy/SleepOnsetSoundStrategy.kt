package com.sleepagent.prototype.intervention.strategy

import com.sleepagent.prototype.intervention.model.RealtimeSleepSnapshot

/**
 * Strategy for managing background sleep sounds during sleep onset.
 * Handles fade-out after stable sleep is detected.
 */
interface SleepOnsetSoundStrategy {

    /**
     * Evaluate whether the background sound should fade out based on the latest snapshot.
     * Returns true if fade-out should begin.
     */
    fun shouldFadeOut(snapshot: RealtimeSleepSnapshot, stableSleepMs: Long): Boolean

    /**
     * Determine if the user has reached stable sleep (N2 or deeper).
     */
    fun isStableSleep(snapshot: RealtimeSleepSnapshot, stableStageMs: Long): Boolean
}
