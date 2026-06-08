package com.sleepagent.prototype.sleep

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sleepagent.prototype.R
import com.sleepagent.prototype.data.ActiveSleepSession
import com.sleepagent.prototype.data.SleepDataSource
import com.sleepagent.prototype.data.SleepSessionStatus
import com.sleepagent.prototype.data.SleepStorageRepository
import com.sleepagent.prototype.device.BleHeadbandDeviceManager
import com.sleepagent.prototype.device.DeviceConnectionState
import com.sleepagent.prototype.device.HeadbandDevice
import com.sleepagent.prototype.device.HeadbandDeviceManager
import com.sleepagent.prototype.device.HeadbandRawPacket
import com.sleepagent.prototype.device.HeadbandStatus
import com.sleepagent.prototype.device.MockHeadbandDeviceManager
import com.sleepagent.prototype.device.SleepOpticalMode
import com.sleepagent.prototype.device.TdcsConfig
import com.sleepagent.prototype.sleep.processing.SleepEegDownsampler
import com.sleepagent.prototype.sleep.processing.SleepSignalPipeline
import com.sleepagent.prototype.sleep.processing.SleepSignalSnapshot
import com.sleepagent.prototype.sleep.staging.MockSleepStageInferenceEngine
import com.sleepagent.prototype.sleep.staging.SleepStageInferenceEngine
import com.sleepagent.prototype.sleep.staging.SleepStagePipeline
import com.sleepagent.prototype.sleep.staging.SleepStagePrediction
import com.sleepagent.prototype.sleep.staging.SleepStageSnapshot
import com.sleepagent.prototype.sleep.staging.TinyEEGNetInferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SleepRecordingState(
    val connectionState: DeviceConnectionState = DeviceConnectionState.DISCONNECTED,
    val deviceStatus: HeadbandStatus = HeadbandStatus(message = "Recording service idle"),
    val latestPacket: HeadbandRawPacket? = null,
    val packetCount: Long = 0L,
    val signalSnapshot: SleepSignalSnapshot = SleepSignalSnapshot(),
    val sleepStageSnapshot: SleepStageSnapshot = SleepStageSnapshot(),
    val sessionId: String? = null,
    val sessionPath: String? = null,
    val isRecording: Boolean = false,
    val message: String? = null
)

