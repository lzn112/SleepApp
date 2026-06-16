package com.sleepagent.prototype.sleep

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sleepagent.prototype.data.SleepLocalPreferences
import com.sleepagent.prototype.data.SleepPlanPreference
import com.sleepagent.prototype.data.SleepPreference
import com.sleepagent.prototype.data.SleepSessionStatus
import com.sleepagent.prototype.device.DeviceConnectionState
import com.sleepagent.prototype.device.HeadbandDevice
import com.sleepagent.prototype.device.HeadbandRawPacket
import com.sleepagent.prototype.device.HeadbandStatus
import com.sleepagent.prototype.device.MissingDevicePermissionsException
import com.sleepagent.prototype.device.SleepOpticalMode
import com.sleepagent.prototype.device.TDCS_DEFAULT_AMPLITUDE
import com.sleepagent.prototype.device.TDCS_DEFAULT_BOOST
import com.sleepagent.prototype.device.TDCS_DEFAULT_CHANNEL
import com.sleepagent.prototype.device.TDCS_DEFAULT_CURRENT
import com.sleepagent.prototype.device.TdcsConfig
import com.sleepagent.prototype.device.TdcsState
import com.sleepagent.prototype.sleep.processing.SleepSignalSnapshot
import com.sleepagent.prototype.sleep.staging.SleepStageSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class SleepScreenMode {
    Setup,
    Monitoring
}

private data class SleepPlanUiState(
    val bedtime: String = "23:30",
    val wakeTime: String = "07:30",
    val smartWakeEnabled: Boolean = true,
    val smartWakeStart: String = "07:00",
    val smartWakeEnd: String = "07:30",
    val soundAidEnabled: Boolean = true,
    val soundAidName: String = "雨声",
    val soundDurationMin: Int = 30,
    val fadeOutEnabled: Boolean = true,
    val aiCompanionEnabled: Boolean = true,
    val aiCompanionMode: String = "呼吸放松",
    val aiCompanionDurationMin: Int = 5,
    val sleepGuardEnabled: Boolean = true
)

private fun SleepPlanPreference.toUiState() = SleepPlanUiState(
    bedtime = bedtime,
    wakeTime = wakeTime,
    smartWakeEnabled = smartWakeEnabled,
    smartWakeStart = smartWakeStart,
    smartWakeEnd = smartWakeEnd,
    soundAidEnabled = soundAidEnabled,
    soundAidName = soundAidName,
    soundDurationMin = soundDurationMin,
    fadeOutEnabled = fadeOutEnabled,
    aiCompanionEnabled = aiCompanionEnabled,
    aiCompanionMode = aiCompanionMode,
    aiCompanionDurationMin = aiCompanionDurationMin,
    sleepGuardEnabled = sleepGuardEnabled
)

private fun SleepPlanUiState.toPreference() = SleepPlanPreference(
    bedtime = bedtime,
    wakeTime = wakeTime,
    smartWakeEnabled = smartWakeEnabled,
    smartWakeStart = smartWakeStart,
    smartWakeEnd = smartWakeEnd,
    soundAidEnabled = soundAidEnabled,
    soundAidName = soundAidName,
    soundDurationMin = soundDurationMin,
    fadeOutEnabled = fadeOutEnabled,
    aiCompanionEnabled = aiCompanionEnabled,
    aiCompanionMode = aiCompanionMode,
    aiCompanionDurationMin = aiCompanionDurationMin,
    sleepGuardEnabled = sleepGuardEnabled
)

private fun SleepPreference.toDefaultPlan() = SleepPlanPreference(
    bedtime = defaultBedtime,
    wakeTime = defaultWakeTime,
    smartWakeEnabled = smartWakeEnabled,
    smartWakeStart = adjustTime(defaultWakeTime, -30),
    smartWakeEnd = defaultWakeTime,
    soundAidEnabled = soundAidPreference.isNotBlank(),
    soundAidName = when {
        "雨声" in soundAidPreference -> "雨声"
        "白噪音" in soundAidPreference -> "白噪音"
        "海浪" in soundAidPreference -> "海浪"
        "森林" in soundAidPreference -> "森林"
        else -> "雨声"
    },
    aiCompanionEnabled = aiCompanionEnabled
)

private data class SleepDeviceUiStatus(
    val isStreaming: Boolean = false,
    val connectedDevice: HeadbandDevice? = null,
    val message: String? = null,
    val opticalMode: SleepOpticalMode = SleepOpticalMode.OFF,
    val tdcs: TdcsState = TdcsState(),
    val connectionDiagnostics: List<String> = emptyList(),
    val scanDiagnostics: List<String> = emptyList(),
    val lastConnectionError: String? = null,
    val lastScanError: String? = null
)

private fun HeadbandStatus.toUiStatus(): SleepDeviceUiStatus {
    return SleepDeviceUiStatus(
        isStreaming = isStreaming,
        connectedDevice = connectedDevice,
        message = message,
        opticalMode = SleepOpticalMode.fromWireValue(opticalMode),
        tdcs = tdcs,
        connectionDiagnostics = connectionDiagnostics,
        scanDiagnostics = scanDiagnostics,
        lastConnectionError = lastConnectionError,
        lastScanError = lastScanError
    )
}

// ── Unified Intervention Models ──

enum class InterventionType { SOUND, ELECTRICAL, SMART_WAKE, AI_COMPANION }

enum class InterventionRunState {
    DISABLED, PREPARING, RUNNING, PAUSED, SWITCHING, COMPLETED, ERROR
}

data class InterventionSummary(
    val type: InterventionType,
    val enabled: Boolean,
    val title: String,
    val summary: String,
    val runState: InterventionRunState = if (enabled) InterventionRunState.RUNNING else InterventionRunState.DISABLED,
    val canAdjust: Boolean = true,
    val canPause: Boolean = false,
    val icon: ImageVector = Icons.Default.AutoAwesome
)

// ── Stimulation Modes ──

enum class StimulationMode(
    val label: String,
    val techName: String,
    val frequency: Int,
    val negative: Int,
    val wave: Int
) {
    TDCS_LIKE("恒流舒缓", "tDCS-like", 50000, 0, 2),
    THETA_5HZ("Theta放松", "5 Hz tACS", 100000, 1, 0),
    ALPHA_10HZ("Alpha安静", "10 Hz tACS", 50000, 1, 0),
    BIPHASIC_5HZ("低频节律", "5 Hz双相矩形", 100000, 1, 2),
    CES_100HZ("微电舒缓", "100 Hz CES-like", 5000, 1, 2)
}

data class StimulationLevel(
    val level: Int,
    val displayName: String,
    val boost: Int,
    val current: Int,
    val amplitude: Int
)

val stimulationLevels = listOf(
    StimulationLevel(1, "轻柔", boost = 1, current = 1, amplitude = 10),
    StimulationLevel(2, "舒适", boost = 1, current = 1, amplitude = 25),
    StimulationLevel(3, "标准", boost = 1, current = 1, amplitude = 40),
    StimulationLevel(4, "加强", boost = 1, current = 2, amplitude = 30),
    StimulationLevel(5, "强效", boost = 2, current = 2, amplitude = 50)
)

// ── Intervention States ──

data class ElectricalInterventionState(
    val enabled: Boolean = false,
    val mode: StimulationMode = StimulationMode.ALPHA_10HZ,
    val level: Int = 1,
    val durationMinutes: Int = 20,
    val stopAfterSleepDetected: Boolean = true,
    val runState: InterventionRunState = InterventionRunState.DISABLED,
    val startedAt: Long? = null,
    val pendingMode: StimulationMode? = null,
    val pendingLevel: Int? = null
)

enum class SoundType(val label: String) {
    RAIN("雨声"),
    WAVES("海浪"),
    PINK_NOISE("粉红噪声"),
    ALPHA_SOUND("个性化Alpha"),
    AI_RELAX("AI引导放松")
}

enum class SoundStopMode(val label: String) {
    FIXED_TIME("固定时间后停止"),
    SLEEP_DETECTED("入睡后渐弱停止"),
    ALL_NIGHT("整夜播放")
}

data class SoundInterventionState(
    val enabled: Boolean = true,
    val soundType: SoundType = SoundType.RAIN,
    val volume: Float = 0.35f,
    val durationMinutes: Int = 30,
    val stopMode: SoundStopMode = SoundStopMode.SLEEP_DETECTED,
    val fadeIn: Boolean = true,
    val fadeOut: Boolean = true,
    val runState: InterventionRunState = InterventionRunState.DISABLED
)

