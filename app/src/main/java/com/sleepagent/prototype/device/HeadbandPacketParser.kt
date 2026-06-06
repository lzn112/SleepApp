package com.sleepagent.prototype.device

/**
 * Parser for the headband BLE notify payload.
 *
 * Frame format:
 * - 51 bytes per frame
 * - byte[0] = 0xA0 frame header
 * - byte[1] = sample sequence
 * - byte[2..25] = 8 EEG channels, 3-byte signed big-endian each
 * - byte[26..29] = device timestamp, uint32 little-endian
 * - byte[30..31] = state, uint16 little-endian
 * - byte[32..49] = IMU payload, 9-axis int16 little-endian
 * - byte[50] = 0xC0 frame tail
 */
class HeadbandPacketParser {
    fun parseNotifyPayload(payload: ByteArray): List<HeadbandRawPacket> {
        if (payload.isEmpty() || payload.size % FRAME_LENGTH != 0) {
            return emptyList()
        }

        val packets = ArrayList<HeadbandRawPacket>(payload.size / FRAME_LENGTH)
        var offset = 0
        while (offset < payload.size) {
            val chunk = payload.copyOfRange(offset, offset + FRAME_LENGTH)
            if ((chunk.first().toInt() and 0xFF) != FRAME_HEADER || (chunk.last().toInt() and 0xFF) != FRAME_TAIL) {
                offset += FRAME_LENGTH
                continue
            }

            val eegCounts = IntArray(CHANNEL_COUNT)
            val eegMicrovolts = FloatArray(CHANNEL_COUNT)
            for (channelIndex in 0 until CHANNEL_COUNT) {
                val base = 2 + channelIndex * BYTES_PER_CHANNEL
                val counts = parseSigned24BitBigEndian(chunk, base)
                eegCounts[channelIndex] = counts
                eegMicrovolts[channelIndex] = countsToMicrovolts(counts)
            }

            val hostTimestamp = System.currentTimeMillis()
            val sequence = chunk[1].toInt() and 0xFF
            val deviceTimestamp = parseUInt32LittleEndian(chunk, 26)
            val state = parseUInt16LittleEndian(chunk, 30)
            val imu = parseImuSampleOrNull(chunk, 32)

            packets += HeadbandRawPacket(
                hostTimestamp = hostTimestamp,
                sequence = sequence,
                sampleNumber = sequence,
                deviceTimestamp = deviceTimestamp,
                state = state,
                eegCounts = eegCounts,
                eegMicrovolts = eegMicrovolts,
                imu = imu,
                signalQuality = null,
                batteryLevel = null
            )
            offset += FRAME_LENGTH
        }

        return packets
    }

    /**
     * The EEG ADC uses a 24-bit signed integer. When bit 23 is set, subtract 0x1000000
     * to perform sign extension into the 32-bit Kotlin Int.
     */
    private fun parseSigned24BitBigEndian(bytes: ByteArray, offset: Int): Int {
        var value = ((bytes[offset].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset + 2].toInt() and 0xFF)
        if ((value and SIGN_BIT_24) != 0) {
            value -= SIGN_EXTENSION_24
        }
        return value
    }

    private fun parseUInt32LittleEndian(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xFF)) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }

    private fun parseUInt16LittleEndian(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun parseInt16LittleEndian(bytes: ByteArray, offset: Int): Int {
        val value = parseUInt16LittleEndian(bytes, offset)
        return if ((value and SIGN_BIT_16) != 0) {
            value - SIGN_EXTENSION_16
        } else {
            value
        }
    }

    private fun parseImuSampleOrNull(bytes: ByteArray, offset: Int): HeadbandImuSample? {
        val hasImuBytes = (0 until IMU_AXIS_COUNT * BYTES_PER_INT16).any { index ->
            bytes[offset + index].toInt() != 0
        }
        if (!hasImuBytes) return null

        return HeadbandImuSample(
            accelX = parseInt16LittleEndian(bytes, offset),
            accelY = parseInt16LittleEndian(bytes, offset + 2),
            accelZ = parseInt16LittleEndian(bytes, offset + 4),
            gyroX = parseInt16LittleEndian(bytes, offset + 6),
            gyroY = parseInt16LittleEndian(bytes, offset + 8),
            gyroZ = parseInt16LittleEndian(bytes, offset + 10),
            magX = parseInt16LittleEndian(bytes, offset + 12),
            magY = parseInt16LittleEndian(bytes, offset + 14),
            magZ = parseInt16LittleEndian(bytes, offset + 16)
        )
    }

    /**
     * ADS1299 conversion from raw counts to microvolts.
     */
    private fun countsToMicrovolts(counts: Int): Float {
        return (counts.toDouble() * VREF / GAIN / ADS1299_FULL_SCALE_COUNTS * 1_000_000.0).toFloat()
    }

    companion object {
        private const val FRAME_LENGTH = 51
        private const val FRAME_HEADER = 0xA0
        private const val FRAME_TAIL = 0xC0
        private const val CHANNEL_COUNT = 8
        private const val BYTES_PER_CHANNEL = 3
        private const val BYTES_PER_INT16 = 2
        private const val IMU_AXIS_COUNT = 9
        private const val SIGN_BIT_24 = 0x800000
        private const val SIGN_EXTENSION_24 = 0x1000000
        private const val SIGN_BIT_16 = 0x8000
        private const val SIGN_EXTENSION_16 = 0x10000
        private const val ADS1299_FULL_SCALE_COUNTS = (1 shl 23) - 1
        private const val VREF = 4.5
        private const val GAIN = 24.0
    }
}
