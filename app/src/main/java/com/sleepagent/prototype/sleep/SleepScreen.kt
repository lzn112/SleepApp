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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
    var sleepPlan by rememberSaveable { mutableStateOf(SleepPlanUiState()) }

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
                onPlanChange = { sleepPlan = it },
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
                                uiMessage = "已进入睡眠监测页。"
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

@Composable
private fun SleepSetupScreen(
    sleepPlan: SleepPlanUiState,
    onPlanChange: (SleepPlanUiState) -> Unit,
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

            // 3. Smart Wake
            SmartWakeCard(
                plan = sleepPlan,
                onPlanChange = onPlanChange
            )

            // 4. Sound Aid
            SoundAidCard(
                plan = sleepPlan,
                onPlanChange = onPlanChange
            )

            // 5. AI Companion
            AiCompanionCard(
                plan = sleepPlan,
                onPlanChange = onPlanChange
            )

            // 6. Sleep Guard
            SleepGuardSettingsCard(
                plan = sleepPlan,
                onPlanChange = onPlanChange
            )

            // 7. Device Readiness
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

            // 8. Device List
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

            // 9. Advanced Debug
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
}

@Composable
private fun SleepMonitorScreen(
    sleepPlan: SleepPlanUiState,
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

    val currentPacket = latestPacket
    val signalState = when {
        currentPacket == null -> "等待 EEG 数据"
        currentPacket.state == null -> "采集中"
        else -> "state=${currentPacket.state}"
    }
    val opticalState = signalSnapshot.opticalState
    val statusMessage = uiMessage ?: deviceStatus.message ?: signalState

    ScreenContainer(
        title = "正在守护你的睡眠",
        subtitle = "设备连接稳定，明早会为你生成睡眠复盘。"
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

            // 2. Tonight's Plan Summary
            TonightPlanSummaryCard(plan = sleepPlan)

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
                    "返回准备页",
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
            if (plan.soundAidEnabled) activeItems.add("${plan.soundAidName} ${plan.soundDurationMin}分钟")
            if (plan.aiCompanionEnabled) activeItems.add("${plan.aiCompanionMode} ${plan.aiCompanionDurationMin}分钟")
            if (activeItems.isNotEmpty()) {
                Text(
                    "已开启：${activeItems.joinToString(" · ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.44f)
                )
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
                        val adjusted = adjustTime(plan.bedtime, -30)
                        onPlanChange(plan.copy(bedtime = adjusted))
                    },
                    onPlus = {
                        val adjusted = adjustTime(plan.bedtime, 30)
                        onPlanChange(plan.copy(bedtime = adjusted))
                    }
                )
                TimeAdjustColumn(
                    label = "目标起床",
                    time = plan.wakeTime,
                    onMinus = {
                        val adjusted = adjustTime(plan.wakeTime, -30)
                        onPlanChange(plan.copy(wakeTime = adjusted))
                    },
                    onPlus = {
                        val adjusted = adjustTime(plan.wakeTime, 30)
                        onPlanChange(plan.copy(wakeTime = adjusted))
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

// ── Setup: Smart Wake ──

@Composable
private fun SmartWakeCard(
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "智能唤醒",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.72f)
                )
                ToggleSwitch(
                    checked = plan.smartWakeEnabled,
                    onCheckedChange = { onPlanChange(plan.copy(smartWakeEnabled = it)) }
                )
            }
            if (plan.smartWakeEnabled) {
                Text(
                    "在 ${plan.smartWakeStart} - ${plan.smartWakeEnd} 之间，尽量选择更轻松的时机唤醒。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.48f)
                )
                Text(
                    "唤醒窗口：${plan.smartWakeStart} - ${plan.smartWakeEnd}   |   最晚唤醒：${plan.smartWakeEnd}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.30f)
                )
            } else {
                Text(
                    "智能唤醒关闭后，将在设定时间准时唤醒。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.36f)
                )
            }
        }
    }
}

// ── Setup: Sound Aid ──