@Composable
fun SleepScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var recordingService by remember { mutableStateOf<SleepRecordingService?>(null) }
    val fallbackRecordingStateFlow = remember { MutableStateFlow(SleepRecordingState()) }
    val recordingState by (recordingService?.recordingState ?: fallbackRecordingStateFlow).collectAsState()
    var useMockManager by rememberSaveable { mutableStateOf(false) }
    val scannedDevices = remember { mutableStateListOf<HeadbandDevice>() }
    var uiMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isStartingSleep by rememberSaveable { mutableStateOf(false) }
    var permissionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var sleepScreenMode by rememberSaveable { mutableStateOf(SleepScreenMode.Setup.name) }
    var selectedDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var sleepPlan by remember {
        val savedPlan = SleepLocalPreferences.loadSleepPlan(appContext)
        val defaultPlan = SleepLocalPreferences.loadSleepPreference(appContext).toDefaultPlan()
        mutableStateOf((savedPlan ?: defaultPlan).toUiState())
    }
    var electricalState by rememberSaveable { mutableStateOf(ElectricalInterventionState()) }
    var soundState by rememberSaveable { mutableStateOf(SoundInterventionState()) }

    DisposableEffect(appContext) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                recordingService = (service as? SleepRecordingService.LocalBinder)?.service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                recordingService = null
            }
        }
        val intent = Intent(appContext, SleepRecordingService::class.java)
        appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose {
            runCatching { appContext.unbindService(connection) }
            recordingService = null
        }
    }

    val screenMode = remember(sleepScreenMode) {
        runCatching { SleepScreenMode.valueOf(sleepScreenMode) }.getOrDefault(SleepScreenMode.Setup)
    }
    val connectionState = recordingState.connectionState
    val uiDeviceStatus = recordingState.deviceStatus.toUiStatus()
    val latestPacket = recordingState.latestPacket
    val packetCount = recordingState.packetCount
    val signalSnapshot = recordingState.signalSnapshot
    val sleepStageSnapshot = recordingState.sleepStageSnapshot
    val currentSessionId = recordingState.sessionId
    val isRecording = recordingState.isRecording
    val displayMessage = uiMessage ?: recordingState.message
    val permissionsToRequest = remember { runtimePermissionsForBle() }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val denied = grantResults.filterValues { granted -> !granted }.keys
        permissionMessage = if (denied.isEmpty()) {
            "BLE 权限已授予"
        } else {
            "仍缺少权限: ${denied.joinToString()}"
        }
    }

    when (screenMode) {
        SleepScreenMode.Setup -> {
            SleepSetupScreen(
                sleepPlan = sleepPlan,
                onPlanChange = { nextPlan ->
                    sleepPlan = nextPlan
                    SleepLocalPreferences.saveSleepPlan(appContext, nextPlan.toPreference())
                },
                electricalState = electricalState,
                onElectricalStateChange = { electricalState = it },
                soundState = soundState,
                onSoundStateChange = { soundState = it },
                useMockManager = useMockManager,
                onUseMockManagerChange = { useMockManager = it },
                connectionState = connectionState,
                deviceStatus = uiDeviceStatus,
                scannedDevices = scannedDevices,
                uiMessage = displayMessage,
                isRecording = isRecording,
                isStartingSleep = isStartingSleep,
                permissionMessage = permissionMessage,
                hasPermissions = hasAllPermissions(context, permissionsToRequest),
                selectedDeviceId = selectedDeviceId,
                onRequestPermissions = { permissionLauncher.launch(permissionsToRequest.toTypedArray()) },
                onScan = {
                    scope.launch {
                        uiMessage = null
                        val service = recordingService
                        if (service == null) {
                            uiMessage = "采集服务尚未就绪，请稍后再试。"
                            return@launch
                        }
                        runBleAction(
                            context = context,
                            permissionsToRequest = permissionsToRequest,
                            onPermissionRequired = {
                                permissionMessage = "扫描前请先授予 BLE 权限。"
                                permissionLauncher.launch(permissionsToRequest.toTypedArray())
                            },
                            onError = { uiMessage = it }
                        ) {
                            scannedDevices.clear()
                            scannedDevices.addAll(service.scan(useMockManager).distinctBy { it.deviceId })
                            uiMessage = if (scannedDevices.isEmpty()) {
                                "没有扫描到兼容设备。"
                            } else {
                                "扫描完成，共找到 ${scannedDevices.size} 台设备。"
                            }
                        }
                    }
                },
                onConnectDevice = { device ->
                    selectedDeviceId = device.deviceId
                    scope.launch {
                        uiMessage = null
                        val service = recordingService
                        if (service == null) {
                            uiMessage = "采集服务尚未就绪，请稍后再试。"
                            return@launch
                        }
                        runBleAction(
                            context = context,
                            permissionsToRequest = permissionsToRequest,
                            onPermissionRequired = {
                                permissionMessage = "连接前请先授予 BLE 权限。"
                                permissionLauncher.launch(permissionsToRequest.toTypedArray())
                            },
                            onError = { uiMessage = it }
                        ) {
                            service.connect(useMockManager, device.deviceId)
                            uiMessage = "已连接 ${device.name}。"
                        }
                    }
                },
                onStartSleep = {
                    scope.launch {
                        uiMessage = null
                        if (isStartingSleep) return@launch
                        val service = recordingService
                        if (service == null) {
                            uiMessage = "采集服务尚未就绪，请稍后再试。"
                            return@launch
                        }
                        if (connectionState != DeviceConnectionState.CONNECTED) {
                            uiMessage = "请先连接设备，再开始睡眠。"
                            return@launch
                        }
                        val connectedDevice = uiDeviceStatus.connectedDevice
                            ?: scannedDevices.firstOrNull { it.deviceId == selectedDeviceId }
                        if (connectedDevice == null) {
                            uiMessage = "还没有拿到已连接设备信息，请重新选择设备后再试。"
                            return@launch
                        }

                        isStartingSleep = true
                        try {
                            runBleAction(
                                context = context,
                                permissionsToRequest = permissionsToRequest,
                                onPermissionRequired = {
                                    permissionMessage = "开始睡眠前请先授予 BLE 权限。"
                                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                                },
                                onError = { uiMessage = it }
                            ) {
                                SleepRecordingService.requestForegroundStart(appContext)
                                service.startRecording(useMockManager, connectedDevice)
                                sleepScreenMode = SleepScreenMode.Monitoring.name
                                uiMessage = null
                            }
                        } finally {
                            isStartingSleep = false
                        }
                    }
                },
                onStopSleep = {
                    scope.launch {
                        val service = recordingService
                        if (service == null) {
                            uiMessage = "采集服务尚未就绪，请稍后再试。"
                            return@launch
                        }
                        isStartingSleep = false
                        var exportHint: String? = null
                        runCatching {
                            exportHint = service.stopRecording(SleepSessionStatus.COMPLETED)
                        }.onFailure { error ->
                            uiMessage = "结束采集失败: ${error.message ?: "unknown error"}"
                        }
                        sleepScreenMode = SleepScreenMode.Setup.name
                        uiMessage = when {
                            exportHint != null -> "已结束睡眠并返回设置页。导出包: $exportHint"
                            !uiMessage.isNullOrEmpty() -> uiMessage.orEmpty()
                            else -> "已结束睡眠并返回设置页。"
                        }
                    }
                },
                onDisconnect = {
                    scope.launch {
                        val service = recordingService
                        if (service == null) {
                            uiMessage = "采集服务尚未就绪，请稍后再试。"
                            return@launch
                        }
                        var exportHint: String? = null
                        isStartingSleep = false
                        runCatching {
                            exportHint = service.disconnect()
                        }.onFailure { error ->
                            uiMessage = "会话收尾失败: ${error.message ?: "unknown error"}"
                        }
                        scannedDevices.clear()
                        selectedDeviceId = null
                        sleepScreenMode = SleepScreenMode.Setup.name
                        uiMessage = when {
                            exportHint != null -> "设备已断开。导出包: $exportHint"
                            !uiMessage.isNullOrEmpty() -> uiMessage.orEmpty()
                            else -> "设备已断开。"
                        }
                    }
                }
            )
        }

        SleepScreenMode.Monitoring -> {
            SleepMonitorScreen(
                sleepPlan = sleepPlan,
                electricalState = electricalState,
                onElectricalStateChange = { electricalState = it },
                soundState = soundState,
                onSoundStateChange = { soundState = it },
                connectionState = connectionState,
                deviceStatus = uiDeviceStatus,
                uiMessage = displayMessage,
                latestPacket = latestPacket,
                packetCount = packetCount,
                signalSnapshot = signalSnapshot,
                sleepStageSnapshot = sleepStageSnapshot,
                sessionId = currentSessionId,
                onHrvChannelChange = { channel -> recordingService?.updateHrvChannel(channel) },
                onFnirsChannelChange = { channel -> recordingService?.updateFnirsChannel(channel) },
                onOpticalModeChange = { mode ->
                    scope.launch {
                        uiMessage = null
                        runCatching {
                            recordingService?.setOpticalMode(mode) ?: error("采集服务尚未就绪，请稍后再试。")
                        }
                            .onFailure { uiMessage = it.message ?: "Failed to switch optical mode" }
                    }
                },
                onStartTdcs = { config ->
                    scope.launch {
                        uiMessage = null
                        runBleAction(
                            context = context,
                            permissionsToRequest = permissionsToRequest,
                            onPermissionRequired = { permissionLauncher.launch(permissionsToRequest.toTypedArray()) },
                            onError = { uiMessage = it }
                        ) {
                            recordingService?.startTdcs(config) ?: error("采集服务尚未就绪，请稍后再试。")
                        }
                    }
                },
                onStopTdcs = { channel ->
                    scope.launch {
                        uiMessage = null
                        runBleAction(
                            context = context,
                            permissionsToRequest = permissionsToRequest,
                            onPermissionRequired = { permissionLauncher.launch(permissionsToRequest.toTypedArray()) },
                            onError = { uiMessage = it }
                        ) {
                            recordingService?.stopTdcs(channel) ?: error("采集服务尚未就绪，请稍后再试。")
                        }
                    }
                },
                onBack = {
                    sleepScreenMode = SleepScreenMode.Setup.name
                    uiMessage = "已返回设置页，监测仍可继续。"
                },
                onEndSleep = {
                    scope.launch {
                        val service = recordingService
                        if (service == null) {
                            uiMessage = "采集服务尚未就绪，请稍后再试。"
                            return@launch
                        }
                        var exportHint: String? = null
                        isStartingSleep = false
                        runCatching {
                            exportHint = service.stopRecording(SleepSessionStatus.COMPLETED)
                        }.onFailure { error ->
                            uiMessage = "结束采集失败: ${error.message ?: "unknown error"}"
                        }
                        sleepScreenMode = SleepScreenMode.Setup.name
                        uiMessage = when {
                            exportHint != null -> "已结束睡眠并返回设置页。导出包: $exportHint"
                            !uiMessage.isNullOrEmpty() -> uiMessage.orEmpty()
                            else -> "已结束睡眠并返回设置页。"
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepSetupScreen(
    sleepPlan: SleepPlanUiState,
    onPlanChange: (SleepPlanUiState) -> Unit,
    electricalState: ElectricalInterventionState,
    onElectricalStateChange: (ElectricalInterventionState) -> Unit,
    soundState: SoundInterventionState,
    onSoundStateChange: (SoundInterventionState) -> Unit,
    useMockManager: Boolean,
    onUseMockManagerChange: (Boolean) -> Unit,
    connectionState: DeviceConnectionState,
    deviceStatus: SleepDeviceUiStatus,
    scannedDevices: List<HeadbandDevice>,
    uiMessage: String?,
    isRecording: Boolean,
    isStartingSleep: Boolean,
    permissionMessage: String?,
    hasPermissions: Boolean,
    selectedDeviceId: String?,
    onRequestPermissions: () -> Unit,
    onScan: () -> Unit,
    onConnectDevice: (HeadbandDevice) -> Unit,
    onStartSleep: () -> Unit,
    onStopSleep: () -> Unit,
    onDisconnect: () -> Unit
) {
    val selectedDevice = scannedDevices.firstOrNull { it.deviceId == selectedDeviceId }
    val statusMessage = uiMessage
        ?: permissionMessage
        ?: deviceStatus.message
        ?: if (connectionState == DeviceConnectionState.CONNECTED) "设备已连接，可开始睡眠。" else "先扫描并连接设备。"

    // Modal states
    var showElectricalSheet by rememberSaveable { mutableStateOf(false) }
    var showSoundSheet by rememberSaveable { mutableStateOf(false) }
    var showSmartWakeSheet by rememberSaveable { mutableStateOf(false) }

    ScreenContainer(
        title = "今晚准备睡觉",
        subtitle = "确认设备和设置，开始你的睡前流程。"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 1. Sleep Prepare Hero
            SleepPrepareHeroCard(
                plan = sleepPlan,
                connectionState = connectionState,
                isStartingSleep = isStartingSleep,
                isRecording = isRecording,
                onStartSleep = onStartSleep
            )

            // 2. Sleep Time Plan
            SleepTimePlanCard(
                plan = sleepPlan,
                onPlanChange = onPlanChange
            )

            // 3. Tonight's Intervention Plan
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "今晚睡眠方案",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.55f)
                )
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Sound row
                        InterventionRow(
                            icon = Icons.Default.MusicNote,
                            title = "入睡声音",
                            summary = if (soundState.enabled) {
                                "${soundState.soundType.label} · ${if (soundState.stopMode == SoundStopMode.FIXED_TIME) "${soundState.durationMinutes}分钟" else soundState.stopMode.label}"
                            } else "未开启",
                            enabled = soundState.enabled,
                            onClick = { showSoundSheet = true }
                        )
                        DividerLine()
                        // Electrical row
                        InterventionRow(
                            icon = Icons.Default.Bolt,
                            title = "节律微电",
                            summary = if (electricalState.enabled) {
                                "${electricalState.mode.label} · ${stimulationLevels.find{it.level==electricalState.level}?.displayName ?: "${electricalState.level}档"}"
                            } else "未开启",
                            enabled = electricalState.enabled,
                            onClick = { showElectricalSheet = true }
                        )
                        DividerLine()
                        // Smart wake row
                        InterventionRow(
                            icon = Icons.Default.Alarm,
                            title = "智能唤醒",
                            summary = if (sleepPlan.smartWakeEnabled) "${sleepPlan.smartWakeStart}–${sleepPlan.smartWakeEnd}" else "未开启",
                            enabled = sleepPlan.smartWakeEnabled,
                            onClick = { showSmartWakeSheet = true }
                        )
                        DividerLine()
                        // AI companion row
                        InterventionRow(
                            icon = Icons.Default.AutoAwesome,
                            title = "AI陪伴",
                            summary = if (sleepPlan.aiCompanionEnabled) "${sleepPlan.aiCompanionMode} · ${sleepPlan.aiCompanionDurationMin}分钟" else "未开启",
                            enabled = sleepPlan.aiCompanionEnabled,
                            onClick = { /* TODO: open AI companion config */ }
                        )
                    }
                }
            }

            // 4. Device Readiness
            DeviceReadinessCard(
                connectionState = connectionState,
                hasPermissions = hasPermissions,
                selectedDeviceName = selectedDevice?.name,
                connectedDeviceName = deviceStatus.connectedDevice?.name,
                statusMessage = statusMessage,
                onScan = onScan,
                onRequestPermissions = onRequestPermissions,
                onDisconnect = onDisconnect
            )

            // 5. Device List
            if (scannedDevices.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "已发现设备",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                    scannedDevices.forEach { device ->
                        DeviceRow(
                            device = device,
                            selected = selectedDeviceId == device.deviceId,
                            connected = deviceStatus.connectedDevice?.deviceId == device.deviceId,
                            onClick = { onConnectDevice(device) }
                        )
                    }
                }
            }

            // 6. Advanced Debug
            AdvancedDebugSection(
                useMockManager = useMockManager,
                onUseMockManagerChange = onUseMockManagerChange,
                connectionDiagnostics = deviceStatus.connectionDiagnostics,
                scanDiagnostics = deviceStatus.scanDiagnostics,
                lastConnectionError = deviceStatus.lastConnectionError,
                lastScanError = deviceStatus.lastScanError
            )
        }
    }

    // ── Modal BottomSheets ──

    // Electrical stimulation config
    if (showElectricalSheet) {
        ElectricalStimulationSheet(
            state = electricalState,
            onDismiss = { showElectricalSheet = false },
            onSave = {
                onElectricalStateChange(it)
                showElectricalSheet = false
            }
        )
    }

    // Sound intervention config
    if (showSoundSheet) {
        SoundInterventionSheet(
            state = soundState,
            onDismiss = { showSoundSheet = false },
            onSave = {
                onSoundStateChange(it)
                showSoundSheet = false
            }
        )
    }

    // Smart wake config
    if (showSmartWakeSheet) {
        SmartWakeSheet(
            plan = sleepPlan,
            onDismiss = { showSmartWakeSheet = false },
            onSave = {
                onPlanChange(it)
                showSmartWakeSheet = false
            }
        )
    }
}

