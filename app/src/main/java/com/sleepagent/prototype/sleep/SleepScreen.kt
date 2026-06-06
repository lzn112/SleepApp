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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sleepagent.prototype.ScreenContainer
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
        title = "睡眠",
        subtitle = "先连设备，再开始睡眠。"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("运行模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(selected = !useMockManager, onClick = { onUseMockManagerChange(false) }, label = { Text("Real BLE") })
                        FilterChip(selected = useMockManager, onClick = { onUseMockManagerChange(true) }, label = { Text("Mock") })
                    }
                    Text(
                        text = if (useMockManager) "当前使用 Mock 设备。" else "当前使用真实 BLE 设备。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            DeviceActionCard(
                connectionState = connectionState,
                isStartingSleep = isStartingSleep,
                isRecording = isRecording,
                hasPermissions = hasPermissions,
                selectedDeviceName = selectedDevice?.name,
                connectedDeviceName = deviceStatus.connectedDevice?.name,
                statusMessage = statusMessage,
                onScan = onScan,
                onStartSleep = onStartSleep,
                onStopSleep = onStopSleep,
                onDisconnect = onDisconnect,
                onRequestPermissions = onRequestPermissions
            )

            if (scannedDevices.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("设备列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            }
        }
    }
}

@Composable
private fun SleepMonitorScreen(
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
        title = "睡眠监测",
        subtitle = "实时查看 EEG 和光学信号。"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!uiMessage.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = uiMessage,
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("当前会话", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = sessionId ?: "未生成会话",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(
                            text = if (connectionState == DeviceConnectionState.CONNECTED) "已连接" else "连接异常",
                            color = if (connectionState == DeviceConnectionState.CONNECTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    Text(text = "设备: ${deviceStatus.connectedDevice?.name ?: "未知设备"}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "状态: $statusMessage", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "光学模式: ${deviceStatus.opticalMode.label}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (deviceStatus.tdcs.active) {
                            "电刺激: 运行中 (${deviceStatus.tdcs.channel}, ${deviceStatus.tdcs.current}mA, amp=${deviceStatus.tdcs.amplitude})"
                        } else {
                            "电刺激: 已停止"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(text = "采样包数: $packetCount", style = MaterialTheme.typography.bodyMedium)
                }
            }

            SleepStageSimpleCard(snapshot = sleepStageSnapshot)

            OpticalModeCard(
                selectedMode = deviceStatus.opticalMode,
                onSelectMode = onOpticalModeChange
            )

            SingleChannelWaveSection(
                title = "EEG 实时波形（CH6）",
                points = signalSnapshot.eeg.series,
                lineColor = MaterialTheme.colorScheme.primary,
                description = "CH6 / 100 Hz / 5-35 Hz 显示通路。"
            )

            when (deviceStatus.opticalMode) {
                SleepOpticalMode.HRV -> HrvSignalSection(
                    selectedChannel = hrvChannel,
                    onSelectChannel = { channel ->
                        hrvChannel = channel
                        onHrvChannelChange(channel)
                    },
                    snapshot = signalSnapshot.hrv
                )
                SleepOpticalMode.FNIRS -> FnirsSignalSection(
                    selectedChannel = fnirsChannel,
                    onSelectChannel = { channel ->
                        fnirsChannel = channel
                        onFnirsChannelChange(channel)
                    },
                    snapshot = signalSnapshot.fnirs
                )
                SleepOpticalMode.OFF -> Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Text(
                        text = "光学通路已关闭",
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("返回设置") }
                Button(onClick = onEndSleep, modifier = Modifier.weight(1f)) { Text("结束睡眠") }
            }

            TdcsControlCard(
                tdcsState = deviceStatus.tdcs,
                boostText = tdcsBoostText,
                onBoostTextChange = { tdcsBoostText = it },
                currentText = tdcsCurrentText,
                onCurrentTextChange = { tdcsCurrentText = it },
                amplitudeText = tdcsAmplitudeText,
                onAmplitudeTextChange = { tdcsAmplitudeText = it },
                inputMessage = tdcsInputMessage,
                onStart = {
                    val boost = tdcsBoostText.toIntOrNull()
                    val current = tdcsCurrentText.toIntOrNull()
                    val amplitude = tdcsAmplitudeText.toIntOrNull()
                    if (boost == null || current == null || amplitude == null) {
                        tdcsInputMessage = "电刺激参数必须是数字。"
                    } else {
                        tdcsInputMessage = null
                        onStartTdcs(
                            TdcsConfig(
                                boost = boost,
                                current = current,
                                amplitude = amplitude
                            )
                        )
                    }
                },
                onStop = {
                    tdcsInputMessage = null
                    onStopTdcs(TDCS_DEFAULT_CHANNEL)
                }
            )
        }
    }
}

@Composable
private fun DeviceActionCard(
    connectionState: DeviceConnectionState,
    isStartingSleep: Boolean,
    isRecording: Boolean,
    hasPermissions: Boolean,
    selectedDeviceName: String?,
    connectedDeviceName: String?,
    statusMessage: String?,
    onScan: () -> Unit,
    onStartSleep: () -> Unit,
    onStopSleep: () -> Unit,
    onDisconnect: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "设备: ${connectedDeviceName ?: selectedDeviceName ?: "-"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasPermissions) {
                    Button(onClick = onScan) { Text("扫描设备") }
                } else {
                    Button(onClick = onRequestPermissions) { Text("授予权限") }
                }
                Button(
                    onClick = onStartSleep,
                    enabled = connectionState == DeviceConnectionState.CONNECTED && !isStartingSleep
                ) {
                    Text(
                        when {
                            isStartingSleep -> "启动中..."
                            isRecording -> "监测中"
                            else -> "开始睡眠"
                        }
                    )
                }
            }
            Text(
                text = "状态: ${statusMessage ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onStopSleep, enabled = isRecording) { Text("结束睡眠") }
                OutlinedButton(onClick = onDisconnect) { Text("断开设备") }
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
                    connected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    selected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    else -> MaterialTheme.colorScheme.surface
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
                Text(device.name, fontWeight = FontWeight.SemiBold)
                StatusBadge(
                    text = when {
                        connected -> "已连接"
                        selected -> "已选择"
                        else -> "点击连接"
                    },
                    color = when {
                        connected -> MaterialTheme.colorScheme.primary
                        selected -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text("RSSI: ${device.rssi}", style = MaterialTheme.typography.bodySmall)
            Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun SleepStageSimpleCard(snapshot: SleepStageSnapshot) {
    val secondsBuffered = snapshot.bufferedSamples / snapshot.sampleRateHz.coerceAtLeast(1)
    val confidenceText = snapshot.latestResult?.let { "置信度 ${(it.confidence * 100).toInt()}%" } ?: "置信度 --"
    val recentStages = snapshot.hypnogram.takeLast(8)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("实时睡眠分期", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = snapshot.currentStage.label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = confidenceText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = snapshot.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "窗口进度 $secondsBuffered / 30 秒",
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { snapshot.epochProgress },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recentStages.isEmpty()) {
                    Text("暂无", style = MaterialTheme.typography.bodyMedium)
                } else {
                    recentStages.forEach { result ->
                        Text(
                            text = result.stage.shortLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpticalModeCard(
    selectedMode: SleepOpticalMode,
    onSelectMode: (SleepOpticalMode) -> Unit
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
                Text("光学模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(SleepOpticalMode.HRV, SleepOpticalMode.FNIRS, SleepOpticalMode.OFF).forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onSelectMode(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TdcsControlCard(
    tdcsState: TdcsState,
    boostText: String,
    onBoostTextChange: (String) -> Unit,
    currentText: String,
    onCurrentTextChange: (String) -> Unit,
    amplitudeText: String,
    onAmplitudeTextChange: (String) -> Unit,
    inputMessage: String?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("电刺激控制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (tdcsState.active) {
                    "状态: 运行中 | boost=${tdcsState.boost}, current=${tdcsState.current}, amplitude=${tdcsState.amplitude}, channel=${tdcsState.channel}"
                } else {
                    "状态: 已停止"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!inputMessage.isNullOrBlank()) {
                Text(
                    text = inputMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = boostText,
                    onValueChange = onBoostTextChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Boost") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = currentText,
                    onValueChange = onCurrentTextChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Current") },
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = amplitudeText,
                onValueChange = onAmplitudeTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amplitude") },
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Text("开启电刺激")
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                    Text("关闭电刺激")
                }
            }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatFloat(value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
            .background(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp))
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
            drawRect(color = Color(0x1422D3EE))
            val gridColor = Color(0x1A94A3B8)
            val centerColor = Color(0x3322D3EE)
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
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
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
            .background(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            drawRect(color = Color(0x1422D3EE))
            val gridColor = Color(0x1A94A3B8)
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
            .background(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            drawRect(color = Color(0x1422D3EE))
            val gridColor = Color(0x1A94A3B8)
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
