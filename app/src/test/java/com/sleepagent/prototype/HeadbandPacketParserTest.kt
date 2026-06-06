package com.sleepagent.prototype

import com.sleepagent.prototype.device.HeadbandPacketParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadbandPacketParserTest {
    @Test
    fun parsesSingleFrameIntoEightChannels() {
        val parser = HeadbandPacketParser()
        val payload = ByteArray(51)
        payload[0] = 0xA0.toByte()
        payload[1] = 7

        val counts = intArrayOf(1, -1, 0x7FFFFF, -0x800000, 100, -100, 5000, -5000)
        counts.forEachIndexed { index, value ->
            val encoded = if (value < 0) value + 0x1000000 else value
            val offset = 2 + index * 3
            payload[offset] = ((encoded shr 16) and 0xFF).toByte()
            payload[offset + 1] = ((encoded shr 8) and 0xFF).toByte()
            payload[offset + 2] = (encoded and 0xFF).toByte()
        }

        payload[26] = 0x78
        payload[27] = 0x56
        payload[28] = 0x34
        payload[29] = 0x12
        payload[30] = 0xCD.toByte()
        payload[31] = 0xAB.toByte()
        putInt16LittleEndian(payload, 32, 256)
        putInt16LittleEndian(payload, 34, -256)
        putInt16LittleEndian(payload, 36, 1024)
        putInt16LittleEndian(payload, 38, 12)
        putInt16LittleEndian(payload, 40, -34)
        putInt16LittleEndian(payload, 42, 56)
        putInt16LittleEndian(payload, 44, 78)
        putInt16LittleEndian(payload, 46, -90)
        putInt16LittleEndian(payload, 48, 1234)
        payload[50] = 0xC0.toByte()

        val packets = parser.parseNotifyPayload(payload)

        assertEquals(1, packets.size)
        assertEquals(7, packets.first().sequence)
        assertEquals(0x12345678L, packets.first().deviceTimestamp)
        assertEquals(0xABCD, packets.first().state)
        assertTrue(packets.first().eegCounts.contentEquals(counts))
        val imu = packets.first().imu
        assertEquals(256, imu?.accelX)
        assertEquals(-256, imu?.accelY)
        assertEquals(1024, imu?.accelZ)
        assertEquals(12, imu?.gyroX)
        assertEquals(-34, imu?.gyroY)
        assertEquals(56, imu?.gyroZ)
        assertEquals(78, imu?.magX)
        assertEquals(-90, imu?.magY)
        assertEquals(1234, imu?.magZ)
    }

    @Test
    fun returnsNullImuWhenReservedBytesAreEmpty() {
        val parser = HeadbandPacketParser()
        val payload = ByteArray(51)
        payload[0] = 0xA0.toByte()
        payload[1] = 1
        payload[50] = 0xC0.toByte()

        val packets = parser.parseNotifyPayload(payload)

        assertEquals(1, packets.size)
        assertEquals(null, packets.first().imu)
    }

    private fun putInt16LittleEndian(bytes: ByteArray, offset: Int, value: Int) {
        val encoded = value and 0xFFFF
        bytes[offset] = (encoded and 0xFF).toByte()
        bytes[offset + 1] = ((encoded shr 8) and 0xFF).toByte()
    }
}