// ── Intervention Row ──

@Composable
private fun InterventionRow(
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) Color(0xFF6C8CFF).copy(alpha = 0.80f) else Color.White.copy(alpha = 0.28f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.78f)
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = if (enabled) 0.50f else 0.30f)
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.28f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.05f))
    )
}

@Composable
private fun SleepMonitorScreen(
    sleepPlan: SleepPlanUiState,
    electricalState: ElectricalInterventionState,
    @Suppress("UNUSED_PARAMETER") onElectricalStateChange: (ElectricalInterventionState) -> Unit,
    soundState: SoundInterventionState,
    @Suppress("UNUSED_PARAMETER") onSoundStateChange: (SoundInterventionState) -> Unit,
    connectionState: DeviceConnectionState,
    deviceStatus: SleepDeviceUiStatus,
    uiMessage: String?,
    latestPacket: HeadbandRawPacket?,
    packetCount: Long,
    signalSnapshot: SleepSignalSnapshot,
    sleepStageSnapshot: SleepStageSnapshot,
    sessionId: String?,
    onHrvChannelChange: (Int) -> Unit,
    onFnirsChannelChange: (Int) -> Unit,
    onOpticalModeChange: (SleepOpticalMode) -> Unit,
    onStartTdcs: (TdcsConfig) -> Unit,
    onStopTdcs: (String) -> Unit,
    onBack: () -> Unit,
    onEndSleep: () -> Unit
) {
    var hrvChannel by rememberSaveable { mutableStateOf(0) }
    var fnirsChannel by rememberSaveable { mutableStateOf(0) }
    var tdcsBoostText by rememberSaveable { mutableStateOf(TDCS_DEFAULT_BOOST.toString()) }
    var tdcsCurrentText by rememberSaveable { mutableStateOf(TDCS_DEFAULT_CURRENT.toString()) }
    var tdcsAmplitudeText by rememberSaveable { mutableStateOf(TDCS_DEFAULT_AMPLITUDE.toString()) }
    var tdcsInputMessage by rememberSaveable { mutableStateOf<String?>(null) }

    // Monitor sheet states
    var showElectricalSheet by rememberSaveable { mutableStateOf(false) }
    var showSoundSheet by rememberSaveable { mutableStateOf(false) }
    var showSmartWakeSheet by rememberSaveable { mutableStateOf(false) }

    val currentPacket = latestPacket
    val signalState = when {
        currentPacket == null -> "等待 EEG 数据"
        currentPacket.state == null -> "采集中"
        else -> "state=${currentPacket.state}"
    }
    val opticalState = signalSnapshot.opticalState
    val statusMessage = uiMessage ?: deviceStatus.message ?: signalState

    ScreenContainer(
        title = "",
        subtitle = ""
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Error banner
            if (!uiMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = uiMessage,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // 1. Sleep Guard Hero
            SleepGuardHeroCard(
                connectionState = connectionState,
                statusMessage = statusMessage,
                onEndSleep = onEndSleep
            )

            // 2. Tonight's Plan (with live status)
            MonitoringInterventionPanel(
                sleepPlan = sleepPlan,
                electricalState = electricalState,
                soundState = soundState,
                onAdjustElectrical = { showElectricalSheet = true },
                onAdjustSound = { showSoundSheet = true },
                onAdjustSmartWake = { showSmartWakeSheet = true }
            )

            // 3. Real-time sleep stage (lightweight strip)
            SleepStageStrip(snapshot = sleepStageSnapshot)

            // 3. Return to setup (secondary)
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "退出睡眠阶段",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.48f),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }

            // 3. Advanced Signal (collapsible EEG / HRV / fNIRS)
            AdvancedSignalCardsSection(
                signalSnapshot = signalSnapshot,
                deviceStatus = deviceStatus,
                hrvChannel = hrvChannel,
                fnirsChannel = fnirsChannel,
                onHrvChannelChange = onHrvChannelChange,
                onFnirsChannelChange = onFnirsChannelChange
            )

            // 4. Debug (collapsible: session, packet, optical, tDCS)
            CollapsedDebugSection(
                sessionId = sessionId,
                packetCount = packetCount,
                opticalMode = deviceStatus.opticalMode,
                tdcsState = deviceStatus.tdcs,
                tdcsBoostText = tdcsBoostText,
                onTdcsBoostTextChange = { tdcsBoostText = it },
                tdcsCurrentText = tdcsCurrentText,
                onTdcsCurrentTextChange = { tdcsCurrentText = it },
                tdcsAmplitudeText = tdcsAmplitudeText,
                onTdcsAmplitudeTextChange = { tdcsAmplitudeText = it },
                tdcsInputMessage = tdcsInputMessage,
                onOpticalModeChange = onOpticalModeChange,
                onStartTdcs = { config -> onStartTdcs(config) },
                onStopTdcs = { onStopTdcs(TDCS_DEFAULT_CHANNEL) }
            )
        }
    }
}

