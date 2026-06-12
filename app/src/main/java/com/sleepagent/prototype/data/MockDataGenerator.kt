package com.sleepagent.prototype.data

import java.util.UUID
import kotlin.random.Random

class MockDataGenerator(private val repository: SleepStorageRepository) {

    suspend fun generateLastSevenDays() {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        
        for (i in 6 downTo 0) {
            val startTime = now - (i * dayMs) - (8 * 60 * 60 * 1000L) 
            generateSession(startTime)
        }
    }

    private suspend fun generateSession(startTimeMs: Long) {
        val sessionId = UUID.randomUUID().toString()
        val durationMs = (7 * 60 + Random.nextInt(120)) * 60 * 1000L // 7-9 hours
        val endTimeMs = startTimeMs + durationMs
        val now = System.currentTimeMillis()

        val session = SleepSessionRecord(
            sessionId = sessionId,
            sourceType = SleepDataSource.MOCK,
            status = SleepSessionStatus.COMPLETED,
            analysisStatus = SleepAnalysisStatus.COMPLETED,
            analysisVersion = "v1.0-mock",
            lastAnalyzedAtEpochMs = now,
            deviceId = "mock-device-id",
            deviceName = "Mock Headband Pro",
            deviceAddress = "00:11:22:33:44:55",
            startedAtEpochMs = startTimeMs,
            endedAtEpochMs = endTimeMs,
            samplingRateHz = 250,
            channelCount = 8,
            rawFormat = "csv",
            rawFilePath = "/mock/path/$sessionId.csv",
            packetCount = durationMs / 4,
            createdAtEpochMs = startTimeMs,
            updatedAtEpochMs = now
        )

        repository.insertCompleteSession(session)

        // Generate Summary
        val deepMs = (durationMs * (0.15 + Random.nextDouble(0.1))).toLong()
        val remMs = (durationMs * (0.2 + Random.nextDouble(0.05))).toLong()
        val lightMs = (durationMs * (0.45 + Random.nextDouble(0.1))).toLong()
        val awakeMs = durationMs - deepMs - remMs - lightMs

        val summary = SleepNightlySummaryRecord(
            summaryId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            summaryVersion = "v1.0-mock",
            sleepDurationMs = durationMs,
            sleepEfficiency = 0.85f + Random.nextFloat() * 0.1f,
            sleepOnsetLatencyMs = (10 + Random.nextInt(20)) * 60 * 1000L,
            wakeAfterSleepOnsetMs = awakeMs,
            wakeCount = Random.nextInt(5),
            deepSleepMs = deepMs,
            remSleepMs = remMs,
            lightSleepMs = lightMs,
            awakeMs = awakeMs,
            avgSignalQuality = 0.9f + Random.nextFloat() * 0.1f,
            dataQualityScore = 0.95f,
            generatedAtEpochMs = now,
            payloadJson = null
        )
        repository.upsertNightlySummary(summary)

        // Generate AI Report
        val report = SleepAiReportRecord(
            reportId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            reportType = SleepReportType.NIGHTLY_EXPLANATION,
            modelName = "GPT-4-Mock",
            promptVersion = "v1",
            inputSnapshotId = null,
            summaryText = "昨晚你的睡眠质量非常优秀。深睡比例达到了 ${ (deepMs * 100 / durationMs).toInt() }%，这有助于你的身体恢复和记忆巩固。建议继续保持规律的作息时间。",
            structuredJson = null,
            confidence = 0.98f,
            createdAtEpochMs = now
        )
        repository.upsertAiReport(report)

        // Generate some Epochs (sampled for performance)
        val epochCount = (durationMs / 30000).toInt()
        val epochs = (0 until epochCount).map { i ->
            SleepEpochRecord(
                sessionId = sessionId,
                epochIndex = i,
                startAtEpochMs = startTimeMs + i * 30000L,
                endAtEpochMs = startTimeMs + (i + 1) * 30000L,
                stage = when {
                    i < 10 -> SleepStage.AWAKE
                    i % 15 == 0 -> SleepStage.REM
                    i % 4 == 0 -> SleepStage.DEEP
                    else -> SleepStage.LIGHT
                },
                confidence = 0.9f,
                avgSignalQuality = 0.95f,
                source = SleepStageSource.MODEL
            )
        }
        repository.upsertEpochs(sessionId, epochs)
    }
}
