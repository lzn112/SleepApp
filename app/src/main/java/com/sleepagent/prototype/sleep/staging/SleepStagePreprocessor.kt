package com.sleepagent.prototype.sleep.staging

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object SleepStagePreprocessor {
    private const val SAMPLE_RATE_HZ = 100
    private const val STD_EPSILON = 1e-6f
    private const val CLIP_LIMIT = 5f

    // ADS1299 24-bit signed saturation limits
    // We receive microvolts now, so use a conservative UV threshold based on full-scale conversion.
    // 8_388_607 counts * 0.022351741790771484 uV/count ~= 187500 uV
    private const val SATURATION_THRESHOLD_UV = 180_000f

    fun preprocess(channels: List<List<Float>>): Array<FloatArray> {
        return Array(channels.size) { channelIndex ->
            val raw = channels[channelIndex]
            if (raw.isEmpty()) return@Array FloatArray(0)

            val interpolated = interpolateSaturatedSamples(raw)
            val notched = applyZeroPhaseNotch(interpolated, SAMPLE_RATE_HZ, 50f)
            val bandpassed = applyFIRBandpass(
                samples = notched,
                sampleRateHz = SAMPLE_RATE_HZ,
                lowHz = 0.5f,
                highHz = 30f,
                taps = 129
            )

            zScoreAndClip(bandpassed)
        }
    }

    private fun interpolateSaturatedSamples(values: List<Float>): List<Float> {
        if (values.isEmpty()) return emptyList()

        val output = values.toMutableList()
        var index = 0

        while (index < output.size) {
            if (!isSaturated(output[index])) {
                index++
                continue
            }

            val start = index
            while (index < output.size && isSaturated(output[index])) {
                index++
            }
            val endExclusive = index

            val leftIndex = start - 1
            val rightIndex = endExclusive

            val leftValue = if (leftIndex >= 0) output[leftIndex] else null
            val rightValue = if (rightIndex < output.size) output[rightIndex] else null

            when {
                leftValue != null && rightValue != null -> {
                    val span = rightIndex - leftIndex
                    for (fillIndex in start until endExclusive) {
                        val ratio = (fillIndex - leftIndex).toFloat() / span.toFloat()
                        output[fillIndex] = leftValue + (rightValue - leftValue) * ratio
                    }
                }

                leftValue != null -> {
                    for (fillIndex in start until endExclusive) {
                        output[fillIndex] = leftValue
                    }
                }

                rightValue != null -> {
                    for (fillIndex in start until endExclusive) {
                        output[fillIndex] = rightValue
                    }
                }

                else -> {
                    for (fillIndex in start until endExclusive) {
                        output[fillIndex] = 0f
                    }
                }
            }
        }

        return output
    }

    private fun isSaturated(valueUv: Float): Boolean {
        return valueUv >= SATURATION_THRESHOLD_UV || valueUv <= -SATURATION_THRESHOLD_UV
    }

    private fun applyZeroPhaseNotch(
        samples: List<Float>,
        sampleRateHz: Int,
        notchFrequencyHz: Float
    ): List<Float> {
        val coefficients = buildNotchCoefficients(sampleRateHz, notchFrequencyHz) ?: return samples
        if (samples.size < 16) return samples

        val forward = applyBiquad(samples, coefficients)
        return applyBiquad(forward.asReversed(), coefficients).asReversed()
    }

    private fun applyBiquad(samples: List<Float>, coefficients: BiquadCoefficients): List<Float> {
        if (samples.isEmpty()) return emptyList()

        val output = ArrayList<Float>(samples.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        for (sample in samples) {
            val x0 = sample.toDouble()
            val y0 = coefficients.b0 * x0 +
                    coefficients.b1 * x1 +
                    coefficients.b2 * x2 -
                    coefficients.a1 * y1 -
                    coefficients.a2 * y2
            output.add(y0.toFloat())
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }

        return output
    }

    private fun buildNotchCoefficients(
        sampleRateHz: Int,
        notchFrequencyHz: Float
    ): BiquadCoefficients? {
        if (sampleRateHz <= 0 || notchFrequencyHz <= 0f) return null

        val nyquist = sampleRateHz / 2.0
        val notchHz = min(notchFrequencyHz.toDouble(), nyquist * 0.98)
        if (notchHz <= 0.0 || notchHz >= nyquist) return null

        val omega = 2.0 * PI * notchHz / sampleRateHz
        val alpha = sin(omega) / (2.0 * NOTCH_Q)
        val cosOmega = cos(omega)
        val a0 = 1.0 + alpha

        return BiquadCoefficients(
            b0 = 1.0 / a0,
            b1 = (-2.0 * cosOmega) / a0,
            b2 = 1.0 / a0,
            a1 = (-2.0 * cosOmega) / a0,
            a2 = (1.0 - alpha) / a0
        )
    }

    private fun applyFIRBandpass(
        samples: List<Float>,
        sampleRateHz: Int,
        lowHz: Float,
        highHz: Float,
        taps: Int
    ): List<Float> {
        if (samples.isEmpty()) return emptyList()

        val effectiveTaps = if (taps % 2 == 0) taps + 1 else taps
        val kernel = buildBandpassKernel(
            sampleRateHz = sampleRateHz,
            lowHz = lowHz,
            highHz = highHz,
            taps = effectiveTaps
        )
        val groupDelay = (effectiveTaps - 1) / 2

        val filtered = ArrayList<Float>(samples.size)
        for (index in samples.indices) {
            var acc = 0.0
            var tapIndex = 0
            while (tapIndex < kernel.size) {
                val sampleIndex = index - tapIndex
                if (sampleIndex >= 0) {
                    acc += samples[sampleIndex] * kernel[tapIndex]
                }
                tapIndex++
            }
            filtered.add(acc.toFloat())
        }

        return filtered
    }

    private fun buildBandpassKernel(
        sampleRateHz: Int,
        lowHz: Float,
        highHz: Float,
        taps: Int
    ): FloatArray {
        val kernel = FloatArray(taps)
        val m = taps - 1
        val lowNorm = (lowHz / sampleRateHz.toFloat()).coerceIn(0.0001f, 0.49f)
        val highNorm = (highHz / sampleRateHz.toFloat()).coerceIn(lowNorm + 0.0001f, 0.499f)

        for (n in 0 until taps) {
            val k = n - m / 2
            val value = (idealLowPass(highNorm, k) - idealLowPass(lowNorm, k)) * hammingWindow(n, m)
            kernel[n] = value.toFloat()
        }

        val centerFrequencyHz = ((lowHz + highHz) / 2f)
            .coerceIn(0.0001f, sampleRateHz / 2f - 0.0001f)
        val centerGain = frequencyResponseMagnitude(kernel, centerFrequencyHz, sampleRateHz)
        if (centerGain > 1e-6) {
            for (index in kernel.indices) {
                kernel[index] = (kernel[index] / centerGain).toFloat()
            }
        }

        return kernel
    }

    private fun idealLowPass(normalizedCutoff: Float, n: Int): Double {
        return if (n == 0) {
            2.0 * normalizedCutoff
        } else {
            sin(2.0 * PI * normalizedCutoff * n) / (PI * n)
        }
    }

    private fun hammingWindow(n: Int, m: Int): Double {
        return 0.54 - 0.46 * cos((2.0 * PI * n) / m)
    }

    private fun frequencyResponseMagnitude(
        kernel: FloatArray,
        frequencyHz: Float,
        sampleRateHz: Int
    ): Double {
        val omega = 2.0 * PI * frequencyHz / sampleRateHz.toDouble()
        var real = 0.0
        var imaginary = 0.0

        for (index in kernel.indices) {
            val phase = omega * index
            val value = kernel[index].toDouble()
            real += value * cos(phase)
            imaginary -= value * sin(phase)
        }

        return sqrt(real * real + imaginary * imaginary)
    }

    private fun zScoreAndClip(values: List<Float>): FloatArray {
        if (values.isEmpty()) return FloatArray(0)

        val mean = values.average().toFloat()
        val centered = FloatArray(values.size) { index ->
            values[index] - mean
        }
        val variance = centered.fold(0f) { acc, value -> acc + value * value } / centered.size
        val std = max(sqrt(variance), STD_EPSILON)

        return FloatArray(values.size) { index ->
            (centered[index] / std).coerceIn(-CLIP_LIMIT, CLIP_LIMIT)
        }
    }

    private data class BiquadCoefficients(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double
    )

    private const val NOTCH_Q = 30.0
}