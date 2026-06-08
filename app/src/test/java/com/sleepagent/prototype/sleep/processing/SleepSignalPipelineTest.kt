package com.sleepagent.prototype.sleep.processing

import com.sleepagent.prototype.device.HeadbandRawPacket
import com.sleepagent.prototype.device.SleepOpticalMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class SleepSignalPipelineTest {
    @Test
    fun eegDisplaySeriesUsesShared100HzPathAndCapsAtFiveSeconds() {
        val pipeline = SleepSignalPipeline()
        var snapshot = SleepSignalSnapshot()

        repeat(600) { index ->
            val counts = IntArray(8)
            val microvolts = FloatArray(8)
            val value = (sin(index / 12.0 * PI) * 1000.0).toFloat()
            counts[5] = value.toInt()
            microvolts[5] = value

            snapshot = pipeline.ingest(
                packet = HeadbandRawPacket(
                    hostTimestamp = index.toLong(),
                    sequence = index and 0xFF,
                    sampleNumber = index and 0xFF,
                    deviceTimestamp = index.toLong(),
                    state = null,
                    eegCounts = counts,
                    eegMicrovolts = microvolts,
                    signalQuality = null,
                    batteryLevel = null
                ),
                opticalMode = SleepOpticalMode.OFF,
                eegSample = DownsampledEegSample(
                    timestampMillis = index.toLong(),
                    channelIndex = 5,
                    valueMicrovolts = value
                )
            )
        }

        assertEquals(100, snapshot.eeg.sampleRateHz)
        assertEquals(5, snapshot.eeg.selectedChannel)
        assertEquals("CH6", snapshot.eeg.selectedLabel)
        assertEquals(500, snapshot.eeg.rawSeries.size)
        assertEquals(436, snapshot.eeg.series.size)
        assertTrue(snapshot.eeg.series.isNotEmpty())
        assertEquals(snapshot.eeg.rawSeries.last(), snapshot.eeg.selectedValue!!)
    }

    @Test
    fun eegDisplayFilterKeepsPassbandGainNearMicrovoltInput() {
        val processor = SleepEegDisplayProcessor()
        val samples = (0 until 500).map { index ->
            (sin(2.0 * PI * 10.0 * index / 100.0) * 100.0).toFloat()
        }

        val series = processor.process(samples)
        val peak = series.map { abs(it) }.maxOrNull() ?: 0f

        assertTrue("10 Hz display gain should stay in microvolt scale, peak=$peak", peak in 70f..130f)
    }

    @Test
    fun eegDisplayFilterSuppressesSubHzBaselineDrift() {
        val processor = SleepEegDisplayProcessor()
        val samples = (0 until 500).map { index ->
            (sin(2.0 * PI * 0.2 * index / 100.0) * 1000.0).toFloat()
        }

        val series = processor.process(samples)
        val peak = series.map { abs(it) }.maxOrNull() ?: 0f

        assertTrue("sub-Hz baseline drift should be suppressed for readable EEG display, peak=$peak", peak < 80f)
    }

    @Test
    fun hrvDisplaySeriesUsesBandpassPeakPipeline() {
        val pipeline = SleepSignalPipeline()

        repeat(900) { index ->
            val counts = IntArray(8)
            val microvolts = FloatArray(8)
            val value = (sin(index / 32.0 * PI) * 16000.0).toFloat()
            counts[0] = value.toInt()
            microvolts[0] = value

            val snapshot = pipeline.ingest(
                packet = HeadbandRawPacket(
                    hostTimestamp = index.toLong(),
                    sequence = index and 0xFF,
                    sampleNumber = index and 0xFF,
                    deviceTimestamp = index.toLong(),
                    state = null,
                    eegCounts = counts,
                    eegMicrovolts = microvolts,
                    signalQuality = null,
                    batteryLevel = null
                ),
                opticalMode = SleepOpticalMode.HRV,
                eegSample = null
            )

            if (index == 899) {
                assertTrue(snapshot.hrv.status == "ready" || snapshot.hrv.status == "low_quality")
                assertTrue(snapshot.hrv.series.isNotEmpty())
                assertTrue(snapshot.hrv.filteredSeries.isNotEmpty())
                assertTrue(snapshot.hrv.rawSeries.isNotEmpty())
                assertTrue(snapshot.hrv.peakSeries.isNotEmpty())
                assertTrue(snapshot.hrv.heartRateBpm != null)
                assertEquals(snapshot.hrv.series, snapshot.hrv.filteredSeries)
            }
        }
    }

    @Test
    fun fnirsDisplaySeriesProducesHboAndHbrSnapshots() {
        val pipeline = SleepSignalPipeline()

        repeat(50) { cycle ->
            val baseCount = 8_400_000 + cycle * 100

            repeat(3) { offset ->
                val index = cycle * 6 + offset
                val counts = IntArray(8)
                val microvolts = FloatArray(8)
                counts[0] = baseCount + offset
                microvolts[0] = counts[0].toFloat()

                pipeline.ingest(
                    packet = HeadbandRawPacket(
                        hostTimestamp = index.toLong(),
                        sequence = index and 0xFF,
                        sampleNumber = index and 0xFF,
                        deviceTimestamp = index.toLong(),
                        state = 3,
                        eegCounts = counts,
                        eegMicrovolts = microvolts,
                        signalQuality = null,
                        batteryLevel = null
                    ),
                    opticalMode = SleepOpticalMode.FNIRS,
                    eegSample = null
                )
            }

            repeat(3) { offset ->
                val index = cycle * 6 + 3 + offset
                val counts = IntArray(8)
                val microvolts = FloatArray(8)
                counts[0] = baseCount + 20 + offset
                microvolts[0] = counts[0].toFloat()

                pipeline.ingest(
                    packet = HeadbandRawPacket(
                        hostTimestamp = index.toLong(),
                        sequence = index and 0xFF,
                        sampleNumber = index and 0xFF,
                        deviceTimestamp = index.toLong(),
                        state = 2,
                        eegCounts = counts,
                        eegMicrovolts = microvolts,
                        signalQuality = null,
                        batteryLevel = null
                    ),
                    opticalMode = SleepOpticalMode.FNIRS,
                    eegSample = null
                )
            }
        }

        val counts = IntArray(8)
        val microvolts = FloatArray(8)
        counts[0] = 8_405_000
        microvolts[0] = counts[0].toFloat()

        val snapshot = pipeline.ingest(
            packet = HeadbandRawPacket(
                hostTimestamp = 9999L,
                sequence = 0,
                sampleNumber = 0,
                deviceTimestamp = 9999L,
                state = 3,
                eegCounts = counts,
                eegMicrovolts = microvolts,
                signalQuality = null,
                batteryLevel = null
            ),
            opticalMode = SleepOpticalMode.FNIRS,
            eegSample = null
        )

        assertEquals("ready", snapshot.fnirs.status)
        assertTrue(snapshot.fnirs.hboSeries.isNotEmpty())
        assertTrue(snapshot.fnirs.hbrSeries.isNotEmpty())
        assertTrue(snapshot.fnirs.hbo != null)
        assertTrue(snapshot.fnirs.hbr != null)
        assertTrue(snapshot.fnirs.selectedIntensity780 != null)
        assertTrue(snapshot.fnirs.selectedIntensity850 != null)
    }
}
