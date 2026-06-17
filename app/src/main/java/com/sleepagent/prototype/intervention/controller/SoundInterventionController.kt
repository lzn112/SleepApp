package com.sleepagent.prototype.intervention.controller

import android.content.Context
import android.os.SystemClock
import com.sleepagent.prototype.data.SleepStage
import com.sleepagent.prototype.data.SoundInterventionEventEntity
import com.sleepagent.prototype.data.SoundInterventionEventTypes
import com.sleepagent.prototype.intervention.audio.AudioPlaybackState
import com.sleepagent.prototype.intervention.audio.AudioTrackPulseAudioPlayer
import com.sleepagent.prototype.intervention.audio.BackgroundAudioPlayer
import com.sleepagent.prototype.intervention.audio.Media3BackgroundAudioPlayer
import com.sleepagent.prototype.intervention.audio.PinkNoiseGenerator
import com.sleepagent.prototype.intervention.audio.PulseAudioPlayer
import com.sleepagent.prototype.intervention.audio.PulsePlaybackResult
import com.sleepagent.prototype.intervention.alpha.AlphaTriggerScheduler
import com.sleepagent.prototype.intervention.alarm.SmartAlarmScheduler
import com.sleepagent.prototype.intervention.model.AlphaInterventionState
import com.sleepagent.prototype.intervention.model.RealtimeSleepSnapshot
import com.sleepagent.prototype.intervention.model.SoundInterventionState
import com.sleepagent.prototype.intervention.model.SoundInterventionType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Central controller for all sleep sound interventions.
 *
 * Manages state transitions, prevents conflicts, and orchestrates
 * background audio, alpha intervention, N3 stimulation, and smart wake.
 *
 * Created and owned by [SleepRecordingService].
 */
