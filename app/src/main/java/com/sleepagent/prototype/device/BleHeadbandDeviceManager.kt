package com.sleepagent.prototype.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * 真实 BLE 头环设备管理器。
 *
 * 当前设计目标：
 * 1. 扫描 MindBridge-v3.11，或者扫描到包含目标 Service UUID 的设备；
 * 2. 用户点击连接后，自动完成 GATT 连接、Service 发现、Notify/Indicate 开启；
 * 3. Notify 开启后立刻自动发送 'b' 启动头环数据流；
 * 4. 只有收到第一帧有效 RAW51 数据后，才把设备状态置为 CONNECTED；
 * 5. startStreaming() 不再负责发送 'b'，只作为“开始睡眠流程”的轻量检查；
 * 6. stopStreaming() 不关闭 BLE 数据流，只表示睡眠流程结束。真正断开设备请调用 disconnect()。
 *
 * 和 Python bci-serve.py 对齐的 BLE 协议：
 * - Service UUID: 0000ae30-0000-1000-8000-00805f9b34fb
 * - Write UUID:   0000ae01-0000-1000-8000-00805f9b34fb
 * - Notify UUID:  0000ae02-0000-1000-8000-00805f9b34fb
 * - Start command: 'b'
 * - Retry command: 's'
 * - Stop command:  "stop"
 */
