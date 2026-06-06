package com.sleepagent.prototype.sleep.staging

import com.sleepagent.prototype.sleep.processing.DownsampledEegSample

class SleepStagePipeline(
    private val sampleRateHz: Int = 100,
    private val epochSeconds: Int = 30,
    private val inferenceEngine: SleepStageInferenceEngine = MockSleepStageInferenceEngine(),
) {
    private val epochSizeSamples = sampleRateHz * epochSeconds
    private val channelBuffer = ArrayList<Float>(epochSizeSamples)
    private val hypnogram = mutableListOf<SleepStageEpochResult>()

    private var epochIndex = 0
    private var latestResult: SleepStageEpochResult? = null

    fun reset() {
        channelBuffer.clear()
        hypnogram.clear()
        epochIndex = 0
        latestResult = null
    }

    fun ingest(sample: DownsampledEegSample) {
        channelBuffer.add(sample.valueMicrovolts)
        if (channelBuffer.size >= epochSizeSamples) {
            runEpochInference(sample.timestampMillis)
        }
    }

    fun snapshot(): SleepStageSnapshot {
        val bufferedSamples = channelBuffer.size
        val progress = if (epochSizeSamples <= 0) 0f else bufferedSamples.toFloat() / epochSizeSamples.toFloat()
        val latest = latestResult
        val status = when {
            latest == null -> "Accumulating 30s EEG window"
            bufferedSamples == 0 -> "Epoch complete; accumulating next window"
            else -> "Accumulating 30s EEG window"
        }
        return SleepStageSnapshot(
            status = status,
            currentStage = latest?.stage ?: SleepStage.Unknown,
            confidence = latest?.confidence ?: 0f,
            epochProgress = progress.coerceIn(0f, 1f),
            sampleRateHz = sampleRateHz,
            epochSizeSamples = epochSizeSamples,
            bufferedSamples = bufferedSamples,
            latestResult = latest,
            hypnogram = hypnogram.toList()
        )
    }

    private fun runEpochInference(timestampMillis: Long) {
        val input = SleepStagePreprocessor.preprocess(listOf(channelBuffer.toList()))
        val prediction = inferenceEngine.predict(input)
        val result = SleepStageEpochResult(
            epochIndex = epochIndex,
            stage = prediction.stage,
            confidence = prediction.confidence,
            timestampMillis = timestampMillis,
            probabilities = prediction.probabilities
        )
        epochIndex += 1
        latestResult = result
        hypnogram.add(result)
        channelBuffer.clear()
    }
}