class SoundInterventionController(
    private val context: Context,
    private val backgroundPlayer: BackgroundAudioPlayer = Media3BackgroundAudioPlayer(context),
    private val pulsePlayer: PulseAudioPlayer = AudioTrackPulseAudioPlayer(),
    private val pinkNoiseGenerator: PinkNoiseGenerator = PinkNoiseGenerator(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /** Callback for persisting intervention events to the database. */
    var eventLogger: ((SoundInterventionEventEntity) -> Unit)? = null

    private val alphaScheduler = AlphaTriggerScheduler()
    private val alarmScheduler: SmartAlarmScheduler = SmartAlarmScheduler(context)

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var interventionJob: Job? = null
    private var prepared: Boolean = false

    private val _state = MutableStateFlow(SoundInterventionState.IDLE)
    val state: StateFlow<SoundInterventionState> = _state.asStateFlow()

    private val _backgroundState = MutableStateFlow(AudioPlaybackState())
    val backgroundPlaybackState: StateFlow<AudioPlaybackState> = _backgroundState.asStateFlow()

    private val _alphaState = MutableStateFlow(AlphaInterventionState())
    val alphaState: StateFlow<AlphaInterventionState> = _alphaState.asStateFlow()

    // N3 counters
    var n3GroupCount: Int = 0
        private set
    var n3PulseCount: Int = 0
        private set

    // Configuration
    var backgroundEnabled: Boolean = true
    var alphaEnabled: Boolean = false
    var deepSleepEnabled: Boolean = false
    var smartWakeEnabled: Boolean = true

    var smartWakeWindowStartNanos: Long = 0L
    var smartWakeWindowEndNanos: Long = 0L

    var latestWakeMinutesFromMidnight: Int = 7 * 60 + 30
    var wakeWindowMinutes: Int = 30

    private var sessionId: String? = null
    private var sleepStartNanos: Long = 0L
    private var stableN3StartNanos: Long = 0L
    private var previousStage: SleepStage? = null
    private var stageStableStartNanos: Long = 0L
    private var consecutiveLightWakeCount: Int = 0
    private var lastN3PulseNanos: Long = 0L
    private var n3GroupPauseStartNanos: Long = 0L
    private var inN3GroupPause: Boolean = false
    private var n3PulsesInGroup: Int = 0

    /**
     * Start the intervention controller for a sleep session.
     * Must be called when sleep recording begins.
     */
    fun start(sessionId: String? = null) {
        if (_state.value != SoundInterventionState.IDLE &&
            _state.value != SoundInterventionState.STOPPED) return

        this.sessionId = sessionId
        reset()
        ensurePrepared()

        _state.value = SoundInterventionState.IDLE
        sleepStartNanos = SystemClock.elapsedRealtimeNanos()
        initializeSmartWakeWindow(sleepStartNanos)

        logEvent(
            type = SoundInterventionType.BACKGROUND,
            eventType = SoundInterventionEventTypes.SESSION_STARTED,
            success = true
        )

        // Start background audio if enabled
        if (backgroundEnabled) {
            startBackgroundAudio()
        }

        // Schedule fallback alarm
        if (smartWakeEnabled) {
            scheduleFallbackAlarm()
        }
    }

    /**
     * Stop all interventions and release resources.
     */
    fun stop() {
        logEvent(
            type = SoundInterventionType.BACKGROUND,
            eventType = SoundInterventionEventTypes.SESSION_STOPPED,
            success = true
        )
        alarmScheduler.cancel()
        interventionJob?.cancel()
        interventionJob = null
        backgroundPlayer.stop()
        pulsePlayer.stop()
        _state.value = SoundInterventionState.STOPPED
    }

    /**
     * Called each time new sleep data is available.
     */
    fun onSnapshot(snapshot: RealtimeSleepSnapshot) {
        if (_state.value == SoundInterventionState.STOPPED ||
            _state.value == SoundInterventionState.FALLBACK_ALARMING) return

        val stage = snapshot.sleepStage
        val now = SystemClock.elapsedRealtimeNanos()

        // Track stage stability
        if (stage != previousStage) {
            stageStableStartNanos = now
        }
        previousStage = stage

        val stableMs = (now - stageStableStartNanos) / 1_000_000L

        // Priority: Smart Wake > N3 > Alpha > Background
        when {
            // Smart wake window check (also handles expiry → fallback alarm)
            smartWakeEnabled && now >= smartWakeWindowStartNanos -> {
                if (now >= smartWakeWindowEndNanos) {
                    triggerFallbackAlarm()
                } else {
                    evaluateSmartWake(snapshot, stableMs)
                }
            }

            // N3 intervention
            deepSleepEnabled && stage == SleepStage.DEEP && stableMs >= 120_000L -> {
                evaluateN3Intervention(snapshot, stableMs)
            }

            // Alpha intervention (only within first 30 minutes of sleep)
            alphaEnabled && isWithinAlphaWindow(now) &&
                (stage == SleepStage.AWAKE || stage == SleepStage.LIGHT) -> {
                evaluateAlphaIntervention(snapshot, stableMs)
            }

            else -> {
                if (_state.value != SoundInterventionState.BACKGROUND_PLAYING) {
                    _state.value = SoundInterventionState.MONITORING
                }
            }
        }
    }

    // ── Private helpers ──

    private fun ensurePrepared() {
        if (!prepared) {
            pulsePlayer.prepare()
            prepared = true
        }
    }

    private fun isWithinAlphaWindow(now: Long): Boolean {
        val elapsedMs = (now - sleepStartNanos) / 1_000_000L
        return elapsedMs < 30 * 60 * 1000L
    }

    private fun initializeSmartWakeWindow(startNanos: Long) {
        if (!smartWakeEnabled) {
            smartWakeWindowStartNanos = Long.MAX_VALUE
            smartWakeWindowEndNanos = Long.MAX_VALUE
            return
        }

        val nowMs = System.currentTimeMillis()
        val targetWakeMs = resolveNextWakeEpochMillis(nowMs, latestWakeMinutesFromMidnight)
        val windowStartMs = targetWakeMs - wakeWindowMinutes.coerceAtLeast(0) * 60_000L
        val nanosUntilWindowStart = (windowStartMs - nowMs).coerceAtLeast(0L) * 1_000_000L
        val nanosUntilWindowEnd = (targetWakeMs - nowMs).coerceAtLeast(0L) * 1_000_000L

        smartWakeWindowStartNanos = startNanos + nanosUntilWindowStart
        smartWakeWindowEndNanos = startNanos + nanosUntilWindowEnd
    }

    private fun resolveNextWakeEpochMillis(nowMs: Long, wakeMinutesFromMidnight: Int): Long {
        val minutesPerDay = 24 * 60
        val normalizedMinutes = ((wakeMinutesFromMidnight % minutesPerDay) + minutesPerDay) % minutesPerDay
        val wakeTodayMs =
            nowMs - (nowMs % 86_400_000L) +
                normalizedMinutes * 60_000L

        return if (wakeTodayMs > nowMs) {
            wakeTodayMs
        } else {
            wakeTodayMs + 86_400_000L
        }
    }

    /**
     * Feed a downsampled EEG sample for alpha phase estimation.
     */
    fun ingestEegSample(sample: Float) {
        alphaScheduler.ingestSample(sample)
    }

    private fun evaluateAlphaIntervention(snapshot: RealtimeSleepSnapshot, stableMs: Long) {
        if (snapshot.eegQuality < 0.70f) return

        if (_state.value != SoundInterventionState.ALPHA_INTERVENTION) {
            _state.value = SoundInterventionState.ALPHA_INTERVENTION
            alphaScheduler.start()
            logEvent(SoundInterventionType.ALPHA, SoundInterventionEventTypes.ALPHA_INTERVENTION_STARTED)
        }

        // Check stop conditions
        val stopReason = alphaScheduler.evaluateTrigger(snapshot, stableMs)
        if (stopReason == null) {
            // evaluateTrigger returned null = should NOT pulse (skip reason recorded internally)
            return
        }

        // Should trigger: check pulse player availability
        if (pulsePlayer.isActive) {
            logEvent(SoundInterventionType.ALPHA,
                SoundInterventionEventTypes.ALPHA_PULSE_SKIPPED,
                reason = "Pulse already active")
            return
        }

        val gain = stopReason // evaluateTrigger returns gain when should trigger
        val result = pulsePlayer.playPulseNow(gain)
        when (result) {
            is PulsePlaybackResult.Playing -> {
                alphaScheduler.onPulseTriggered(gain)
                logEvent(
                    type = SoundInterventionType.ALPHA,
                    eventType = SoundInterventionEventTypes.ALPHA_PULSE_TRIGGERED,
                    audioGain = gain,
                    pulseIndex = alphaScheduler.pulseCount,
                    success = true
                )
            }
            is PulsePlaybackResult.Skipped -> {
                alphaScheduler.onPulseSkipped(result.reason)
                logEvent(SoundInterventionType.ALPHA,
                    SoundInterventionEventTypes.ALPHA_PULSE_SKIPPED,
                    reason = result.reason)
            }
            is PulsePlaybackResult.Error -> {
                logEvent(SoundInterventionType.ALPHA,
                    SoundInterventionEventTypes.PULSE_PLAY_FAILED,
                    reason = result.message)
            }
            is PulsePlaybackResult.Scheduled -> {}
        }
    }

    private fun evaluateN3Intervention(snapshot: RealtimeSleepSnapshot, stableMs: Long) {
        if (snapshot.eegQuality < 0.70f) {
            stopN3Intervention("bad_signal")
            return
        }

        // Handle group pause
        if (inN3GroupPause) {
            val pauseElapsed = (SystemClock.elapsedRealtimeNanos() - n3GroupPauseStartNanos) / 1_000_000L
            if (pauseElapsed < 45_000L) return // still in pause
            inN3GroupPause = false
        }

        // Start new group if not in one
        if (n3PulsesInGroup == 0 && n3GroupCount >= 10) {
            stopN3Intervention("max_groups")
            return
        }

        if (_state.value != SoundInterventionState.N3_STIMULATING) {
            _state.value = SoundInterventionState.N3_STIMULATING
            n3GroupCount++
            n3PulsesInGroup = 0
            logEvent(
                type = SoundInterventionType.DEEP_SLEEP,
                eventType = SoundInterventionEventTypes.N3_GROUP_STARTED,
                groupIndex = n3GroupCount
            )
        }

        // Check refractory between pulses (500ms min)
        val sinceLastPulse = (SystemClock.elapsedRealtimeNanos() - lastN3PulseNanos) / 1_000_000L
        if (sinceLastPulse < 500L) return

        // Check pulse limit per group
        if (n3PulsesInGroup >= 8) {
            // Group finished, enter pause
            inN3GroupPause = true
            n3GroupPauseStartNanos = SystemClock.elapsedRealtimeNanos()
            n3PulsesInGroup = 0
            _state.value = SoundInterventionState.N3_OBSERVING
            logEvent(
                type = SoundInterventionType.DEEP_SLEEP,
                eventType = SoundInterventionEventTypes.N3_GROUP_FINISHED,
                groupIndex = n3GroupCount
            )
            return
        }

        // Trigger pulse
        val result = pulsePlayer.playPulseNow(0.04f)
        when (result) {
            is PulsePlaybackResult.Playing -> {
                n3PulsesInGroup++
                n3PulseCount++
                lastN3PulseNanos = SystemClock.elapsedRealtimeNanos()
                logEvent(
                    type = SoundInterventionType.DEEP_SLEEP,
                    eventType = SoundInterventionEventTypes.N3_PULSE_TRIGGERED,
                    groupIndex = n3GroupCount,
                    pulseIndex = n3PulsesInGroup,
                    audioGain = 0.04f,
                    success = true
                )
            }
            is PulsePlaybackResult.Skipped -> {
                logEvent(
                    type = SoundInterventionType.DEEP_SLEEP,
                    eventType = SoundInterventionEventTypes.N3_PULSE_SKIPPED,
                    reason = result.reason
                )
            }
            is PulsePlaybackResult.Error -> {
                logEvent(
                    type = SoundInterventionType.DEEP_SLEEP,
                    eventType = SoundInterventionEventTypes.PULSE_PLAY_FAILED,
                    reason = result.message
                )
            }
            is PulsePlaybackResult.Scheduled -> { /* not used for immediate play */ }
        }
    }

    private fun evaluateSmartWake(snapshot: RealtimeSleepSnapshot, stableMs: Long) {
        if (_state.value != SoundInterventionState.SMART_WAKE_WINDOW) {
            _state.value = SoundInterventionState.SMART_WAKE_WINDOW
            logEvent(
                type = SoundInterventionType.SMART_WAKE,
                eventType = SoundInterventionEventTypes.SMART_WAKE_WINDOW_STARTED
            )
        }

        val stage = snapshot.sleepStage
        val score = when (stage) {
            SleepStage.AWAKE -> 1.0f
            SleepStage.LIGHT -> 0.9f
            SleepStage.DEEP -> -1.5f
            SleepStage.REM -> 0.55f
            SleepStage.UNKNOWN -> 0f
        }

        if (score >= 0.75f) {
            consecutiveLightWakeCount++
        } else {
            consecutiveLightWakeCount = 0
        }

        // Two consecutive windows → trigger wake
        if (consecutiveLightWakeCount >= 2) {
            triggerSmartWake()
        }
    }

    private fun triggerSmartWake() {
        _state.value = SoundInterventionState.SMART_WAKING
        backgroundPlayer.stop()
        pulsePlayer.stop()
        alphaScheduler.stop("Smart wake triggered")

        logEvent(
            type = SoundInterventionType.SMART_WAKE,
            eventType = SoundInterventionEventTypes.SMART_WAKE_TRIGGERED,
            success = true
        )
        logEvent(
            type = SoundInterventionType.SMART_WAKE,
            eventType = SoundInterventionEventTypes.WAKE_SOUND_STARTED,
            audioGain = 0.03f
        )

        scope.launch {
            backgroundPlayer.playLoop(
                com.sleepagent.prototype.R.raw.wake_morning_birds,
                0.03f
            )
            delay(500)
            backgroundPlayer.fadeTo(0.50f, 180_000L)
        }
    }

    /**
     * Trigger fallback alarm (when smart wake failed or time expired).
     */
    fun triggerFallbackAlarm() {
        if (_state.value == SoundInterventionState.FALLBACK_ALARMING) return
        _state.value = SoundInterventionState.FALLBACK_ALARMING
        backgroundPlayer.stop()
        pulsePlayer.stop()

        logEvent(
            type = SoundInterventionType.FALLBACK_ALARM,
            eventType = SoundInterventionEventTypes.FALLBACK_ALARM_TRIGGERED,
            success = true
        )

        scope.launch {
            backgroundPlayer.playLoop(
                com.sleepagent.prototype.R.raw.alarm_soft_plucks,
                0.50f
            )
        }
    }

    private fun startBackgroundAudio() {
        scope.launch {
            backgroundPlayer.playLoop(
                com.sleepagent.prototype.R.raw.sleep_rain_gentle,
                0.12f
            )
            _state.value = SoundInterventionState.BACKGROUND_PLAYING
            logEvent(
                type = SoundInterventionType.BACKGROUND,
                eventType = SoundInterventionEventTypes.BACKGROUND_STARTED,
                backgroundSoundKey = "RAIN_GENTLE",
                audioGain = 0.12f,
                success = true
            )
        }
    }

    private fun stopN3Intervention(reason: String) {
        pulsePlayer.stop()
        n3PulsesInGroup = 0
        inN3GroupPause = false
        val eventType = when (reason) {
            "bad_signal" -> SoundInterventionEventTypes.STOPPED_BAD_SIGNAL
            "stage_change" -> SoundInterventionEventTypes.STOPPED_STAGE_CHANGE
            "motion" -> SoundInterventionEventTypes.STOPPED_MOTION
            "device_disconnected" -> SoundInterventionEventTypes.STOPPED_DEVICE_DISCONNECTED
            "max_groups" -> SoundInterventionEventTypes.N3_INTERVENTION_STOPPED
            else -> SoundInterventionEventTypes.N3_INTERVENTION_STOPPED
        }
        logEvent(SoundInterventionType.DEEP_SLEEP, eventType, reason = reason)
        _state.value = SoundInterventionState.MONITORING
    }

    /**
     * User confirmed awake — stop all sound and vibration.
     */
    fun userConfirmedAwake() {
        backgroundPlayer.stop()
        pulsePlayer.stop()
        alarmScheduler.cancel()
        logEvent(
            type = SoundInterventionType.SMART_WAKE,
            eventType = SoundInterventionEventTypes.USER_CONFIRMED_AWAKE,
            success = true
        )
        _state.value = SoundInterventionState.STOPPED
    }

    /**
     * Pause all intervention audio (e.g., for phone call).
     */
    fun pauseAll() {
        backgroundPlayer.pause()
        pulsePlayer.stop()
    }

    fun release() {
        stop()
        backgroundPlayer.release()
        pulsePlayer.release()
        prepared = false
    }

    private fun scheduleFallbackAlarm() {
        val scheduled = alarmScheduler.canScheduleExactAlarms()
        if (scheduled) {
            alarmScheduler.schedule(latestWakeMinutesFromMidnight, wakeWindowMinutes)
        }
        logEvent(
            type = SoundInterventionType.FALLBACK_ALARM,
            eventType = SoundInterventionEventTypes.FALLBACK_ALARM_SCHEDULED,
            success = scheduled,
            reason = if (!scheduled) "缺少精确闹钟权限" else null
        )
    }

    private fun reset() {
        interventionJob?.cancel()
        n3GroupCount = 0
        n3PulseCount = 0
        n3PulsesInGroup = 0
        inN3GroupPause = false
        consecutiveLightWakeCount = 0
        previousStage = null
        stageStableStartNanos = 0L
        stableN3StartNanos = 0L
        lastN3PulseNanos = 0L
        n3GroupPauseStartNanos = 0L
        sleepStartNanos = 0L
        _alphaState.update { AlphaInterventionState() }
        alphaScheduler.reset()
    }

    // ── Event logging ──

    private fun logEvent(
        type: SoundInterventionType,
        eventType: String,
        backgroundSoundKey: String? = null,
        audioGain: Float? = null,
        groupIndex: Int? = null,
        pulseIndex: Int? = null,
        reason: String? = null,
        success: Boolean? = null
    ) {
        val logger = eventLogger ?: return
        val now = System.currentTimeMillis()
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val entity = SoundInterventionEventEntity(
            sessionId = sessionId ?: "",
            timestampMillis = now,
            elapsedRealtimeNanos = nowNanos,
            interventionType = type.name,
            eventType = eventType,
            interventionState = _state.value.name,
            backgroundSoundKey = backgroundSoundKey,
            audioGain = audioGain,
            groupIndex = groupIndex,
            pulseIndex = pulseIndex,
            success = success,
            reason = reason
        )
        logger(entity)
    }
}
