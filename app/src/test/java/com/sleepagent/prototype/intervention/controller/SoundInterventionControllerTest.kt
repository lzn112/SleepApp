package com.sleepagent.prototype.intervention.controller

import com.sleepagent.prototype.data.SleepStage
import com.sleepagent.prototype.data.SoundInterventionEventEntity
import com.sleepagent.prototype.data.SoundInterventionEventTypes as Events
import com.sleepagent.prototype.intervention.audio.*
import com.sleepagent.prototype.intervention.model.RealtimeSleepSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class SoundInterventionControllerTest {
    private class Pulse : PulseAudioPlayer {
        var calls = 0
        var stops = 0
        var result: PulsePlaybackResult = PulsePlaybackResult.Playing
        override var isActive = false
        override fun prepare() {}
        override fun schedulePulse(targetElapsedRealtimeNanos: Long, gain: Float) = result
        override fun playPulseNow(gain: Float): PulsePlaybackResult { calls++; return result }
        override fun stop() { stops++; isActive = false }
        override fun release() {}
    }
    private class Background : BackgroundAudioPlayer {
        override val state = MutableStateFlow(AudioPlaybackState())
        override suspend fun playLoop(resourceId: Int, gain: Float) {}
        override suspend fun playOnce(resourceId: Int, gain: Float) {}
        override suspend fun switchLoop(resourceId: Int, gain: Float, crossFadeDurationMs: Long) {}
        override suspend fun fadeTo(targetGain: Float, durationMs: Long) {}
        override fun pause() {}
        override fun stop() {}
        override fun release() {}
    }
    private class Fixture {
        var now = 1_000_000_000L
        val pulse = Pulse()
        val events = mutableListOf<SoundInterventionEventEntity>()
        val controller = SoundInterventionController(null, Background(), pulse,
            ioDispatcher = Dispatchers.Unconfined, nowNanos = { now }, wallTimeMillis = { 123456L })
        init {
            controller.backgroundEnabled = false
            controller.smartWakeEnabled = false
            controller.eventLogger = { events += it }
            controller.start("test-session")
        }
        fun snapshot(stage: SleepStage = SleepStage.DEEP) = RealtimeSleepSnapshot(
            123456L, now, stage, mapOf(stage to .9f), eegQuality = .95f,
            isRealModelResult = true, stageEpochIndex = (now / 30_000_000_000L).toInt(), stageAgeMillis = 0)
        fun tick(stage: SleepStage = SleepStage.DEEP) { now += 1_000_000_000L; controller.onSnapshot(snapshot(stage)) }
        fun stableN3() { controller.onSnapshot(snapshot()); repeat(120) { tick() } }
        fun alphaSignal() { repeat(300) { controller.ingestEegSample((20 * sin(it * 2 * Math.PI * 10 / 100)).toFloat()) } }
        fun close() = controller.release()
    }

    @Test fun `start does not play open loop and repeated start is idempotent`() {
        val f = Fixture()
        try {
            f.controller.start("other")
            assertEquals(0, f.pulse.calls)
            assertEquals(1, f.events.count { it.eventType == Events.SESSION_STARTED })
        } finally { f.close() }
    }
    @Test fun `N3 waits for stability and records feedback with accepted pulse`() {
        val f = Fixture()
        try {
            f.controller.onSnapshot(f.snapshot())
            repeat(119) { f.tick() }
            assertEquals(0, f.pulse.calls)
            f.tick()
            assertEquals(1, f.pulse.calls)
            val event = f.events.last { it.eventType == Events.N3_PULSE_TRIGGERED }
            assertEquals("test-session", event.sessionId)
            assertEquals("DEEP", event.sleepStage)
            assertEquals(.95f, event.eegQuality!!, 0f)
            assertEquals(true, event.success)
            assertEquals(1, event.groupIndex)
        } finally { f.close() }
    }
    @Test fun `N3 has eight pulses per group and ten groups per session`() {
        val f = Fixture()
        try {
            f.stableN3()
            repeat(1200) { f.tick() }
            assertEquals(80, f.pulse.calls)
            assertEquals(10, f.controller.n3GroupCount)
            assertEquals(10, f.events.count { it.eventType == Events.N3_GROUP_FINISHED })
            assertEquals(80, f.events.count { it.eventType == Events.N3_PULSE_TRIGGERED })
        } finally { f.close() }
    }
    @Test fun `stage change interrupts N3 and requires fresh stability`() {
        val f = Fixture()
        try {
            f.stableN3(); val count = f.pulse.calls
            f.tick(SleepStage.REM)
            repeat(119) { f.tick() }
            assertEquals(count, f.pulse.calls)
            assertTrue(f.events.any { it.reason == "stage_change" })
        } finally { f.close() }
    }
    @Test fun `invalid feedback prevents pulses and resets stability`() {
        val variants: List<(RealtimeSleepSnapshot) -> RealtimeSleepSnapshot> = listOf(
            { it.copy(eegQuality = .1f) }, { it.copy(eegQuality = Float.NaN) },
            { it.copy(isDeviceConnected = false) }, { it.copy(isRealModelResult = false) },
            { it.copy(stageAgeMillis = 46000) }, { it.copy(motionLevel = .5f) },
            { it.copy(elapsedRealtimeNanos = it.elapsedRealtimeNanos - 3_000_000_000L) })
        variants.forEach { invalid ->
            val f = Fixture()
            try {
                f.stableN3(); val count = f.pulse.calls
                f.controller.onSnapshot(invalid(f.snapshot()))
                repeat(119) { f.tick() }
                assertEquals(count, f.pulse.calls)
            } finally { f.close() }
        }
    }
    @Test fun `watchdog stops on missing packets and coalesces skip logs`() {
        val f = Fixture()
        try {
            f.stableN3(); val stops = f.pulse.stops
            f.now += 3_000_000_000L
            repeat(20) { f.controller.checkDataTimeout() }
            assertTrue(f.pulse.stops > stops)
            assertEquals(2, f.events.count { it.eventType.endsWith("SKIPPED") && it.reason == "data_timeout" })
        } finally { f.close() }
    }
    @Test fun `alpha needs EEG feedback and obeys refractory and session duration`() {
        val f = Fixture()
        try {
            f.controller.onSnapshot(f.snapshot(SleepStage.AWAKE))
            assertEquals(0, f.pulse.calls)
            f.alphaSignal()
            f.controller.onSnapshot(f.snapshot(SleepStage.AWAKE))
            assertEquals(1, f.pulse.calls)
            f.tick(SleepStage.AWAKE)
            assertEquals(1, f.pulse.calls)
            f.tick(SleepStage.AWAKE)
            assertEquals(2, f.pulse.calls)
            f.controller.alphaMaxDurationMinutes = 0
            f.tick(SleepStage.AWAKE)
            assertEquals(2, f.pulse.calls)
            assertEquals(2, f.events.count { it.eventType == Events.ALPHA_PULSE_TRIGGERED })
        } finally { f.close() }
    }
    @Test fun `playback failures are logged without incrementing delivered count`() {
        val f = Fixture()
        try {
            f.pulse.result = PulsePlaybackResult.Error("audio failure")
            f.stableN3()
            assertEquals(0, f.controller.n3PulseCount)
            val event = f.events.last { it.eventType == Events.PULSE_PLAY_FAILED }
            assertEquals(false, event.success)
            assertEquals("audio failure", event.reason)
        } finally { f.close() }
    }
    @Test fun `pause and stop prevent subsequent automatic stimulation`() {
        val f = Fixture()
        try {
            f.stableN3(); val count = f.pulse.calls
            f.controller.pauseAll()
            repeat(200) { f.tick() }
            assertEquals(count, f.pulse.calls)
            f.controller.stop(); f.controller.stop()
            assertEquals(1, f.events.count { it.eventType == Events.SESSION_STOPPED })
        } finally { f.close() }
    }
    @Test fun `smart wake requires distinct consecutive model epochs`() {
        val f = Fixture()
        try {
            f.controller.smartWakeEnabled = true
            f.controller.smartWakeWindowStartNanos = 0
            f.controller.smartWakeWindowEndNanos = Long.MAX_VALUE
            repeat(50) { f.controller.onSnapshot(f.snapshot(SleepStage.LIGHT).copy(stageEpochIndex = 10)) }
            assertFalse(f.events.any { it.eventType == Events.SMART_WAKE_TRIGGERED })
            f.controller.onSnapshot(f.snapshot(SleepStage.LIGHT).copy(stageEpochIndex = 11))
            repeat(10) { f.controller.onSnapshot(f.snapshot(SleepStage.LIGHT).copy(stageEpochIndex = 12)) }
            assertEquals(1, f.events.count { it.eventType == Events.SMART_WAKE_TRIGGERED })
        } finally { f.close() }
    }
}