// ── Setup Composables ──

@Composable
private fun SleepPrepareHeroCard(
    plan: SleepPlanUiState,
    connectionState: DeviceConnectionState,
    isStartingSleep: Boolean,
    isRecording: Boolean,
    onStartSleep: () -> Unit
) {
    val isReady = connectionState == DeviceConnectionState.CONNECTED
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "今晚准备睡觉",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.94f)
                )
                StatusBadge(
                    text = if (isReady) "设备就绪" else "待连接",
                    color = if (isReady) Color(0xFF2FCBBC) else Color.White.copy(alpha = 0.40f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("预计入睡", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.38f))
                    Text(plan.bedtime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.90f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("目标起床", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.38f))
                    Text(plan.wakeTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.90f))
                }
                if (plan.smartWakeEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("智能唤醒", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.38f))
                        Text("${plan.smartWakeStart}-${plan.smartWakeEnd}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6C8CFF))
                    }
                }
            }

            // Active mechanisms summary
            val activeItems = mutableListOf<String>()
            if (plan.soundAidEnabled) {
                activeItems.add("助眠声音：${plan.soundAidName} · ${plan.soundDurationMin} 分钟" +
                    if (plan.fadeOutEnabled) "后渐弱" else "后停止")
            }
            if (plan.aiCompanionEnabled) {
                activeItems.add("AI 陪伴：${plan.aiCompanionMode} · ${plan.aiCompanionDurationMin} 分钟")
            }
            if (activeItems.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    activeItems.forEach { item ->
                        Text(
                            item,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.44f)
                        )
                    }
                }
            }

            Button(
                onClick = onStartSleep,
                enabled = isReady && !isStartingSleep,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6C8CFF),
                    contentColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.08f),
                    disabledContentColor = Color.White.copy(alpha = 0.30f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when {
                        isStartingSleep -> "准备中..."
                        isRecording -> "正在监测中"
                        !isReady -> "请先连接设备"
                        else -> "开始睡前流程"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

// ── Setup: Sleep Time Plan ──

@Composable
private fun SleepTimePlanCard(
    plan: SleepPlanUiState,
    onPlanChange: (SleepPlanUiState) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "今晚时间",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.72f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TimeAdjustColumn(
                    label = "预计入睡",
                    time = plan.bedtime,
                    onMinus = {
                        val adjusted = adjustTime(plan.bedtime, -15)
                        onPlanChange(plan.copy(bedtime = adjusted))
                    },
                    onPlus = {
                        val adjusted = adjustTime(plan.bedtime, 15)
                        onPlanChange(plan.copy(bedtime = adjusted))
                    }
                )
                TimeAdjustColumn(
                    label = "目标起床",
                    time = plan.wakeTime,
                    onMinus = {
                        val adjusted = adjustTime(plan.wakeTime, -15)
                        onPlanChange(plan.copy(
                            wakeTime = adjusted,
                            smartWakeStart = adjustTime(adjusted, -30),
                            smartWakeEnd = adjusted
                        ))
                    },
                    onPlus = {
                        val adjusted = adjustTime(plan.wakeTime, 15)
                        onPlanChange(plan.copy(
                            wakeTime = adjusted,
                            smartWakeStart = adjustTime(adjusted, -30),
                            smartWakeEnd = adjusted
                        ))
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("提前30分钟" to -30, "照常" to 0, "晚30分钟" to 30).forEach { (label, offset) ->
                    val isActive = when (offset) {
                        -30 -> plan.bedtime == "23:00"
                        0 -> plan.bedtime == "23:30"
                        30 -> plan.bedtime == "00:00"
                        else -> false
                    }
                    Surface(
                        onClick = {
                            val adjusted = adjustTime("23:30", offset)
                            onPlanChange(plan.copy(bedtime = adjusted))
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isActive) Color(0xFF6C8CFF).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                        border = if (isActive) BorderStroke(1.dp, Color(0xFF6C8CFF).copy(alpha = 0.30f)) else null
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.44f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Text(
                "预计睡眠约 8 小时",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.34f)
            )
        }
    }
}

@Composable
private fun TimeAdjustColumn(
    label: String,
    time: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = onMinus,
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Text(
                    "-15",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(
                time,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.90f)
            )
            Surface(
                onClick = onPlus,
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Text(
                    "+15",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.38f))
    }
}

// ── Modal BottomSheet: Electrical Stimulation ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElectricalStimulationSheet(
    state: ElectricalInterventionState,
    onDismiss: () -> Unit,
    onSave: (ElectricalInterventionState) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(state.mode) }
    var level by rememberSaveable { mutableStateOf(state.level) }
    var durationMin by rememberSaveable { mutableStateOf(state.durationMinutes) }
    var stopOnSleep by rememberSaveable { mutableStateOf(state.stopAfterSleepDetected) }
    var showMoreModes by rememberSaveable { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF151B30),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            SheetHeader("节律微电", onDismiss)

            // Mode selection
            GroupLabel("模式")
            val primaryModes = listOf(StimulationMode.ALPHA_10HZ, StimulationMode.THETA_5HZ, StimulationMode.CES_100HZ)
            val extraModes = listOf(StimulationMode.TDCS_LIKE, StimulationMode.BIPHASIC_5HZ)
            val visibleModes = if (showMoreModes) primaryModes + extraModes else primaryModes
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                visibleModes.forEach { m ->
                    val selected = mode == m
                    Surface(
                        onClick = { mode = m },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Color(0xFF6C8CFF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                        border = if (selected) BorderStroke(1.dp, Color(0xFF6C8CFF).copy(alpha = 0.30f)) else null
                    ) {
                        Text(
                            m.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.44f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            TextButton(
                text = if (showMoreModes) "收起更多模式" else "更多模式",
                onClick = { showMoreModes = !showMoreModes }
            )

            // Intensity (level)
            GroupLabel("强度")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                stimulationLevels.forEach { lvl ->
                    val selected = level == lvl.level
                    Surface(
                        onClick = { level = lvl.level },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Color(0xFF6C8CFF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                        border = if (selected) BorderStroke(1.dp, Color(0xFF6C8CFF).copy(alpha = 0.30f)) else null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "档${lvl.level}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.44f)
                            )
                            Text(
                                lvl.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color(0xFF6C8CFF).copy(alpha = 0.70f) else Color.White.copy(alpha = 0.28f)
                            )
                        }
                    }
                }
            }

            // Duration
            GroupLabel("持续时间")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10, 20, 30).forEach { mins ->
                    val selected = durationMin == mins
                    Surface(
                        onClick = { durationMin = mins },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Color(0xFF6C8CFF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                        border = if (selected) BorderStroke(1.dp, Color(0xFF6C8CFF).copy(alpha = 0.30f)) else null
                    ) {
                        Text(
                            "${mins}分钟",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.44f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Stop after sleep detected
            ToggleRow("检测到入睡后自动停止", stopOnSleep) { stopOnSleep = it }

            // Save / Close
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "关闭微电",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.50f),
                        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Button(
                    onClick = {
                        onSave(state.copy(
                            enabled = true,
                            mode = mode,
                            level = level,
                            durationMinutes = durationMin,
                            stopAfterSleepDetected = stopOnSleep
                        ))
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C8CFF), contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Modal BottomSheet: Sound Intervention ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundInterventionSheet(
    state: SoundInterventionState,
    onDismiss: () -> Unit,
    onSave: (SoundInterventionState) -> Unit
) {
    var soundType by rememberSaveable { mutableStateOf(state.soundType) }
    var volume by rememberSaveable { mutableStateOf(state.volume) }
    var durationMin by rememberSaveable { mutableStateOf(state.durationMinutes) }
    var stopMode by rememberSaveable { mutableStateOf(state.stopMode) }
    var fadeOut by rememberSaveable { mutableStateOf(state.fadeOut) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF151B30),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SheetHeader("入睡声音", onDismiss)

            // Sound type
            GroupLabel("声音类型")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SoundType.entries.forEach { st ->
                    val selected = soundType == st
                    Surface(
                        onClick = { soundType = st },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Color(0xFF6C8CFF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                        border = if (selected) BorderStroke(1.dp, Color(0xFF6C8CFF).copy(alpha = 0.30f)) else null
                    ) {
                        Text(
                            st.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.44f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Volume
            GroupLabel("音量 · ${(volume * 100).toInt()}%")
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                androidx.compose.material3.Slider(
                    value = volume,
                    onValueChange = { volume = (it * 100).roundToInt() / 100f },
                    valueRange = 0.05f..1f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Color(0xFF6C8CFF),
                        activeTrackColor = Color(0xFF6C8CFF),
                        inactiveTrackColor = Color.White.copy(alpha = 0.10f)
                    )
                )
            }

            // Duration
            GroupLabel("播放时长")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 45, 60).forEach { mins ->
                    val selected = durationMin == mins
                    Surface(
                        onClick = { durationMin = mins },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Color(0xFF6C8CFF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                        border = if (selected) BorderStroke(1.dp, Color(0xFF6C8CFF).copy(alpha = 0.30f)) else null
                    ) {
                        Text(
                            "${mins}分钟",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.44f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Stop mode
            GroupLabel("停止方式")
            SoundStopMode.entries.forEach { sm ->
                val selected = stopMode == sm
                Surface(
                    onClick = { stopMode = sm },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) Color(0xFF6C8CFF).copy(alpha = 0.12f) else Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            sm.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.50f)
                        )
                    }
                }
            }

            // Fade out
            ToggleRow("渐弱停止", fadeOut) { fadeOut = it }

            // Save / Close
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.50f),
                        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Button(
                    onClick = {
                        onSave(state.copy(
                            enabled = true,
                            soundType = soundType,
                            volume = volume,
                            durationMinutes = durationMin,
                            stopMode = stopMode,
                            fadeOut = fadeOut
                        ))
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C8CFF), contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Modal BottomSheet: Smart Wake ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartWakeSheet(
    plan: SleepPlanUiState,
    onDismiss: () -> Unit,
    onSave: (SleepPlanUiState) -> Unit
) {
    var enabled by rememberSaveable { mutableStateOf(plan.smartWakeEnabled) }
    var startTime by rememberSaveable { mutableStateOf(plan.smartWakeStart) }
    var endTime by rememberSaveable { mutableStateOf(plan.smartWakeEnd) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF151B30),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SheetHeader("智能唤醒", onDismiss)

            // Enable toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "启用智能唤醒",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.62f)
                )
                ToggleSwitch(enabled) { enabled = it }
            }

            if (enabled) {
                Text(
                    "在 ${startTime} - ${endTime} 之间，尽量选择更轻松的时机唤醒。最晚会在 ${endTime} 准时唤醒。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.48f)
                )

                GroupLabel("唤醒窗口")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        val selected = startTime == adjustTime(endTime, -mins)
                        Surface(
                            onClick = {
                                startTime = adjustTime(endTime, -mins)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) Color(0xFF6C8CFF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                            border = if (selected) BorderStroke(1.dp, Color(0xFF6C8CFF).copy(alpha = 0.30f)) else null
                        ) {
                            Text(
                                "${mins}分钟",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.44f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Text(
                    "唤醒窗口：${startTime} - ${endTime}   |   最晚唤醒：${endTime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.30f)
                )
            }

            // Save
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.50f),
                        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Button(
                    onClick = {
                        onSave(plan.copy(
                            smartWakeEnabled = enabled,
                            smartWakeStart = startTime,
                            smartWakeEnd = endTime
                        ))
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C8CFF), contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Sheet Shared Components ──

@Composable
private fun SheetHeader(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.94f)
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White.copy(alpha = 0.50f))
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = 0.44f)
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.50f)
        )
        ToggleSwitch(checked, onToggle)
    }
}

@Composable
private fun ToggleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = if (checked) Color(0xFF6C8CFF).copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f)
    ) {
        Text(
            if (checked) "开启" else "关闭",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (checked) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.40f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun TextButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF6C8CFF).copy(alpha = 0.70f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    )
}

// ── Time helper ──

private fun adjustTime(time: String, deltaMin: Int): String {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 23
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 30
    val total = hour * 60 + minute + deltaMin
    val adjusted = (total + 1440) % 1440
    return "%02d:%02d".format(adjusted / 60, adjusted % 60)
}

// ── Setup Composables ──

@Composable
private fun DeviceReadinessCard(
    connectionState: DeviceConnectionState,
    hasPermissions: Boolean,
    selectedDeviceName: String?,
    connectedDeviceName: String?,
    statusMessage: String?,
    onScan: () -> Unit,
    onRequestPermissions: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnected = connectionState == DeviceConnectionState.CONNECTED
    val deviceName = connectedDeviceName ?: selectedDeviceName
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "设备准备",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.72f)
                )
                StatusBadge(
                    text = if (isConnected) "已连接" else "未连接",
                    color = if (isConnected) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.30f)
                )
            }

            if (isConnected && deviceName != null) {
                Text(
                    "$deviceName 已连接",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.94f)
                )
                Text(
                    "信号准备中，今晚可以开始监测。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
            } else {
                Text(
                    "头环未连接",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.94f)
                )
                Text(
                    "睡前请先连接设备，确保整晚监测稳定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasPermissions) {
                    Button(
                        onClick = onScan,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6C8CFF),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            "查找头环",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Button(
                        onClick = onRequestPermissions,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B6B),
                            contentColor = Color.White
                        )
                    ) {
                        Text("授予权限")
                    }
                }
                if (isConnected) {
                    Surface(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.06f)
                    ) {
                        Text(
                            "断开设备",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.50f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            statusMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.40f)
                )
            }
        }
    }
}

