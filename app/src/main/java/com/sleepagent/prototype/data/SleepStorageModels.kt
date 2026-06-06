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
