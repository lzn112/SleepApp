package com.sleepagent.prototype.intervention.model

import com.sleepagent.prototype.data.SleepStage

/**
 * Real-time snapshot of sleep state for intervention decision-making.
 * Updated by SleepRecordingService whenever new data arrives.
 */
data class RealtimeSleepSnapshot(
    val timestampMillis: Long,
    val elapsedRealtimeNanos: Long,

    val sleepStage: SleepStage,
    val stageProbabilities: Map<SleepStage, Float> = emptyMap(),

    val eegQuality: Float = 0f,
    val motionLevel: Float? = null,
    val heartRate: Float? = null,

    val isDeviceConnected: Boolean = true
)
