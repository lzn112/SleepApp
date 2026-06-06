package com.sleepagent.prototype.device

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class MockHeadbandDeviceManager(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : HeadbandDeviceManager {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _connectionState = MutableStateFlow(DeviceConnectionState.DISCONNECTED)
    private val _deviceStatus = MutableStateFlow(HeadbandStatus(message = "Mock headband ready"))
    private val _rawDataFlow = MutableSharedFlow<HeadbandRawPacket>(
        extraBufferCapacity = 256
    )

    private var connectedDevice: HeadbandDevice? = null
    private var streamingJob: Job? = null
    private var opticalMode: SleepOpticalMode = SleepOpticalMode.OFF
    private var tdcsState: TdcsState = TdcsState()

    override val connectionState = _connectionState.asStateFlow()
    override val deviceStatus = _deviceStatus.asStateFlow()
    override val rawDataFlow: Flow<HeadbandRawPacket> = _rawDataFlow.asSharedFlow()

    override suspend fun scan(): List<HeadbandDevice> {
        _connectionState.value = DeviceConnectionState.SCANNING
        delay(500)
        _connectionState.value = if (connectedDevice != null) {
            DeviceConnectionState.CONNECTED
        } else {
            DeviceConnectionState.DISCONNECTED
        }
        return listOf(
            HeadbandDevice(
                deviceId = "MOCK-001",
                name = "MindBridge-v3.11 (Mock)",
                address = "00:11:22:33:44:55",
                rssi = -42
            )
        )
    }

    override suspend fun connect(deviceId: String) {
        _connectionState.value = DeviceConnectionState.CONNECTING
        delay(300)
        connectedDevice = HeadbandDevice(
            deviceId = deviceId,
            name = "MindBridge-v3.11 (Mock)",
            address = "00:11:22:33:44:55",
            rssi = -42
        )
        _connectionState.value = DeviceConnectionState.CONNECTED
        _deviceStatus.value = HeadbandStatus(
            isStreaming = false,
            connectedDevice = connectedDevice,
            message = "Connected to mock headband",
            opticalMode = opticalMode.wireValue,
            tdcs = tdcsState
        )
    }

    override suspend fun disconnect() {
        stopStreaming()
        connectedDevice = null
        tdcsState = TdcsState()
        _connectionState.value = DeviceConnectionState.DISCONNECTED
        _deviceStatus.value = HeadbandStatus(message = "Mock device disconnected")
    }

    override suspend fun startStreaming() {
        val device = connectedDevice ?: error("Mock device is not connected")
        if (streamingJob != null) return

        _deviceStatus.update {
            it.copy(
                isStreaming = true,
                connectedDevice = device,
                packetCount = 0L,
                message = "Mock streaming started",
                opticalMode = opticalMode.wireValue
            )
        }

        streamingJob = scope.launch {
            var sequence = 0
            while (isActive) {
                val counts = IntArray(8) { channel ->
                    (sin((sequence + channel * 8) / 25.0 * PI) * 25000).toInt()
                }
                val packet = HeadbandRawPacket(
                    hostTimestamp = System.currentTimeMillis(),
                    sequence = sequence and 0xFF,
                    sampleNumber = sequence and 0xFF,
                    deviceTimestamp = (sequence * 4L) and 0xFFFFFFFF,
                    state = 1,
                    eegCounts = counts,
                    eegMicrovolts = FloatArray(8) { idx ->
                        counts[idx] / 10.0f
                    },
                    imu = HeadbandImuSample(
                        accelX = ((sin(sequence / 20.0 * PI) * 512.0)).toInt(),
                        accelY = ((sin((sequence + 8) / 20.0 * PI) * 512.0)).toInt(),
                        accelZ = 1024,
                        gyroX = ((sin(sequence / 30.0 * PI) * 128.0)).toInt(),
                        gyroY = ((sin((sequence + 10) / 30.0 * PI) * 128.0)).toInt(),
                        gyroZ = ((sin((sequence + 20) / 30.0 * PI) * 128.0)).toInt(),
                        magX = 64,
                        magY = -32,
                        magZ = 128
                    ),
                    signalQuality = 1.0f,
                    batteryLevel = 90
                )
                _rawDataFlow.emit(packet)
                _deviceStatus.update {
                    it.copy(
                        isStreaming = true,
                        latestPacket = packet,
                        packetCount = it.packetCount + 1,
                        lastPacketTimestamp = packet.hostTimestamp,
                        opticalMode = opticalMode.wireValue,
                        tdcs = tdcsState
                    )
                }
                sequence += 1
                delay(4)
            }
        }
    }

    override suspend fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        _deviceStatus.update {
            it.copy(isStreaming = false, message = "Mock streaming stopped", opticalMode = opticalMode.wireValue)
        }
    }

    override suspend fun setOpticalMode(mode: SleepOpticalMode) {
        opticalMode = mode
        _deviceStatus.update {
            it.copy(
                opticalMode = opticalMode.wireValue,
                message = "Mock optical mode: ${mode.label}",
                tdcs = tdcsState
            )
        }
    }

    override suspend fun startTdcs(config: TdcsConfig) {
        val safe = config.sanitized()
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
                message = "Mock tDCS started",
                tdcs = tdcsState
            )
        }
    }

    override suspend fun stopTdcs(channel: String) {
        val safeChannel = channel.trim().ifBlank { tdcsState.channel.ifBlank { TDCS_DEFAULT_CHANNEL } }
        tdcsState = tdcsState.copy(
            active = false,
            current = 0,
            amplitude = 0,
            channel = safeChannel,
            lastCommandAt = System.currentTimeMillis()
        )
        _deviceStatus.update {
            it.copy(
                message = "Mock tDCS stopped",
                tdcs = tdcsState
            )
        }
    }
}
