package com.sleepagent.prototype.sleep.processing

import com.sleepagent.prototype.device.SleepOpticalMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.sqrt

internal class SleepHrvDisplayProcessor(
    private val sourceSampleRateHz: Int = 250,
    private val bufferSeconds: Float = 90f,
    private val displaySeconds: Float = 5f,
    private val bandpassLowHz: Float = 0.6f,
    private val bandpassHighHz: Float = 4f
) {
    private val maxBufferSize = max(1, (sourceSampleRateHz * bufferSeconds).toInt())
    private val displayWindowSize = max(1, (sourceSampleRateHz * displaySeconds).toInt())
    private val minWarmupSamples = max(8, sourceSampleRateHz * 2)
    private val buffer = ArrayList<TimedSample>(maxBufferSize)
    private var smoothedHeartRateBpm: Float? = null

    fun reset() {
        buffer.clear()
        smoothedHeartRateBpm = null
    }

    fun appendSample(timestamp: Long, value: Float) {
        buffer.appendCapped(
            TimedSample(timestamp = timestamp, value = value),
            maxBufferSize
        )
    }

    fun buildSnapshot(
        selectedChannel: Int,
        selectedLabel: String,
        opticalMode: SleepOpticalMode,
        selectedValue: Float?,
        signalQuality: Float?
    ): SleepHrvSnapshot {
        val sourceMode = opticalMode.wireValue
        if (sourceMode != SleepOpticalMode.HRV.wireValue) {
            return SleepHrvSnapshot(
                selectedChannel = selectedChannel,
                selectedLabel = selectedLabel,
                sampleRateHz = sourceSampleRateHz,
                opticalMode = sourceMode,
                status = if (sourceMode == SleepOpticalMode.FNIRS.wireValue) "inactive" else "off",
                selectedValue = selectedValue,
                signalQuality = signalQuality
            )
        }

        if (buffer.isEmpty()) {
            return SleepHrvSnapshot(
                selectedChannel = selectedChannel,
                selectedLabel = selectedLabel,
                sampleRateHz = sourceSampleRateHz,
                opticalMode = sourceMode,
                status = "waiting_for_samples",
                selectedValue = selectedValue,
                signalQuality = 20f
            )
        }

        val window = displayWindow()
        if (window.isEmpty()) {
            return SleepHrvSnapshot(
                selectedChannel = selectedChannel,
                selectedLabel = selectedLabel,
                sampleRateHz = sourceSampleRateHz,
                opticalMode = sourceMode,
                status = "waiting_for_samples",
                selectedValue = selectedValue,
                signalQuality = 20f
            )
        }

        val rawSeries = window.map { it.value }
        val processingSampleRateHz = sourceSampleRateHz
        val filteredSeries = bandpassSmooth(rawSeries, processingSampleRateHz)
        val peaks = detectPeaks(filteredSeries, processingSampleRateHz)
        val ibiValuesMs = buildIbiValues(peaks, processingSampleRateHz)
        val summary = summarizeIbi(ibiValuesMs)
        val heartRateBpm = if (ibiValuesMs.size >= 3) {
            summary.heartRateBpm?.let { smoothHeartRate(it) }
        } else {
            null
        }
        val peakSeries = peaks.map { filteredSeries[it] }
        val quality = signalQualityIndex(peaks, ibiValuesMs)

        val status = when {
            rawSeries.size < minWarmupSamples -> "warming_up"
            quality != null && quality < 45f -> "low_quality"
            ibiValuesMs.isEmpty() -> "waiting_for_peaks"
            else -> "ready"
        }

        return SleepHrvSnapshot(
            selectedChannel = selectedChannel,
            selectedLabel = selectedLabel,
            sampleRateHz = processingSampleRateHz,
            opticalMode = sourceMode,
            status = status,
            selectedValue = selectedValue,
            series = filteredSeries,
            rawSeries = rawSeries,
            filteredSeries = filteredSeries,
            peakSeries = peakSeries,
            ibiSeries = ibiValuesMs.map { it.toFloat() },
            heartRateBpm = heartRateBpm,
            ibiMs = summary.ibiMs,
            rmssdMs = summary.rmssdMs,
            sdnnMs = summary.sdnnMs,
            pnn50Percent = summary.pnn50Percent,
            signalQuality = quality ?: signalQuality
        )
    }

    private fun displayWindow(): List<TimedSample> {
        if (buffer.isEmpty()) return emptyList()
        return buffer.takeLast(displayWindowSize)
    }

    private fun bandpassSmooth(values: List<Float>, sampleRateHz: Int): List<Float> {
        if (values.size < 5) return values

        val centered = values.map { it - values.average().toFloat() }
        val sampleRate = sampleRateHz.coerceAtLeast(1)
        val nyquist = sampleRate / 2f
        val highCutoffHz = minOf(bandpassHighHz, nyquist * 0.9f)
        val lowCutoffHz = minOf(bandpassLowHz, max(highCutoffHz - 0.1f, 0.1f))
        if (highCutoffHz <= lowCutoffHz) return centered

        val forward = applyLowPass(
            applyHighPass(centered, sampleRate, lowCutoffHz),
            sampleRate,
            highCutoffHz
        )
        return applyLowPass(
            applyHighPass(forward.asReversed(), sampleRate, lowCutoffHz),
            sampleRate,
            highCutoffHz
        ).asReversed()
    }

    private fun applyHighPass(samples: List<Float>, sampleRateHz: Int, cutoffHz: Float): List<Float> {
        if (samples.isEmpty()) return emptyList()

        val dt = 1.0 / sampleRateHz.coerceAtLeast(1).toDouble()
        val rc = 1.0 / (2.0 * PI * cutoffHz.toDouble())
        val alpha = rc / (rc + dt)

        val output = ArrayList<Float>(samples.size)
        var previousOutput = 0.0
        var previousInput = samples.first().toDouble()
        for (sample in samples) {
            val input = sample.toDouble()
            val value = alpha * (previousOutput + input - previousInput)
            output.add(value.toFloat())
            previousOutput = value
            previousInput = input
        }
        return output
    }

    private fun applyLowPass(samples: List<Float>, sampleRateHz: Int, cutoffHz: Float): List<Float> {
        if (samples.isEmpty()) return emptyList()

        val dt = 1.0 / sampleRateHz.coerceAtLeast(1).toDouble()
        val rc = 1.0 / (2.0 * PI * cutoffHz.toDouble())
        val alpha = dt / (rc + dt)

        val output = ArrayList<Float>(samples.size)
        var previousOutput = samples.first().toDouble()
        for (sample in samples) {
            val input = sample.toDouble()
            previousOutput += alpha * (input - previousOutput)
            output.add(previousOutput.toFloat())
        }
        return output
    }

    private fun detectPeaks(filtered: List<Float>, sampleRateHz: Int): List<Int> {
        if (filtered.size < 8) return emptyList()

        val threshold = filtered.average().toFloat() + filtered.stdDev() * 0.35f
        val minDistance = max(1, (sampleRateHz * 60f / 150f).toInt())
        val peaks = ArrayList<Int>()
        var lastPeakIndex = -minDistance
        for (index in 1 until filtered.lastIndex) {
            if (index - lastPeakIndex < minDistance) continue
            val prev = filtered[index - 1]
            val current = filtered[index]
            val next = filtered[index + 1]
            if (current >= prev && current > next && current > threshold) {
                peaks.add(index)
                lastPeakIndex = index
            }
        }
        return peaks
    }

    private fun buildIbiValues(peaks: List<Int>, sampleRateHz: Int): List<Long> {
        if (peaks.size < 2) return emptyList()
        val safeSampleRate = sampleRateHz.coerceAtLeast(1)
        return peaks.zipWithNext { a, b ->
            (((b - a).toDouble() * 1000.0) / safeSampleRate.toDouble()).toLong().coerceAtLeast(1L)
        }.filter { it in 350L..1500L }
    }

    private fun summarizeIbi(ibiValuesMs: List<Long>): HrvSummary {
        if (ibiValuesMs.isEmpty()) {
            return HrvSummary()
        }

        val recent = ibiValuesMs.takeLast(minOf(16, ibiValuesMs.size)).map { it.toFloat() }
        val robustIbiMs = robustRecentIbiMs(recent)
        val heartRateBpm = 60_000f / max(1f, robustIbiMs)

        if (recent.size < 2) {
            return HrvSummary(
                heartRateBpm = heartRateBpm,
                ibiMs = robustIbiMs
            )
        }

        val diffs = recent.zipWithNext { a, b -> b - a }
        val rmssd = if (diffs.isNotEmpty()) sqrt(diffs.map { it * it }.average().toFloat()) else null
        val sdnn = if (recent.size > 1) recent.stdDev() else null
        val pnn50 = if (diffs.isNotEmpty()) diffs.count { abs(it) > 50f } * 100f / diffs.size else null
        return HrvSummary(
            heartRateBpm = heartRateBpm,
            ibiMs = robustIbiMs,
            rmssdMs = rmssd,
            sdnnMs = sdnn,
            pnn50Percent = pnn50
        )
    }

    private fun robustRecentIbiMs(recentIbiMs: List<Float>): Float {
        if (recentIbiMs.isEmpty()) return 0f
        if (recentIbiMs.size <= 3) return recentIbiMs.median()

        val sortedValues = recentIbiMs.sorted()
        val trim = minOf(
            maxOf((sortedValues.size * 0.15f).toInt(), 0),
            maxOf((sortedValues.size - 1) / 2, 0)
        )
        val trimmed = if (trim > 0) {
            sortedValues.subList(trim, sortedValues.size - trim)
        } else {
            sortedValues
        }
        return trimmed.median()
    }

    private fun smoothHeartRate(rawHeartRateBpm: Float): Float {
        val bounded = if (smoothedHeartRateBpm == null) {
            rawHeartRateBpm
        } else {
            val previous = smoothedHeartRateBpm!!
            previous + (rawHeartRateBpm.coerceIn(previous - 6f, previous + 6f) - previous) * 0.22f
        }
        smoothedHeartRateBpm = bounded
        return bounded
    }

    private fun signalQualityIndex(peaks: List<Int>, ibiValuesMs: List<Long>): Float? {
        if (peaks.size < 2 || ibiValuesMs.isEmpty()) return 20f

        var score = 100f
        val validRatio = ibiValuesMs.count { it in 350L..1500L }.toFloat() / ibiValuesMs.size.toFloat()
        score -= (1f - validRatio) * 35f

        if (ibiValuesMs.size >= 3) {
            val recent = ibiValuesMs.takeLast(minOf(16, ibiValuesMs.size)).map { it.toFloat() }
            val mean = recent.average().toFloat()
            val coefficient = recent.stdDev() / max(mean, 1e-6f)
            score -= minOf(25f, coefficient * 80f)
        }

        score += minOf(10f, minOf(peaks.size, 12) * 0.8f)
        return score.coerceIn(0f, 100f)
    }

    private fun MutableList<TimedSample>.appendCapped(value: TimedSample, maxSize: Int) {
        add(value)
        while (size > maxSize) {
            removeAt(0)
        }
    }

    private fun List<Float>.stdDev(): Float {
        if (size < 2) return 0f
        val mean = average().toFloat()
        val variance = map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }

    private fun List<Float>.median(): Float {
        if (isEmpty()) return 0f
        val sortedValues = sorted()
        val middle = sortedValues.size / 2
        return if (sortedValues.size % 2 == 0) {
            (sortedValues[middle - 1] + sortedValues[middle]) / 2f
        } else {
            sortedValues[middle]
        }
    }

    private data class TimedSample(
        val timestamp: Long,
        val value: Float
    )

    private data class HrvSummary(
        val heartRateBpm: Float? = null,
        val ibiMs: Float? = null,
        val rmssdMs: Float? = null,
        val sdnnMs: Float? = null,
        val pnn50Percent: Float? = null
    )
}