@Composable
private fun SoundAidCard(
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "声音助眠",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.72f)
                )
                ToggleSwitch(
                    checked = plan.soundAidEnabled,
                    onCheckedChange = { onPlanChange(plan.copy(soundAidEnabled = it)) }
                )
            }
            if (plan.soundAidEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("雨声", "白噪音", "海浪", "森林").forEach { name ->
                        val selected = plan.soundAidName == name
                        Surface(
                            onClick = { onPlanChange(plan.copy(soundAidName = name)) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) Color(0xFF6C8CFF).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                            border = if (selected) BorderStroke(1.dp, Color(0xFF6C8CFF).copy(alpha = 0.30f)) else null
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Color(0xFF6C8CFF) else Color.White.copy(alpha = 0.44f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        val selected = plan.soundDurationMin == mins
                        Surface(
                            onClick = { onPlanChange(plan.copy(soundDurationMin = mins)) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) Color(0xFF6C8CFF).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f)
                        ) {
                            Text(
                                "${mins}分钟",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Color(0xFF6C8CFF).copy(alpha = 0.80f) else Color.White.copy(alpha = 0.40f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Text(
                    "${plan.soundAidName} · ${plan.soundDurationMin} 分钟后渐弱",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.44f)
                )
            } else {
                Text(
                    "辅助放松入睡，不会整夜播放。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.36f)
                )
            }
        }
    }
}

// ── Setup: AI Companion ──

@Composable
private fun AiCompanionCard(
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AI 陪伴",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.72f)
                )
                ToggleSwitch(
                    checked = plan.aiCompanionEnabled,
                    onCheckedChange = { onPlanChange(plan.copy(aiCompanionEnabled = it)) }
                )
            }
            if (plan.aiCompanionEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("呼吸放松", "身体扫描", "睡前故事").forEach { mode ->
                        val selected = plan.aiCompanionMode == mode
                        Surface(
                            onClick = { onPlanChange(plan.copy(aiCompanionMode = mode)) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) Color(0xFFA29BFE).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                            border = if (selected) BorderStroke(1.dp, Color(0xFFA29BFE).copy(alpha = 0.30f)) else null
                        ) {
                            Text(
                                mode,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Color(0xFFA29BFE) else Color.White.copy(alpha = 0.44f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Text(
                    "${plan.aiCompanionMode} · ${plan.aiCompanionDurationMin} 分钟",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFA29BFE)
                )
                Text(
                    "睡前我会用低刺激的方式陪你放松，结束后自动安静。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.44f)
                )
            } else {
                Text(
                    "睡前短流程陪伴，结束后自动安静，不是整晚聊天。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.36f)
                )
            }
        }
    }
}

// ── Setup: Sleep Guard Settings ──

@Composable
private fun SleepGuardSettingsCard(
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "睡眠守护",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.72f)
                )
                ToggleSwitch(
                    checked = plan.sleepGuardEnabled,
                    onCheckedChange = { onPlanChange(plan.copy(sleepGuardEnabled = it)) }
                )
            }
            if (plan.sleepGuardEnabled) {
                Text(
                    "开启后将保持低干扰状态：低亮度、声音渐弱、设备断连提醒、明早生成报告。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.48f)
                )
            } else {
                Text(
                    "关闭守护模式后，睡眠监测照常运行，但不会主动提示异常。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.36f)
                )
            }
        }
    }
}

// ── Toggle Switch ──

@Composable
private fun ToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
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
            Text(
                "高级模式",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.38f)
            )
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "正在守护你的睡眠",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.94f)
                )
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
private fun TonightPlanSummaryCard(plan: SleepPlanUiState) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "今晚设置",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.55f)
            )
            val items = mutableListOf<String>()
            items.add("预计起床：${plan.wakeTime}")
            if (plan.smartWakeEnabled) items.add("智能唤醒：${plan.smartWakeStart} - ${plan.smartWakeEnd}")
            if (plan.soundAidEnabled) items.add("助眠声音：${plan.soundAidName} · ${plan.soundDurationMin} 分钟后渐弱")
            if (plan.aiCompanionEnabled) items.add("AI 陪伴：${plan.aiCompanionMode} ${plan.aiCompanionDurationMin} 分钟")
            items.forEach { item ->
                Text(
                    item,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.40f)
                )
            }
        }
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
