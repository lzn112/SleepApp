package com.sleepagent.prototype.sleep.staging

class MockSleepStageInferenceEngine : SleepStageInferenceEngine {
    private val classificationStages = listOf(
        SleepStage.Wake,
        SleepStage.N1,
        SleepStage.N2,
        SleepStage.N3,
        SleepStage.REM
    )
    private val sequence = listOf(
        SleepStage.Wake,
        SleepStage.N1,
        SleepStage.N2,
        SleepStage.N2,
        SleepStage.N3,
        SleepStage.N3,
        SleepStage.N2,
        SleepStage.REM
    )

    private var index = 0

    override fun predict(input: Array<FloatArray>): SleepStagePrediction {
        val stage = sequence[index % sequence.size]
        index += 1
        val confidence = 0.82f
        val probabilities = classificationStages.map { item ->
            SleepStageProbability(
                stage = item,
                probability = if (item == stage) confidence else (1f - confidence) / (classificationStages.size - 1)
            )
        }
        return SleepStagePrediction(
            stage = stage,
            confidence = confidence,
            probabilities = probabilities
        )
    }
}