@Composable
private fun AdvancedDebugSection(
    useMockManager: Boolean,
    onUseMockManagerChange: (Boolean) -> Unit,
    connectionDiagnostics: List<String>,
    scanDiagnostics: List<String>,
    lastConnectionError: String?,
    lastScanError: String?
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "高级模式",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.38f)
                    )
                    Text(
                        "数据源与连接诊断默认收起",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.26f)
                    )
                }
                Text(
                    if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.40f)
                )
            }

            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = !useMockManager,
                        onClick = { onUseMockManagerChange(false) },
                        label = { Text("Real BLE", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = useMockManager,
                        onClick = { onUseMockManagerChange(true) },
                        label = { Text("Mock", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                if (connectionDiagnostics.isNotEmpty()) {
                    Text(
                        "连接诊断: ${connectionDiagnostics.joinToString(" · ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.26f)
                    )
                }
                if (scanDiagnostics.isNotEmpty()) {
                    Text(
                        "扫描诊断: ${scanDiagnostics.joinToString(" · ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.26f)
                    )
                }
                if (lastConnectionError != null) {
                    Text(
                        "连接错误: $lastConnectionError",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF6B6B).copy(alpha = 0.50f)
                    )
                }
                if (lastScanError != null) {
                    Text(
                        "扫描错误: $lastScanError",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF6B6B).copy(alpha = 0.50f)
                    )
                }
            }
        }
    }
}

