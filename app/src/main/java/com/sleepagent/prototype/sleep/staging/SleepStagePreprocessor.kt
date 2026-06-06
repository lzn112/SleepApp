package com.sleepagent.prototype.sleep.staging

object SleepStagePreprocessor {
    private const val STD_EPSILON = 1e-6f
    private const val CLIP_LIMIT = 5f

    fun preprocess(channels: List<List<Float>>): Array<FloatArray> {
        return Array(channels.size) { channelIndex ->
            val values = channels[channelIndex]
            if (values.isEmpty()) return@Array FloatArray(0)

            val mean = values.average().toFloat()
            val centered = FloatArray(values.size) { sampleIndex ->
                values[sampleIndex] - mean
            }
            val variance = centered.fold(0f) { acc, value -> acc + value * value } / centered.size
            val std = maxOf(kotlin.math.sqrt(variance), STD_EPSILON)

            FloatArray(values.size) { sampleIndex ->
                val normalized = centered[sampleIndex] / std
                normalized.coerceIn(-CLIP_LIMIT, CLIP_LIMIT)
            }
        }
    }
}
