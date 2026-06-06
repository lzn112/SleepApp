package com.sleepagent.prototype.data

import org.junit.Assert.assertTrue
import org.junit.Test

class SleepSessionCsvWriterTest {
    @Test
    fun rawHeaderIncludesImuColumns() {
        val header = SleepSessionCsvWriter.RAW_CSV_HEADER

        assertTrue(header.contains("imu_accel_x"))
        assertTrue(header.contains("imu_accel_y"))
        assertTrue(header.contains("imu_accel_z"))
        assertTrue(header.contains("imu_gyro_x"))
        assertTrue(header.contains("imu_gyro_y"))
        assertTrue(header.contains("imu_gyro_z"))
        assertTrue(header.contains("imu_mag_x"))
        assertTrue(header.contains("imu_mag_y"))
        assertTrue(header.contains("imu_mag_z"))
    }
}