// ── Monitor Composables ──

@Composable
private fun SleepGuardHeroCard(
    connectionState: DeviceConnectionState,
    statusMessage: String?,
    onEndSleep: () -> Unit
) {
    val isStable = connectionState == DeviceConnectionState.CONNECTED
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(
                    text = if (isStable) "连接稳定" else "连接异常",
                    color = if (isStable) Color(0xFF6C8CFF) else Color(0xFFFF6B6B)
                )
            }

            Text(
                if (isStable) "监测已开启，明早为你生成睡眠复盘。"
                else "设备连接异常，正在尝试恢复...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.62f)
            )

            statusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.44f))
            }

            Button(
                onClick = onEndSleep,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B6B).copy(alpha = 0.12f),
                    contentColor = Color(0xFFFF6B6B)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "结束睡眠",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

// ── Monitor: Tonight's Plan Summary ──

@Composable
private fun MonitoringInterventionPanel(
    sleepPlan: SleepPlanUiState,
    electricalState: ElectricalInterventionState,
    soundState: SoundInterventionState,
    onAdjustElectrical: () -> Unit,
    onAdjustSound: () -> Unit,
    onAdjustSmartWake: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "今晚方案",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
            )
            // Sound
            MonitoringInterventionRow(
                icon = Icons.Default.MusicNote,
                title = "入睡声音",
                summary = if (soundState.enabled) {
                    val status = when (soundState.runState) {
                        InterventionRunState.RUNNING -> "播放中"
                        InterventionRunState.PAUSED -> "已暂停"
                        InterventionRunState.SWITCHING -> "切换中"
                        else -> ""
                    }
                    "${soundState.soundType.label}${if (status.isNotEmpty()) " · $status" else ""} · 音量${(soundState.volume * 100).toInt()}%"
                } else "未开启",
                runState = soundState.runState,
                onClick = onAdjustSound
            )
            DividerLine()
            // Electrical
            MonitoringInterventionRow(
                icon = Icons.Default.Bolt,
                title = "节律微电",
                summary = if (electricalState.enabled) {
                    when (electricalState.runState) {
                        InterventionRunState.RUNNING -> "${electricalState.mode.label} · ${stimulationLevels.find{it.level==electricalState.level}?.displayName ?: "${electricalState.level}档"}"
                        InterventionRunState.PAUSED -> "已暂停"
                        InterventionRunState.SWITCHING -> "正在平滑调整…"
                        InterventionRunState.PREPARING -> "准备中…"
                        else -> "${electricalState.mode.label}"
                    }
                } else "未开启",
                runState = electricalState.runState,
                onClick = onAdjustElectrical
            )
            DividerLine()
            // Smart wake
            MonitoringInterventionRow(
                icon = Icons.Default.Alarm,
                title = "智能唤醒",
                summary = if (sleepPlan.smartWakeEnabled) "${sleepPlan.smartWakeStart}–${sleepPlan.smartWakeEnd}" else "未开启",
                runState = InterventionRunState.RUNNING.takeIf { sleepPlan.smartWakeEnabled } ?: InterventionRunState.DISABLED,
                onClick = onAdjustSmartWake
            )
            DividerLine()
            // AI
            MonitoringInterventionRow(
                icon = Icons.Default.AutoAwesome,
                title = "AI陪伴",
                summary = if (sleepPlan.aiCompanionEnabled) "${sleepPlan.aiCompanionMode}" else "未开启",
                runState = InterventionRunState.RUNNING.takeIf { sleepPlan.aiCompanionEnabled } ?: InterventionRunState.DISABLED,
                onClick = { /* TODO: AI companion */ }
            )
        }
    }
}

@Composable
private fun MonitoringInterventionRow(
    icon: ImageVector,
    title: String,
    summary: String,
    runState: InterventionRunState,
    onClick: () -> Unit
) {
    val stateDotColor = when (runState) {
        InterventionRunState.RUNNING -> Color(0xFF2FCBBC)
        InterventionRunState.PAUSED -> Color(0xFFFFC857)
        InterventionRunState.SWITCHING, InterventionRunState.PREPARING -> Color(0xFFFF9F43)
        InterventionRunState.ERROR -> Color(0xFFFF6B6B)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (runState == InterventionRunState.DISABLED) Color.White.copy(alpha = 0.28f) else Color(0xFF6C8CFF).copy(alpha = 0.80f),
                modifier = Modifier.size(20.dp)
            )
            if (stateDotColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(stateDotColor, CircleShape)
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.78f)
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = if (runState == InterventionRunState.DISABLED) 0.30f else 0.50f)
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.28f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Real-time Sleep Stage Strip ──

@Composable
private fun SleepStageStrip(snapshot: SleepStageSnapshot) {
    val stageLabel = snapshot.currentStage.label
    val recentStages = snapshot.hypnogram.takeLast(8)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "当前睡眠状态",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.40f)
                )
                Text(
                    "$stageLabel 中",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6C8CFF)
                )
            }

            if (recentStages.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    recentStages.forEach { result ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    Color(0xFF6C8CFF).copy(alpha = 0.35f),
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

// ── Collapsible Signal Card ──

@Composable
private fun CollapsibleSignalCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.62f)
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.38f)
                    )
                }
                Text(
                    if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6C8CFF).copy(alpha = 0.70f)
                )
            }
            if (expanded) {
                content()
            }
        }
    }
}

// ── Advanced Signal Cards Section (EEG / HRV / fNIRS) ──

@Composable
private fun AdvancedSignalCardsSection(
    signalSnapshot: SleepSignalSnapshot,
    deviceStatus: SleepDeviceUiStatus,
    hrvChannel: Int,
    fnirsChannel: Int,
    onHrvChannelChange: (Int) -> Unit,
    onFnirsChannelChange: (Int) -> Unit
) {
    var eegExpanded by rememberSaveable { mutableStateOf(false) }
    var hrvExpanded by rememberSaveable { mutableStateOf(false) }
    var fnirsExpanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "高级信号",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.44f)
        )
        Text(
            "仅在需要查看原始信号时展开",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.30f)
        )

        CollapsibleSignalCard(
            title = "EEG",
            subtitle = "脑电信号",
            expanded = eegExpanded,
            onExpandedChange = { eegExpanded = it }
        ) {
            SingleChannelWaveSection(
                title = "EEG 波形",
                points = signalSnapshot.eeg.rawSeries.ifEmpty { signalSnapshot.eeg.series },
                lineColor = Color(0xFF6C8CFF),
                description = "100 Hz 原始波形"
            )
        }

        CollapsibleSignalCard(
            title = "HRV",
            subtitle = "心率变异信号",
            expanded = hrvExpanded,
            onExpandedChange = { hrvExpanded = it }
        ) {
            if (deviceStatus.opticalMode == SleepOpticalMode.HRV) {
                HrvSignalSection(
                    selectedChannel = hrvChannel,
                    onSelectChannel = { channel -> onHrvChannelChange(channel) },
                    snapshot = signalSnapshot.hrv
                )
            } else {
                Text(
                    "HRV 数据准备中，请先切换到 HRV 光学模式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.36f)
                )
            }
        }

        CollapsibleSignalCard(
            title = "fNIRS",
            subtitle = "近红外信号",
            expanded = fnirsExpanded,
            onExpandedChange = { fnirsExpanded = it }
        ) {
            if (deviceStatus.opticalMode == SleepOpticalMode.FNIRS) {
                FnirsSignalSection(
                    selectedChannel = fnirsChannel,
                    onSelectChannel = { channel -> onFnirsChannelChange(channel) },
                    snapshot = signalSnapshot.fnirs
                )
            } else {
                Text(
                    "fNIRS 数据准备中，请先切换到 fNIRS 光学模式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.36f)
                )
            }
        }
    }
}

// ── Collapsed Debug Section ──

