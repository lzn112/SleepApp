package com.sleepagent.prototype.sleep.staging

import com.sleepagent.prototype.sleep.processing.DownsampledEegSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepStagePipelineTest {
    @Test
    fun snapshotTracksProgressBeforeEpochCompletes() {
        val pipeline = SleepStagePipeline()

        repeat(2999) { index ->
            pipeline.ingest(sample(index))
        }

        val snapshot = pipeline.snapshot()
        assertEquals(100, snapshot.sampleRateHz)
        assertEquals(3000, snapshot.epochSizeSamples)
        assertEquals(2999, snapshot.bufferedSamples)
        assertNull(snapshot.latestResult)
        assertTrue(snapshot.epochProgress > 0.99f)
    }

    @Test
    fun doesNotInferUntilFiveEpochsComplete() {
        val pipeline = SleepStagePipeline()

        repeat(3000 * 4) { index ->
            pipeline.ingest(sample(index))
        }

        val snapshot = pipeline.snapshot()
        assertNull(snapshot.latestResult)
        assertEquals(0, snapshot.hypnogram.size)
        assertEquals(0, snapshot.bufferedSamples)
    }

    @Test
    fun infersCenterEpochWhenFiveEpochsComplete() {
        val pipeline = SleepStagePipeline()

        repeat(3000 * 5) { index ->
            pipeline.ingest(sample(index))
        }

        val snapshot = pipeline.snapshot()
        assertNotNull(snapshot.latestResult)
        assertEquals(SleepStage.Wake, snapshot.currentStage)
        assertEquals(0, snapshot.bufferedSamples)
        assertEquals(1, snapshot.hypnogram.size)
        assertEquals(2, snapshot.latestResult?.epochIndex)
        assertEquals(0f, snapshot.epochProgress, 1e-6f)
    }

    @Test
    fun slidesOneEpochAtATimeAfterFirstPrediction() {
        val pipeline = SleepStagePipeline()

        repeat(3000 * 6) { index ->
            pipeline.ingest(sample(index))
        }

        val snapshot = pipeline.snapshot()
        assertNotNull(snapshot.latestResult)
        assertEquals(2, snapshot.hypnogram.size)
        assertEquals(3, snapshot.latestResult?.epochIndex)
        assertEquals(0, snapshot.bufferedSamples)
    }

    private fun sample(index: Int): DownsampledEegSample {
        return DownsampledEegSample(
            timestampMillis = index.toLong(),
            channelIndex = 5,
            valueMicrovolts = index.toFloat()
        )
    }
}