class SleepRecordingService : Service() {
    inner class LocalBinder : Binder() {
        val service: SleepRecordingService
            get() = this@SleepRecordingService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operationMutex = Mutex()
    private val sessionIoMutex = Mutex()
    private val repository by lazy { SleepStorageRepository(applicationContext) }

    private val _recordingState = MutableStateFlow(SleepRecordingState())
    val recordingState: StateFlow<SleepRecordingState> = _recordingState.asStateFlow()

    private var manager: HeadbandDeviceManager? = null
    private var managerUsesMock: Boolean? = null
    private var connectionJob: Job? = null
    private var statusJob: Job? = null
    private var rawDataJob: Job? = null
    private var activeSession: ActiveSleepSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundStarted = false

    private var eegDownsampler = SleepEegDownsampler()
    private var signalPipeline = SleepSignalPipeline(snapshotIntervalPackets = 24)
    private val sleepStageInferenceEngine = LazyFallbackSleepStageInferenceEngine { applicationContext }
    private var sleepStagePipeline = SleepStagePipeline(inferenceEngine = sleepStageInferenceEngine)

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_RECORDING_FOREGROUND) {
            ensureForeground()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.launch {
            runCatching { finishActiveSession(SleepSessionStatus.ABORTED) }
            runCatching { manager?.disconnect() }
            releaseWakeLock()
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    suspend fun scan(useMockManager: Boolean): List<HeadbandDevice> = operationMutex.withLock {
        val currentManager = getOrCreateManager(useMockManager)
        currentManager.scan()
    }

    suspend fun connect(useMockManager: Boolean, deviceId: String) = operationMutex.withLock {
        val currentManager = getOrCreateManager(useMockManager)
        currentManager.connect(deviceId)
        _recordingState.update { it.copy(message = "已连接设备。") }
    }

    suspend fun startRecording(useMockManager: Boolean, fallbackDevice: HeadbandDevice?) = operationMutex.withLock {
        if (_recordingState.value.isRecording) return@withLock
        ensureForeground()
        acquireWakeLock()

        val currentManager = getOrCreateManager(useMockManager)
        val connectedDevice = _recordingState.value.deviceStatus.connectedDevice ?: fallbackDevice
        check(connectedDevice != null) { "还没有拿到已连接设备信息，请重新选择设备后再试。" }

        resetSignalProcessors()
        val session = repository.startSession(
            device = connectedDevice,
            sourceType = if (useMockManager) SleepDataSource.MOCK else SleepDataSource.BLE
        )
        activeSession = session
        _recordingState.update {
            it.copy(
                latestPacket = null,
                packetCount = 0L,
                signalSnapshot = SleepSignalSnapshot(),
                sleepStageSnapshot = SleepStageSnapshot(),
                sessionId = session.sessionId,
                sessionPath = session.rawFilePath,
                isRecording = false,
                message = "正在启动睡眠采集..."
            )
        }

        try {
            currentManager.startStreaming()
            _recordingState.update {
                it.copy(isRecording = true, message = "已进入睡眠监测页。")
            }
            updateForegroundNotification()
        } catch (error: Throwable) {
            val failedSession = activeSession
            activeSession = null
            if (failedSession != null) {
                runCatching { repository.finishSession(failedSession, SleepSessionStatus.ABORTED) }
            }
            releaseWakeLock()
            stopForegroundIfIdle()
            _recordingState.update {
                it.copy(
                    sessionId = null,
                    sessionPath = null,
                    isRecording = false,
                    message = error.message ?: "启动采集失败"
                )
            }
            throw error
        }
    }

    suspend fun stopRecording(status: SleepSessionStatus = SleepSessionStatus.COMPLETED): String? =
        operationMutex.withLock {
            val currentManager = manager
            runCatching { currentManager?.stopStreaming() }
            val exportHint = finishActiveSession(status)
            resetSignalProcessors()
            _recordingState.update {
                it.copy(
                    latestPacket = null,
                    packetCount = 0L,
                    signalSnapshot = SleepSignalSnapshot(),
                    sleepStageSnapshot = SleepStageSnapshot(),
                    sessionId = null,
                    sessionPath = null,
                    isRecording = false,
                    message = if (exportHint != null) {
                        "已结束睡眠。导出包: $exportHint"
                    } else {
                        "已结束睡眠。"
                    }
                )
            }
            releaseWakeLock()
            stopForegroundIfIdle()
            exportHint
        }

    suspend fun disconnect(): String? = operationMutex.withLock {
        val wasRecording = _recordingState.value.isRecording
        val exportHint = finishActiveSession(
            if (wasRecording) SleepSessionStatus.ABORTED else SleepSessionStatus.COMPLETED
        )
        runCatching { manager?.stopStreaming() }
        runCatching { manager?.disconnect() }
        resetSignalProcessors()
        _recordingState.update {
            it.copy(
                connectionState = DeviceConnectionState.DISCONNECTED,
                deviceStatus = HeadbandStatus(message = "Disconnected"),
                latestPacket = null,
                packetCount = 0L,
                signalSnapshot = SleepSignalSnapshot(),
                sleepStageSnapshot = SleepStageSnapshot(),
                sessionId = null,
                sessionPath = null,
                isRecording = false,
                message = if (exportHint != null) {
                    "设备已断开。导出包: $exportHint"
                } else {
                    "设备已断开。"
                }
            )
        }
        releaseWakeLock()
        stopForegroundIfIdle()
        exportHint
    }

    suspend fun setOpticalMode(mode: SleepOpticalMode) = operationMutex.withLock {
        manager?.setOpticalMode(mode) ?: error("Headband is not connected")
        signalPipeline.handleOpticalModeChange(mode)
    }

    suspend fun startTdcs(config: TdcsConfig) = operationMutex.withLock {
        manager?.startTdcs(config) ?: error("Headband is not connected")
    }

    suspend fun stopTdcs(channel: String) = operationMutex.withLock {
        manager?.stopTdcs(channel) ?: error("Headband is not connected")
    }

    fun updateHrvChannel(channel: Int) {
        signalPipeline.updateChannelSelection(hrvChannel = channel)
    }

    fun updateFnirsChannel(channel: Int) {
        signalPipeline.updateChannelSelection(fnirsChannel = channel)
    }

    private suspend fun getOrCreateManager(useMockManager: Boolean): HeadbandDeviceManager {
        val existing = manager
        if (existing != null && managerUsesMock == useMockManager) return existing

        check(!_recordingState.value.isRecording) { "正在采集时不能切换数据源。" }
        runCatching { existing?.disconnect() }
        connectionJob?.cancel()
        statusJob?.cancel()
        rawDataJob?.cancel()
        resetSignalProcessors()

        val next = if (useMockManager) {
            MockHeadbandDeviceManager()
        } else {
            BleHeadbandDeviceManager(applicationContext)
        }
        manager = next
        managerUsesMock = useMockManager
        attachManagerCollectors(next)
        _recordingState.value = SleepRecordingState(
            message = if (useMockManager) "Mock 采集服务已就绪。" else "BLE 采集服务已就绪。"
        )
        return next
    }

    private fun attachManagerCollectors(currentManager: HeadbandDeviceManager) {
        connectionJob = serviceScope.launch {
            currentManager.connectionState.collectLatest { state ->
                _recordingState.update { it.copy(connectionState = state) }
            }
        }
        statusJob = serviceScope.launch {
            currentManager.deviceStatus.collectLatest { status ->
                _recordingState.update { it.copy(deviceStatus = status) }
            }
        }
        rawDataJob = serviceScope.launch {
            currentManager.rawDataFlow.collect { packet ->
                handleRawPacket(packet)
            }
        }
    }

    private suspend fun handleRawPacket(packet: HeadbandRawPacket) {
        logRaw51Check(packet)

        val downsampledEegSample = eegDownsampler.ingest(packet)
        val snapshot = signalPipeline.ingest(
            packet = packet,
            opticalMode = SleepOpticalMode.fromWireValue(_recordingState.value.deviceStatus.opticalMode),
            eegSample = downsampledEegSample
        )
        if (downsampledEegSample != null) {
            sleepStagePipeline.ingest(downsampledEegSample)
        }
        val stageSnapshot = sleepStagePipeline.snapshot()

        val nextPacketCount = sessionIoMutex.withLock {
            val session = activeSession
            if (_recordingState.value.isRecording && session != null) {
                repository.appendPacket(session, packet)
                session.packetCount
            } else {
                _recordingState.value.packetCount
            }
        }

        _recordingState.update {
            it.copy(
                latestPacket = packet,
                packetCount = nextPacketCount,
                signalSnapshot = snapshot,
                sleepStageSnapshot = stageSnapshot
            )
        }
    }

    private fun logRaw51Check(packet: HeadbandRawPacket) {
        val counts = packet.eegCounts
        if (counts.size < 8) return
        Log.d(
            "RAW51_CHECK",
            "seq=${packet.sequence} " +
                "ch5=${counts[4]} " +
                "ch6=${counts[5]} " +
                "ch7=${counts[6]} " +
                "ch8=${counts[7]} " +
                "diff57=${counts[4] - counts[6]} " +
                "diff68=${counts[5] - counts[7]}"
        )
    }

    private suspend fun finishActiveSession(status: SleepSessionStatus): String? {
        val finishedSession = sessionIoMutex.withLock {
            val session = activeSession ?: return@withLock null
            activeSession = null
            repository.finishSession(session, status)
        } ?: return null
        if (finishedSession.packetCount <= 0L) return null
        return runCatching {
            repository.exportSessionBundle(finishedSession).locationHint
        }.onFailure { error ->
            _recordingState.update {
                it.copy(message = "采集已保存，但导出失败: ${error.message ?: "unknown error"}")
            }
        }.getOrNull()
    }

    private fun resetSignalProcessors() {
        eegDownsampler = SleepEegDownsampler()
        signalPipeline = SleepSignalPipeline(snapshotIntervalPackets = 24)
        // Re-use the same engine instance — no need to reload the model weights.
        sleepStagePipeline = SleepStagePipeline(inferenceEngine = sleepStageInferenceEngine)
    }

    private fun acquireWakeLock() {
        val existing = wakeLock
        if (existing?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SleepAgent:SleepRecording"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun ensureForeground() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        foregroundStarted = true
    }

    private fun updateForegroundNotification() {
        if (!foregroundStarted) return
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun stopForegroundIfIdle() {
        if (!foregroundStarted || _recordingState.value.isRecording) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        foregroundStarted = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Sleep recording",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val packetCount = _recordingState.value.packetCount
        val text = if (_recordingState.value.isRecording) {
            "正在采集睡眠数据，已记录 $packetCount 包"
        } else {
            "正在准备睡眠采集"
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SleepAgent 睡眠采集")
            .setContentText(text)
            .setOngoing(_recordingState.value.isRecording)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val ACTION_START_RECORDING_FOREGROUND =
            "com.sleepagent.prototype.action.START_RECORDING_FOREGROUND"
        private const val NOTIFICATION_CHANNEL_ID = "sleep_recording"
        private const val NOTIFICATION_ID = 1001

        fun requestForegroundStart(context: Context) {
            val intent = Intent(context, SleepRecordingService::class.java).apply {
                action = ACTION_START_RECORDING_FOREGROUND
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

private class LazyFallbackSleepStageInferenceEngine(
    private val contextProvider: () -> Context
) : SleepStageInferenceEngine {
    private val fallbackEngine = MockSleepStageInferenceEngine()
    private var realEngine: SleepStageInferenceEngine? = null
    private var realEngineDisabled = false

    override fun predict(input: Array<FloatArray>): SleepStagePrediction {
        if (realEngineDisabled) {
            return fallbackEngine.predict(input)
        }

        return runCatching {
            val engine = realEngine ?: TinyEEGNetInferenceEngine(contextProvider()).also {
                realEngine = it
            }
            engine.predict(input)
        }.getOrElse {
            realEngineDisabled = true
            realEngine = null
            fallbackEngine.predict(input)
        }
    }
}
