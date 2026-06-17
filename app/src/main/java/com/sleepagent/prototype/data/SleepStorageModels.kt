package com.sleepagent.prototype.data

data class SleepSessionRecord(
    val sessionId: String,
    val sourceType: SleepDataSource,
    val status: SleepSessionStatus,
    val analysisStatus: SleepAnalysisStatus,
    val analysisVersion: String?,
    val lastAnalyzedAtEpochMs: Long?,
    val deviceId: String?,
    val deviceName: String?,
    val deviceAddress: String?,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val samplingRateHz: Int,
    val channelCount: Int,
    val rawFormat: String,
    val rawFilePath: String,
    val packetCount: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

data class SleepEpochRecord(
    val id: Long = 0L,
    val sessionId: String,
    val epochIndex: Int,
    val startAtEpochMs: Long,
    val endAtEpochMs: Long,
    val stage: SleepStage = SleepStage.UNKNOWN,
    val confidence: Float? = null,
    val avgSignalQuality: Float? = null,
    val source: SleepStageSource = SleepStageSource.MODEL,
    val featuresJson: String? = null
)

enum class SleepDataSource {
    BLE,
    MOCK,
    IMPORT
}

enum class SleepSessionStatus {
    RECORDING,
    COMPLETED,
    ABORTED
}

enum class SleepStage {
    AWAKE,
    LIGHT,
    DEEP,
    REM,
    UNKNOWN
}

enum class SleepStageSource {
    MODEL,
    MANUAL,
    IMPORT
}

enum class SleepAnalysisStatus {
    NONE,
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    PARTIAL
}

enum class AnalysisJobType {
    FEATURE_EXTRACTION,
    SLEEP_STAGING,
    NIGHTLY_SUMMARY,
    AI_REPORT,
    CUSTOM
}

enum class AnalysisJobStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELED
}

