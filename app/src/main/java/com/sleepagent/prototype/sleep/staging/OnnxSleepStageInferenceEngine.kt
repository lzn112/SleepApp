package com.sleepagent.prototype.sleep.staging

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * ONNX Runtime implementation for the 4-class sleep staging model.
 *
 * Expected input:
 * - input.size == 1
 * - input[0].size == 15000
 * - input[0] is already preprocessed to match training:
 *   5 epochs x 3000 samples/epoch = 15000 samples total
 *
 * Model contract from mobile_export_summary.json:
 * - input name:  eeg
 * - output name: logits
 * - input shape: [1, 1, 15000]
 * - output shape: [1, 4]
 */
class OnnxSleepStageInferenceEngine(
    context: Context
) : SleepStageInferenceEngine, AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    private val stages = listOf(
        SleepStage.Wake,
        SleepStage.Light,
        SleepStage.N3,
        SleepStage.REM
    )

    init {
        val modelPath = assetFilePath(context, MODEL_ASSET)

        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // If you want to experiment later on real Android devices:
            // addNnapi()
        }

        session = options.use { sessionOptions ->
            env.createSession(modelPath, sessionOptions)
        }

        require(session.inputNames.contains(INPUT_NAME)) {
            "ONNX model input '$INPUT_NAME' not found. Actual inputs=${session.inputNames}"
        }
        require(session.outputNames.contains(OUTPUT_NAME)) {
            "ONNX model output '$OUTPUT_NAME' not found. Actual outputs=${session.outputNames}"
        }
    }

    override fun predict(input: Array<FloatArray>): SleepStagePrediction {
        require(input.size == 1) {
            "Expected a single EEG channel, got ${input.size} channels"
        }

        val signal = input[0]
        require(signal.size == EXPECTED_INPUT_SAMPLES) {
            "Expected $EXPECTED_INPUT_SAMPLES samples, got ${signal.size}"
        }

        try {
            val shape = longArrayOf(1L, 1L, EXPECTED_INPUT_SAMPLES.toLong())

            OnnxTensor.createTensor(env, FloatBuffer.wrap(signal), shape).use { tensor ->
                val inputs = mapOf(INPUT_NAME to tensor)
                val requestedOutputs = setOf(OUTPUT_NAME)

                session.run(inputs, requestedOutputs).use { result ->
                    val outputValue = result.get(OUTPUT_NAME).orElseThrow {
                        IllegalStateException("Model output '$OUTPUT_NAME' missing from inference result")
                    }

                    val logits = extractLogits(outputValue)
                    require(logits.size == stages.size) {
                        "Expected ${stages.size} logits, got ${logits.size}"
                    }

                    val probabilities = softmax(logits)
                    val bestIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0

                    return SleepStagePrediction(
                        stage = stages[bestIndex],
                        confidence = probabilities[bestIndex],
                        probabilities = stages.mapIndexed { index, stage ->
                            SleepStageProbability(
                                stage = stage,
                                probability = probabilities[index]
                            )
                        }
                    )
                }
            }
        } catch (error: OrtException) {
            throw IllegalStateException("ONNX inference failed: ${error.message}", error)
        }
    }

    override fun close() {
        session.close()
        // OrtEnvironment.close() is effectively a no-op in newer ORT Java releases,
        // and this shared singleton is typically kept for the app lifetime.
    }

    private fun extractLogits(outputValue: OnnxValue): FloatArray {
        require(outputValue is OnnxTensor) {
            "Expected ONNX tensor output, got ${outputValue.javaClass.simpleName}"
        }

        val value = outputValue.value
        return when (value) {
            is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                val batch = value as Array<FloatArray>
                require(batch.isNotEmpty()) { "Output tensor batch is empty" }
                batch[0]
            }
            else -> {
                throw IllegalStateException(
                    "Unexpected ONNX output value type: ${value?.javaClass?.name ?: "null"}"
                )
            }
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size) { index ->
            exp((logits[index] - maxLogit).toDouble()).toFloat()
        }
        val sum = exps.sum().takeIf { it > 0f } ?: 1f
        return FloatArray(exps.size) { index -> exps[index] / sum }
    }

    /**
     * Copies the ONNX asset into internal storage and returns the absolute path.
     * ORT Java can load from a file path directly.
     */
    private fun assetFilePath(context: Context, assetName: String): String {
        val outFile = File(context.filesDir, assetName)
        if (outFile.exists() && outFile.length() > 0L) {
            return outFile.absolutePath
        }

        context.assets.open(assetName).use { input ->
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }

        return outFile.absolutePath
    }

    companion object {
        private const val MODEL_ASSET = "model.onnx"
        private const val INPUT_NAME = "eeg"
        private const val OUTPUT_NAME = "logits"
        private const val EXPECTED_INPUT_SAMPLES = 15_000
    }
}