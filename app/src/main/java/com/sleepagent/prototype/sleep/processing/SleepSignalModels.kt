package com.sleepagent.prototype.sleep.processing

data class SleepSignalSnapshot(
    val packetCount: Long = 0L,
    val lastPacketTimestamp: Long? = null,
    val opticalMode: String = "off",
    val opticalState: SleepOpticalStateSnapshot = SleepOpticalStateSnapshot(),
    val eeg: SleepEegSnapshot = SleepEegSnapshot(),
    val hrv: SleepHrvSnapshot = SleepHrvSnapshot(),
    val fnirs: SleepFnirsSnapshot = SleepFnirsSnapshot()
)

data class SleepOpticalStateSnapshot(
    val rawState: Int? = null,
    val ledOn: Int? = null,
    val modeCode: Int? = null,
    val modeName: String = "unknown",
    val activeModeName: String = "unknown",
    val legacyState: Boolean = false,
    val normalizedFnirsState: Int? = null
)

data class SleepEegSnapshot(
    val selectedChannel: Int = 5,
    val selectedLabel: String = "CH6",
    val sampleRateHz: Int = 100,
    val status: String = "waiting_for_samples",
    val selectedValue: Float? = null,
    val signalQuality: Float? = null,
    val rawSeries: List<Float> = emptyList(),
    val series: List<Float> = emptyList()
)

data class SleepHrvSnapshot(
    val selectedChannel: Int = 0,
    val selectedLabel: String = "CH1",
    val sampleRateHz: Int = 250,
    val opticalMode: String = "off",
    val status: String = "inactive",
    val selectedValue: Float? = null,
    val series: List<Float> = emptyList(),
    val rawSeries: List<Float> = emptyList(),
    val filteredSeries: List<Float> = emptyList(),
    val peakSeries: List<Float> = emptyList(),
    val ibiSeries: List<Float> = emptyList(),
    val heartRateBpm: Float? = null,
    val ibiMs: Float? = null,
    val rmssdMs: Float? = null,
    val sdnnMs: Float? = null,
    val pnn50Percent: Float? = null,
    val signalQuality: Float? = null
)

data class SleepFnirsSnapshot(
    val selectedChannel: Int = 0,
    val selectedLabel: String = "CH1",
    val sampleRateHz: Int = 250,
    val opticalMode: String = "off",
    val status: String = "inactive",
    val rawState: Int? = null,
    val ledOn: Int? = null,
    val modeCode: Int? = null,
    val modeName: String = "unknown",
    val activeModeName: String = "unknown",
    val legacyState: Boolean = false,
    val lastState: Int? = null,
    val selectedIntensity780: Float? = null,
    val selectedIntensity850: Float? = null,
    val hbo: Float? = null,
    val hbr: Float? = null,
    val hboSeries: List<Float> = emptyList(),
    val hbrSeries: List<Float> = emptyList(),
    val series: List<Float> = emptyList()
)
