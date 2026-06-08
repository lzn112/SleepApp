package com.sleepagent.prototype.sleep.staging

import com.sleepagent.prototype.sleep.processing.DownsampledEegSample
import java.util.ArrayDeque

class SleepStagePipeline(
    private val sampleRateHz: Int = 100,
    private val epochSeconds: Int = 30,
    private val contextEpochs: Int = 5,
    private val inferenceEngine: SleepStageInferenceEngine = MockSleepStageInferenceEngine(),
) {
    private val epochSizeSamples = sampleRateHz * epochSeconds
    private val centerEpochOffset = contextEpochs / 2

    private val currentEpochBuffer = ArrayList<Float>(epochSizeSamples)
    private val completedEpochWindow = ArrayDeque<CompletedEpoch>(contextEpochs)
    private val hypnogram = mutableListOf<SleepStageEpochResult>()

    private var nextEpochIndex = 0
    private var latestResult: SleepStageEpochResult? = null

    fun reset() {
        currentEpochBuffer.clear()
        completedEpochWindow.clear()
        hypnogram.clear()
        nextEpochIndex = 0
        latestResult = null
    }

    fun ingest(sample: DownsampledEegSample) {
        currentEpochBuffer.add(sample.valueMicrovolts)
        if (currentEpochBuffer.size >= epochSizeSamples) {
            completeEpoch(sample.timestampMillis)
        }
    }

    fun snapshot(): SleepStageSnapshot {
        val bufferedSamples = currentEpochBuffer.size
        val progress = if (epochSizeSamples <= 0) 0f else bufferedSamples.toFloat() / epochSizeSamples.toFloat()

        val status = when {
            latestResult == null && completedEpochWindow.size < contextEpochs -> {
                "Accumulating context window (${completedEpochWindow.size}/$contextEpochs epochs)"
            }
            latestResult == null -> {
                "Waiting for first context prediction"
            }
            else -> {
                "Collecting next epoch for context inference"
            }
        }

        return SleepStageSnapshot(
            status = status,
            currentStage = latestResult?.stage ?: SleepStage.Unknown,
            confidence = latestResult?.confidence ?: 0f,
            epochProgress = progress.coerceIn(0f, 1f),
            sampleRateHz = sampleRateHz,
            epochSizeSamples = epochSizeSamples,
            bufferedSamples = bufferedSamples,
            latestResult = latestResult,
            hypnogram = hypnogram.toList()
        )
    }

    private fun completeEpoch(timestampMillis: Long) {
        val rawEpoch = currentEpochBuffer.toList()
        val preprocessedEpoch = SleepStagePreprocessor.preprocess(listOf(rawEpoch))[0]

        completedEpochWindow.addLast(
            CompletedEpoch(
                epochIndex = nextEpochIndex,
                timestampMillis = timestampMillis,
                samples = preprocessedEpoch
            )
        )
        nextEpochIndex += 1
        currentEpochBuffer.clear()

        if (completedEpochWindow.size >= contextEpochs) {
            runContextInference()
        }
    }

    private fun runContextInference() {
        val window = completedEpochWindow.toList()
        require(window.size == contextEpochs) {
            "Expected $contextEpochs epochs in context window, got ${window.size}"
        }

        val concatenated = FloatArray(epochSizeSamples * contextEpochs)
        var offset = 0
        window.forEach { epoch ->
            require(epoch.samples.size == epochSizeSamples) {
                "Expected epoch size $epochSizeSamples, got ${epoch.samples.size}"
            }
            System.arraycopy(epoch.samples, 0, concatenated, offset, epoch.samples.size)
            offset += epoch.samples.size
        }

        val prediction = inferenceEngine.predict(arrayOf(concatenated))
        val centerEpoch = window[centerEpochOffset]

        val result = SleepStageEpochResult(
            epochIndex = centerEpoch.epochIndex,
            stage = prediction.stage,
            confidence = prediction.confidence,
            timestampMillis = centerEpoch.timestampMillis,
            probabilities = prediction.probabilities
        )

        latestResult = result
        hypnogram.add(result)

        // Slide by one epoch: [0,1,2,3,4] -> infer center 2, then keep [1,2,3,4]
        completedEpochWindow.removeFirst()
    }

    private data class CompletedEpoch(
        val epochIndex: Int,
        val timestampMillis: Long,
        val samples: FloatArray
    )
}