package com.sleepagent.prototype.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Seeds a deterministic demo week for product/UI testing.
 *
 * Data model:
 * - Past 6 nights: completed mock sessions + nightly summaries + 30s epochs + events + payloadJson.
 * - Tonight: a SleepPlanPreference saved to local preferences.
 *
 * This does not touch BLE, SleepRecordingService, staging inference, or database schema.
 */
object DemoWeekDataSeeder {
    private const val DEMO_PREFIX = "demo_week_"
    private const val DEMO_DEVICE_ID = "demo-headband-001"
    private const val DEMO_DEVICE_NAME = "SleepAgent Demo Headband"
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_EPOCH = 30_000L

    suspend fun seedPastWeekAndTonight(
        context: Context,
        force: Boolean = false,
        seedTonightAsCompleted: Boolean = false
    ): DemoSeedResult {
        val appContext = context.applicationContext
        val repository = SleepStorageRepository(appContext)
        val today = LocalDate.now()
        val completedSessionIds = mutableListOf<String>()

        val nightProfiles = listOf(
            NightProfile(
                daysBeforeToday = 6,
                bedtime = "00:20",
                wakeTime = "07:00",
                sleepDurationMin = 380,
                sleepEfficiency = 0.76f,
                sleepOnsetLatencyMin = 36,
                wakeCount = 4,
                deepMin = 52,
                remMin = 65,
                dataQuality = 0.78f,
                movementCount = 28,
                strongMovementCount = 7,
                longestQuietMin = 72,
                avgSpo2 = 95,
                minSpo2 = 91,
                lowSpo2Events = 1,
                avgHrv = 34,
                hrvLabel = "一般"
            ),
            NightProfile(
                daysBeforeToday = 5,
                bedtime = "00:05",
                wakeTime = "07:10",
                sleepDurationMin = 398,
                sleepEfficiency = 0.79f,
                sleepOnsetLatencyMin = 32,
                wakeCount = 3,
                deepMin = 58,
                remMin = 72,
                dataQuality = 0.81f,
                movementCount = 24,
                strongMovementCount = 6,
                longestQuietMin = 86,
                avgSpo2 = 95,
                minSpo2 = 92,
                lowSpo2Events = 1,
                avgHrv = 36,
                hrvLabel = "一般"
            ),
            NightProfile(
                daysBeforeToday = 4,
                bedtime = "23:52",
                wakeTime = "07:15",
                sleepDurationMin = 415,
                sleepEfficiency = 0.81f,
                sleepOnsetLatencyMin = 29,
                wakeCount = 3,
                deepMin = 65,
                remMin = 80,
                dataQuality = 0.83f,
                movementCount = 22,
                strongMovementCount = 5,
                longestQuietMin = 96,
                avgSpo2 = 96,
                minSpo2 = 92,
                lowSpo2Events = 0,
                avgHrv = 38,
                hrvLabel = "一般"
            ),
            NightProfile(
                daysBeforeToday = 3,
                bedtime = "23:43",
                wakeTime = "07:20",
                sleepDurationMin = 423,
                sleepEfficiency = 0.84f,
                sleepOnsetLatencyMin = 25,
                wakeCount = 2,
                deepMin = 72,
                remMin = 85,
                dataQuality = 0.85f,
                movementCount = 19,
                strongMovementCount = 4,
                longestQuietMin = 112,
                avgSpo2 = 96,
                minSpo2 = 93,
                lowSpo2Events = 0,
                avgHrv = 40,
                hrvLabel = "较好"
            ),
            NightProfile(
                daysBeforeToday = 2,
                bedtime = "23:36",
                wakeTime = "07:22",
                sleepDurationMin = 430,
                sleepEfficiency = 0.85f,
                sleepOnsetLatencyMin = 22,
                wakeCount = 2,
                deepMin = 78,
                remMin = 90,
                dataQuality = 0.87f,
                movementCount = 17,
                strongMovementCount = 4,
                longestQuietMin = 126,
                avgSpo2 = 96,
                minSpo2 = 93,
                lowSpo2Events = 0,
                avgHrv = 42,
                hrvLabel = "较好"
            ),
            NightProfile(
                daysBeforeToday = 1,
                bedtime = "23:28",
                wakeTime = "07:18",
                sleepDurationMin = 438,
                sleepEfficiency = 0.88f,
                sleepOnsetLatencyMin = 18,
                wakeCount = 2,
                deepMin = 86,
                remMin = 96,
                dataQuality = 0.90f,
                movementCount = 14,
                strongMovementCount = 3,
                longestQuietMin = 148,
                avgSpo2 = 97,
                minSpo2 = 94,
                lowSpo2Events = 0,
                avgHrv = 45,
                hrvLabel = "较好"
            )
        )

        nightProfiles.forEach { profile ->
            val sessionId = seedCompletedNight(
                repository = repository,
                today = today,
                profile = profile,
                force = force
            )
            if (sessionId != null) completedSessionIds.add(sessionId)
        }

        var tonightSessionId: String? = null
        if (seedTonightAsCompleted) {
            val tonightProfile = nightProfiles.last().copy(
                daysBeforeToday = 0,
                bedtime = "23:25",
                wakeTime = "07:30",
                sleepDurationMin = 455,
                sleepEfficiency = 0.89f,
                sleepOnsetLatencyMin = 16,
                wakeCount = 1,
                deepMin = 92,
                remMin = 102,
                dataQuality = 0.91f,
                movementCount = 12,
                strongMovementCount = 2,
                longestQuietMin = 158,
                avgSpo2 = 97,
                minSpo2 = 94,
                lowSpo2Events = 0,
                avgHrv = 46,
                hrvLabel = "较好"
            )
            tonightSessionId = seedCompletedNight(
                repository = repository,
                today = today,
                profile = tonightProfile,
                force = force
            )
        }

        val tonightPlan = SleepPlanPreference(
            bedtime = "23:25",
            wakeTime = "07:30",
            smartWakeEnabled = true,
            smartWakeStart = "07:00",
            smartWakeEnd = "07:30",
            soundAidEnabled = true,
            soundAidName = "雨声",
            soundDurationMin = 30,
            fadeOutEnabled = true,
            aiCompanionEnabled = true,
            aiCompanionMode = "呼吸放松",
            aiCompanionDurationMin = 5,
            sleepGuardEnabled = true
        )
        SleepLocalPreferences.saveSleepPlan(appContext, tonightPlan)

        return DemoSeedResult(
            completedSessionIds = completedSessionIds,
            tonightPlanId = "${DEMO_PREFIX}plan_${today.format(DateTimeFormatter.BASIC_ISO_DATE)}",
            tonightSessionId = tonightSessionId
        )
    }

