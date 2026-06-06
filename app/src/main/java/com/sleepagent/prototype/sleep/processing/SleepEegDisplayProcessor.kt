package com.sleepagent.prototype.sleep.processing

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal class SleepEegDisplayProcessor(
    private val sourceSampleRateHz: Int = 100,
    private val notchFrequencyHz: Float = 0f,
    private val bandpassLowHz: Float = 5f,
    private val bandpassHighHz: Float = 35f,
    bandpassTaps: Int = 129
) {
    private val effectiveBandpassTaps = if (bandpassTaps % 2 == 0) bandpassTaps + 1 else bandpassTaps
    private val bandpassGroupDelay = (effectiveBandpassTaps - 1) / 2
    private val bandpassKernel = buildBandpassKernel()
    private val notchCoefficients = buildNotchCoefficients()
    val outputSampleRateHz: Int
        get() = sourceSampleRateHz

    fun process(samples: List<Float>): List<Float> {
        if (samples.size < 8) return samples.toList()

        val centered = samples.map { it - samples.average().toFloat() }
        val notched = applyZeroPhaseNotch(centered)
        return applyFIRBandpass(notched).drop(bandpassGroupDelay)
    }

    private fun applyZeroPhaseNotch(samples: List<Float>): List<Float> {
        val coefficients = notchCoefficients ?: return samples.toList()
        if (samples.size < 16) return samples.toList()

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
            val input = sample.toDouble()
            val value = coefficients.b0 * input +
                coefficients.b1 * x1 +
                coefficients.b2 * x2 -
                coefficients.a1 * y1 -
                coefficients.a2 * y2
            output.add(value.toFloat())
            x2 = x1
            x1 = input
            y2 = y1
            y1 = value
        }
        return output
    }

    private fun applyFIRBandpass(samples: List<Float>): List<Float> {
        if (samples.isEmpty()) return emptyList()

        val output = ArrayList<Float>(samples.size)
        for (index in samples.indices) {
            var acc = 0.0
            var tapIndex = 0
            while (tapIndex < bandpassKernel.size) {
                val sampleIndex = index - tapIndex
                if (sampleIndex >= 0) {
                    acc += samples[sampleIndex] * bandpassKernel[tapIndex]
                }
                tapIndex++
            }
            output.add(acc.toFloat())
        }
        return output
    }

    private fun buildBandpassKernel(): FloatArray {
        val kernel = FloatArray(effectiveBandpassTaps)
        val m = effectiveBandpassTaps - 1
        val lowNorm = (bandpassLowHz / sourceSampleRateHz.toFloat()).coerceIn(0.0001f, 0.49f)
        val highNorm = (bandpassHighHz / sourceSampleRateHz.toFloat()).coerceIn(lowNorm + 0.0001f, 0.499f)

        for (n in 0 until effectiveBandpassTaps) {
            val k = n - m / 2
            val value = (idealLowPass(highNorm, k) - idealLowPass(lowNorm, k)) * hammingWindow(n, m)
            kernel[n] = value.toFloat()
        }

        val centerFrequencyHz = ((bandpassLowHz + bandpassHighHz) / 2f)
            .coerceIn(0.0001f, sourceSampleRateHz / 2f - 0.0001f)
        val centerGain = frequencyResponseMagnitude(kernel, centerFrequencyHz)
        if (centerGain > 1e-6) {
            for (index in kernel.indices) {
                kernel[index] = (kernel[index] / centerGain).toFloat()
            }
        }

        return kernel
    }

    private fun frequencyResponseMagnitude(kernel: FloatArray, frequencyHz: Float): Double {
        val omega = 2.0 * Math.PI * frequencyHz / sourceSampleRateHz.toDouble()
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

    private fun buildNotchCoefficients(): BiquadCoefficients? {
        if (sourceSampleRateHz <= 0 || notchFrequencyHz <= 0f) return null

        val nyquist = sourceSampleRateHz / 2.0
        val notchHz = minOf(notchFrequencyHz.toDouble(), nyquist * 0.98)
        if (notchHz <= 0.0 || notchHz >= nyquist) return null

        val omega = 2.0 * Math.PI * notchHz / sourceSampleRateHz
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

    private fun idealLowPass(normalizedCutoff: Float, n: Int): Double {
        return if (n == 0) {
            2.0 * normalizedCutoff
        } else {
            val x = Math.PI * n
            sin(2.0 * Math.PI * normalizedCutoff * n) / x
        }
    }

    private fun hammingWindow(n: Int, m: Int): Double {
        return 0.54 - 0.46 * cos((2.0 * Math.PI * n) / m)
    }

    private data class BiquadCoefficients(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double
    )

    companion object {
        private const val NOTCH_Q = 30.0
    }
}