@Composable
private fun CollapsedDebugSection(
    sessionId: String?,
    packetCount: Long,
    opticalMode: SleepOpticalMode,
    tdcsState: TdcsState,
    tdcsBoostText: String,
    onTdcsBoostTextChange: (String) -> Unit,
    tdcsCurrentText: String,
    onTdcsCurrentTextChange: (String) -> Unit,
    tdcsAmplitudeText: String,
    onTdcsAmplitudeTextChange: (String) -> Unit,
    tdcsInputMessage: String?,
    onOpticalModeChange: (SleepOpticalMode) -> Unit,
    onStartTdcs: (TdcsConfig) -> Unit,
    onStopTdcs: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "高级调试",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.38f)
                    )
                    Text(
                        "仅供调试与研究使用",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.26f)
                    )
                }
                Text(
                    if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.40f)
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text("会话: ${sessionId?.take(8) ?: "未生成"}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.30f))
                        Text("数据包: $packetCount", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.30f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text("光学模式: ${opticalMode.label}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.30f))
                        Text(
                            if (tdcsState.active) "电刺激: 运行中" else "电刺激: 已停止",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.30f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(SleepOpticalMode.HRV, SleepOpticalMode.FNIRS, SleepOpticalMode.OFF).forEach { mode ->
                            FilterChip(
                                selected = opticalMode == mode,
                                onClick = { onOpticalModeChange(mode) },
                                label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // tDCS controls (inside debug)
                    Text(
                        "研究者控制",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.38f)
                    )
                    Text(
                        "仅用于调试/研究，请确认参数后再操作。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF6B6B).copy(alpha = 0.45f)
                    )
                    Text(
                        if (tdcsState.active) {
                            "状态: 运行中 | boost=${tdcsState.boost}mA, current=${tdcsState.current}, amplitude=${tdcsState.amplitude}"
                        } else {
                            "状态: 已停止"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.32f)
                    )
                    if (!tdcsInputMessage.isNullOrBlank()) {
                        Text(tdcsInputMessage, style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF6B6B))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = tdcsBoostText,
                            onValueChange = onTdcsBoostTextChange,
                            modifier = Modifier.weight(1f),
                            label = { Text("Boost") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = tdcsCurrentText,
                            onValueChange = onTdcsCurrentTextChange,
                            modifier = Modifier.weight(1f),
                            label = { Text("Current") },
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = tdcsAmplitudeText,
                        onValueChange = onTdcsAmplitudeTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Amplitude") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = {
                                val boost = tdcsBoostText.toIntOrNull()
                                val current = tdcsCurrentText.toIntOrNull()
                                val amplitude = tdcsAmplitudeText.toIntOrNull()
                                if (boost != null && current != null && amplitude != null) {
                                    onStartTdcs(TdcsConfig(boost = boost, current = current, amplitude = amplitude))
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFF6B6B).copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "开启",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFF6B6B),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                        Surface(
                            onClick = onStopTdcs,
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.06f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "关闭",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.50f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: HeadbandDevice,
    selected: Boolean,
    connected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = when {
                    connected -> Color(0xFF6C8CFF).copy(alpha = 0.16f)
                    selected -> Color.White.copy(alpha = 0.10f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(device.name, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.94f))
                StatusBadge(
                    text = when {
                        connected -> "已连接"
                        selected -> "已选择"
                        else -> "点击连接"
                    },
                    color = when {
                        connected -> Color(0xFF6C8CFF)
                        selected -> Color.White.copy(alpha = 0.50f)
                        else -> Color.White.copy(alpha = 0.36f)
                    }
                )
            }
            Text("RSSI: ${device.rssi}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.50f))
            Text(device.address, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.38f))
        }
    }
}

@Composable
private fun SingleChannelWaveSection(
    title: String,
    points: List<Float>,
    lineColor: Color,
    description: String
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EegWaveChart(
                points = points,
                lineColor = lineColor,
                emptyLabel = "等待 EEG 数据"
            )
        }
    }
}

@Composable
private fun HrvSignalSection(
    selectedChannel: Int,
    onSelectChannel: (Int) -> Unit,
    snapshot: com.sleepagent.prototype.sleep.processing.SleepHrvSnapshot
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("光学通路 · HRV", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("显示心率、质量分数和滤波后的波形。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ChannelPicker(
                channelOptions = listOf(0, 1),
                selectedChannel = selectedChannel,
                onSelectChannel = onSelectChannel
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    MetricMiniCard(
                        label = "心率 bpm",
                        value = if (snapshot.status == "ready" || snapshot.status == "low_quality") {
                            snapshot.heartRateBpm
                        } else {
                            null
                        }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    MetricMiniCard(label = "质量分数", value = snapshot.signalQuality)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    MetricMiniCard(label = "IBI ms", value = snapshot.ibiMs)
                }
                Box(modifier = Modifier.weight(1f)) {
                    StatusMiniCard(snapshot.status)
                }
            }
            SignalTrendChart(
                points = snapshot.filteredSeries.ifEmpty { snapshot.series },
                lineColor = MaterialTheme.colorScheme.tertiary,
                emptyLabel = "等待 HRV 数据"
            )
        }
    }
}

@Composable
private fun FnirsSignalSection(
    selectedChannel: Int,
    onSelectChannel: (Int) -> Unit,
    snapshot: com.sleepagent.prototype.sleep.processing.SleepFnirsSnapshot
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("光学通路 · fNIRS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("显示 780/850 原始强度与 HbO/HbR。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ChannelPicker(
                channelOptions = listOf(0, 1),
                selectedChannel = selectedChannel,
                onSelectChannel = onSelectChannel
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    MetricMiniCard(label = "HbO", value = snapshot.hbo)
                }
                Box(modifier = Modifier.weight(1f)) {
                    MetricMiniCard(label = "HbR", value = snapshot.hbr)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    MetricMiniCard(label = "780 原始", value = snapshot.selectedIntensity780)
                }
                Box(modifier = Modifier.weight(1f)) {
                    MetricMiniCard(label = "850 原始", value = snapshot.selectedIntensity850)
                }
            }
            DualSeriesTrendChart(
                hboPoints = snapshot.hboSeries.ifEmpty { snapshot.series },
                hbrPoints = snapshot.hbrSeries,
                hboColor = MaterialTheme.colorScheme.secondary,
                hbrColor = MaterialTheme.colorScheme.tertiary,
                emptyLabel = "等待 fNIRS 数据"
            )
        }
    }
}

@Composable
private fun ChannelPicker(
    channelOptions: List<Int>,
    selectedChannel: Int,
    onSelectChannel: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        channelOptions.forEach { channel ->
            FilterChip(
                selected = selectedChannel == channel,
                onClick = { onSelectChannel(channel) },
                label = { Text(channelName(channel)) }
            )
        }
    }
}