class BleHeadbandDeviceManager(
    context: Context,
    private val parser: HeadbandPacketParser = HeadbandPacketParser(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val targetDeviceName: String = TARGET_DEVICE_NAME
) : HeadbandDeviceManager {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    /**
     * 串行化外部操作，避免 scan/connect/disconnect/start/stop 同时执行导致 GATT 状态混乱。
     */
    private val operationMutex = Mutex()

    private val _connectionState = MutableStateFlow(DeviceConnectionState.DISCONNECTED)
    private val _deviceStatus = MutableStateFlow(HeadbandStatus(message = "Real BLE headband idle"))
    private val _rawDataFlow = MutableSharedFlow<HeadbandRawPacket>(extraBufferCapacity = 512)

    /**
     * 最近一次解析到有效 RAW51 帧的本机时间。connect() 会用它判断 b 命令是否真的启动了数据流。
     */
    private val lastValidPacketElapsedRealtime = MutableStateFlow(0L)

    private val scanDiagnosticsLimit = 20
    private val connectionDiagnosticsLimit = 40
    private val rawPayloadDiagnosticsLimit = 12

    /**
     * BLE notify 可能被系统拆包，所以这里缓存字节流，再按 RAW51 帧头/帧尾切帧。
     */
    private val incomingPayloadBuffer = ArrayList<Byte>(FRAME_LENGTH * 8)
    private val incomingPayloadBufferLock = Any()

    private var currentGatt: BluetoothGatt? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var connectedDevice: HeadbandDevice? = null
    private var opticalMode: SleepOpticalMode = SleepOpticalMode.OFF
    private var tdcsState: TdcsState = TdcsState()

    /**
     * GATT 连接阶段等待项：等 Service 找到 + Notify/Indicate CCCD 写成功。
     */
    private var pendingConnection = CompletableDeferred<Unit>()

    /**
     * 只对 WRITE_TYPE_DEFAULT 可靠等待；WRITE_NO_RESPONSE 通常不会有 onCharacteristicWrite 回调。
     */
    private var pendingCharacteristicWrite = CompletableDeferred<Boolean>()
    private var pendingDescriptorWrite = CompletableDeferred<Boolean>()
    private var descriptorWritePurpose = DescriptorWritePurpose.NONE

    /**
     * 设备层是否已启动并持续接收数据。
     *
     * 注意：这个变量不是“是否正在记录睡眠”。
     * 睡眠 session 的开始/结束应由 Repository/UI 控制。
     */
    private var isDeviceStreaming = false

    override val connectionState = _connectionState.asStateFlow()
    override val deviceStatus = _deviceStatus.asStateFlow()
    override val rawDataFlow: Flow<HeadbandRawPacket> = _rawDataFlow.asSharedFlow()

    /**
     * 扫描头环。
     *
     * 匹配规则：
     * - 优先匹配设备名 MindBridge-v3.11；
     * - 如果广播里包含目标 Service UUID，也视为候选设备。
     */
    override suspend fun scan(): List<HeadbandDevice> = operationMutex.withLock {
        withContext(ioDispatcher) {
            ensureScanPermissions()
            val adapter = requireBluetoothAdapter()
            val scanner = adapter.bluetoothLeScanner ?: error("BLE scanner unavailable")
            val previousState = _connectionState.value

            _connectionState.value = DeviceConnectionState.SCANNING
            _deviceStatus.update {
                it.copy(
                    message = "Scanning for $targetDeviceName",
                    rawScanEventCount = 0,
                    scanDiagnostics = listOf("scan:start targetName=$targetDeviceName service=$SERVICE_UUID"),
                    lastScanError = null
                )
            }

            val scanResults = LinkedHashMap<String, HeadbandDevice>()
            val callback = object : ScanCallback() {
                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { result -> handleScanResult(result, scanResults) }
                }

                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    handleScanResult(result, scanResults)
                }

                override fun onScanFailed(errorCode: Int) {
                    val readable = scanErrorToString(errorCode)
                    Log.e(TAG, "onScanFailed errorCode=$errorCode readable=$readable")
                    _deviceStatus.update {
                        it.copy(
                            message = "Scan failed: $readable",
                            lastScanError = "onScanFailed($errorCode): $readable",
                            scanDiagnostics = (it.scanDiagnostics + "scan:failed code=$errorCode readable=$readable")
                                .takeLast(scanDiagnosticsLimit)
                        )
                    }
                }

                @SuppressLint("MissingPermission")
                private fun handleScanResult(
                    result: ScanResult,
                    resultsMap: MutableMap<String, HeadbandDevice>
                ) {
                    val device = result.device ?: return
                    val scanRecord = result.scanRecord
                    val advertisedName = device.name ?: scanRecord?.deviceName
                    val serviceUuids = scanRecord?.serviceUuids?.map { it.uuid }.orEmpty()
                    val matchesName = advertisedName == targetDeviceName
                    val matchesService = SERVICE_UUID in serviceUuids
                    val isTargetCandidate = matchesName || matchesService
                    val servicePreview = if (serviceUuids.isEmpty()) "-" else serviceUuids.joinToString(limit = 3)

                    Log.d(
                        TAG,
                        "scanResult addr=${device.address} name=${advertisedName ?: "-"} rssi=${result.rssi} " +
                            "matchesName=$matchesName matchesService=$matchesService services=$servicePreview"
                    )

                    _deviceStatus.update { status ->
                        val nextCount = status.rawScanEventCount + 1
                        val entry = buildString {
                            append("scan[$nextCount] addr=${device.address} ")
                            append("name=${advertisedName ?: "-"} ")
                            append("rssi=${result.rssi} ")
                            append("matchName=$matchesName ")
                            append("matchService=$matchesService")
                            if (servicePreview != "-") append(" services=$servicePreview")
                        }
                        status.copy(
                            rawScanEventCount = nextCount,
                            scanDiagnostics = (status.scanDiagnostics + entry).takeLast(scanDiagnosticsLimit),
                            message = if (isTargetCandidate) {
                                "Scan candidate: ${advertisedName ?: device.address}"
                            } else {
                                status.message
                            }
                        )
                    }

                    if (!isTargetCandidate) return

                    val headband = HeadbandDevice(
                        deviceId = device.address,
                        name = advertisedName ?: UNKNOWN_DEVICE_NAME,
                        address = device.address,
                        rssi = result.rssi
                    )
                    resultsMap[device.address] = headband
                }
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            try {
                scanner.startScan(emptyList(), settings, callback)
                delay(SCAN_DURATION_MS)
            } finally {
                runCatching { scanner.stopScan(callback) }
                _connectionState.value = if (previousState == DeviceConnectionState.CONNECTED && currentGatt != null) {
                    DeviceConnectionState.CONNECTED
                } else {
                    DeviceConnectionState.DISCONNECTED
                }
                _deviceStatus.update {
                    it.copy(
                        message = "Scan finished: ${scanResults.size} result(s)",
                        scanDiagnostics = (it.scanDiagnostics + "scan:finished results=${scanResults.size}")
                            .takeLast(scanDiagnosticsLimit)
                    )
                }
            }

            val allResults = scanResults.values.sortedByDescending { it.rssi }
            val preferred = allResults.filter { it.name == targetDeviceName }
            if (preferred.isNotEmpty()) preferred else allResults
        }
    }

    /**
     * 连接设备，并在连接阶段自动启动数据流。
     *
     * 关键变化：
     * - 连接成功 + Notify 开启后，自动发送 'b'；
     * - 只有收到第一帧有效 RAW51 后，才设置 CONNECTED，并向 UI 暴露 connectedDevice；
     * - 如果没收到数据，connect() 直接失败，避免“假连接”。
     */
    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceId: String): Unit = operationMutex.withLock {
        withContext(ioDispatcher) {
            ensureConnectPermissions()
            val adapter = requireBluetoothAdapter()

            // 连接新设备前先清理旧 GATT，Android BLE 重复 GATT 很容易导致异常状态。
            disconnectInternal(resetStatus = false)

            val remoteDevice = adapter.getRemoteDevice(deviceId)
            pendingConnection = CompletableDeferred()
            descriptorWritePurpose = DescriptorWritePurpose.NONE
            clearIncomingPayloadBuffer()
            lastValidPacketElapsedRealtime.value = 0L
            isDeviceStreaming = false

            _connectionState.value = DeviceConnectionState.CONNECTING
            _deviceStatus.update {
                it.copy(
                    connectedDevice = null,
                    isStreaming = false,
                    packetCount = 0L,
                    latestPacket = null,
                    lastPacketTimestamp = null,
                    message = "Connecting to $deviceId",
                    connectionDiagnostics = listOf("connect:start deviceId=$deviceId"),
                    lastConnectionError = null
                )
            }

            currentGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                remoteDevice.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } else {
                @Suppress("DEPRECATION")
                remoteDevice.connectGatt(appContext, false, gattCallback)
            }

            val connectError = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                runCatching { pendingConnection.await() }.exceptionOrNull()
            } ?: if (pendingConnection.isCompleted) null else IllegalStateException("connect timeout")

            if (connectError != null) {
                val readable = connectionErrorToString(connectError)
                _deviceStatus.update {
                    it.copy(
                        message = "连接失败：$readable",
                        lastConnectionError = readable,
                        connectionDiagnostics = (it.connectionDiagnostics + "connect:failed $readable")
                            .takeLast(connectionDiagnosticsLimit)
                    )
                }
                disconnectInternal(resetStatus = false)
                error("连接失败：$readable")
            }

            _deviceStatus.update {
                it.copy(
                    message = "Notify ready, auto starting stream",
                    connectionDiagnostics = (it.connectionDiagnostics + "connect:notifyReady autoStart=b")
                        .takeLast(connectionDiagnosticsLimit)
                )
            }

            try {
                startDeviceStreamAndWaitFirstPacket()
            } catch (error: Throwable) {
                val readable = connectionErrorToString(error)
                _deviceStatus.update {
                    it.copy(
                        message = "连接失败：$readable",
                        lastConnectionError = readable,
                        connectionDiagnostics = (it.connectionDiagnostics + "connect:streamFailed $readable")
                            .takeLast(connectionDiagnosticsLimit)
                    )
                }
                disconnectInternal(resetStatus = false)
                throw error
            }

            // 收到有效数据后，才把设备暴露给 UI。
            connectedDevice = HeadbandDevice(
                deviceId = remoteDevice.address,
                name = remoteDevice.name ?: targetDeviceName,
                address = remoteDevice.address,
                rssi = 0
            )
            _connectionState.value = DeviceConnectionState.CONNECTED
            _deviceStatus.update {
                it.copy(
                    connectedDevice = connectedDevice,
                    isStreaming = true,
                    message = "设备已就绪：${connectedDevice?.name}",
                    tdcs = tdcsState,
                    connectionDiagnostics = (it.connectionDiagnostics + "connect:ready deviceId=${remoteDevice.address}")
                        .takeLast(connectionDiagnosticsLimit)
                )
            }
        }
    }

    override suspend fun disconnect(): Unit = operationMutex.withLock {
        withContext(ioDispatcher) {
            disconnectInternal(resetStatus = true)
        }
    }

    /**
     * 开始睡眠流程。
     *
     * 现在 BLE 数据流已在 connect() 阶段自动启动，所以这里不再发送 'b'。
     * UI 可以继续调用这个函数，但它只做“设备是否已收到有效数据”的检查。
     */
    override suspend fun startStreaming(): Unit = operationMutex.withLock {
        withContext(ioDispatcher) {
            ensureConnected()
            val hasReceivedData = lastValidPacketElapsedRealtime.value > 0L
            check(hasReceivedData) { "设备尚未收到有效 RAW51 数据，不能开始睡眠流程" }

            _deviceStatus.update {
                it.copy(
                    isStreaming = true,
                    message = "设备数据流已就绪，可以进入睡眠流程",
                    connectionDiagnostics = (it.connectionDiagnostics + "startSleep:noOp streamAlreadyReady")
                        .takeLast(connectionDiagnosticsLimit)
                )
            }
        }
    }

    /**
     * 结束睡眠流程。
     *
     * 默认不发送 stop，也不停止 BLE 数据流。这样用户结束一次睡眠记录后，设备仍可保持连接和预览数据。
     * 真正断开和释放 BLE，请调用 disconnect()。
     */
    override suspend fun stopStreaming(): Unit = operationMutex.withLock {
        withContext(ioDispatcher) {
            _deviceStatus.update {
                it.copy(
                    isStreaming = isDeviceStreaming,
                    message = "睡眠流程已结束，设备连接保持",
                    connectionDiagnostics = (it.connectionDiagnostics + "stopSleep:noOp keepBleStreaming")
                        .takeLast(connectionDiagnosticsLimit)
                )
            }
        }
    }

    override suspend fun setOpticalMode(mode: SleepOpticalMode): Unit = operationMutex.withLock {
        withContext(ioDispatcher) {
            ensureConnected()
            val payload = """{"type":"${mode.wireValue}"}""".encodeToByteArray() + byteArrayOf('\n'.code.toByte())
            writeCommand(payload)
            opticalMode = mode
            _deviceStatus.update {
                it.copy(
                    opticalMode = mode.wireValue,
                    message = "Optical mode: ${mode.label}",
                    connectionDiagnostics = (it.connectionDiagnostics + "control:opticalMode=${mode.wireValue}")
                        .takeLast(connectionDiagnosticsLimit)
                )
            }
        }
    }

    override suspend fun startTdcs(config: TdcsConfig): Unit = operationMutex.withLock {
        withContext(ioDispatcher) {
            ensureConnected()
            val safe = config.sanitized()
            val (payload1, payload2) = buildTdcsPayloadPair(safe, active = true)
            writeCommand(payload1.toBleTextPayload())
            delay(TDCS_COMMAND_GAP_MS)
            writeCommand(payload2.toBleTextPayload())

            tdcsState = TdcsState(
                active = true,
                boost = safe.boost,
                current = safe.current,
                amplitude = safe.amplitude,
                channel = safe.channel,
                lastCommandAt = System.currentTimeMillis()
            )
            _deviceStatus.update {
                it.copy(
                    message = "tDCS started: boost=${safe.boost}, current=${safe.current}, amplitude=${safe.amplitude}, channel=${safe.channel}",
                    tdcs = tdcsState,
                    connectionDiagnostics = (it.connectionDiagnostics + "control:tdcs=start boost=${safe.boost} current=${safe.current} amplitude=${safe.amplitude} channel=${safe.channel}")
                        .takeLast(connectionDiagnosticsLimit)
                )
            }
        }
    }

    override suspend fun stopTdcs(channel: String): Unit = operationMutex.withLock {
        withContext(ioDispatcher) {
            ensureConnected()
            val safeChannel = channel.trim().ifBlank { tdcsState.channel.ifBlank { TDCS_DEFAULT_CHANNEL } }
            val safe = TdcsConfig(
                boost = tdcsState.boost,
                current = 0,
                amplitude = 0,
                channel = safeChannel
            )
            val (payload1, payload2) = buildTdcsPayloadPair(safe, active = false)
            writeCommand(payload1.toBleTextPayload())
            delay(TDCS_COMMAND_GAP_MS)
            writeCommand(payload2.toBleTextPayload())

            tdcsState = tdcsState.copy(
                active = false,
                current = 0,
                amplitude = 0,
                channel = safeChannel,
                lastCommandAt = System.currentTimeMillis()
            )
            _deviceStatus.update {
                it.copy(
                    message = "tDCS stopped: channel=$safeChannel",
                    tdcs = tdcsState,
                    connectionDiagnostics = (it.connectionDiagnostics + "control:tdcs=stop channel=$safeChannel")
                        .takeLast(connectionDiagnosticsLimit)
                )
            }
        }
    }

    /**
     * 连接成功、Notify/Indicate 开启后自动启动头环数据流。
     *
     * 不要从外部直接调用，也不要在这里加 operationMutex。
     */
    private suspend fun startDeviceStreamAndWaitFirstPacket() {
        ensureConnected()

        val baseline = lastValidPacketElapsedRealtime.value
        isDeviceStreaming = true

        _deviceStatus.update {
            it.copy(
                isStreaming = true,
                message = "已连接，正在发送启动命令 b",
                connectionDiagnostics = (it.connectionDiagnostics + "stream:autoStart send=b")
                    .takeLast(connectionDiagnosticsLimit)
            )
        }

        writeCommand(START_STREAM_COMMAND)

        val receivedAfterStart = withTimeoutOrNull(STREAM_START_TIMEOUT_MS) {
            lastValidPacketElapsedRealtime.first { it > baseline }
        }

        if (receivedAfterStart != null) {
            _deviceStatus.update {
                it.copy(
                    isStreaming = true,
                    message = "已收到头环数据，设备就绪",
                    connectionDiagnostics = (it.connectionDiagnostics + "stream:firstPacketReceived")
                        .takeLast(connectionDiagnosticsLimit)
                )
            }
            return
        }

        // 兼容 Python 端的 Restart 按钮：先发 's'，再发 'b'。
        _deviceStatus.update {
            it.copy(
                message = "未收到数据，尝试发送 s 后重新发送 b",
                connectionDiagnostics = (it.connectionDiagnostics + "stream:retry send=s then b")
                    .takeLast(connectionDiagnosticsLimit)
            )
        }

        writeCommand(RECONNECT_COMMAND)
        delay(RECONNECT_COMMAND_DELAY_MS)
        writeCommand(START_STREAM_COMMAND)

        val receivedAfterRetry = withTimeoutOrNull(STREAM_START_TIMEOUT_MS) {
            lastValidPacketElapsedRealtime.first { it > baseline }
        }

        if (receivedAfterRetry == null) {
            isDeviceStreaming = false
            _deviceStatus.update {
                it.copy(
                    isStreaming = false,
                    message = "设备已连接，但没有收到有效 RAW51 数据",
                    lastConnectionError = "streamStartTimeout",
                    connectionDiagnostics = (it.connectionDiagnostics + "stream:failed noRaw51")
                        .takeLast(connectionDiagnosticsLimit)
                )
            }
            error("设备已连接，但 ${STREAM_START_TIMEOUT_MS}ms 内没有收到有效 RAW51 数据")
        }

        _deviceStatus.update {
            it.copy(
                isStreaming = true,
                message = "已收到头环数据，设备就绪",
                connectionDiagnostics = (it.connectionDiagnostics + "stream:firstPacketReceivedAfterRetry")
                    .takeLast(connectionDiagnosticsLimit)
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun disconnectInternal(resetStatus: Boolean = true) {
        isDeviceStreaming = false
        val gatt = currentGatt
        if (gatt != null) {
            // 断开前尝试发 stop，避免头环继续推流。失败不影响释放 GATT。
            runCatching {
                if (writeCharacteristic != null) writeCommand(STOP_STREAM_COMMAND)
            }
            runCatching { disableNotifications(gatt) }
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }

        currentGatt = null
        notifyCharacteristic = null
        writeCharacteristic = null
        connectedDevice = null
        tdcsState = TdcsState()
        descriptorWritePurpose = DescriptorWritePurpose.NONE
        clearIncomingPayloadBuffer()
        _connectionState.value = DeviceConnectionState.DISCONNECTED

        if (resetStatus) {
            _deviceStatus.value = HeadbandStatus(message = "Disconnected")
        }
    }

    /**
     * 关闭通知。这里只作为清理动作，失败不抛出到外层。
     */
    @SuppressLint("MissingPermission")
    private suspend fun disableNotifications(gatt: BluetoothGatt) {
        val notify = notifyCharacteristic ?: return
        gatt.setCharacteristicNotification(notify, false)
        val cccd = notify.getDescriptor(CCCD_UUID) ?: return
        pendingDescriptorWrite = CompletableDeferred()
        descriptorWritePurpose = DescriptorWritePurpose.DISABLE_NOTIFY
        if (!writeDescriptorCompat(gatt, cccd, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) return
        withTimeoutOrNull(DESCRIPTOR_WRITE_TIMEOUT_MS) { pendingDescriptorWrite.await() }
    }

    private fun ensureConnected() {
        check(currentGatt != null && notifyCharacteristic != null && writeCharacteristic != null) {
            "Headband is not connected"
        }
    }

    /**
     * 写 BLE 命令。
     *
     * 重要：这里会根据 Write Characteristic 的 properties 自动选择：
     * - WRITE_TYPE_NO_RESPONSE：更接近很多硬件串口透传 BLE 模块的行为；
     * - WRITE_TYPE_DEFAULT：需要等待 onCharacteristicWrite 回调。
     */
    @SuppressLint("MissingPermission")
    private suspend fun writeCommand(command: ByteArray) {
        val gatt = currentGatt ?: error("BluetoothGatt is null")
        val characteristic = writeCharacteristic ?: error("Write characteristic is null")
        val payload = command
        val props = characteristic.properties
        val writeType = selectWriteType(props)

        Log.d(
            TAG,
            "writeCommand ascii=${payload.toAsciiForLog()} hex=${payload.toHex()} " +
                "writeType=${writeTypeToString(writeType)} props=${describeProperties(props)}"
        )

        pendingCharacteristicWrite = CompletableDeferred()

        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, payload, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }

        check(started) { "Failed to write command ${payload.toHex()}" }

        // WRITE_NO_RESPONSE 通常不会触发 onCharacteristicWrite；只确认 writeCharacteristic() 启动成功即可。
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) return

        val success = withTimeoutOrNull(CHARACTERISTIC_WRITE_TIMEOUT_MS) {
            pendingCharacteristicWrite.await()
        } ?: false

        check(success) { "Command write failed: ${payload.toHex()}" }
    }

    private fun selectWriteType(props: Int): Int {
        return when {
            props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 ->
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

            props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ->
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            else -> error("Write characteristic does not support write, props=${describeProperties(props)}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeDescriptorCompat(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun requireBluetoothAdapter(): BluetoothAdapter {
        val adapter = bluetoothAdapter ?: error("Bluetooth not supported on this device")
        check(adapter.isEnabled) { "Bluetooth is disabled" }
        return adapter
    }

    private fun ensureScanPermissions() {
        val missing = requiredRuntimePermissions().filterNot(::hasPermission)
        if (missing.isNotEmpty()) throw MissingDevicePermissionsException(missing)
        ensureLocationServicesEnabledForLegacyBle()
    }

    private fun ensureConnectPermissions() {
        val missing = requiredRuntimePermissions().filterNot(::hasPermission)
        if (missing.isNotEmpty()) throw MissingDevicePermissionsException(missing)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requiredRuntimePermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun ensureLocationServicesEnabledForLegacyBle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return

        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true

        check(isEnabled) { "Android 11 及以下扫描 BLE 设备时，请先打开系统定位开关。" }
    }

    /**
     * 处理 BLE notify/indicate 传入的数据。
     *
     * RAW51 帧格式：
     * - 51 bytes；
     * - 0xA0 开头；
     * - 0xC0 结尾。
     */
    private fun handleIncomingPayload(payload: ByteArray) {
        if (payload.isEmpty()) return

        Log.d(
            TAG,
            "notify payload len=${payload.size} hex=${payload.toHex()} " +
                "base64=${Base64.encodeToString(payload, Base64.NO_WRAP)}"
        )

        val packets = synchronized(incomingPayloadBufferLock) {
            payload.forEach { byte -> incomingPayloadBuffer.add(byte) }

            val parsedPackets = ArrayList<HeadbandRawPacket>()
            var invalidDropCount = 0

            while (incomingPayloadBuffer.size >= FRAME_LENGTH) {
                val headerIndex = incomingPayloadBuffer.indexOf(FRAME_HEADER_BYTE.toByte())
                if (headerIndex < 0) {
                    invalidDropCount += incomingPayloadBuffer.size
                    incomingPayloadBuffer.clear()
                    break
                }

                if (headerIndex > 0) {
                    invalidDropCount += headerIndex
                    incomingPayloadBuffer.subList(0, headerIndex).clear()
                }

                if (incomingPayloadBuffer.size < FRAME_LENGTH) break

                val chunk = ByteArray(FRAME_LENGTH)
                for (index in 0 until FRAME_LENGTH) chunk[index] = incomingPayloadBuffer[index]

                val validFrame =
                    (chunk.first().toInt() and 0xFF) == FRAME_HEADER_BYTE &&
                        (chunk.last().toInt() and 0xFF) == FRAME_TAIL

                if (!validFrame) {
                    invalidDropCount += 1
                    incomingPayloadBuffer.removeAt(0)
                    continue
                }

                incomingPayloadBuffer.subList(0, FRAME_LENGTH).clear()
                parsedPackets.addAll(parser.parseNotifyPayload(chunk))
            }

            if (invalidDropCount > 0) {
                Log.w(TAG, "Dropped $invalidDropCount byte(s) while aligning RAW51 frames")
            }

            parsedPackets
        }

        if (packets.isEmpty()) {
            _deviceStatus.update {
                it.copy(
                    message = "Received notify payload, but no valid RAW51 frame was parsed",
                    connectionDiagnostics = (it.connectionDiagnostics + "notify:invalid payloadLen=${payload.size}")
                        .takeLast(connectionDiagnosticsLimit)
                )
            }
            return
        }

        lastValidPacketElapsedRealtime.value = SystemClock.elapsedRealtime()
        isDeviceStreaming = true

        // 设备层持续产出 rawDataFlow。是否写入 sleep session，由 UI/Repository 控制。
        packets.forEach { packet ->
            _rawDataFlow.tryEmit(packet)
            _deviceStatus.update {
                it.copy(
                    isStreaming = true,
                    latestPacket = packet,
                    packetCount = it.packetCount + 1,
                    lastPacketTimestamp = packet.hostTimestamp,
                    message = "Streaming"
                )
            }
        }
    }

    private fun clearIncomingPayloadBuffer() {
        synchronized(incomingPayloadBufferLock) {
            incomingPayloadBuffer.clear()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                _deviceStatus.update {
                    it.copy(
                        message = "GATT connection failed: $status",
                        lastConnectionError = "onConnectionStateChange status=$status newState=$newState",
                        connectionDiagnostics = (it.connectionDiagnostics + "gatt:connectionFailed status=$status newState=$newState")
                            .takeLast(connectionDiagnosticsLimit)
                    )
                }
                pendingConnection.completeExceptionally(IllegalStateException("GATT connection failed: $status"))
                disconnectAndReset(gatt, "GATT connection failed: $status")
                return
            }

            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    currentGatt = gatt
                    _deviceStatus.update {
                        it.copy(
                            message = "GATT connected, requesting MTU",
                            connectionDiagnostics = (it.connectionDiagnostics + "gatt:connected requestMtu=$REQUEST_MTU")
                                .takeLast(connectionDiagnosticsLimit)
                        )
                    }

                    // 请求较大 MTU，减少 RAW51 被拆包的概率。即便失败，仍继续 discoverServices。
                    val mtuRequested = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        runCatching { gatt.requestMtu(REQUEST_MTU) }.getOrDefault(false)
                    } else {
                        false
                    }

                    if (!mtuRequested) {
                        Log.w(TAG, "requestMtu failed to start, discovering services directly")
                        discoverServicesOrFail(gatt)
                    }
                }

                BluetoothGatt.STATE_DISCONNECTED -> {
                    _deviceStatus.update {
                        it.copy(
                            message = "Disconnected",
                            connectionDiagnostics = (it.connectionDiagnostics + "gatt:disconnected")
                                .takeLast(connectionDiagnosticsLimit)
                        )
                    }
                    if (!pendingConnection.isCompleted) {
                        pendingConnection.completeExceptionally(
                            IllegalStateException("Disconnected before services were ready")
                        )
                    }
                    disconnectAndReset(gatt, "Disconnected")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "onMtuChanged mtu=$mtu status=$status")
            _deviceStatus.update {
                it.copy(
                    connectionDiagnostics = (it.connectionDiagnostics + "gatt:mtuChanged mtu=$mtu status=$status")
                        .takeLast(connectionDiagnosticsLimit)
                )
            }
            discoverServicesOrFail(gatt)
        }

        @SuppressLint("MissingPermission")
        private fun discoverServicesOrFail(gatt: BluetoothGatt) {
            _deviceStatus.update {
                it.copy(
                    message = "Discovering services",
                    connectionDiagnostics = (it.connectionDiagnostics + "gatt:discoverServices")
                        .takeLast(connectionDiagnosticsLimit)
                )
            }
            if (!gatt.discoverServices()) {
                pendingConnection.completeExceptionally(
                    IllegalStateException("discoverServices() returned false")
                )
                disconnectAndReset(gatt, "discoverServices() failed to start")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered status=$status services=${gatt.services.map { it.uuid }}")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                _deviceStatus.update {
                    it.copy(
                        message = "Service discovery failed: $status",
                        lastConnectionError = "onServicesDiscovered status=$status",
                        connectionDiagnostics = (it.connectionDiagnostics + "gatt:servicesFailed status=$status")
                            .takeLast(connectionDiagnosticsLimit)
                    )
                }
                pendingConnection.completeExceptionally(IllegalStateException("Service discovery failed: $status"))
                disconnectAndReset(gatt, "Service discovery failed: $status")
                return
            }

            val service: BluetoothGattService = gatt.getService(SERVICE_UUID) ?: run {
                _deviceStatus.update {
                    it.copy(
                        message = "Service $SERVICE_UUID not found",
                        lastConnectionError = "serviceMissing",
                        connectionDiagnostics = (it.connectionDiagnostics + "gatt:serviceMissing $SERVICE_UUID")
                            .takeLast(connectionDiagnosticsLimit)
                    )
                }
                pendingConnection.completeExceptionally(IllegalStateException("Service $SERVICE_UUID not found"))
                disconnectAndReset(gatt, "Required service not found")
                return
            }

            notifyCharacteristic = service.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID)
            writeCharacteristic = service.getCharacteristic(WRITE_CHARACTERISTIC_UUID)

            val notify = notifyCharacteristic
            val write = writeCharacteristic
            if (notify == null || write == null) {
                _deviceStatus.update {
                    it.copy(
                        message = "Notify/write characteristic missing",
                        lastConnectionError = "characteristicMissing",
                        connectionDiagnostics = (it.connectionDiagnostics + "gatt:characteristicMissing")
                            .takeLast(connectionDiagnosticsLimit)
                    )
                }
                pendingConnection.completeExceptionally(IllegalStateException("Notify/write characteristic missing"))
                disconnectAndReset(gatt, "Required characteristics not found")
                return
            }

            Log.d(TAG, "notify props=${describeProperties(notify.properties)} raw=${notify.properties}")
            Log.d(TAG, "write props=${describeProperties(write.properties)} raw=${write.properties}")
            Log.d(TAG, "notify descriptors=${notify.descriptors.map { it.uuid }}")

            val cccd = notify.getDescriptor(CCCD_UUID)
            if (cccd == null) {
                _deviceStatus.update {
                    it.copy(
                        message = "CCCD descriptor missing",
                        lastConnectionError = "cccdMissing",
                        connectionDiagnostics = (it.connectionDiagnostics + "gatt:cccdMissing")
                            .takeLast(connectionDiagnosticsLimit)
                    )
                }
                pendingConnection.completeExceptionally(IllegalStateException("CCCD descriptor missing"))
                disconnectAndReset(gatt, "CCCD descriptor missing")
                return
            }

            if (!gatt.setCharacteristicNotification(notify, true)) {
                _deviceStatus.update {
                    it.copy(
                        message = "setCharacteristicNotification failed",
                        lastConnectionError = "setCharacteristicNotificationFailed",
                        connectionDiagnostics = (it.connectionDiagnostics + "gatt:setCharacteristicNotificationFailed")
                            .takeLast(connectionDiagnosticsLimit)
                    )
                }
                pendingConnection.completeExceptionally(IllegalStateException("setCharacteristicNotification failed"))
                disconnectAndReset(gatt, "Unable to enable notification")
                return
            }

            val cccdValue = selectCccdValue(notify.properties)
            pendingDescriptorWrite = CompletableDeferred()
            descriptorWritePurpose = DescriptorWritePurpose.ENABLE_NOTIFY

            _deviceStatus.update {
                it.copy(
                    message = "Enabling notify/indicate",
                    connectionDiagnostics = (it.connectionDiagnostics +
                        "gatt:enableCccd value=${cccdValue.toHex()} props=${describeProperties(notify.properties)}")
                        .takeLast(connectionDiagnosticsLimit)
                )
            }

            val started = writeDescriptorCompat(gatt, cccd, cccdValue)
            if (!started) {
                _deviceStatus.update {
                    it.copy(
                        message = "Failed to write CCCD",
                        lastConnectionError = "cccdWriteFailed",
                        connectionDiagnostics = (it.connectionDiagnostics + "gatt:cccdWriteFailed")
                            .takeLast(connectionDiagnosticsLimit)
                    )
                }
                pendingConnection.completeExceptionally(IllegalStateException("CCCD write failed to start"))
                disconnectAndReset(gatt, "Failed to write CCCD")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d(
                TAG,
                "onDescriptorWrite uuid=${descriptor.uuid} status=$status purpose=$descriptorWritePurpose"
            )

            val success = status == BluetoothGatt.GATT_SUCCESS
            pendingDescriptorWrite.complete(success)

            if (!success) {
                _deviceStatus.update {
                    it.copy(
                        message = "Descriptor write failed: $status",
                        lastConnectionError = "descriptorWrite status=$status",
                        connectionDiagnostics = (it.connectionDiagnostics + "gatt:descriptorWriteFailed status=$status")
                            .takeLast(connectionDiagnosticsLimit)
                    )
                }
                if (!pendingConnection.isCompleted) {
                    pendingConnection.completeExceptionally(
                        IllegalStateException("Descriptor write failed: $status")
                    )
                }
                disconnectAndReset(gatt, "Descriptor write failed: $status")
                return
            }

            if (
                descriptor.uuid == CCCD_UUID &&
                descriptorWritePurpose == DescriptorWritePurpose.ENABLE_NOTIFY &&
                !pendingConnection.isCompleted
            ) {
                _deviceStatus.update {
                    it.copy(
                        message = "Notify/indicate enabled",
                        connectionDiagnostics = (it.connectionDiagnostics + "gatt:cccdEnabled")
                            .takeLast(connectionDiagnosticsLimit)
                    )
                }
                pendingConnection.complete(Unit)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val payload = characteristic.value ?: return
            handleIncomingPayload(payload)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingPayload(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d(
                TAG,
                "onCharacteristicWrite uuid=${characteristic.uuid} status=$status " +
                    "success=${status == BluetoothGatt.GATT_SUCCESS}"
            )
            pendingCharacteristicWrite.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    private fun selectCccdValue(props: Int): ByteArray {
        return when {
            props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ->
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

            props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE

            else -> error("Notify characteristic does not support notify/indicate, props=${describeProperties(props)}")
        }
    }

    private fun disconnectAndReset(gatt: BluetoothGatt, message: String) {
        isDeviceStreaming = false
        runCatching { gatt.close() }
        currentGatt = null
        notifyCharacteristic = null
        writeCharacteristic = null
        connectedDevice = null
        descriptorWritePurpose = DescriptorWritePurpose.NONE
        clearIncomingPayloadBuffer()
        _connectionState.value = DeviceConnectionState.DISCONNECTED
        _deviceStatus.value = HeadbandStatus(message = message)
    }

    private enum class DescriptorWritePurpose {
        NONE,
        ENABLE_NOTIFY,
        DISABLE_NOTIFY
    }

    private fun connectionErrorToString(error: Throwable): String {
        return when (error) {
            is TimeoutCancellationException -> "TIMEOUT"
            else -> error.message ?: error::class.java.simpleName
        }
    }

    private fun scanErrorToString(errorCode: Int): String {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APP_REG_FAILED"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
            ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "OUT_OF_HW_RESOURCES"
            else -> "UNKNOWN_ERROR"
        }
    }

    private fun describeProperties(props: Int): String {
        val names = mutableListOf<String>()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) names += "READ"
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) names += "WRITE"
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) names += "WRITE_NO_RESPONSE"
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) names += "NOTIFY"
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) names += "INDICATE"
        return names.joinToString("|").ifBlank { "NONE" }
    }

    private fun writeTypeToString(writeType: Int): String {
        return when (writeType) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT -> "WRITE_TYPE_DEFAULT"
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE -> "WRITE_TYPE_NO_RESPONSE"
            BluetoothGattCharacteristic.WRITE_TYPE_SIGNED -> "WRITE_TYPE_SIGNED"
            else -> "UNKNOWN($writeType)"
        }
    }

    private fun String.toBleTextPayload(): ByteArray {
        return encodeToByteArray() + byteArrayOf('\n'.code.toByte())
    }

    private fun ByteArray.toHex(): String = joinToString(separator = " ") { byte ->
        "%02X".format(byte.toInt() and 0xFF)
    }

    private fun ByteArray.toAsciiForLog(): String {
        return runCatching { decodeToString() }.getOrDefault("-")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    companion object {
        private const val TAG = "BleHeadband"
        private const val TARGET_DEVICE_NAME = "MindBridge-v3.11"
        private const val UNKNOWN_DEVICE_NAME = "Unknown BLE Device"

        /** Python 端 Start Sampling 对应 send_command(b"b")。 */
        private val START_STREAM_COMMAND = byteArrayOf('b'.code.toByte())

        /** Python 端 Restart 按钮对应发送 's'。 */
        private val RECONNECT_COMMAND = byteArrayOf('s'.code.toByte())

        /** Python 端 Stop Sampling 对应 send_command(b"stop")。 */
        private val STOP_STREAM_COMMAND = "stop".encodeToByteArray()

        private const val SCAN_DURATION_MS = 4_000L
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val STREAM_START_TIMEOUT_MS = 3_000L
        private const val RECONNECT_COMMAND_DELAY_MS = 350L
        private const val DESCRIPTOR_WRITE_TIMEOUT_MS = 1_500L
        private const val CHARACTERISTIC_WRITE_TIMEOUT_MS = 1_500L

        private const val REQUEST_MTU = 247

        private const val FRAME_LENGTH = 51
        private const val FRAME_HEADER_BYTE = 0xA0
        private const val FRAME_TAIL = 0xC0

        val SERVICE_UUID: UUID = UUID.fromString("0000ae30-0000-1000-8000-00805f9b34fb")
        val NOTIFY_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb")
        val WRITE_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