data class AnalysisJobRecord(
    val jobId: String,
    val sessionId: String,
    val jobType: AnalysisJobType,
    val status: AnalysisJobStatus,
    val algorithmVersion: String?,
    val startedAtEpochMs: Long?,
    val finishedAtEpochMs: Long?,
    val errorMessage: String?,
    val payloadJson: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

enum class SleepWindowType {
    THIRTY_SECONDS,
    FIVE_MINUTES,
    CUSTOM
}

data class SleepFeatureWindowRecord(
    val id: Long = 0L,
    val sessionId: String,
    val windowType: SleepWindowType,
    val windowIndex: Int,
    val startAtEpochMs: Long,
    val endAtEpochMs: Long,
    val deltaPower: Double?,
    val thetaPower: Double?,
    val alphaPower: Double?,
    val betaPower: Double?,
    val thetaAlphaRatio: Double?,
    val signalQuality: Float?,
    val artifactRatio: Float?,
    val featureVersion: String?,
    val featuresJson: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

data class SleepNightlySummaryRecord(
    val summaryId: String,
    val sessionId: String,
    val summaryVersion: String?,
    val sleepDurationMs: Long?,
    val sleepEfficiency: Float?,
    val sleepOnsetLatencyMs: Long?,
    val wakeAfterSleepOnsetMs: Long?,
    val wakeCount: Int?,
    val deepSleepMs: Long?,
    val remSleepMs: Long?,
    val lightSleepMs: Long?,
    val awakeMs: Long?,
    val avgSignalQuality: Float?,
    val dataQualityScore: Float?,
    val generatedAtEpochMs: Long,
    val payloadJson: String?
)

enum class SleepEventType {
    DEVICE_DISCONNECTED,
    SIGNAL_DROPOUT,
    AWAKENING,
    DEVICE_REMOVED,
    USER_MARKER,
    CUSTOM
}

enum class SleepEventSource {
    DEVICE,
    SCRIPT,
    USER,
    AI
}

data class SleepEventRecord(
    val eventId: String,
    val sessionId: String,
    val eventType: SleepEventType,
    val startAtEpochMs: Long,
    val endAtEpochMs: Long?,
    val severity: Int?,
    val confidence: Float?,
    val source: SleepEventSource,
    val payloadJson: String?,
    val createdAtEpochMs: Long
)

enum class SleepReportType {
    NIGHTLY_EXPLANATION,
    WEEKLY_TREND,
    RISK_ALERT,
    CUSTOM
}

data class SleepAiReportRecord(
    val reportId: String,
    val sessionId: String,
    val reportType: SleepReportType,
    val modelName: String,
    val promptVersion: String?,
    val inputSnapshotId: String?,
    val summaryText: String,
    val structuredJson: String?,
    val confidence: Float?,
    val createdAtEpochMs: Long
)

enum class SleepEvidenceType {
    SESSION,
    EPOCH,
    FEATURE_WINDOW,
    EVENT,
    NIGHTLY_SUMMARY,
    CUSTOM
}

data class AiEvidenceLinkRecord(
    val id: Long = 0L,
    val reportId: String,
    val evidenceType: SleepEvidenceType,
    val evidenceRefId: String,
    val note: String?
)

class ActiveSleepSession internal constructor(
    val sessionId: String,
    val rawFilePath: String,
    val startedAtEpochMs: Long,
    val sourceType: SleepDataSource,
    internal val csvHandle: SleepSessionCsvWriter.SessionHandle,
    internal var packetCount: Long = 0L
)

// ── Sound Intervention Database Entities ──

data class SoundInterventionConfigEntity(
    val id: Long = 0L,
    // Background
    val sleepBackgroundEnabled: Boolean = true,
    val sleepBackgroundSoundKey: String = "RAIN_GENTLE",
    val sleepBackgroundGain: Float = 0.12f,
    val sleepBackgroundDurationMinutes: Int = 30,
    val keepBackgroundAllNight: Boolean = false,
    val fadeAfterSleepOnset: Boolean = true,
    val backgroundFadeDurationSeconds: Int = 60,
    // Alpha
    val alphaInterventionEnabled: Boolean = false,
    val alphaMode: String = "ALPHA_AWARE",
    val alphaTargetPhaseDeg: Float = 0f,
    val alphaPhaseToleranceDeg: Float = 30f,
    val alphaPulseGain: Float = 0.04f,
    val alphaMaxDurationMinutes: Int = 30,
    val alphaMinimumSignalQuality: Float = 0.70f,
    // Deep sleep
    val deepSleepEnabled: Boolean = false,
    val deepSleepPulseGain: Float = 0.04f,
    val deepSleepStableSeconds: Int = 120,
    val deepSleepMaxPulsesPerGroup: Int = 8,
    val deepSleepObserveSeconds: Int = 45,
    val deepSleepMaxGroups: Int = 10,
    // Smart wake
    val smartWakeEnabled: Boolean = true,
    val latestWakeMinutesFromMidnight: Int = 7 * 60 + 30,
    val wakeWindowMinutes: Int = 30,
    val allowRemWake: Boolean = false,
    val wakeSoundKey: String = "MORNING_BIRDS",
    val fallbackAlarmSoundKey: String = "SOFT_PLUCKS_ALARM",
    val vibrationEnabled: Boolean = false,
    val wakeInitialGain: Float = 0.03f,
    val wakeMaxGain: Float = 0.50f,
    val wakeRampDurationSeconds: Int = 180,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class AlphaCalibrationEntity(
    val id: Long = 0L,
    val sessionId: String? = null,
    val deviceId: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val individualAlphaFrequencyHz: Float? = null,
    val peakPower: Float? = null,
    val peakProminence: Float? = null,
    val signalQuality: Float = 0f,
    val success: Boolean = false,
    val failureReason: String? = null,
    val algorithmVersion: String = "1.0"
)

data class SoundInterventionEventEntity(
    val id: Long = 0L,
    val sessionId: String,
    val timestampMillis: Long,
    val elapsedRealtimeNanos: Long,
    val interventionType: String,
    val eventType: String,
    val interventionState: String? = null,
    val sleepStage: String? = null,
    val stageProbability: Float? = null,
    val eegQuality: Float? = null,
    val motionLevel: Float? = null,
    val heartRate: Float? = null,
    val backgroundSoundKey: String? = null,
    val audioGain: Float? = null,
    val alphaMode: String? = null,
    val individualAlphaFrequencyHz: Float? = null,
    val targetPhaseDeg: Float? = null,
    val estimatedPhaseDeg: Float? = null,
    val predictedPlaybackPhaseDeg: Float? = null,
    val phaseErrorDeg: Float? = null,
    val alphaAmplitude: Float? = null,
    val groupIndex: Int? = null,
    val pulseIndex: Int? = null,
    val success: Boolean? = null,
    val reason: String? = null,
    val metadataJson: String? = null
)

object SoundInterventionEventTypes {
    const val SESSION_STARTED = "SESSION_STARTED"
    const val BACKGROUND_STARTED = "BACKGROUND_STARTED"
    const val BACKGROUND_SWITCHED = "BACKGROUND_SWITCHED"
    const val BACKGROUND_FADE_STARTED = "BACKGROUND_FADE_STARTED"
    const val BACKGROUND_STOPPED = "BACKGROUND_STOPPED"
    const val ALPHA_CALIBRATION_STARTED = "ALPHA_CALIBRATION_STARTED"
    const val ALPHA_CALIBRATION_SUCCEEDED = "ALPHA_CALIBRATION_SUCCEEDED"
    const val ALPHA_CALIBRATION_FAILED = "ALPHA_CALIBRATION_FAILED"
    const val ALPHA_INTERVENTION_STARTED = "ALPHA_INTERVENTION_STARTED"
    const val ALPHA_PULSE_TRIGGERED = "ALPHA_PULSE_TRIGGERED"
    const val ALPHA_PULSE_SKIPPED = "ALPHA_PULSE_SKIPPED"
    const val ALPHA_INTERVENTION_STOPPED = "ALPHA_INTERVENTION_STOPPED"
    const val N3_GROUP_STARTED = "N3_GROUP_STARTED"
    const val N3_PULSE_TRIGGERED = "N3_PULSE_TRIGGERED"
    const val N3_PULSE_SKIPPED = "N3_PULSE_SKIPPED"
    const val N3_GROUP_FINISHED = "N3_GROUP_FINISHED"
    const val N3_INTERVENTION_STOPPED = "N3_INTERVENTION_STOPPED"
    const val SMART_WAKE_WINDOW_STARTED = "SMART_WAKE_WINDOW_STARTED"
    const val SMART_WAKE_TRIGGERED = "SMART_WAKE_TRIGGERED"
    const val WAKE_SOUND_STARTED = "WAKE_SOUND_STARTED"
    const val VIBRATION_STARTED = "VIBRATION_STARTED"
    const val FALLBACK_ALARM_SCHEDULED = "FALLBACK_ALARM_SCHEDULED"
    const val FALLBACK_ALARM_TRIGGERED = "FALLBACK_ALARM_TRIGGERED"
    const val USER_CONFIRMED_AWAKE = "USER_CONFIRMED_AWAKE"
    const val STOPPED_STAGE_CHANGE = "STOPPED_STAGE_CHANGE"
    const val STOPPED_BAD_SIGNAL = "STOPPED_BAD_SIGNAL"
    const val STOPPED_MOTION = "STOPPED_MOTION"
    const val STOPPED_DEVICE_DISCONNECTED = "STOPPED_DEVICE_DISCONNECTED"
    const val PULSE_PLAY_FAILED = "PULSE_PLAY_FAILED"
    const val SESSION_STOPPED = "SESSION_STOPPED"
}