@Composable
private fun MetricMiniCard(label: String, value: Float?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatFloat(value),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun StatusMiniCard(status: String) {
    val label = when (status) {
        "ready" -> "稳定"
        "low_quality" -> "低质"
        "warming_up" -> "预热中"
        "waiting_for_peaks" -> "等峰值"
        "waiting_for_samples" -> "等采样"
        "inactive" -> "未启用"
        "off" -> "关闭"
        else -> status
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EegWaveChart(
    points: List<Float>,
    lineColor: Color,
    emptyLabel: String
) {
    val displayPoints = remember(points) { downsampleForChart(points, maxPoints = 720) }
    val visibleRangeUv = remember(displayPoints) { computeEegVisibleRange(displayPoints) }
    val normalized = remember(displayPoints, visibleRangeUv) { normalizeSeries(displayPoints, visibleRangeUv) }
    val scaleValues = remember(visibleRangeUv) {
        listOf(
            visibleRangeUv,
            visibleRangeUv / 2f,
            0f,
            -visibleRangeUv / 2f,
            -visibleRangeUv
        )
    }
    val hasData = normalized.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            scaleValues.forEach { value ->
                Text(
                    text = formatScaleLabel(value),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(start = 44.dp)
        ) {
            drawRect(color = lineColor.copy(alpha = 0.08f))
            val gridColor = lineColor.copy(alpha = 0.18f)
            val centerColor = lineColor.copy(alpha = 0.35f)
            val width = size.width
            val height = size.height
            val verticalStep = width / 4f
            val horizontalStep = height / 4f
            repeat(5) { index ->
                val x = verticalStep * index
                drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
            }
            scaleValues.forEachIndexed { index, value ->
                val y = horizontalStep * index
                drawLine(
                    color = if (index == 2) centerColor else gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = if (index == 2) 1.6f else 1f
                )
            }
            if (hasData) {
                drawPath(
                    buildPath(normalized, size),
                    lineColor,
                    style = Stroke(width = 3.0f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        if (!hasData) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(start = 44.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(emptyLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SignalTrendChart(
    points: List<Float>,
    lineColor: Color,
    emptyLabel: String
) {
    val displayPoints = remember(points) { downsampleForChart(points, maxPoints = 720) }
    val normalized = remember(displayPoints) { normalizeTrendSeries(displayPoints) }
    val hasData = normalized.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            drawRect(color = lineColor.copy(alpha = 0.08f))
            val gridColor = lineColor.copy(alpha = 0.18f)
            val width = size.width
            val height = size.height
            val verticalStep = width / 4f
            val horizontalStep = height / 4f
            repeat(5) { index ->
                val x = verticalStep * index
                drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
            }
            repeat(5) { index ->
                val y = horizontalStep * index
                drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
            }
            if (hasData) {
                drawPath(
                    buildPath(normalized, size),
                    lineColor,
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        if (!hasData) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(emptyLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DualSeriesTrendChart(
    hboPoints: List<Float>,
    hbrPoints: List<Float>,
    hboColor: Color,
    hbrColor: Color,
    emptyLabel: String
) {
    val displayHboPoints = remember(hboPoints) { downsampleForChart(hboPoints, maxPoints = 360) }
    val displayHbrPoints = remember(hbrPoints) { downsampleForChart(hbrPoints, maxPoints = 360) }
    val normalized = remember(displayHboPoints, displayHbrPoints) {
        normalizeDualTrendSeries(displayHboPoints, displayHbrPoints)
    }
    val hasData = normalized.first.isNotEmpty() || normalized.second.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            drawRect(color = hboColor.copy(alpha = 0.08f))
            val gridColor = hboColor.copy(alpha = 0.18f)
            val width = size.width
            val height = size.height
            val verticalStep = width / 4f
            val horizontalStep = height / 4f
            repeat(5) { index ->
                val x = verticalStep * index
                drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
            }
            repeat(5) { index ->
                val y = horizontalStep * index
                drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
            }
            if (normalized.first.isNotEmpty()) {
                drawPath(
                    buildPath(normalized.first, size),
                    hboColor,
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            if (normalized.second.isNotEmpty()) {
                drawPath(
                    buildPath(normalized.second, size),
                    hbrColor,
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("HbO", style = MaterialTheme.typography.labelSmall, color = hboColor)
            Text("HbR", style = MaterialTheme.typography.labelSmall, color = hbrColor)
        }

        if (!hasData) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(emptyLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun buildPath(points: List<Float>, size: Size): Path {
    val path = Path()
    if (points.isEmpty() || size.width <= 0f || size.height <= 0f) return path

    val padX = 8f
    val padY = 10f
    val usableWidth = max(1f, size.width - padX * 2f)
    val usableHeight = max(1f, size.height - padY * 2f)
    val step = if (points.size <= 1) usableWidth else usableWidth / (points.size - 1)

    points.forEachIndexed { index, point ->
        val x = padX + index * step
        val y = padY + ((100f - point).coerceIn(0f, 100f) / 100f) * usableHeight
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    return path
}

private fun downsampleForChart(points: List<Float>, maxPoints: Int): List<Float> {
    if (points.size <= maxPoints || maxPoints < 3) return points

    val bucketCount = (maxPoints / 2).coerceAtLeast(1)
    val bucketSize = kotlin.math.ceil(points.size / bucketCount.toDouble()).toInt().coerceAtLeast(1)
    val reduced = ArrayList<Float>(maxPoints)
    var start = 0
    while (start < points.size) {
        val endExclusive = min(points.size, start + bucketSize)
        var minValue = Float.POSITIVE_INFINITY
        var maxValue = Float.NEGATIVE_INFINITY
        var minIndex = start
        var maxIndex = start

        for (index in start until endExclusive) {
            val value = points[index]
            if (!value.isFinite()) continue
            if (value < minValue) {
                minValue = value
                minIndex = index
            }
            if (value > maxValue) {
                maxValue = value
                maxIndex = index
            }
        }

        if (minValue.isFinite() && maxValue.isFinite()) {
            if (minIndex <= maxIndex) {
                reduced.add(minValue)
                if (maxIndex != minIndex && reduced.size < maxPoints) {
                    reduced.add(maxValue)
                }
            } else {
                reduced.add(maxValue)
                if (reduced.size < maxPoints) {
                    reduced.add(minValue)
                }
            }
        }
        start = endExclusive
    }

    return if (reduced.isNotEmpty()) reduced.take(maxPoints) else points.take(maxPoints)
}

private fun normalizeSeries(points: List<Float>, visibleRangeUv: Float): List<Float> {
    val finite = points.filter { it.isFinite() }
    if (finite.size < 3) return emptyList()

    val range = visibleRangeUv.coerceAtLeast(1f)
    return finite.map { value ->
        val clipped = value.coerceIn(-range, range)
        50f + (clipped / range) * 34f
    }
}

private fun normalizeTrendSeries(points: List<Float>): List<Float> {
    val finite = points.filter { it.isFinite() }
    if (finite.size < 3) return emptyList()

    val minValue = finite.minOrNull() ?: return emptyList()
    val maxValue = finite.maxOrNull() ?: return emptyList()
    val range = (maxValue - minValue)
    if (range < 1e-3f) {
        return List(finite.size) { 50f }
    }

    return finite.map { value ->
        16f + ((value - minValue) / range) * 68f
    }
}

private fun normalizeDualTrendSeries(primary: List<Float>, secondary: List<Float>): Pair<List<Float>, List<Float>> {
    val combined = (primary + secondary).filter { it.isFinite() }
    if (combined.size < 3) return emptyList<Float>() to emptyList()

    val minValue = combined.minOrNull() ?: return emptyList<Float>() to emptyList()
    val maxValue = combined.maxOrNull() ?: return emptyList<Float>() to emptyList()
    val range = maxValue - minValue
    if (range < 1e-3f) {
        val centerPrimary = primary.filter { it.isFinite() }.map { 50f }
        val centerSecondary = secondary.filter { it.isFinite() }.map { 50f }
        return centerPrimary to centerSecondary
    }

    return normalizeSharedTrendSeries(primary, minValue, range) to normalizeSharedTrendSeries(secondary, minValue, range)
}

private fun normalizeSharedTrendSeries(points: List<Float>, minValue: Float, range: Float): List<Float> {
    return points.filter { it.isFinite() }.map { value ->
        16f + ((value - minValue) / range) * 68f
    }
}

private fun computeEegVisibleRange(points: List<Float>): Float {
    val finite = points.filter { it.isFinite() }
    if (finite.isEmpty()) return EEG_VISIBLE_RANGE_DEFAULT_UV

    val sortedAbs = finite.map { abs(it) }.sorted()
    val index = ceil(sortedAbs.lastIndex * 0.95f).toInt().coerceIn(0, sortedAbs.lastIndex)
    val percentile = sortedAbs[index]
    val target = (percentile * 1.35f).coerceIn(EEG_VISIBLE_RANGE_MIN_UV, EEG_VISIBLE_RANGE_MAX_UV)
    return ceil(target / EEG_VISIBLE_RANGE_STEP_UV) * EEG_VISIBLE_RANGE_STEP_UV
}

private const val EEG_VISIBLE_RANGE_DEFAULT_UV = 250f
private const val EEG_VISIBLE_RANGE_MIN_UV = 100f
private const val EEG_VISIBLE_RANGE_MAX_UV = 1200f
private const val EEG_VISIBLE_RANGE_STEP_UV = 50f

private fun formatScaleLabel(value: Float): String {
    val rounded = value.toInt()
    return if (rounded == 0) "0" else if (rounded > 0) "+$rounded" else rounded.toString()
}

private fun channelName(channel: Int): String {
    return "ch${channel + 1}"
}

private fun formatFloat(value: Float?): String {
    return when {
        value == null -> "--"
        value.isNaN() || value.isInfinite() -> "--"
        else -> "%.1f".format(value)
    }
}

private fun MutableList<Float>.appendCapped(value: Float, maxSize: Int) {
    add(value)
    while (size > maxSize) {
        removeAt(0)
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

private suspend fun runBleAction(
    context: android.content.Context,
    permissionsToRequest: List<String>,
    onPermissionRequired: () -> Unit,
    onError: (String) -> Unit,
    action: suspend () -> Unit
) {
    if (!hasAllPermissions(context, permissionsToRequest)) {
        onPermissionRequired()
        return
    }

    runCatching { action() }.onFailure { throwable ->
        when (throwable) {
            is MissingDevicePermissionsException -> onPermissionRequired()
            else -> onError(throwable.message ?: "未知错误")
        }
    }
}

private fun hasAllPermissions(context: android.content.Context, permissions: List<String>): Boolean {
    return permissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

private fun runtimePermissionsForBle(): List<String> {
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_CONNECT
    } else {
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }
    return permissions
}

@Composable
private fun ScreenContainer(
    @Suppress("UNUSED_PARAMETER") title: String,
    @Suppress("UNUSED_PARAMETER") subtitle: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF10172A),
                        Color(0xFF0B1020),
                        Color(0xFF070B16)
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 140.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                content()
            }
        }
    }
}
