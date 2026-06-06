package com.sleepagent.prototype.sleep.processing

import com.sleepagent.prototype.device.HeadbandRawPacket
import com.sleepagent.prototype.device.SleepOpticalMode

class SleepSignalPipeline(
    hrvChannel: Int = 0,
    fnirsChannel: Int = 0,
    private val eegWindowSize: Int = DEFAULT_EEG_WINDOW_SIZE,
    private val fnirsWindowSize: Int = DEFAULT_FNIRS_WINDOW_SIZE,
    private val fnirsBaselinePoints: Int = 50,
    private val snapshotIntervalPackets: Int = 1
) {
    private val effectiveSnapshotIntervalPackets = snapshotIntervalPackets.coerceAtLeast(1)
    private var hrvChannel = sanitizeOpticalChannel(hrvChannel)
    private var fnirsChannel = sanitizeOpticalChannel(fnirsChannel)

    private val eegBuffer = ArrayList<Float>(eegWindowSize)
    private val fnirs780Segment = ArrayList<Float>(fnirsWindowSize)
    private val fnirs850Segment = ArrayList<Float>(fnirsWindowSize)
    private val fnirs780Averages = ArrayList<Float>(fnirsWindowSize)
    private val fnirs850Averages = ArrayList<Float>(fnirsWindowSize)
    private val fnirsHboSeries = ArrayList<Float>(fnirsWindowSize)
    private val fnirsHbrSeries = ArrayList<Float>(fnirsWindowSize)
    private val fnirs780RawSeries = ArrayList<Float>(fnirsWindowSize)
    private val fnirs850RawSeries = ArrayList<Float>(fnirsWindowSize)
    private val eegDisplayProcessor = SleepEegDisplayProcessor()
    private val hrvDisplayProcessor = SleepHrvDisplayProcessor()

    private var packetCount = 0L
    private var lastPacketTimestamp: Long? = null
    private var lastMode: SleepOpticalMode = SleepOpticalMode.OFF
    private var lastRawState: Int? = null
    private var lastLedOn: Int? = null
    private var lastModeCode: Int? = null
    private var lastModeName: String = "unknown"
    private var lastActiveModeName: String = "unknown"
    private var lastLegacyState: Boolean = false
    private var lastNormalizedFnirsState: Int? = null
    private var fnirsSegmentState: Int? = null
    private var fnirsHboBaseline: Float? = null
    private var fnirsHbrBaseline: Float? = null
    private var cachedSnapshot = SleepSignalSnapshot()
    private var packetsSinceSnapshot = 0

    fun reset() {
        eegBuffer.clear()
        hrvDisplayProcessor.reset()
        fnirs780Segment.clear()
        fnirs850Segment.clear()
        fnirs780Averages.clear()
        fnirs850Averages.clear()
        fnirsHboSeries.clear()
        fnirsHbrSeries.clear()
        fnirs780RawSeries.clear()
        fnirs850RawSeries.clear()
        packetCount = 0L
        lastPacketTimestamp = null
        lastMode = SleepOpticalMode.OFF
        lastRawState = null
        lastLedOn = null
        lastModeCode = null
        lastModeName = "unknown"
        lastActiveModeName = "unknown"
        lastLegacyState = false
        lastNormalizedFnirsState = null
        fnirsSegmentState = null
        fnirsHboBaseline = null
        fnirsHbrBaseline = null
        cachedSnapshot = SleepSignalSnapshot()
        packetsSinceSnapshot = 0
    }

    fun updateChannelSelection(
        hrvChannel: Int = this.hrvChannel,
        fnirsChannel: Int = this.fnirsChannel
    ) {
        val safeHrvChannel = sanitizeOpticalChannel(hrvChannel)
        val safeFnirsChannel = sanitizeOpticalChannel(fnirsChannel)
        if (this.hrvChannel != safeHrvChannel) {
            this.hrvChannel = safeHrvChannel
            hrvDisplayProcessor.reset()
            packetsSinceSnapshot = effectiveSnapshotIntervalPackets
        }
        if (this.fnirsChannel != safeFnirsChannel) {
            this.fnirsChannel = safeFnirsChannel
            clearFnirsBuffers()
            packetsSinceSnapshot = effectiveSnapshotIntervalPackets
        }
    }

    fun handleOpticalModeChange(mode: SleepOpticalMode) {
        if (mode == lastMode) return
        when (mode) {
            SleepOpticalMode.HRV -> {
                hrvDisplayProcessor.reset()
            }
            SleepOpticalMode.FNIRS -> {
                clearFnirsBuffers()
            }
            SleepOpticalMode.OFF -> {
                hrvDisplayProcessor.reset()
                clearFnirsBuffers()
            }
        }
        lastMode = mode
        packetsSinceSnapshot = effectiveSnapshotIntervalPackets
    }

    fun ingest(
        packet: HeadbandRawPacket,
        opticalMode: SleepOpticalMode,
        eegSample: DownsampledEegSample?
    ): SleepSignalSnapshot {
        packetCount += 1
        packetsSinceSnapshot += 1
        lastPacketTimestamp = packet.hostTimestamp
        lastMode = opticalMode

        val rawState = packet.state
        val decoded = decodeOpticalState(
            rawState = rawState,
            fallbackMode = opticalMode.wireValue
        )
        lastRawState = rawState
        lastLedOn = decoded.ledOn
        lastModeCode = decoded.modeCode
        lastModeName = decoded.modeName
        lastActiveModeName = decoded.activeModeName
        lastLegacyState = decoded.legacyState
        lastNormalizedFnirsState = decoded.normalizedFnirsState

        val eegValue = eegSample?.valueMicrovolts
        if (eegValue != null) {
            eegBuffer.appendCapped(eegValue, eegWindowSize)
        }

        val hrvValue = packet.eegCounts.getOrNull(hrvChannel)?.let(::countsToIntensity)
        if (hrvValue != null) {
            hrvDisplayProcessor.appendSample(packet.hostTimestamp, hrvValue)
        }

        val fnirsValue = packet.eegCounts.getOrNull(fnirsChannel)?.let(::countsToIntensity)
        if (fnirsValue != null) {
            processFnirsSample(
                rawState = rawState,
                normalizedState = decoded.normalizedFnirsState,
                intensity = fnirsValue
            )
        }

        val shouldRecomputeSnapshot = packetsSinceSnapshot >= effectiveSnapshotIntervalPackets ||
            cachedSnapshot.packetCount == 0L ||
            opticalMode.wireValue != cachedSnapshot.opticalMode

        if (shouldRecomputeSnapshot) {
            val eegSnapshot = buildEegSnapshot(packet)
            val hrvSnapshot = buildHrvSnapshot(packet, opticalMode)
            val fnirsSnapshot = buildFnirsSnapshot(packet, opticalMode)
            cachedSnapshot = buildSignalSnapshot(
                opticalMode = opticalMode,
                rawState = rawState,
                decoded = decoded,
                eegSnapshot = eegSnapshot,
                hrvSnapshot = hrvSnapshot,
                fnirsSnapshot = fnirsSnapshot
            )
            packetsSinceSnapshot = 0
            return cachedSnapshot
        }

        cachedSnapshot = cachedSnapshot.copy(
            packetCount = packetCount,
            lastPacketTimestamp = lastPacketTimestamp,
            opticalMode = opticalMode.wireValue,
            opticalState = SleepOpticalStateSnapshot(
                rawState = rawState,
                ledOn = decoded.ledOn,
                modeCode = decoded.modeCode,
                modeName = decoded.modeName,
                activeModeName = decoded.activeModeName,
                legacyState = decoded.legacyState,
                normalizedFnirsState = decoded.normalizedFnirsState
            )
        )
        return cachedSnapshot
    }

    private fun buildSignalSnapshot(
        opticalMode: SleepOpticalMode,
        rawState: Int?,
        decoded: OpticalStateDecoded,
        eegSnapshot: SleepEegSnapshot,
        hrvSnapshot: SleepHrvSnapshot,
        fnirsSnapshot: SleepFnirsSnapshot
    ): SleepSignalSnapshot {
        return SleepSignalSnapshot(
            packetCount = packetCount,
            lastPacketTimestamp = lastPacketTimestamp,
            opticalMode = opticalMode.wireValue,
            opticalState = SleepOpticalStateSnapshot(
                rawState = rawState,
                ledOn = decoded.ledOn,
                modeCode = decoded.modeCode,
                modeName = decoded.modeName,
                activeModeName = decoded.activeModeName,
                legacyState = decoded.legacyState,
                normalizedFnirsState = decoded.normalizedFnirsState
            ),
            eeg = eegSnapshot,
            hrv = hrvSnapshot,
            fnirs = fnirsSnapshot
        )
    }

    private fun buildEegSnapshot(packet: HeadbandRawPacket): SleepEegSnapshot {
        val series = eegDisplayProcessor.process(eegBuffer)
        val selectedValue = series.lastOrNull() ?: eegBuffer.lastOrNull()
        return SleepEegSnapshot(
            selectedChannel = DEFAULT_EEG_CHANNEL,
            selectedLabel = channelLabel(DEFAULT_EEG_CHANNEL),
            sampleRateHz = eegDisplayProcessor.outputSampleRateHz,
            status = if (series.size < 3) "warming_up" else "ready",
            selectedValue = selectedValue,
            signalQuality = packet.signalQuality,
            series = series
        )
    }

    private fun buildHrvSnapshot(packet: HeadbandRawPacket, opticalMode: SleepOpticalMode): SleepHrvSnapshot {
        val selectedValue = packet.eegCounts.getOrNull(hrvChannel)?.let(::countsToIntensity)
        return hrvDisplayProcessor.buildSnapshot(
            selectedChannel = hrvChannel,
            selectedLabel = channelLabel(hrvChannel),
            opticalMode = opticalMode,
            selectedValue = selectedValue,
            signalQuality = packet.signalQuality
        )
    }

    private fun buildFnirsSnapshot(
        packet: HeadbandRawPacket,
        opticalMode: SleepOpticalMode
    ): SleepFnirsSnapshot {
        val selectedValue = packet.eegCounts.getOrNull(fnirsChannel)?.let(::countsToIntensity)
        if (opticalMode != SleepOpticalMode.FNIRS) {
            return SleepFnirsSnapshot(
                selectedChannel = fnirsChannel,
                selectedLabel = channelLabel(fnirsChannel),
                sampleRateHz = 250,
                opticalMode = opticalMode.wireValue,
                status = "inactive",
                rawState = lastRawState,
                ledOn = lastLedOn,
                modeCode = lastModeCode,
                modeName = lastModeName,
                activeModeName = lastActiveModeName,
                legacyState = lastLegacyState,
                lastState = lastNormalizedFnirsState,
                selectedIntensity780 = fnirs780RawSeries.lastOrNull(),
                selectedIntensity850 = fnirs850RawSeries.lastOrNull(),
                hbo = fnirsHboSeries.lastOrNull(),
                hbr = fnirsHbrSeries.lastOrNull(),
                hboSeries = fnirsHboSeries.toList(),
                hbrSeries = fnirsHbrSeries.toList(),
                series = fnirsHboSeries.toList()
            )
        }

        val status = when {
            fnirs780Averages.size < fnirsBaselinePoints || fnirs850Averages.size < fnirsBaselinePoints -> "warming_up"
            else -> "ready"
        }

        return SleepFnirsSnapshot(
            selectedChannel = fnirsChannel,
            selectedLabel = channelLabel(fnirsChannel),
            sampleRateHz = 250,
            opticalMode = opticalMode.wireValue,
            status = status,
            rawState = lastRawState,
            ledOn = lastLedOn,
            modeCode = lastModeCode,
            modeName = lastModeName,
            activeModeName = lastActiveModeName,
            legacyState = lastLegacyState,
            lastState = lastNormalizedFnirsState,
            selectedIntensity780 = fnirs780RawSeries.lastOrNull(),
            selectedIntensity850 = fnirs850RawSeries.lastOrNull(),
            hbo = fnirsHboSeries.lastOrNull(),
            hbr = fnirsHbrSeries.lastOrNull(),
            hboSeries = fnirsHboSeries.toList(),
            hbrSeries = fnirsHbrSeries.toList(),
            series = fnirsHboSeries.toList()
        )
    }

    private fun processFnirsSample(rawState: Int?, normalizedState: Int?, intensity: Float) {
        val normalized = normalizeFnirsState(
            rawState = rawState,
            normalizedState = normalizedState,
            activeModeName = lastActiveModeName
        )
        if (normalized == null) return

        if (fnirsSegmentState == null) {
            fnirsSegmentState = normalized
        } else if (normalized != fnirsSegmentState) {
            flushFnirsSegment(fnirsSegmentState)
            if (fnirsSegmentState == 2) {
                updateHboHbr()
            }
            fnirsSegmentState = normalized
        }

        currentFnirsSegment(normalized).add(intensity)
        if (normalized == 1) {
            fnirs780RawSeries.appendCapped(intensity, fnirsWindowSize)
        } else {
            fnirs850RawSeries.appendCapped(intensity, fnirsWindowSize)
        }
    }

    private fun flushFnirsSegment(state: Int?) {
        when (state) {
            1 -> {
                val mean = middleThirdMean(fnirs780Segment)
                if (mean != null) {
                    fnirs780Averages.appendCapped(mean, fnirsWindowSize)
                }
                fnirs780Segment.clear()
            }
            2 -> {
                val mean = middleThirdMean(fnirs850Segment)
                if (mean != null) {
                    fnirs850Averages.appendCapped(mean, fnirsWindowSize)
                }
                fnirs850Segment.clear()
            }
        }
    }

    private fun updateHboHbr() {
        val size = minOf(fnirs780Averages.size, fnirs850Averages.size)
        if (size < fnirsBaselinePoints) return

        val i780 = fnirs780Averages.takeLast(size).map { it.toDouble().coerceAtLeast(Double.MIN_VALUE) }
        val i850 = fnirs850Averages.takeLast(size).map { it.toDouble().coerceAtLeast(Double.MIN_VALUE) }
        val base780 = i780.take(fnirsBaselinePoints).average().coerceAtLeast(Double.MIN_VALUE)
        val base850 = i850.take(fnirsBaselinePoints).average().coerceAtLeast(Double.MIN_VALUE)
        val att780 = i780.map { -kotlin.math.log10(it / base780) }
        val att850 = i850.map { -kotlin.math.log10(it / base850) }
        val hbo = smoothFnirsValue(att850.lastOrNull()?.toFloat(), isHbo = true)
        val hbr = smoothFnirsValue(att780.lastOrNull()?.toFloat(), isHbo = false)
        if (hbo != null) {
            fnirsHboSeries.appendCapped(hbo, fnirsWindowSize)
        }
        if (hbr != null) {
            fnirsHbrSeries.appendCapped(hbr, fnirsWindowSize)
        }
    }

    private fun currentFnirsSegment(state: Int): MutableList<Float> {
        return if (state == 1) fnirs780Segment else fnirs850Segment
    }

    private fun clearFnirsBuffers() {
        fnirs780Segment.clear()
        fnirs850Segment.clear()
        fnirs780Averages.clear()
        fnirs850Averages.clear()
        fnirsHboSeries.clear()
        fnirsHbrSeries.clear()
        fnirs780RawSeries.clear()
        fnirs850RawSeries.clear()
        fnirsHboBaseline = null
        fnirsHbrBaseline = null
        lastRawState = null
        lastLedOn = null
        lastModeCode = null
        lastModeName = "unknown"
        lastActiveModeName = "unknown"
        lastLegacyState = false
        lastNormalizedFnirsState = null
        fnirsSegmentState = null
    }

    private fun smoothFnirsValue(value: Float?, isHbo: Boolean): Float? {
        if (value == null) return null
        val baseline = if (isHbo) fnirsHboBaseline else fnirsHbrBaseline
        val next = if (baseline == null) value else baseline * 0.82f + value * 0.18f
        if (isHbo) {
            fnirsHboBaseline = next
        } else {
            fnirsHbrBaseline = next
        }
        return next
    }

    private fun normalizeFnirsState(
        rawState: Int?,
        normalizedState: Int?,
        activeModeName: String
    ): Int? {
        if (activeModeName != "fnirs") return null
        val state = normalizedState ?: rawState
        return when (state) {
            1 -> 1
            2 -> 2
            3 -> 1
            else -> null
        }
    }

    private fun decodeOpticalState(rawState: Int?, fallbackMode: String): OpticalStateDecoded {
        if (rawState == null) return OpticalStateDecoded()
        val ledOn = rawState and 0x1
        val modeCode = rawState shr 1
        val legacyState = rawState == 0 || rawState == 1
        val modeName = if (legacyState) {
            "legacy-state"
        } else {
            when (modeCode) {
                0 -> "off"
                1 -> "fnirs"
                2 -> "ppd"
                else -> "unknown"
            }
        }
        val activeModeName = if (legacyState) {
            fallbackMode
        } else {
            modeName
        }
        return OpticalStateDecoded(
            rawState = rawState,
            ledOn = ledOn,
            modeCode = modeCode,
            modeName = modeName,
            activeModeName = activeModeName,
            legacyState = legacyState,
            normalizedFnirsState = normalizeFnirsState(
                rawState = rawState,
                normalizedState = rawState,
                activeModeName = activeModeName
            )
        )
    }

    private fun channelLabel(channel: Int): String {
        return when (channel) {
            else -> "CH${channel + 1}"
        }
    }

    private fun sanitizeOpticalChannel(channel: Int): Int {
        return if (channel in OPTICAL_CHANNELS) channel else DEFAULT_OPTICAL_CHANNEL
    }

    private fun countsToIntensity(counts: Int): Float {
        return counts.toFloat() + (1 shl 23)
    }

    private fun MutableList<Float>.appendCapped(value: Float, maxSize: Int) {
        add(value)
        while (size > maxSize) {
            removeAt(0)
        }
    }

    private fun middleThirdMean(values: List<Float>): Float? {
        val size = values.size
        if (size < 3) return null
        val start = size / 3
        val end = (2 * size) / 3
        if (end <= start) return null
        return values.subList(start, end).average().toFloat()
    }

    private data class OpticalStateDecoded(
        val rawState: Int? = null,
        val ledOn: Int? = null,
        val modeCode: Int? = null,
        val modeName: String = "unknown",
        val activeModeName: String = "unknown",
        val legacyState: Boolean = false,
        val normalizedFnirsState: Int? = null
    )

    companion object {
        private val OPTICAL_CHANNELS = setOf(0, 1)
        private const val DEFAULT_EEG_CHANNEL = 5
        private const val DEFAULT_OPTICAL_CHANNEL = 0
        private const val DEFAULT_EEG_SAMPLE_RATE_HZ = 100
        private const val DEFAULT_EEG_DISPLAY_SECONDS = 5
        private const val DEFAULT_EEG_WINDOW_SIZE = DEFAULT_EEG_SAMPLE_RATE_HZ * DEFAULT_EEG_DISPLAY_SECONDS
        private const val DEFAULT_FNIRS_WINDOW_SIZE = 208
    }
}