    private suspend fun seedCompletedNight(
        repository: SleepStorageRepository,
        today: LocalDate,
        profile: NightProfile,
        force: Boolean
    ): String? {
        val startDate = today.minusDays(profile.daysBeforeToday.toLong())
        val startTime = parseTime(profile.bedtime)
        val wakeDate = if (parseTime(profile.wakeTime).isAfter(startTime)) startDate else startDate.plusDays(1)
        val wakeTime = parseTime(profile.wakeTime)
        val startedAt = toEpochMs(startDate, startTime)
        val endedAt = toEpochMs(wakeDate, wakeTime)
        val sessionId = "$DEMO_PREFIX${startDate.format(DateTimeFormatter.BASIC_ISO_DATE)}"

        if (!force && repository.getSession(sessionId) != null) {
            return null
        }

        val now = System.currentTimeMillis()
        val timeInBedMs = endedAt - startedAt
        val sleepDurationMs = profile.sleepDurationMin * MILLIS_PER_MINUTE
        val deepMs = profile.deepMin * MILLIS_PER_MINUTE
        val remMs = profile.remMin * MILLIS_PER_MINUTE
        val lightMs = max(0L, sleepDurationMs - deepMs - remMs)
        val awakeMs = max(0L, timeInBedMs - sleepDurationMs)
        val wasoMs = max(0L, awakeMs - profile.sleepOnsetLatencyMin * MILLIS_PER_MINUTE)

        val session = SleepSessionRecord(
            sessionId = sessionId,
            sourceType = SleepDataSource.MOCK,
            status = SleepSessionStatus.COMPLETED,
            analysisStatus = SleepAnalysisStatus.COMPLETED,
            analysisVersion = "demo-week-v2",
            lastAnalyzedAtEpochMs = now,
            deviceId = DEMO_DEVICE_ID,
            deviceName = DEMO_DEVICE_NAME,
            deviceAddress = "DE:MO:WE:EK:00:${profile.daysBeforeToday.toString().padStart(2, '0')}",
            startedAtEpochMs = startedAt,
            endedAtEpochMs = endedAt,
            samplingRateHz = 250,
            channelCount = 8,
            rawFormat = "demo",
            rawFilePath = "demo://$sessionId/raw.csv",
            packetCount = max(1L, timeInBedMs / 4L),
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        repository.insertCompleteSession(session)

        val payloadJson = buildPayloadJson(profile, timeInBedMs / MILLIS_PER_MINUTE)
        val summary = SleepNightlySummaryRecord(
            summaryId = "${sessionId}_summary",
            sessionId = sessionId,
            summaryVersion = "demo-week-v2",
            sleepDurationMs = sleepDurationMs,
            sleepEfficiency = profile.sleepEfficiency,
            sleepOnsetLatencyMs = profile.sleepOnsetLatencyMin * MILLIS_PER_MINUTE,
            wakeAfterSleepOnsetMs = wasoMs,
            wakeCount = profile.wakeCount,
            deepSleepMs = deepMs,
            remSleepMs = remMs,
            lightSleepMs = lightMs,
            awakeMs = awakeMs,
            avgSignalQuality = profile.dataQuality,
            dataQualityScore = min(0.98f, profile.dataQuality + 0.03f),
            generatedAtEpochMs = now,
            payloadJson = payloadJson
        )
        repository.upsertNightlySummary(summary)

        val epochs = buildEpochs(
            sessionId = sessionId,
            startedAt = startedAt,
            endedAt = endedAt,
            profile = profile,
            deepEpochTarget = (deepMs / MILLIS_PER_EPOCH).toInt(),
            remEpochTarget = (remMs / MILLIS_PER_EPOCH).toInt()
        )
        repository.upsertEpochs(sessionId, epochs)

        val events = buildWakeEvents(sessionId, epochs, profile.wakeCount, now)
        repository.insertEvents(events)

        val report = SleepAiReportRecord(
            reportId = "${sessionId}_ai_report",
            sessionId = sessionId,
            reportType = SleepReportType.NIGHTLY_EXPLANATION,
            modelName = "DemoWeekMock",
            promptVersion = "demo-v2",
            inputSnapshotId = sessionId,
            summaryText = "这是一条演示睡眠报告数据，用于测试 UI 展示。昨晚总睡眠 ${formatHourMinute(profile.sleepDurationMin)}，睡眠效率 ${(profile.sleepEfficiency * 100).toInt()}%。",
            structuredJson = null,
            confidence = 0.92f,
            createdAtEpochMs = now
        )
        repository.upsertAiReport(
            report = report,
            evidenceLinks = listOf(
                AiEvidenceLinkRecord(
                    reportId = report.reportId,
                    evidenceType = SleepEvidenceType.SESSION,
                    evidenceRefId = sessionId,
                    note = "演示 session"
                ),
                AiEvidenceLinkRecord(
                    reportId = report.reportId,
                    evidenceType = SleepEvidenceType.NIGHTLY_SUMMARY,
                    evidenceRefId = summary.summaryId,
                    note = "演示 nightly summary"
                )
            )
        )

        return sessionId
    }

    private fun buildEpochs(
        sessionId: String,
        startedAt: Long,
        endedAt: Long,
        profile: NightProfile,
        deepEpochTarget: Int,
        remEpochTarget: Int
    ): List<SleepEpochRecord> {
        val totalEpochs = ((endedAt - startedAt) / MILLIS_PER_EPOCH).toInt().coerceAtLeast(1)
        val latencyEpochs = (profile.sleepOnsetLatencyMin * MILLIS_PER_MINUTE / MILLIS_PER_EPOCH).toInt()
            .coerceIn(0, totalEpochs - 1)
        val stages = MutableList(totalEpochs) { SleepStage.LIGHT }

        for (i in 0 until latencyEpochs) {
            stages[i] = SleepStage.AWAKE
        }

        var deepRemaining = deepEpochTarget
        var remRemaining = remEpochTarget
        val sleepStart = latencyEpochs
        val sleepEpochCount = totalEpochs - latencyEpochs

        for (i in sleepStart until totalEpochs) {
            val p = (i - sleepStart).toFloat() / max(1, sleepEpochCount).toFloat()
            val preferred = when {
                p < 0.08f -> SleepStage.LIGHT
                p < 0.24f -> SleepStage.DEEP
                p < 0.34f -> SleepStage.LIGHT
                p < 0.43f -> SleepStage.REM
                p < 0.52f -> SleepStage.DEEP
                p < 0.64f -> SleepStage.LIGHT
                p < 0.76f -> SleepStage.REM
                p < 0.88f -> SleepStage.LIGHT
                else -> SleepStage.REM
            }
            stages[i] = when (preferred) {
                SleepStage.DEEP -> if (deepRemaining-- > 0) SleepStage.DEEP else SleepStage.LIGHT
                SleepStage.REM -> if (remRemaining-- > 0) SleepStage.REM else SleepStage.LIGHT
                else -> SleepStage.LIGHT
            }
        }

        if (deepRemaining > 0) {
            for (i in sleepStart until totalEpochs) {
                if (deepRemaining <= 0) break
                val p = (i - sleepStart).toFloat() / max(1, sleepEpochCount).toFloat()
                if (p < 0.60f && stages[i] == SleepStage.LIGHT) {
                    stages[i] = SleepStage.DEEP
                    deepRemaining--
                }
            }
        }
        if (remRemaining > 0) {
            for (i in (totalEpochs - 1) downTo sleepStart) {
                if (remRemaining <= 0) break
                if (stages[i] == SleepStage.LIGHT) {
                    stages[i] = SleepStage.REM
                    remRemaining--
                }
            }
        }

        val wakeAnchors = listOf(0.32f, 0.56f, 0.74f, 0.88f)
        wakeAnchors.take(profile.wakeCount).forEachIndexed { index, anchor ->
            val center = sleepStart + (sleepEpochCount * anchor).toInt()
            val durationEpochs = if (index == 0) 3 else 2
            for (j in 0 until durationEpochs) {
                val target = (center + j).coerceIn(sleepStart, totalEpochs - 1)
                stages[target] = SleepStage.AWAKE
            }
        }

        return stages.mapIndexed { index, stage ->
            val confidence = when (stage) {
                SleepStage.AWAKE -> 0.86f
                SleepStage.LIGHT -> 0.88f
                SleepStage.DEEP -> 0.91f
                SleepStage.REM -> 0.89f
                SleepStage.UNKNOWN -> 0.65f
            }
            SleepEpochRecord(
                sessionId = sessionId,
                epochIndex = index,
                startAtEpochMs = startedAt + index * MILLIS_PER_EPOCH,
                endAtEpochMs = startedAt + (index + 1) * MILLIS_PER_EPOCH,
                stage = stage,
                confidence = confidence,
                avgSignalQuality = profile.dataQuality,
                source = SleepStageSource.MODEL,
                featuresJson = null
            )
        }
    }

    private fun buildWakeEvents(
        sessionId: String,
        epochs: List<SleepEpochRecord>,
        wakeCount: Int,
        now: Long
    ): List<SleepEventRecord> {
        if (wakeCount <= 0) return emptyList()
        val awakeAfterLatency = epochs
            .dropWhile { it.stage == SleepStage.AWAKE }
            .withIndex()
            .filter { it.value.stage == SleepStage.AWAKE }

        return awakeAfterLatency
            .take(wakeCount)
            .mapIndexed { index, indexed ->
                val epoch = indexed.value
                SleepEventRecord(
                    eventId = "${sessionId}_wake_${index + 1}",
                    sessionId = sessionId,
                    eventType = SleepEventType.AWAKENING,
                    startAtEpochMs = epoch.startAtEpochMs,
                    endAtEpochMs = epoch.endAtEpochMs + 90_000L,
                    severity = 1,
                    confidence = 0.82f,
                    source = SleepEventSource.SCRIPT,
                    payloadJson = "{\"demo\":true,\"label\":\"短暂醒来\"}",
                    createdAtEpochMs = now
                )
            }
    }

    private fun buildPayloadJson(profile: NightProfile, timeInBedMin: Long): String {
        val movementPoints = buildChartPoints(timeInBedMin, 30) { minute ->
            val base = 0.08 + (profile.movementCount / 120.0)
            val spike = if ((minute + profile.daysBeforeToday * 7) % 110 < 12) 0.55 else 0.0
            (base + spike).coerceAtMost(1.0)
        }
        val spo2Points = buildChartPoints(timeInBedMin, 30) { minute ->
            val dip = if ((minute + 40) % 180 < 20) 1.2 else 0.0
            (profile.avgSpo2 - dip + ((minute % 90) / 90.0)).coerceIn(profile.minSpo2.toDouble(), 99.0)
        }
        val hrvPoints = buildChartPoints(timeInBedMin, 30) { minute ->
            val recovery = minute.toDouble() / max(1L, timeInBedMin).toDouble() * 8.0
            profile.avgHrv - 4.0 + recovery + ((minute % 60) / 60.0 * 2.0)
        }
        val below90Minutes = if (profile.lowSpo2Events > 0) 2 else 0

        return """
            {
              "demo": true,
              "movement": {
                "movementCount": ${profile.movementCount},
                "strongMovementCount": ${profile.strongMovementCount},
                "longestQuietMinutes": ${profile.longestQuietMin},
                "highMovementPeriod": "后半夜",
                "points": $movementPoints
              },
              "spo2": {
                "avg": ${profile.avgSpo2},
                "min": ${profile.minSpo2},
                "lowEventCount": ${profile.lowSpo2Events},
                "below90Minutes": $below90Minutes,
                "points": $spo2Points
              },
              "hrv": {
                "avg": ${profile.avgHrv},
                "min": ${max(18, profile.avgHrv - 12)},
                "max": ${profile.avgHrv + 16},
                "recoveryLabel": "${profile.hrvLabel}",
                "points": $hrvPoints
              },
              "health": {
                "darkCircleRisk": ${darkCircleRisk(profile)},
                "skinRecovery": ${skinRecovery(profile)},
                "bodyRecovery": ${bodyRecovery(profile)},
                "brainRecovery": ${brainRecovery(profile)},
                "emotionStability": ${emotionStability(profile)},
                "immuneRecovery": ${immuneRecovery(profile)}
              }
            }
        """.trimIndent()
    }

    private fun buildChartPoints(
        timeInBedMin: Long,
        stepMin: Int,
        valueAt: (Int) -> Double
    ): String {
        val points = mutableListOf<String>()
        var minute = 0
        while (minute <= timeInBedMin) {
            points.add("{\"minute\":$minute,\"value\":${String.format(Locale.US, "%.2f", valueAt(minute))}}")
            minute += stepMin
        }
        return points.joinToString(prefix = "[", postfix = "]")
    }

    private fun darkCircleRisk(profile: NightProfile): Int {
        val sleepPenalty = max(0, 450 - profile.sleepDurationMin) / 7
        val latencyPenalty = max(0, profile.sleepOnsetLatencyMin - 15)
        return (8 + sleepPenalty + latencyPenalty + profile.wakeCount * 3).coerceIn(5, 65)
    }

    private fun skinRecovery(profile: NightProfile): Int =
        (70 + profile.deepMin / 4 + ((profile.sleepEfficiency - 0.75f) * 80).toInt()).coerceIn(60, 96)

    private fun bodyRecovery(profile: NightProfile): Int =
        (68 + profile.deepMin / 3 + ((profile.sleepEfficiency - 0.75f) * 90).toInt()).coerceIn(60, 97)

    private fun brainRecovery(profile: NightProfile): Int =
        (68 + profile.remMin / 3 + (profile.sleepDurationMin - 380) / 8).coerceIn(60, 97)

    private fun emotionStability(profile: NightProfile): Int =
        (72 + ((profile.sleepEfficiency - 0.75f) * 100).toInt() - profile.wakeCount * 3 - max(0, profile.sleepOnsetLatencyMin - 20) / 2).coerceIn(55, 96)

    private fun immuneRecovery(profile: NightProfile): Int =
        (70 + profile.deepMin / 4 + (profile.sleepDurationMin - 380) / 10).coerceIn(60, 96)

    private fun parseTime(value: String): LocalTime = LocalTime.parse(value)

    private fun toEpochMs(date: LocalDate, time: LocalTime): Long =
        LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun formatHourMinute(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}h${minutes.toString().padStart(2, '0')}m"
    }

    private data class NightProfile(
        val daysBeforeToday: Int,
        val bedtime: String,
        val wakeTime: String,
        val sleepDurationMin: Int,
        val sleepEfficiency: Float,
        val sleepOnsetLatencyMin: Int,
        val wakeCount: Int,
        val deepMin: Int,
        val remMin: Int,
        val dataQuality: Float,
        val movementCount: Int,
        val strongMovementCount: Int,
        val longestQuietMin: Int,
        val avgSpo2: Int,
        val minSpo2: Int,
        val lowSpo2Events: Int,
        val avgHrv: Int,
        val hrvLabel: String
    )
}

data class DemoSeedResult(
    val completedSessionIds: List<String>,
    val tonightPlanId: String,
    val tonightSessionId: String? = null
)
