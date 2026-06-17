package com.sleepagent.prototype.device

import org.json.JSONObject

internal const val TDCS_DEFAULT_BOOST = 3
internal const val TDCS_DEFAULT_CHANNEL = "0001"
internal const val TDCS_DEFAULT_CURRENT = 2
internal const val TDCS_DEFAULT_AMPLITUDE = 50
internal const val TDCS_COMMAND_GAP_MS = 1_000L

private const val TDCS_FREQUENCY = 20_000
private const val TDCS_NEGATIVE = 0
private const val TDCS_WAVE = 2
private val TDCS_CONSTANT_WAVE_HEX = "0a".repeat(64)

private const val LEGACY_SINE_WAVE_HEX =
    "0c1825313d4a55616d78838d97a1abb4" +
        "bcc5ccd4dae0e6ebf0f4f7fafcfdfeff" +
        "fefdfcfaf7f4f0ebe6e0dad4ccc5bcb4" +
        "aba1978d83786d61554a3d3125180c00"

data class TdcsConfig(
    val boost: Int = TDCS_DEFAULT_BOOST,
    val current: Int = TDCS_DEFAULT_CURRENT,
    val amplitude: Int = TDCS_DEFAULT_AMPLITUDE,
    val channel: String = TDCS_DEFAULT_CHANNEL,
    val frequency: Int = TDCS_FREQUENCY,
    val negative: Int = TDCS_NEGATIVE,
    val wave: Int = TDCS_WAVE,
    val waveDataHex: String = TDCS_CONSTANT_WAVE_HEX
)

data class TdcsState(
    val active: Boolean = false,
    val boost: Int = TDCS_DEFAULT_BOOST,
    val current: Int = 0,
    val amplitude: Int = 0,
    val channel: String = TDCS_DEFAULT_CHANNEL,
    val frequency: Int = TDCS_FREQUENCY,
    val negative: Int = TDCS_NEGATIVE,
    val wave: Int = TDCS_WAVE,
    val waveDataHex: String = TDCS_CONSTANT_WAVE_HEX,
    val lastCommandAt: Long? = null
)

internal fun TdcsConfig.sanitized(): TdcsConfig {
    return copy(
        boost = boost.coerceIn(0, 255),
        current = current.coerceIn(0, 4),
        amplitude = amplitude.coerceIn(0, 255),
        channel = channel.trim().ifBlank { TDCS_DEFAULT_CHANNEL },
        frequency = frequency.coerceAtLeast(1),
        negative = negative.coerceIn(0, 1),
        wave = wave.coerceIn(0, 2),
        waveDataHex = waveDataHex.trim().lowercase().ifBlank { TDCS_CONSTANT_WAVE_HEX }
    )
}

internal fun buildTdcsPayloadPair(config: TdcsConfig, active: Boolean): Pair<String, String> {
    val safe = config.sanitized()
    val first = buildTdcsJson(
        boost = safe.boost,
        current = if (active) safe.current else 0,
        amplitude = if (active) safe.amplitude else 0,
        frequency = safe.frequency,
        negative = safe.negative,
        wave = safe.wave,
        channel = safe.channel,
        action = if (active) 1 else 0
    )
    val second = buildTdcsWaveJson(
        channel = safe.channel,
        wave = safe.wave,
        action = if (active) 1 else 0,
        waveDataHex = if (active) safe.waveDataHex else "00".repeat(64)
    )
    return first to second
}

private fun buildTdcsJson(
    boost: Int,
    current: Int,
    amplitude: Int,
    frequency: Int,
    negative: Int,
    wave: Int,
    channel: String,
    action: Int
): String {
    return JSONObject().apply {
        put("boost", boost)
        put("channel", channel)
        put("current", current)
        put("amplitude", amplitude)
        put("frequency", frequency)
        put("negative", negative)
        put("wave", wave)
        put("action", action)
    }.toString()
}

private fun buildTdcsWaveJson(
    channel: String,
    wave: Int,
    action: Int,
    waveDataHex: String
): String {
    return JSONObject().apply {
        put("channel", channel)
        put("wave", wave)
        put("action", action)
        put("new_wave_data", waveDataHex)
    }.toString()
}

internal fun tdcsConstantWaveHex(): String = TDCS_CONSTANT_WAVE_HEX

internal fun tdcsPositiveHalfSineHex(): String = LEGACY_SINE_WAVE_HEX
