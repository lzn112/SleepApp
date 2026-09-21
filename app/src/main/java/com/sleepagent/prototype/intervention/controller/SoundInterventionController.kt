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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val ALPHA_OPEN_LOOP_INTERVAL_MS = 2_000L
private const val N3_OPEN_LOOP_INTERVAL_MS = 2_500L
private const val ALPHA_OPEN_LOOP_INITIAL_DELAY_MS = 1_000L
private const val N3_OPEN_LOOP_INITIAL_DELAY_MS = 1_750L

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
    private var alphaOpenLoopJob: Job? = null
    private var n3OpenLoopJob: Job? = null
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
    private var stableN3StartNanos: Long = 0L
    private var previousStage: SleepStage? = null
    private var stageStableStartNanos: Long = 0L
    private var consecutiveLightWakeCount: Int = 0
    private var lastN3PulseNanos: Long = 0L
    private var n3GroupPauseStartNanos: Long = 0L
    private var inN3GroupPause: Boolean = false
    private var n3PulsesInGroup: Int = 0
    private var backgroundStopJob: Job? = null
    private var backgroundFadeStarted = false
    private var alphaOpenLoopPulseCount: Int = 0
    private var n3OpenLoopPulseCount: Int = 0

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
        syncAlphaSchedulerConfig()

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

        startOpenLoopInterventionsIfNeeded()
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
        stopOpenLoopInterventions(logStopped = true)
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
        updateOpenLoopInterventions()

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

    fun previewPulse(gain: Float): PulsePlaybackResult {
        ensurePrepared()
        return pulsePlayer.playPulseNow(gain.coerceIn(0.01f, 0.20f))
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
            deepSleepEnabled && n3OpenLoopJob?.isActive != true &&
                stage == SleepStage.DEEP && stableMs >= 120_000L -> {
                evaluateN3Intervention(snapshot, stableMs)
            }

            // Alpha intervention (only within first 30 minutes of sleep)
            alphaEnabled && alphaOpenLoopJob?.isActive != true && isWithinAlphaWindow(now) &&
                (stage == SleepStage.AWAKE || stage == SleepStage.LIGHT) -> {
                evaluateAlphaIntervention(snapshot, stableMs)
            }

            else -> {
                maybeFadeBackgroundAfterSleepOnset(snapshot, stableMs)
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

    private fun updateOpenLoopInterventions() {
        if (!isSessionActiveForOpenLoop()) return

        if (alphaEnabled) {
            startAlphaOpenLoopIfNeeded()
        } else {
            stopAlphaOpenLoop(logStopped = true)
        }

        if (deepSleepEnabled) {
            startN3OpenLoopIfNeeded()
        } else {
            stopN3OpenLoop(logStopped = true)
        }
    }

    private fun startOpenLoopInterventionsIfNeeded() {
        if (alphaEnabled) startAlphaOpenLoopIfNeeded()
        if (deepSleepEnabled) startN3OpenLoopIfNeeded()
    }

    private fun isSessionActiveForOpenLoop(): Boolean {
        val currentState = _state.value
        return sleepStartNanos > 0L &&
            currentState != SoundInterventionState.STOPPED &&
            currentState != SoundInterventionState.FALLBACK_ALARMING
    }

    private fun startAlphaOpenLoopIfNeeded() {
        if (alphaOpenLoopJob?.isActive == true) return
        alphaOpenLoopPulseCount = 0
        logEvent(
            type = SoundInterventionType.ALPHA,
            eventType = SoundInterventionEventTypes.ALPHA_INTERVENTION_STARTED,
            success = true,
            reason = "open_loop"
        )
        alphaOpenLoopJob = scope.launch {
            delay(ALPHA_OPEN_LOOP_INITIAL_DELAY_MS)
            while (isActive && alphaEnabled && isSessionActiveForOpenLoop() && isWithinAlphaWindow(SystemClock.elapsedRealtimeNanos())) {
                playOpenLoopPulse(
                    type = SoundInterventionType.ALPHA,
                    triggeredEventType = SoundInterventionEventTypes.ALPHA_PULSE_TRIGGERED,
                    skippedEventType = SoundInterventionEventTypes.ALPHA_PULSE_SKIPPED,
                    gain = alphaPulseGain,
                    pulseIndex = ++alphaOpenLoopPulseCount
                )
                delay(ALPHA_OPEN_LOOP_INTERVAL_MS)
            }
            if (!isActive) return@launch
            stopAlphaOpenLoop(logStopped = true)
        }
    }

    private fun startN3OpenLoopIfNeeded() {
        if (n3OpenLoopJob?.isActive == true) return
        n3OpenLoopPulseCount = 0
        logEvent(
            type = SoundInterventionType.DEEP_SLEEP,
            eventType = SoundInterventionEventTypes.N3_OPEN_LOOP_STARTED,
            success = true,
            reason = "beta_deep_sleep_lock_open_loop"
        )
        n3OpenLoopJob = scope.launch {
            delay(N3_OPEN_LOOP_INITIAL_DELAY_MS)
            while (isActive && deepSleepEnabled && isSessionActiveForOpenLoop()) {
                playOpenLoopPulse(
                    type = SoundInterventionType.DEEP_SLEEP,
                    triggeredEventType = SoundInterventionEventTypes.N3_PULSE_TRIGGERED,
                    skippedEventType = SoundInterventionEventTypes.N3_PULSE_SKIPPED,
                    gain = deepSleepPulseGain,
                    pulseIndex = ++n3OpenLoopPulseCount
                )
                delay(N3_OPEN_LOOP_INTERVAL_MS)
            }
            if (!isActive) return@launch
            stopN3OpenLoop(logStopped = true)
        }
    }

    private fun playOpenLoopPulse(
        type: SoundInterventionType,
        triggeredEventType: String,
        skippedEventType: String,
        gain: Float,
        pulseIndex: Int
    ) {
        if (pulsePlayer.isActive) {
            logEvent(
                type = type,
                eventType = skippedEventType,
                pulseIndex = pulseIndex,
                reason = "Pulse already active",
                success = false
            )
            return
        }

        when (val result = pulsePlayer.playPulseNow(gain)) {
            is PulsePlaybackResult.Playing -> {
                logEvent(
                    type = type,
                    eventType = triggeredEventType,
                    audioGain = gain,
                    pulseIndex = pulseIndex,
                    success = true,
                    reason = "open_loop"
                )
            }
            is PulsePlaybackResult.Skipped -> {
                logEvent(
                    type = type,
                    eventType = skippedEventType,
                    pulseIndex = pulseIndex,
                    reason = result.reason,
                    success = false
                )
            }
            is PulsePlaybackResult.Error -> {
                logEvent(
                    type = type,
                    eventType = SoundInterventionEventTypes.PULSE_PLAY_FAILED,
                    pulseIndex = pulseIndex,
                    reason = result.message,
                    success = false
                )
            }
            is PulsePlaybackResult.Scheduled -> Unit
        }
    }

    private fun stopOpenLoopInterventions(logStopped: Boolean) {
        stopAlphaOpenLoop(logStopped)
        stopN3OpenLoop(logStopped)
    }

    private fun stopAlphaOpenLoop(logStopped: Boolean) {
        val hadJob = alphaOpenLoopJob != null
        alphaOpenLoopJob?.cancel()
        alphaOpenLoopJob = null
        if (logStopped && hadJob) {
            logEvent(
                type = SoundInterventionType.ALPHA,
                eventType = SoundInterventionEventTypes.ALPHA_INTERVENTION_STOPPED,
                success = true,
                reason = "open_loop"
            )
        }
    }

    private fun stopN3OpenLoop(logStopped: Boolean) {
        val hadJob = n3OpenLoopJob != null
        n3OpenLoopJob?.cancel()
        n3OpenLoopJob = null
        if (logStopped && hadJob) {
            logEvent(
                type = SoundInterventionType.DEEP_SLEEP,
                eventType = SoundInterventionEventTypes.N3_OPEN_LOOP_STOPPED,
                success = true,
                reason = "beta_deep_sleep_lock_open_loop"
            )
        }
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
        val result = pulsePlayer.playPulseNow(deepSleepPulseGain)
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
        stopOpenLoopInterventions(logStopped = true)

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
        stopOpenLoopInterventions(logStopped = true)

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
        backgroundStopJob?.cancel()
        scope.launch {
            backgroundPlayer.playLoop(
                backgroundSoundResId,
                backgroundGain
            )
            _state.value = SoundInterventionState.BACKGROUND_PLAYING
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
                if (_state.value == SoundInterventionState.BACKGROUND_PLAYING) {
                    if (fadeAfterSleepOnset) {
                        backgroundPlayer.fadeTo(0f, backgroundFadeDurationMillis)
                    }
                    backgroundPlayer.stop()
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
        if (_state.value != SoundInterventionState.BACKGROUND_PLAYING) return
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
    fun userConfirmedAwake() {
        backgroundPlayer.stop()
        pulsePlayer.stop()
        stopOpenLoopInterventions(logStopped = true)
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
        stopOpenLoopInterventions(logStopped = true)
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
        stopOpenLoopInterventions(logStopped = false)
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
        stableN3StartNanos = 0L
        lastN3PulseNanos = 0L
        n3GroupPauseStartNanos = 0L
        sleepStartNanos = 0L
        alphaOpenLoopPulseCount = 0
        n3OpenLoopPulseCount = 0
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
