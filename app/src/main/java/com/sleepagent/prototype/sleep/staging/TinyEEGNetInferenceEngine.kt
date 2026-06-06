package com.sleepagent.prototype.sleep.staging

import android.content.Context
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import kotlin.math.exp

/**
 * TinyEEGNetInferenceEngine
 *
 * Runs the TinyEEGNet sleep-staging model (model.ptl) via PyTorch Mobile.
 *
 * Expected input
 * --------------
 * input[0]  : FloatArray of 3000 samples (30 s × 100 Hz, FP1-M2 channel)
 *             already z-scored and clipped to ±5 by [SleepStagePreprocessor]
 *
 * Model I/O
 * ---------
 * Input tensor  : float32 [1, 1, 3000]   (batch=1, channel=1, time=3000)
 * Output tensor : float32 [1, 5]          raw logits → softmax → probs
 *
 * Label order   : Wake(0)  N1(1)  N2(2)  N3(3)  REM(4)
 *
 * Usage
 * -----
 * 1. Copy model.ptl into app/src/main/assets/
 * 2. Instantiate once (e.g. in your ViewModel or Service):
 *        val engine = TinyEEGNetInferenceEngine(context)
 * 3. Pass to SleepStagePipeline:
 *        val pipeline = SleepStagePipeline(inferenceEngine = engine)
 */
class TinyEEGNetInferenceEngine(context: Context) : SleepStageInferenceEngine {

    private val module: Module = LiteModuleLoader.load(assetFilePath(context, MODEL_ASSET))

    // Stage labels must match Python training order: Wake N1 N2 N3 REM
    private val stages = listOf(
        SleepStage.Wake,
        SleepStage.N1,
        SleepStage.N2,
        SleepStage.N3,
        SleepStage.REM,
    )

    override fun predict(input: Array<FloatArray>): SleepStagePrediction {
        require(input.isNotEmpty() && input[0].isNotEmpty()) {
            "predict() received empty input"
        }

        val channelData = input[0]   // FloatArray(3000), already preprocessed

        // Build input tensor: shape [1, 1, 3000]
        val inputTensor = Tensor.fromBlob(
            channelData,
            longArrayOf(1L, 1L, channelData.size.toLong())
        )

        // Run forward pass
        val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
        val logits = outputTensor.dataAsFloatArray   // FloatArray(5), raw logits

        // Numerically-stable softmax
        val probs = softmax(logits)

        // Build result
        val bestIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
        val probabilities = stages.mapIndexed { i, stage ->
            SleepStageProbability(stage = stage, probability = probs[i])
        }

        return SleepStagePrediction(
            stage      = stages[bestIdx],
            confidence = probs[bestIdx],
            probabilities = probabilities,
        )
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun softmax(logits: FloatArray): FloatArray {
        val maxVal = logits.max() ?: 0f
        val exps   = FloatArray(logits.size) { exp((logits[it] - maxVal).toDouble()).toFloat() }
        val sum    = exps.sum()
        return FloatArray(exps.size) { exps[it] / sum }
    }

    /**
     * Copies the asset to internal storage on first run (required by PyTorch
     * Mobile's file-path API), then returns the path.
     */
    private fun assetFilePath(context: Context, assetName: String): String {
        val outFile = File(context.filesDir, assetName)
        if (outFile.exists() && outFile.length() > 0) return outFile.absolutePath

        context.assets.open(assetName).use { input ->
            FileOutputStream(outFile).use { output ->
                val buf = ByteArray(8 * 1024)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                }
                output.flush()
            }
        }
        return outFile.absolutePath
    }

    companion object {
        private const val MODEL_ASSET = "model.ptl"
    }
}
