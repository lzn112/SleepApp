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
    fun completesInferenceAtThreeThousandSamples() {
        val pipeline = SleepStagePipeline()

        repeat(3000) { index ->
            pipeline.ingest(sample(index))
        }

        val snapshot = pipeline.snapshot()
        assertNotNull(snapshot.latestResult)
        assertEquals(SleepStage.Wake, snapshot.currentStage)
        assertEquals(0, snapshot.bufferedSamples)
        assertEquals(1, snapshot.hypnogram.size)
        assertEquals(0f, snapshot.epochProgress, 1e-6f)
    }

    private fun sample(index: Int): DownsampledEegSample {
        return DownsampledEegSample(
            timestampMillis = index.toLong(),
            channelIndex = 5,
            valueMicrovolts = index.toFloat()
        )
    }
}
