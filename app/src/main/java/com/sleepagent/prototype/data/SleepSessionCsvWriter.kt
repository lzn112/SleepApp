package com.sleepagent.prototype.data

import android.content.Context
import com.sleepagent.prototype.device.HeadbandRawPacket
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SleepSessionCsvWriter(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun createSession(sessionId: String = generateSessionId()): SessionHandle {
        return withContext(ioDispatcher) {
            val sessionDir = File(context.filesDir, "sleep_sessions/$sessionId")
            sessionDir.mkdirs()
            val csvFile = File(sessionDir, RAW_CSV_FILE_NAME)
            val writer = csvFile.bufferedWriter()
            writer.write(RAW_CSV_HEADER)
            writer.newLine()
            writer.flush()
            SessionHandle(sessionId, csvFile, writer)
        }
    }

    inner class SessionHandle internal constructor(
        val sessionId: String,
        val csvFile: File,
        private val writer: BufferedWriter
    ) {
        suspend fun append(packet: HeadbandRawPacket) {
            withContext(ioDispatcher) {
                writer.write(packet.toCsvRow())
                writer.newLine()
            }
        }

        suspend fun close() {
            withContext(ioDispatcher) {
                writer.flush()
                writer.close()
            }
        }
    }

    private fun HeadbandRawPacket.toCsvRow(): String {
        val columns = buildList {
            add(hostTimestamp.toString())
            add(deviceTimestamp?.toString().orEmpty())
            add(state?.toString().orEmpty())
            eegCounts.forEach { add(it.toString()) }
            add(imu?.accelX?.toString().orEmpty())
            add(imu?.accelY?.toString().orEmpty())
            add(imu?.accelZ?.toString().orEmpty())
            add(imu?.gyroX?.toString().orEmpty())
            add(imu?.gyroY?.toString().orEmpty())
            add(imu?.gyroZ?.toString().orEmpty())
            add(imu?.magX?.toString().orEmpty())
            add(imu?.magY?.toString().orEmpty())
            add(imu?.magZ?.toString().orEmpty())
        }
        return columns.joinToString(",")
    }

    private fun generateSessionId(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return formatter.format(Date())
    }

    companion object {
        internal const val RAW_CSV_FILE_NAME = "raw.csv"
        internal const val RAW_CSV_HEADER =
            "host_timestamp,device_timestamp,state," +
                "ch1_counts,ch2_counts,ch3_counts,ch4_counts,ch5_counts,ch6_counts,ch7_counts,ch8_counts," +
                "imu_accel_x,imu_accel_y,imu_accel_z,imu_gyro_x,imu_gyro_y,imu_gyro_z," +
                "imu_mag_x,imu_mag_y,imu_mag_z"
    }
}
