package com.sleepagent.prototype.sleep.processing

import com.sleepagent.prototype.device.HeadbandRawPacket

data class DownsampledEegSample(
    val timestampMillis: Long,
    val channelIndex: Int,
    val valueMicrovolts: Float
)

class SleepEegDownsampler(
    private val sourceSampleRateHz: Int = 250,
    private val targetSampleRateHz: Int = 100,
    private val channelIndex: Int = DEFAULT_CHANNEL_INDEX
) {
    private var phase = 0
    private var bucketSum = 0f
    private var bucketCount = 0
    private var lastTimestampMillis: Long? = null

    fun reset() {
        phase = 0
        bucketSum = 0f
        bucketCount = 0
        lastTimestampMillis = null
    }

    fun ingest(packet: HeadbandRawPacket): DownsampledEegSample? {
        val value = packet.eegMicrovolts.getOrNull(channelIndex) ?: return null
        bucketSum += value
        bucketCount += 1
        lastTimestampMillis = packet.hostTimestamp
        phase += targetSampleRateHz

        if (phase < sourceSampleRateHz || bucketCount <= 0) return null

        val output = DownsampledEegSample(
            timestampMillis = lastTimestampMillis ?: packet.hostTimestamp,
            channelIndex = channelIndex,
            valueMicrovolts = bucketSum / bucketCount.toFloat()
        )
        phase -= sourceSampleRateHz
        bucketSum = 0f
        bucketCount = 0
        lastTimestampMillis = null
        return output
    }

    companion object {
        const val DEFAULT_CHANNEL_INDEX = 5
    }
}
