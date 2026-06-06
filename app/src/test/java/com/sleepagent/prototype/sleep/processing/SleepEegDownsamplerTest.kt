package com.sleepagent.prototype.sleep.processing

import com.sleepagent.prototype.device.HeadbandRawPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepEegDownsamplerTest {
    @Test
    fun outputsHundredSamplesFromTwoHundredFiftyRawSamples() {
        val downsampler = SleepEegDownsampler()
        var outputCount = 0

        repeat(250) { index ->
            if (downsampler.ingest(packet(index, 10f)) != null) {
                outputCount += 1
            }
        }

        assertEquals(100, outputCount)
    }

    @Test
    fun preservesConstantSignalMean() {
        val downsampler = SleepEegDownsampler()
        val outputs = mutableListOf<Float>()

        repeat(25) { index ->
            downsampler.ingest(packet(index, 42f))?.let { outputs += it.valueMicrovolts }
        }

        assertTrue(outputs.isNotEmpty())
        outputs.forEach { value ->
            assertEquals(42f, value, 1e-6f)
        }
    }

    @Test
    fun usesAlternatingThreeAndTwoSampleBuckets() {
        val downsampler = SleepEegDownsampler()
        val outputs = mutableListOf<Float>()

        repeat(10) { index ->
            downsampler.ingest(packet(index, index.toFloat()))?.let { outputs += it.valueMicrovolts }
        }

        assertEquals(listOf(1f, 3.5f, 6f, 8.5f), outputs)
    }

    private fun packet(index: Int, eegValue: Float): HeadbandRawPacket {
        val counts = IntArray(8)
        val microvolts = FloatArray(8)
        counts[5] = eegValue.toInt()
        microvolts[5] = eegValue
        return HeadbandRawPacket(
            hostTimestamp = index.toLong(),
            sequence = index and 0xFF,
            sampleNumber = index and 0xFF,
            deviceTimestamp = index.toLong(),
            state = null,
            eegCounts = counts,
            eegMicrovolts = microvolts,
            signalQuality = null,
            batteryLevel = null
        )
    }
}
