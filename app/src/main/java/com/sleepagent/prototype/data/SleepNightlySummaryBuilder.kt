package com.sleepagent.prototype.data

import org.json.JSONObject
import java.util.UUID

object SleepNightlySummaryBuilder {
    private const val VERSION = "local_summary_v1"
    private const val EPOCH_MS = 30_000L

    fun build(
        session: SleepSessionRecord,
        epochs: List<SleepEpochRecord>
    ): SleepNightlySummaryRecord? {
        if (epochs.isEmpty()) return null

        val startedAt = session.startedAtEpochMs
        val endedAt = session.endedAtEpochMs ?: epochs.maxOf { it.endAtEpochMs }
        val totalDurationMs = (endedAt - startedAt).coerceAtLeast(EPOCH_MS)

        val awakeMs = epochs.count { it.stage == SleepStage.AWAKE } * EPOCH_MS
        val lightMs = epochs.count { it.stage == SleepStage.LIGHT } * EPOCH_MS
        val deepMs = epochs.count { it.stage == SleepStage.DEEP } * EPOCH_MS
        val remMs = epochs.count { it.stage == SleepStage.REM } * EPOCH_MS

        val sleepDurationMs = lightMs + deepMs + remMs
        val sleepEfficiency = sleepDurationMs.toFloat() / totalDurationMs.toFloat()

        val firstSleepEpoch = epochs.firstOrNull {
            it.stage == SleepStage.LIGHT || it.stage == SleepStage.DEEP || it.stage == SleepStage.REM
        }

        val sleepOnsetLatencyMs = firstSleepEpoch?.let {
            (it.startAtEpochMs - startedAt).coerceAtLeast(0L)
        }

        val epochsAfterSleepOnset = firstSleepEpoch?.let { first ->
            epochs.filter { it.epochIndex >= first.epochIndex }
        }.orEmpty()

        val wakeAfterSleepOnsetMs = epochsAfterSleepOnset.count {
            it.stage == SleepStage.AWAKE
        } * EPOCH_MS

        val wakeCount = epochsAfterSleepOnset
            .zipWithNext()
            .count { (prev, next) ->
                prev.stage != SleepStage.AWAKE && next.stage == SleepStage.AWAKE
            }

        val avgSignalQuality = epochs
            .mapNotNull { it.avgSignalQuality }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()

        val dataQualityScore = when {
            avgSignalQuality != null -> avgSignalQuality.coerceIn(0f, 1f)
            else -> (epochs.size * EPOCH_MS).toFloat()
                .div(totalDurationMs.toFloat())
                .coerceIn(0f, 1f)
        }

        val now = System.currentTimeMillis()

        val payload = JSONObject().apply {
            put("epoch_count", epochs.size)
            put("total_duration_ms", totalDurationMs)
            put("unknown_epoch_count", epochs.count { it.stage == SleepStage.UNKNOWN })
        }

        return SleepNightlySummaryRecord(
            summaryId = UUID.randomUUID().toString(),
            sessionId = session.sessionId,
            summaryVersion = VERSION,
            sleepDurationMs = sleepDurationMs,
            sleepEfficiency = sleepEfficiency,
            sleepOnsetLatencyMs = sleepOnsetLatencyMs,
            wakeAfterSleepOnsetMs = wakeAfterSleepOnsetMs,
            wakeCount = wakeCount,
            deepSleepMs = deepMs,
            remSleepMs = remMs,
            lightSleepMs = lightMs,
            awakeMs = awakeMs,
            avgSignalQuality = avgSignalQuality,
            dataQualityScore = dataQualityScore,
            generatedAtEpochMs = now,
            payloadJson = payload.toString()
        )
    }
}
