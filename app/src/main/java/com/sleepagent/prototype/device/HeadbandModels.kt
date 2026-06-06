package com.sleepagent.prototype.device

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class HeadbandDevice(
    val deviceId: String,
    val name: String,
    val address: String,
    val rssi: Int
)

enum class DeviceConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

data class HeadbandRawPacket(
    val hostTimestamp: Long,
    val sequence: Int,
    val sampleNumber: Int,
    val deviceTimestamp: Long?,
    val state: Int?,
    val eegCounts: IntArray,
    val eegMicrovolts: FloatArray,
    val imu: HeadbandImuSample? = null,
    val signalQuality: Float?,
    val batteryLevel: Int?
)

data class HeadbandImuSample(
    val accelX: Int,
    val accelY: Int,
    val accelZ: Int,
    val gyroX: Int,
    val gyroY: Int,
    val gyroZ: Int,
    val magX: Int,
    val magY: Int,
    val magZ: Int
)

data class HeadbandStatus(
    val isStreaming: Boolean = false,
    val connectedDevice: HeadbandDevice? = null,
    val latestPacket: HeadbandRawPacket? = null,
    val packetCount: Long = 0L,
    val lastPacketTimestamp: Long? = null,
    val message: String? = null,
    val opticalMode: String? = null,
    val connectionDiagnostics: List<String> = emptyList(),
    val lastConnectionError: String? = null,
    val rawScanEventCount: Int = 0,
    val scanDiagnostics: List<String> = emptyList(),
    val lastScanError: String? = null,
    val tdcs: TdcsState = TdcsState()
)

enum class SleepOpticalMode(
    val wireValue: String,
    val label: String
) {
    HRV("ppd", "HRV"),
    FNIRS("fnirs", "fNIRS"),
    OFF("off", "关闭");

    companion object {
        fun fromWireValue(value: String?): SleepOpticalMode {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireValue == normalized } ?: OFF
        }
    }
}

interface HeadbandDeviceManager {
    val connectionState: StateFlow<DeviceConnectionState>
    val deviceStatus: StateFlow<HeadbandStatus>
    val rawDataFlow: Flow<HeadbandRawPacket>

    suspend fun scan(): List<HeadbandDevice>
    suspend fun connect(deviceId: String)
    suspend fun disconnect()
    suspend fun startStreaming()
    suspend fun stopStreaming()
    suspend fun setOpticalMode(mode: SleepOpticalMode)
    suspend fun startTdcs(config: TdcsConfig = TdcsConfig())
    suspend fun stopTdcs(channel: String = TDCS_DEFAULT_CHANNEL)
}

class MissingDevicePermissionsException(
    val permissions: List<String>
) : IllegalStateException("Missing permissions: ${permissions.joinToString()}")
