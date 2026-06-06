package com.sleepagent.prototype.sleep.staging

enum class SleepStage(
    val label: String,
    val shortLabel: String
) {
    Wake(label = "Wake / 清醒", shortLabel = "W"),
    N1(label = "N1 / 浅睡", shortLabel = "N1"),
    N2(label = "N2 / 浅睡", shortLabel = "N2"),
    N3(label = "N3 / 深睡", shortLabel = "N3"),
    REM(label = "REM / 快速眼动", shortLabel = "REM"),
    Unknown(label = "等待数据", shortLabel = "-")
}

data class SleepStageProbability(
    val stage: SleepStage,
    val probability: Float
)

data class SleepStagePrediction(
    val stage: SleepStage,
    val confidence: Float,
    val probabilities: List<SleepStageProbability> = emptyList()
)

data class SleepStageEpochResult(
    val epochIndex: Int,
    val stage: SleepStage,
    val confidence: Float,
    val timestampMillis: Long,
    val probabilities: List<SleepStageProbability> = emptyList()
)

data class SleepStageSnapshot(
    val status: String = "Accumulating 30s EEG window",
    val currentStage: SleepStage = SleepStage.Unknown,
    val confidence: Float = 0f,
    val epochProgress: Float = 0f,
    val sampleRateHz: Int = 100,
    val epochSizeSamples: Int = 3000,
    val bufferedSamples: Int = 0,
    val latestResult: SleepStageEpochResult? = null,
    val hypnogram: List<SleepStageEpochResult> = emptyList()
)
