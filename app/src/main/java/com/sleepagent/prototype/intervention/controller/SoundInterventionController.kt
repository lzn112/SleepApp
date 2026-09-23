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
import com.sleepagent.prototype.intervention.audio.SleepSoundKey
import com.sleepagent.prototype.intervention.model.AlphaInterventionConfig
import com.sleepagent.prototype.intervention.model.AlphaInterventionState
import com.sleepagent.prototype.intervention.model.RealtimeSleepSnapshot
import com.sleepagent.prototype.intervention.model.SoundInterventionState
import com.sleepagent.prototype.intervention.model.SoundInterventionType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    context: Context?,
    private val backgroundPlayer: BackgroundAudioPlayer = Media3BackgroundAudioPlayer(requireNotNull(context)),
    private val pulsePlayer: PulseAudioPlayer = AudioTrackPulseAudioPlayer(),
    private val pinkNoiseGenerator: PinkNoiseGenerator = PinkNoiseGenerator(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val nowNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val wallTimeMillis: () -> Long = System::currentTimeMillis
) {

    /** Callback for persisting intervention events to the database. */
    var eventLogger: ((SoundInterventionEventEntity) -> Unit)? = null

    private val alphaScheduler = AlphaTriggerScheduler(nowNanos = nowNanos)
    private val alarmScheduler = context?.let { SmartAlarmScheduler(it) }

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
    var backgroundSoundResId: Int = com.sleepagent.prototype.R.raw.sleep_rain_gentle
    var backgroundSoundKey: String = "RAIN_GENTLE"
    var backgroundGain: Float = 0.12f
    var backgroundDurationMinutes: Int = 30
    var keepBackgroundAllNight: Boolean = false
    var fadeAfterSleepOnset: Boolean = true
    var backgroundFadeDurationMillis: Long = 60_000L
    var alphaEnabled: Boolean = true
    var alphaPulseGain: Float = 0.04f
    var alphaMaxDurationMinutes: Int = 30
    var deepSleepEnabled: Boolean = true
    var deepSleepPulseGain: Float = 0.04f
    var smartWakeEnabled: Boolean = true

    var smartWakeWindowStartNanos: Long = 0L
    var smartWakeWindowEndNanos: Long = 0L

    var latestWakeMinutesFromMidnight: Int = 7 * 60 + 30
    var wakeWindowMinutes: Int = 30

    private var sessionId: String? = null
    private var sleepStartNanos: Long = 0L
    private var previousStage: SleepStage? = null
    private var stageStableStartNanos: Long = 0L
    private var consecutiveLightWakeCount: Int = 0
    private var lastN3PulseNanos: Long = 0L
    private var n3GroupPauseStartNanos: Long = 0L
    private var inN3GroupPause: Boolean = false
    private var n3PulsesInGroup: Int = 0
    private var playbackJob: Job? = null
    private var backgroundStopJob: Job? = null
    private var backgroundFadeStarted = false
    private var sessionActive = false
    private var paused = false
    private var latestSnapshot: RealtimeSleepSnapshot? = null
    private var lastSnapshotNanos = 0L
    private var lastWakeEpoch: Int? = null
    private val skipReasons = mutableMapOf<SoundInterventionType, String>()

    /**
     * Start the intervention controller for a sleep session.
     * Must be called when sleep recording begins.
     */
    @Synchronized
    fun start(sessionId: String? = null) {
        if (sessionActive) return
        require(!sessionId.isNullOrBlank()) { "A recording session is required for stimulation logs" }

        this.sessionId = sessionId
        reset()
        ensurePrepared()
        syncAlphaSchedulerConfig()

        sessionActive = true
        paused = false
        _state.value = SoundInterventionState.MONITORING
        alphaScheduler.start()
        sleepStartNanos = nowNanos()
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

        lastSnapshotNanos = nowNanos()
        interventionJob = scope.launch {
            while (isActive) {
                delay(500)
                checkDataTimeout()
            }
        }
    }

    /**
     * Stop all interventions and release resources.
     */
    @Synchronized
    fun stop() {
        if (!sessionActive) return
        stopClosedLoop("session_stopped")
        sessionActive = false
        alphaScheduler.stop("session_stopped")
        _alphaState.value = alphaScheduler.state
        playbackJob?.cancel()
        logEvent(
            type = SoundInterventionType.BACKGROUND,
            eventType = SoundInterventionEventTypes.SESSION_STOPPED,
            success = true
        )
        alarmScheduler?.cancel()
        interventionJob?.cancel()
        interventionJob = null
        backgroundStopJob?.cancel()
        backgroundStopJob = null
        backgroundPlayer.stop()
        pulsePlayer.stop()
        _state.value = SoundInterventionState.STOPPED
    }

    /**
     * Apply sound intervention config from UI settings.
     * Call this before or during a sleep session to update active sound parameters.
     */
    @Synchronized
    fun applySoundConfig(
        backgroundEnabled: Boolean,
        soundKeyName: String,
        gain: Float,
        durationMin: Int,
        keepAllNight: Boolean,
        fadeAfterSleepOnset: Boolean,
        fadeDurationSeconds: Int,
        alphaEnabled: Boolean,
        alphaGain: Float,
        alphaMaxMin: Int,
        deepSleepEnabled: Boolean,
        deepSleepGain: Float
    ) {
        val key = SleepSoundKey.resolve(soundKeyName)
        this.backgroundEnabled = backgroundEnabled
        backgroundSoundResId = key.rawResId
        backgroundSoundKey = key.name
        backgroundGain = gain.coerceIn(0.01f, 0.50f)
        backgroundDurationMinutes = durationMin.coerceAtLeast(1)
        keepBackgroundAllNight = keepAllNight
        this.fadeAfterSleepOnset = fadeAfterSleepOnset
        backgroundFadeDurationMillis = fadeDurationSeconds.coerceAtLeast(1) * 1_000L
        this.alphaEnabled = alphaEnabled
        this.alphaPulseGain = alphaGain.coerceIn(0.01f, 0.15f)
        this.alphaMaxDurationMinutes = alphaMaxMin.coerceAtLeast(1)
        this.deepSleepEnabled = deepSleepEnabled
        this.deepSleepPulseGain = deepSleepGain.coerceIn(0.01f, 0.20f)
        syncAlphaSchedulerConfig()
        if (!alphaEnabled && _state.value == SoundInterventionState.ALPHA_INTERVENTION ||
            !deepSleepEnabled && (_state.value == SoundInterventionState.N3_STIMULATING ||
                _state.value == SoundInterventionState.N3_OBSERVING)) stopClosedLoop("disabled")

        if (_state.value == SoundInterventionState.BACKGROUND_PLAYING) {
            if (backgroundEnabled) {
                startBackgroundAudio()
            } else {
                backgroundStopJob?.cancel()
                backgroundStopJob = null
                backgroundPlayer.stop()
                _state.value = SoundInterventionState.MONITORING
            }
        }
    }

    @Synchronized
    fun applySmartWakeConfig(
        enabled: Boolean,
        windowStartMinutesFromMidnight: Int,
        windowEndMinutesFromMidnight: Int
    ) {
        smartWakeEnabled = enabled
        latestWakeMinutesFromMidnight = windowEndMinutesFromMidnight
        val minutesPerDay = 24 * 60
        val rawWindowMinutes = windowEndMinutesFromMidnight - windowStartMinutesFromMidnight
        val normalizedWindowMinutes = ((rawWindowMinutes % minutesPerDay) + minutesPerDay) % minutesPerDay
        wakeWindowMinutes = if (normalizedWindowMinutes == 0) 30 else normalizedWindowMinutes

        if (sleepStartNanos > 0L) {
            initializeSmartWakeWindow(sleepStartNanos)
        }
    }

    @Synchronized
    fun previewPulse(gain: Float): PulsePlaybackResult {
        ensurePrepared()
        return pulsePlayer.playPulseNow(gain.coerceIn(0.01f, 0.20f))
    }

    /**
     * Called each time new sleep data is available.
     */
    @Synchronized
    fun onSnapshot(snapshot: RealtimeSleepSnapshot) {
        if (!sessionActive || paused || _state.value == SoundInterventionState.FALLBACK_ALARMING) return
        val now = nowNanos()
        val gap = now - lastSnapshotNanos
        latestSnapshot = snapshot
        lastSnapshotNanos = now
        if (gap > 2_000_000_000L) invalidateFeedback("data_timeout")
        // A smart wake runs once; the deadline still promotes it to the fallback alarm.
        if (smartWakeEnabled && now >= smartWakeWindowEndNanos) {
            triggerFallbackAlarm()
            return
        }
        if (_state.value == SoundInterventionState.SMART_WAKING) return
        val invalid = when {
            !snapshot.isDeviceConnected -> "device_disconnected"
            !snapshot.isRealModelResult -> "untrusted_model"
            snapshot.stageAgeMillis !in 0..45_000L -> "stale_stage"
            now - snapshot.elapsedRealtimeNanos !in 0..2_000_000_000L -> "stale_signal"
            !snapshot.eegQuality.isFinite() || snapshot.eegQuality < 0.70f -> "bad_signal"
            snapshot.motionLevel?.let { !it.isFinite() || it > 0.3f } == true -> "motion"
            snapshot.sleepStage == SleepStage.UNKNOWN -> "unknown_stage"
            else -> null
        }
        if (invalid != null) {
            invalidateFeedback(invalid)
            return
        }
        val stage = snapshot.sleepStage
        if (stage != previousStage) {
            stopClosedLoop("stage_change")
            stageStableStartNanos = now
            previousStage = stage
        }
        val stableMs = (now - stageStableStartNanos) / 1_000_000L
        maybeFadeBackgroundAfterSleepOnset(snapshot, stableMs)
        when {
            smartWakeEnabled && now >= smartWakeWindowStartNanos -> {
                stopClosedLoop("smart_wake_window")
                evaluateSmartWake(snapshot, stableMs)
            }
            deepSleepEnabled && stage == SleepStage.DEEP -> {
                if (stableMs >= 120_000L) evaluateN3Intervention(snapshot, stableMs)
                else logSkip(SoundInterventionType.DEEP_SLEEP, "awaiting_stable_n3")
            }
            alphaEnabled && isWithinAlphaWindow(now) &&
                (stage == SleepStage.AWAKE || stage == SleepStage.LIGHT) ->
                evaluateAlphaIntervention(snapshot, stableMs)
            else -> stopClosedLoop("stage_or_window_ineligible")
        }
        _alphaState.value = alphaScheduler.state
    }

    /** Called by both the watchdog and BLE connection collector, even with no packets. */
    @Synchronized
    fun invalidateFeedback(reason: String) {
        if (!sessionActive) return
        stopClosedLoop(reason)
        alphaScheduler.invalidateSignal()
        previousStage = null
        consecutiveLightWakeCount = 0
        lastWakeEpoch = null
        if (alphaEnabled) logSkip(SoundInterventionType.ALPHA, reason)
        if (deepSleepEnabled) logSkip(SoundInterventionType.DEEP_SLEEP, reason)
        _alphaState.value = alphaScheduler.state
    }

    @Synchronized
    fun checkDataTimeout() {
        if (sessionActive && !paused && nowNanos() - lastSnapshotNanos > 2_000_000_000L) {
            invalidateFeedback("data_timeout")
        }
    }

    private fun logSkip(type: SoundInterventionType, reason: String) {
        // Log decision transitions, not every 100 Hz sample; every accepted pulse is logged.
        if (skipReasons.put(type, reason) == reason) return
        logEvent(type, if (type == SoundInterventionType.ALPHA)
            SoundInterventionEventTypes.ALPHA_PULSE_SKIPPED else SoundInterventionEventTypes.N3_PULSE_SKIPPED,
            reason = reason, success = false)
    }

    private fun stopClosedLoop(reason: String) {
        if (_state.value == SoundInterventionState.ALPHA_INTERVENTION) {
            logEvent(SoundInterventionType.ALPHA, SoundInterventionEventTypes.ALPHA_INTERVENTION_STOPPED,
                reason = reason, success = true)
            _state.value = SoundInterventionState.MONITORING
        }
        if (_state.value == SoundInterventionState.N3_STIMULATING ||
            _state.value == SoundInterventionState.N3_OBSERVING) stopN3Intervention(reason)
        pulsePlayer.stop()
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
        return elapsedMs < alphaMaxDurationMinutes * 60 * 1000L
    }

    private fun syncAlphaSchedulerConfig() {
        alphaScheduler.updateConfig(
            AlphaInterventionConfig(
                enabled = alphaEnabled,
                pulseGain = alphaPulseGain,
                maxDurationMinutes = alphaMaxDurationMinutes
            )
        )
    }

    private fun initializeSmartWakeWindow(startNanos: Long) {
        if (!smartWakeEnabled) {
            smartWakeWindowStartNanos = Long.MAX_VALUE
            smartWakeWindowEndNanos = Long.MAX_VALUE
            return
        }

        val nowMs = wallTimeMillis()
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
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.Instant.ofEpochMilli(nowMs).atZone(zone)
        var target = now.toLocalDate().atTime(normalizedMinutes / 60, normalizedMinutes % 60).atZone(zone)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.toInstant().toEpochMilli()
    }

    /**
     * Feed a downsampled EEG sample for alpha phase estimation.
     */
    @Synchronized
    fun ingestEegSample(sample: Float) {
        alphaScheduler.ingestSample(sample)
    }

    private fun evaluateAlphaIntervention(snapshot: RealtimeSleepSnapshot, stableMs: Long) {
        if (snapshot.eegQuality < 0.70f) return

        if (_state.value != SoundInterventionState.ALPHA_INTERVENTION) {
            _state.value = SoundInterventionState.ALPHA_INTERVENTION
            logEvent(SoundInterventionType.ALPHA, SoundInterventionEventTypes.ALPHA_INTERVENTION_STARTED)
        }

        // Check stop conditions
        val stopReason = alphaScheduler.evaluateTrigger(snapshot, stableMs)
        if (stopReason == null) {
            logSkip(SoundInterventionType.ALPHA, alphaScheduler.lastSkipReason ?: "ineligible")
            return
        }

        // Should trigger: check pulse player availability
        if (pulsePlayer.isActive) {
            logSkip(SoundInterventionType.ALPHA, "pulse_busy")
            return
        }

        val gain = stopReason // evaluateTrigger returns gain when should trigger
        val result = runCatching { pulsePlayer.playPulseNow(gain) }
            .getOrElse { PulsePlaybackResult.Error(it.message ?: "playback_exception") }
        when (result) {
            is PulsePlaybackResult.Playing -> {
                skipReasons.remove(SoundInterventionType.ALPHA)
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
                    reason = result.message, audioGain = gain)
                pauseAll()
            }
            is PulsePlaybackResult.Scheduled -> {
                pulsePlayer.stop()
                logSkip(SoundInterventionType.ALPHA, "unexpected_scheduled_result")
            }
        }
    }

    private fun evaluateN3Intervention(snapshot: RealtimeSleepSnapshot, stableMs: Long) {

        // Handle group pause
        if (inN3GroupPause) {
            val pauseElapsed = (nowNanos() - n3GroupPauseStartNanos) / 1_000_000L
            if (pauseElapsed < 45_000L) {
                logSkip(SoundInterventionType.DEEP_SLEEP, "group_pause")
                return
            }
            inN3GroupPause = false
        }

        // Start new group if not in one
        if (n3PulsesInGroup == 0 && n3GroupCount >= 10) {
            logSkip(SoundInterventionType.DEEP_SLEEP, "max_groups")
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
        val sinceLastPulse = (nowNanos() - lastN3PulseNanos) / 1_000_000L
        if (lastN3PulseNanos > 0L && sinceLastPulse < 500L) return
        if (pulsePlayer.isActive) {
            logSkip(SoundInterventionType.DEEP_SLEEP, "pulse_busy")
            return
        }

        // Check pulse limit per group
        if (n3PulsesInGroup >= 8) {
            // Group finished, enter pause
            inN3GroupPause = true
            n3GroupPauseStartNanos = nowNanos()
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
        val result = runCatching { pulsePlayer.playPulseNow(deepSleepPulseGain) }
            .getOrElse { PulsePlaybackResult.Error(it.message ?: "playback_exception") }
        when (result) {
            is PulsePlaybackResult.Playing -> {
                skipReasons.remove(SoundInterventionType.DEEP_SLEEP)
                n3PulsesInGroup++
                n3PulseCount++
                lastN3PulseNanos = nowNanos()
                logEvent(
                    type = SoundInterventionType.DEEP_SLEEP,
                    eventType = SoundInterventionEventTypes.N3_PULSE_TRIGGERED,
                    groupIndex = n3GroupCount,
                    pulseIndex = n3PulsesInGroup,
                    audioGain = deepSleepPulseGain,
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
                    reason = result.message, audioGain = deepSleepPulseGain,
                    groupIndex = n3GroupCount, pulseIndex = n3PulsesInGroup + 1
                )
                pauseAll()
            }
            is PulsePlaybackResult.Scheduled -> {
                pulsePlayer.stop()
                logSkip(SoundInterventionType.DEEP_SLEEP, "unexpected_scheduled_result")
            }
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

        val epoch = snapshot.stageEpochIndex ?: return
        if (epoch == lastWakeEpoch) return
        if (lastWakeEpoch != null && epoch != lastWakeEpoch!! + 1) consecutiveLightWakeCount = 0
        lastWakeEpoch = epoch
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
        stopClosedLoop("smart_wake")
        backgroundStopJob?.cancel()
        _state.value = SoundInterventionState.SMART_WAKING
        backgroundPlayer.stop()
        pulsePlayer.stop()
        alphaScheduler.stop("Smart wake triggered")
        stopClosedLoop("interrupted")

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

        playbackJob?.cancel()
        playbackJob = scope.launch {
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
    @Synchronized
    fun triggerFallbackAlarm() {
        if (!sessionActive || paused) return
        if (_state.value == SoundInterventionState.FALLBACK_ALARMING) return
        stopClosedLoop("fallback_alarm")
        backgroundStopJob?.cancel()
        _state.value = SoundInterventionState.FALLBACK_ALARMING
        backgroundPlayer.stop()
        pulsePlayer.stop()
        stopClosedLoop("interrupted")

        logEvent(
            type = SoundInterventionType.FALLBACK_ALARM,
            eventType = SoundInterventionEventTypes.FALLBACK_ALARM_TRIGGERED,
            success = true
        )

        playbackJob?.cancel()
        playbackJob = scope.launch {
            backgroundPlayer.playLoop(
                com.sleepagent.prototype.R.raw.alarm_soft_plucks,
                0.50f
            )
        }
    }

    private fun startBackgroundAudio() {
        backgroundStopJob?.cancel()
        playbackJob?.cancel()
        playbackJob = scope.launch {
            backgroundPlayer.playLoop(
                backgroundSoundResId,
                backgroundGain
            )
            if (_state.value == SoundInterventionState.MONITORING) _state.value = SoundInterventionState.BACKGROUND_PLAYING
            logEvent(
                type = SoundInterventionType.BACKGROUND,
                eventType = SoundInterventionEventTypes.BACKGROUND_STARTED,
                backgroundSoundKey = backgroundSoundKey,
                audioGain = backgroundGain,
                success = true
            )
        }
        if (!keepBackgroundAllNight) {
            backgroundStopJob = scope.launch {
                delay(backgroundDurationMinutes * 60_000L)
                if (sessionActive && _state.value != SoundInterventionState.SMART_WAKING &&
                    _state.value != SoundInterventionState.FALLBACK_ALARMING) {
                    if (fadeAfterSleepOnset) {
                        backgroundPlayer.fadeTo(0f, backgroundFadeDurationMillis)
                    }
                    backgroundPlayer.stop()
                    if (_state.value == SoundInterventionState.BACKGROUND_PLAYING)
                        _state.value = SoundInterventionState.MONITORING
                    logEvent(
                        type = SoundInterventionType.BACKGROUND,
                        eventType = SoundInterventionEventTypes.BACKGROUND_STOPPED,
                        backgroundSoundKey = backgroundSoundKey,
                        audioGain = 0f,
                        success = true,
                        reason = "duration_elapsed"
                    )
                }
            }
        }
    }

    private fun maybeFadeBackgroundAfterSleepOnset(snapshot: RealtimeSleepSnapshot, stableMs: Long) {
        if (!backgroundEnabled || !fadeAfterSleepOnset || keepBackgroundAllNight || backgroundFadeStarted) return
        if (!backgroundPlayer.state.value.isPlaying) return
        if (snapshot.sleepStage != SleepStage.LIGHT && snapshot.sleepStage != SleepStage.DEEP) return
        if (stableMs < 120_000L) return

        backgroundFadeStarted = true
        backgroundStopJob?.cancel()
        backgroundStopJob = scope.launch {
            logEvent(
                type = SoundInterventionType.BACKGROUND,
                eventType = SoundInterventionEventTypes.BACKGROUND_FADE_STARTED,
                backgroundSoundKey = backgroundSoundKey,
                audioGain = backgroundGain,
                success = true,
                reason = "sleep_onset"
            )
            backgroundPlayer.fadeTo(0f, backgroundFadeDurationMillis)
            backgroundPlayer.stop()
            if (_state.value == SoundInterventionState.BACKGROUND_PLAYING)
                _state.value = SoundInterventionState.MONITORING
            logEvent(
                type = SoundInterventionType.BACKGROUND,
                eventType = SoundInterventionEventTypes.BACKGROUND_STOPPED,
                backgroundSoundKey = backgroundSoundKey,
                audioGain = 0f,
                success = true,
                reason = "sleep_onset"
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
    @Synchronized
    fun userConfirmedAwake() {
        stop()
        backgroundPlayer.stop()
        pulsePlayer.stop()
        stopClosedLoop("interrupted")
        alarmScheduler?.cancel()
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
    @Synchronized
    fun pauseAll() {
        paused = true
        playbackJob?.cancel()
        backgroundStopJob?.cancel()
        backgroundPlayer.pause()
        pulsePlayer.stop()
        stopClosedLoop("interrupted")
    }

    @Synchronized
    fun release() {
        stop()
        backgroundPlayer.release()
        pulsePlayer.release()
        prepared = false
        scope.cancel()
    }

    private fun scheduleFallbackAlarm() {
        val scheduled = alarmScheduler?.canScheduleExactAlarms() == true
        if (scheduled) {
            alarmScheduler?.schedule(latestWakeMinutesFromMidnight, wakeWindowMinutes)
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
        alphaScheduler.reset()
        playbackJob?.cancel()
        backgroundStopJob?.cancel()
        backgroundStopJob = null
        n3GroupCount = 0
        n3PulseCount = 0
        n3PulsesInGroup = 0
        inN3GroupPause = false
        backgroundFadeStarted = false
        consecutiveLightWakeCount = 0
        previousStage = null
        stageStableStartNanos = 0L
        lastN3PulseNanos = 0L
        n3GroupPauseStartNanos = 0L
        sleepStartNanos = 0L
        latestSnapshot = null
        lastWakeEpoch = null
        skipReasons.clear()
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
        val recordingSession = sessionId ?: return
        val feedback = latestSnapshot
        val alpha = alphaScheduler.debugInfo()
        val now = wallTimeMillis()
        val nowNanos = nowNanos()
        val entity = SoundInterventionEventEntity(
            sessionId = recordingSession,
            timestampMillis = now,
            elapsedRealtimeNanos = nowNanos,
            interventionType = type.name,
            eventType = eventType,
            interventionState = _state.value.name,
            sleepStage = feedback?.sleepStage?.name,
            stageProbability = feedback?.stageProbabilities?.get(feedback.sleepStage),
            eegQuality = feedback?.eegQuality,
            motionLevel = feedback?.motionLevel,
            heartRate = feedback?.heartRate,
            alphaMode = if (type == SoundInterventionType.ALPHA) "ALPHA_AWARE" else null,
            individualAlphaFrequencyHz = if (type == SoundInterventionType.ALPHA) alpha["iaf_hz"] as? Float else null,
            estimatedPhaseDeg = if (type == SoundInterventionType.ALPHA) alpha["phase_deg"] as? Float else null,
            alphaAmplitude = if (type == SoundInterventionType.ALPHA) alpha["amplitude"] as? Float else null,
            metadataJson = "{\"control_mode\":\"closed_loop\",\"stage_epoch\":${feedback?.stageEpochIndex},\"real_model\":${feedback?.isRealModelResult ?: false},\"model_context_delay_ms\":60000}",
            backgroundSoundKey = backgroundSoundKey,
            audioGain = audioGain,
            groupIndex = groupIndex,
            pulseIndex = pulseIndex,
            success = success ?: if (eventType.endsWith("SKIPPED") || eventType == SoundInterventionEventTypes.PULSE_PLAY_FAILED) false else null,
            reason = reason
        )
        logger(entity)
    }
}
