package com.sleepagent.prototype.report

import com.sleepagent.prototype.data.SleepEpochRecord
import com.sleepagent.prototype.data.SleepStage
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SleepStageChartTest {

    private val baseTime = Instant.parse("2025-06-12T15:30:00Z").toEpochMilli()
    private val epochMs = 30_000L

    private fun makeEpoch(index: Int, stage: SleepStage, offsetMs: Long = 0): SleepEpochRecord {
        val start = baseTime + index * epochMs + offsetMs
        return SleepEpochRecord(
            sessionId = "test-session",
            epochIndex = index,
            startAtEpochMs = start,
            endAtEpochMs = start + epochMs,
            stage = stage
        )
    }

    // ── 1. Segment merging ──

    @Test
    fun `consecutive same stages are merged`() {
        val epochs = listOf(
            makeEpoch(0, SleepStage.AWAKE),
            makeEpoch(1, SleepStage.AWAKE),
            makeEpoch(2, SleepStage.AWAKE),
        )
        val segments = buildSleepSegments(epochs)
        assertEquals(1, segments.size)
        assertEquals(SleepStage.AWAKE, segments[0].stage)
        assertEquals(baseTime, segments[0].startTimeMillis)
        assertEquals(baseTime + 3 * epochMs, segments[0].endTimeMillis)
    }

    @Test
    fun `different stages are not merged`() {
        val epochs = listOf(
            makeEpoch(0, SleepStage.AWAKE),
            makeEpoch(1, SleepStage.REM),
            makeEpoch(2, SleepStage.DEEP),
        )
        val segments = buildSleepSegments(epochs)
        assertEquals(3, segments.size)
        assertEquals(SleepStage.AWAKE, segments[0].stage)
        assertEquals(SleepStage.REM, segments[1].stage)
        assertEquals(SleepStage.DEEP, segments[2].stage)
    }

    @Test
    fun `mixed consecutive same and different stages`() {
        val epochs = listOf(
            makeEpoch(0, SleepStage.AWAKE),
            makeEpoch(1, SleepStage.AWAKE),
            makeEpoch(2, SleepStage.REM),
            makeEpoch(3, SleepStage.REM),
            makeEpoch(4, SleepStage.DEEP),
        )
        val segments = buildSleepSegments(epochs)
        assertEquals(3, segments.size)
        assertEquals(SleepStage.AWAKE, segments[0].stage)
        assertEquals(SleepStage.REM, segments[1].stage)
        assertEquals(SleepStage.DEEP, segments[2].stage)
        assertEquals(2, segments[0].endEpochIndex - segments[0].startEpochIndex + 1)
    }

    // ── 2. NO_DATA gaps ──

    @Test
    fun `time gap inserts NO_DATA`() {
        val epochs = listOf(
            makeEpoch(0, SleepStage.AWAKE),
            makeEpoch(5, SleepStage.AWAKE), // 5 epoch gap = 150s > 60s threshold
        )
        val segments = buildSleepSegments(epochs)
        assertEquals(3, segments.size) // AWAKE, NO_DATA, AWAKE
        assertEquals(SleepStage.UNKNOWN, segments[1].stage)
    }

    @Test
    fun `small gap does not insert NO_DATA`() {
        val epochs = listOf(
            makeEpoch(0, SleepStage.AWAKE),
            makeEpoch(1, SleepStage.AWAKE),
        )
        val segments = buildSleepSegments(epochs)
        assertEquals(1, segments.size)
    }

    @Test
    fun `NO_DATA is never mapped to Wake`() {
        val epochs = listOf(
            makeEpoch(0, SleepStage.AWAKE),
            makeEpoch(10, SleepStage.DEEP),
        )
        val segments = buildSleepSegments(epochs)
        val noDataSeg = segments.find { it.stage == SleepStage.UNKNOWN }
        assertNotNull(noDataSeg)
        assertEquals(ChartRenderStage.NO_DATA, mapToRenderStage(SleepStage.UNKNOWN, SleepStageDisplayMode.DETAILED))
    }

    // ── 3. Sorting ──

    @Test
    fun `epochs are sorted by start time`() {
        val epochs = listOf(
            makeEpoch(3, SleepStage.DEEP),
            makeEpoch(1, SleepStage.AWAKE),
            makeEpoch(2, SleepStage.REM),
        )
        val segments = buildSleepSegments(epochs)
        assertEquals(baseTime + 1 * epochMs, segments[0].startTimeMillis)
    }

    @Test
    fun `unordered epochs produce correctly ordered segments`() {
        val epochs = listOf(
            makeEpoch(5, SleepStage.DEEP),
            makeEpoch(0, SleepStage.AWAKE),
            makeEpoch(3, SleepStage.REM),
        )
        val segments = buildSleepSegments(epochs)
        assertEquals(SleepStage.AWAKE, segments[0].stage)
        assertEquals(SleepStage.REM, segments[1].stage)
        assertEquals(SleepStage.DEEP, segments.last().stage)
    }

    // ── 4. Simple vs Detailed mode ──

    @Test
    fun `SIMPL E mode does not separate N1 and N2`() {
        val epochs = listOf(
            makeEpoch(0, SleepStage.AWAKE),
            makeEpoch(1, SleepStage.LIGHT),
            makeEpoch(2, SleepStage.LIGHT),
            makeEpoch(3, SleepStage.DEEP),
        )
        val sum = buildStageSummaries(epochs, SleepStageDisplayMode.SIMPLE)
        val lightSum = sum.find { it.label == "浅睡" }
        assertNotNull(lightSum)
        // Simple mode should have exactly: 清醒, REM, 浅睡, 深睡
        assertEquals(4, sum.size)
    }

    @Test
    fun `DETAILED mode has more stages`() {
        val epochs = listOf(
            makeEpoch(0, SleepStage.AWAKE),
            makeEpoch(1, SleepStage.LIGHT),
            makeEpoch(2, SleepStage.LIGHT),
            makeEpoch(3, SleepStage.DEEP),
            makeEpoch(4, SleepStage.REM),
        )
        val sum = buildStageSummaries(epochs, SleepStageDisplayMode.DETAILED)
        // Should have: 清醒, REM, N1, N2, N3 (深睡)
        assertEquals(5, sum.size)
    }

    // ── 5. Duration calculations ──

    @Test
    fun `stage durations are correct`() {
        val epochs = listOf(
            makeEpoch(0, SleepStage.AWAKE),
            makeEpoch(1, SleepStage.DEEP),
            makeEpoch(2, SleepStage.DEEP),
            makeEpoch(3, SleepStage.DEEP),
        )
        val summary = buildStageSummaries(epochs, SleepStageDisplayMode.SIMPLE)
        val awake = summary.find { it.label == "清醒" }!!
        val deep = summary.find { it.label == "深睡" }!!
        assertEquals(30_000L, awake.durationMs)
        assertEquals(90_000L, deep.durationMs)
    }

    @Test
    fun `formatDurationShort produces correct strings`() {
        assertEquals("1分", formatDurationShort(60_000L))
        assertEquals("5分", formatDurationShort(300_000L))
        assertEquals("1时", formatDurationShort(3_600_000L))
        assertEquals("1时30分", formatDurationShort(5_400_000L))
        assertEquals("2时", formatDurationShort(7_200_000L))
    }

    // ── 6. Edge cases ──

    @Test
    fun `single epoch produces one segment`() {
        val epochs = listOf(makeEpoch(0, SleepStage.AWAKE))
        val segments = buildSleepSegments(epochs)
        assertEquals(1, segments.size)
    }

    @Test
    fun `empty epochs produce empty segments`() {
        val segments = buildSleepSegments(emptyList())
        assertTrue(segments.isEmpty())
    }

    @Test
    fun `very short stage is still visible`() {
        val epochs = listOf(
            makeEpoch(0, SleepStage.AWAKE),
            makeEpoch(1, SleepStage.AWAKE),
            makeEpoch(2, SleepStage.REM),
            makeEpoch(3, SleepStage.AWAKE),
        )
        val segments = buildSleepSegments(epochs)
        assertEquals(3, segments.size) // AWAKE, REM, AWAKE
        assertEquals(1, segments[1].endEpochIndex - segments[1].startEpochIndex + 1)
    }

    @Test
    fun `UNKNOWN stage mapped to NO_DATA`() {
        assertEquals(
            ChartRenderStage.NO_DATA,
            mapToRenderStage(SleepStage.UNKNOWN, SleepStageDisplayMode.DETAILED)
        )
    }

    @Test
    fun `formatHhmm returns correct time string`() {
        val epoch = Instant.parse("2025-06-12T15:30:00Z").toEpochMilli()
        val result = formatHhmm(epoch)
        assertTrue(result.matches(Regex("\\d{2}:\\d{2}")))
    }

    // ── 7. Summary labels ──

    @Test
    fun `summary percent does not exceed 100`() {
        val epochs = (0 until 10).map { makeEpoch(it, SleepStage.DEEP) }
        val sum = buildStageSummaries(epochs, SleepStageDisplayMode.SIMPLE)
        val totalPercent = sum.sumOf { (it.percent * 100).toInt() }
        // Deep = 100%, others 0% = total 100%
        assertTrue(totalPercent <= 100)
    }

    @Test
    fun `awake percent based on inBed time in SIMPLE mode`() {
        val epochs = (0 until 10).map { makeEpoch(it, SleepStage.AWAKE) }
        val sum = buildStageSummaries(epochs, SleepStageDisplayMode.SIMPLE)
        val awake = sum.find { it.label == "清醒" }!!
        // Duration = 300000ms correctly
        assertEquals(300_000L, awake.durationMs)
    }
}
