package com.sleepagent.prototype.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.sleepagent.prototype.device.HeadbandDevice
import com.sleepagent.prototype.device.HeadbandRawPacket
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SleepStorageRepository(
    context: Context,
    private val csvWriter: SleepSessionCsvWriter = SleepSessionCsvWriter(context.applicationContext),
    private val sessionExportWriter: SleepSessionExportWriter = SleepSessionExportWriter(context.applicationContext),
    private val databaseHelper: SleepStorageDatabaseHelper = SleepStorageDatabaseHelper(context.applicationContext),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun startSession(
        device: HeadbandDevice?,
        sourceType: SleepDataSource,
        samplingRateHz: Int = DEFAULT_SAMPLING_RATE_HZ,
        channelCount: Int = DEFAULT_CHANNEL_COUNT
    ): ActiveSleepSession {
        val startedAt = System.currentTimeMillis()
        val csvSession = csvWriter.createSession()
        val now = System.currentTimeMillis()
        val sessionRecord = SleepSessionRecord(
            sessionId = csvSession.sessionId,
            sourceType = sourceType,
            status = SleepSessionStatus.RECORDING,
            analysisStatus = SleepAnalysisStatus.NONE,
            analysisVersion = null,
            lastAnalyzedAtEpochMs = null,
            deviceId = device?.deviceId,
            deviceName = device?.name,
            deviceAddress = device?.address,
            startedAtEpochMs = startedAt,
            endedAtEpochMs = null,
            samplingRateHz = samplingRateHz,
            channelCount = channelCount,
            rawFormat = RAW_FORMAT_CSV,
            rawFilePath = csvSession.csvFile.absolutePath,
            packetCount = 0L,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )

        return withContext(ioDispatcher) {
            runCatching {
                databaseHelper.writableDatabase.insertOrThrow(
                    SleepStorageDatabaseHelper.TABLE_SLEEP_SESSION,
                    null,
                    sessionRecord.toContentValues()
                )
                ActiveSleepSession(
                    sessionId = sessionRecord.sessionId,
                    rawFilePath = sessionRecord.rawFilePath,
                    startedAtEpochMs = startedAt,
                    sourceType = sourceType,
                    csvHandle = csvSession
                )
            }.getOrElse { error ->
                runCatching { csvSession.close() }
                throw error
            }
        }
    }

    suspend fun appendPacket(
        activeSession: ActiveSleepSession,
        packet: HeadbandRawPacket
    ) {
        activeSession.csvHandle.append(packet)
        activeSession.packetCount += 1
    }

    suspend fun exportSessionBundle(
        session: SleepSessionRecord
    ): SleepSessionExportResult {
        return sessionExportWriter.exportSessionBundle(session)
    }

    suspend fun finishSession(
        activeSession: ActiveSleepSession,
        status: SleepSessionStatus = SleepSessionStatus.COMPLETED
    ): SleepSessionRecord {
        return withContext(ioDispatcher) {
            activeSession.csvHandle.close()
            requireNotNull(
                updateSessionState(
                    sessionId = activeSession.sessionId,
                    status = status,
                    analysisStatus = if (status == SleepSessionStatus.COMPLETED) {
                        SleepAnalysisStatus.PENDING
                    } else {
                        SleepAnalysisStatus.NONE
                    },
                    packetCount = activeSession.packetCount,
                    analysisVersion = null,
                    lastAnalyzedAtEpochMs = null
                )
            ) {
                "Sleep session ${activeSession.sessionId} was not found after finalization"
            }
        }
    }

    suspend fun markAnalysisState(
        sessionId: String,
        analysisStatus: SleepAnalysisStatus,
        analysisVersion: String? = null,
        lastAnalyzedAtEpochMs: Long? = null
    ): SleepSessionRecord? {
        return withContext(ioDispatcher) {
            updateSessionState(
                sessionId = sessionId,
                status = null,
                analysisStatus = analysisStatus,
                packetCount = null,
                analysisVersion = analysisVersion,
                lastAnalyzedAtEpochMs = lastAnalyzedAtEpochMs
            )
        }
    }

    suspend fun upsertEpochs(
        sessionId: String,
        epochs: List<SleepEpochRecord>
    ) {
        if (epochs.isEmpty()) return

        withContext(ioDispatcher) {
            val database = databaseHelper.writableDatabase
            database.beginTransaction()
            try {
                epochs.forEach { epoch ->
                    database.insertWithOnConflict(
                        SleepStorageDatabaseHelper.TABLE_SLEEP_EPOCH,
                        null,
                        epoch.copy(sessionId = sessionId).toContentValues(),
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    suspend fun listEpochs(sessionId: String): List<SleepEpochRecord> {
        return withContext(ioDispatcher) {
            databaseHelper.readableDatabase.query(
                SleepStorageDatabaseHelper.TABLE_SLEEP_EPOCH,
                null,
                "${SleepStorageDatabaseHelper.COLUMN_SESSION_ID} = ?",
                arrayOf(sessionId),
                null,
                null,
                "${SleepStorageDatabaseHelper.COLUMN_EPOCH_INDEX} ASC"
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toSleepEpochRecord())
                    }
                }
            }
        }
    }

    suspend fun upsertFeatureWindows(
        sessionId: String,
        windows: List<SleepFeatureWindowRecord>
    ) {
        if (windows.isEmpty()) return

        withContext(ioDispatcher) {
            val database = databaseHelper.writableDatabase
            database.beginTransaction()
            try {
                windows.forEach { window ->
                    database.insertWithOnConflict(
                        TABLE_SLEEP_FEATURE_WINDOW,
                        null,
                        window.copy(sessionId = sessionId).toContentValues(),
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    suspend fun upsertNightlySummary(summary: SleepNightlySummaryRecord) {
        withContext(ioDispatcher) {
            databaseHelper.writableDatabase.insertWithOnConflict(
                TABLE_SLEEP_NIGHTLY_SUMMARY,
                null,
                summary.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    suspend fun getNightlySummary(sessionId: String): SleepNightlySummaryRecord? {
        return withContext(ioDispatcher) {
            databaseHelper.readableDatabase.query(
                TABLE_SLEEP_NIGHTLY_SUMMARY,
                null,
                "${SleepStorageDatabaseHelper.COLUMN_SESSION_ID} = ?",
                arrayOf(sessionId),
                null,
                null,
                null
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.toSleepNightlySummaryRecord()
            }
        }
    }

    suspend fun insertEvents(events: List<SleepEventRecord>) {
        if (events.isEmpty()) return

        withContext(ioDispatcher) {
            val database = databaseHelper.writableDatabase
            database.beginTransaction()
            try {
                events.forEach { event ->
                    database.insertWithOnConflict(
                        TABLE_SLEEP_EVENT,
                        null,
                        event.toContentValues(),
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    suspend fun upsertAiReport(
        report: SleepAiReportRecord,
        evidenceLinks: List<AiEvidenceLinkRecord> = emptyList()
    ) {
        withContext(ioDispatcher) {
            val database = databaseHelper.writableDatabase
            database.beginTransaction()
            try {
                database.insertWithOnConflict(
                    TABLE_SLEEP_AI_REPORT,
                    null,
                    report.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                evidenceLinks.forEach { link ->
                    database.insertWithOnConflict(
                        TABLE_AI_EVIDENCE_LINK,
                        null,
                        link.copy(reportId = report.reportId).toContentValues(),
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    suspend fun getLatestAiReport(sessionId: String): SleepAiReportRecord? {
        return withContext(ioDispatcher) {
            databaseHelper.readableDatabase.query(
                TABLE_SLEEP_AI_REPORT,
                null,
                "${SleepStorageDatabaseHelper.COLUMN_SESSION_ID} = ?",
                arrayOf(sessionId),
                null,
                null,
                "${SleepStorageDatabaseHelper.COLUMN_CREATED_AT_EPOCH_MS} DESC",
                "1"
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.toSleepAiReportRecord()
            }
        }
    }

    suspend fun upsertAnalysisJob(job: AnalysisJobRecord) {
        withContext(ioDispatcher) {
            databaseHelper.writableDatabase.insertWithOnConflict(
                TABLE_SLEEP_ANALYSIS_JOB,
                null,
                job.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )
            markAnalysisState(
                sessionId = job.sessionId,
                analysisStatus = when (job.status) {
                    AnalysisJobStatus.PENDING -> SleepAnalysisStatus.PENDING
                    AnalysisJobStatus.RUNNING -> SleepAnalysisStatus.PROCESSING
                    AnalysisJobStatus.SUCCESS -> SleepAnalysisStatus.COMPLETED
                    AnalysisJobStatus.FAILED -> SleepAnalysisStatus.FAILED
                    AnalysisJobStatus.CANCELED -> SleepAnalysisStatus.PARTIAL
                },
                analysisVersion = job.algorithmVersion,
                lastAnalyzedAtEpochMs = job.finishedAtEpochMs
            )
        }
    }

    suspend fun getSession(sessionId: String): SleepSessionRecord? {
        return withContext(ioDispatcher) {
            databaseHelper.readableDatabase.query(
                SleepStorageDatabaseHelper.TABLE_SLEEP_SESSION,
                null,
                "${SleepStorageDatabaseHelper.COLUMN_SESSION_ID} = ?",
                arrayOf(sessionId),
                null,
                null,
                null
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.toSleepSessionRecord()
            }
        }
    }

    suspend fun listRecentSessions(limit: Int = 20): List<SleepSessionRecord> {
        return withContext(ioDispatcher) {
            databaseHelper.readableDatabase.query(
                SleepStorageDatabaseHelper.TABLE_SLEEP_SESSION,
                null,
                null,
                null,
                null,
                null,
                "${SleepStorageDatabaseHelper.COLUMN_STARTED_AT_EPOCH_MS} DESC",
                limit.toString()
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toSleepSessionRecord())
                    }
                }
            }
        }
    }

    private suspend fun updateSessionState(
        sessionId: String,
        status: SleepSessionStatus?,
        analysisStatus: SleepAnalysisStatus,
        packetCount: Long?,
        analysisVersion: String?,
        lastAnalyzedAtEpochMs: Long?
    ): SleepSessionRecord? {
        val endedAt = if (status != null) System.currentTimeMillis() else null
        val updatedAt = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(SleepStorageDatabaseHelper.COLUMN_ANALYSIS_STATUS, analysisStatus.name)
            put(SleepStorageDatabaseHelper.COLUMN_ANALYSIS_VERSION, analysisVersion)
            put(SleepStorageDatabaseHelper.COLUMN_LAST_ANALYZED_AT_EPOCH_MS, lastAnalyzedAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_UPDATED_AT_EPOCH_MS, updatedAt)
            if (status != null) {
                put(SleepStorageDatabaseHelper.COLUMN_STATUS, status.name)
                put(SleepStorageDatabaseHelper.COLUMN_ENDED_AT_EPOCH_MS, endedAt)
            }
            if (packetCount != null) {
                put(SleepStorageDatabaseHelper.COLUMN_PACKET_COUNT, packetCount)
            }
        }

        databaseHelper.writableDatabase.update(
            SleepStorageDatabaseHelper.TABLE_SLEEP_SESSION,
            values,
            "${SleepStorageDatabaseHelper.COLUMN_SESSION_ID} = ?",
            arrayOf(sessionId)
        )
        return getSession(sessionId)
    }

    private fun SleepSessionRecord.toContentValues(): ContentValues {
        return ContentValues().apply {
            put(SleepStorageDatabaseHelper.COLUMN_SESSION_ID, sessionId)
            put(SleepStorageDatabaseHelper.COLUMN_SOURCE_TYPE, sourceType.name)
            put(SleepStorageDatabaseHelper.COLUMN_STATUS, status.name)
            put(SleepStorageDatabaseHelper.COLUMN_ANALYSIS_STATUS, analysisStatus.name)
            put(SleepStorageDatabaseHelper.COLUMN_ANALYSIS_VERSION, analysisVersion)
            put(SleepStorageDatabaseHelper.COLUMN_LAST_ANALYZED_AT_EPOCH_MS, lastAnalyzedAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_DEVICE_ID, deviceId)
            put(SleepStorageDatabaseHelper.COLUMN_DEVICE_NAME, deviceName)
            put(SleepStorageDatabaseHelper.COLUMN_DEVICE_ADDRESS, deviceAddress)
            put(SleepStorageDatabaseHelper.COLUMN_STARTED_AT_EPOCH_MS, startedAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_ENDED_AT_EPOCH_MS, endedAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_SAMPLING_RATE_HZ, samplingRateHz)
            put(SleepStorageDatabaseHelper.COLUMN_CHANNEL_COUNT, channelCount)
            put(SleepStorageDatabaseHelper.COLUMN_RAW_FORMAT, rawFormat)
            put(SleepStorageDatabaseHelper.COLUMN_RAW_FILE_PATH, rawFilePath)
            put(SleepStorageDatabaseHelper.COLUMN_PACKET_COUNT, packetCount)
            put(SleepStorageDatabaseHelper.COLUMN_CREATED_AT_EPOCH_MS, createdAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_UPDATED_AT_EPOCH_MS, updatedAtEpochMs)
        }
    }

    private fun SleepEpochRecord.toContentValues(): ContentValues {
        return ContentValues().apply {
            put(SleepStorageDatabaseHelper.COLUMN_SESSION_ID, sessionId)
            put(SleepStorageDatabaseHelper.COLUMN_EPOCH_INDEX, epochIndex)
            put(SleepStorageDatabaseHelper.COLUMN_EPOCH_START_AT_EPOCH_MS, startAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_EPOCH_END_AT_EPOCH_MS, endAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_STAGE, stage.name)
            put(SleepStorageDatabaseHelper.COLUMN_CONFIDENCE, confidence)
            put(SleepStorageDatabaseHelper.COLUMN_AVG_SIGNAL_QUALITY, avgSignalQuality)
            put(SleepStorageDatabaseHelper.COLUMN_STAGE_SOURCE, source.name)
            put(SleepStorageDatabaseHelper.COLUMN_FEATURES_JSON, featuresJson)
        }
    }

    private fun SleepFeatureWindowRecord.toContentValues(): ContentValues {
        return ContentValues().apply {
            put(SleepStorageDatabaseHelper.COLUMN_SESSION_ID, sessionId)
            put(SleepStorageDatabaseHelper.COLUMN_WINDOW_TYPE, windowType.name)
            put(SleepStorageDatabaseHelper.COLUMN_WINDOW_INDEX, windowIndex)
            put(SleepStorageDatabaseHelper.COLUMN_EPOCH_START_AT_EPOCH_MS, startAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_EPOCH_END_AT_EPOCH_MS, endAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_DELTA_POWER, deltaPower)
            put(SleepStorageDatabaseHelper.COLUMN_THETA_POWER, thetaPower)
            put(SleepStorageDatabaseHelper.COLUMN_ALPHA_POWER, alphaPower)
            put(SleepStorageDatabaseHelper.COLUMN_BETA_POWER, betaPower)
            put(SleepStorageDatabaseHelper.COLUMN_THETA_ALPHA_RATIO, thetaAlphaRatio)
            put(SleepStorageDatabaseHelper.COLUMN_AVG_SIGNAL_QUALITY, signalQuality)
            put(SleepStorageDatabaseHelper.COLUMN_ARTIFACT_RATIO, artifactRatio)
            put(SleepStorageDatabaseHelper.COLUMN_FEATURE_VERSION, featureVersion)
            put(SleepStorageDatabaseHelper.COLUMN_FEATURES_JSON, featuresJson)
            put(SleepStorageDatabaseHelper.COLUMN_CREATED_AT_EPOCH_MS, createdAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_UPDATED_AT_EPOCH_MS, updatedAtEpochMs)
        }
    }

    private fun SleepNightlySummaryRecord.toContentValues(): ContentValues {
        return ContentValues().apply {
            put(SleepStorageDatabaseHelper.COLUMN_SUMMARY_ID, summaryId)
            put(SleepStorageDatabaseHelper.COLUMN_SESSION_ID, sessionId)
            put(SleepStorageDatabaseHelper.COLUMN_SUMMARY_VERSION, summaryVersion)
            put(SleepStorageDatabaseHelper.COLUMN_SLEEP_DURATION_MS, sleepDurationMs)
            put(SleepStorageDatabaseHelper.COLUMN_SLEEP_EFFICIENCY, sleepEfficiency)
            put(SleepStorageDatabaseHelper.COLUMN_SLEEP_ONSET_LATENCY_MS, sleepOnsetLatencyMs)
            put(SleepStorageDatabaseHelper.COLUMN_WAKE_AFTER_SLEEP_ONSET_MS, wakeAfterSleepOnsetMs)
            put(SleepStorageDatabaseHelper.COLUMN_WAKE_COUNT, wakeCount)
            put(SleepStorageDatabaseHelper.COLUMN_DEEP_SLEEP_MS, deepSleepMs)
            put(SleepStorageDatabaseHelper.COLUMN_REM_SLEEP_MS, remSleepMs)
            put(SleepStorageDatabaseHelper.COLUMN_LIGHT_SLEEP_MS, lightSleepMs)
            put(SleepStorageDatabaseHelper.COLUMN_AWAKE_MS, awakeMs)
            put(SleepStorageDatabaseHelper.COLUMN_AVG_SIGNAL_QUALITY, avgSignalQuality)
            put(SleepStorageDatabaseHelper.COLUMN_DATA_QUALITY_SCORE, dataQualityScore)
            put(SleepStorageDatabaseHelper.COLUMN_PAYLOAD_JSON, payloadJson)
            put(SleepStorageDatabaseHelper.COLUMN_CREATED_AT_EPOCH_MS, generatedAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_UPDATED_AT_EPOCH_MS, generatedAtEpochMs)
        }
    }

    private fun SleepEventRecord.toContentValues(): ContentValues {
        return ContentValues().apply {
            put(SleepStorageDatabaseHelper.COLUMN_EVENT_ID, eventId)
            put(SleepStorageDatabaseHelper.COLUMN_SESSION_ID, sessionId)
            put(SleepStorageDatabaseHelper.COLUMN_EVENT_TYPE, eventType.name)
            put(SleepStorageDatabaseHelper.COLUMN_EVENT_START_AT_EPOCH_MS, startAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_EVENT_END_AT_EPOCH_MS, endAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_SEVERITY, severity)
            put(SleepStorageDatabaseHelper.COLUMN_CONFIDENCE, confidence)
            put(SleepStorageDatabaseHelper.COLUMN_EVENT_SOURCE, source.name)
            put(SleepStorageDatabaseHelper.COLUMN_PAYLOAD_JSON, payloadJson)
            put(SleepStorageDatabaseHelper.COLUMN_CREATED_AT_EPOCH_MS, createdAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_UPDATED_AT_EPOCH_MS, createdAtEpochMs)
        }
    }

    private fun SleepAiReportRecord.toContentValues(): ContentValues {
        return ContentValues().apply {
            put(SleepStorageDatabaseHelper.COLUMN_REPORT_ID, reportId)
            put(SleepStorageDatabaseHelper.COLUMN_SESSION_ID, sessionId)
            put(SleepStorageDatabaseHelper.COLUMN_REPORT_TYPE, reportType.name)
            put(SleepStorageDatabaseHelper.COLUMN_MODEL_NAME, modelName)
            put(SleepStorageDatabaseHelper.COLUMN_PROMPT_VERSION, promptVersion)
            put(SleepStorageDatabaseHelper.COLUMN_INPUT_SNAPSHOT_ID, inputSnapshotId)
            put(SleepStorageDatabaseHelper.COLUMN_SUMMARY_TEXT, summaryText)
            put(SleepStorageDatabaseHelper.COLUMN_STRUCTURED_JSON, structuredJson)
            put(SleepStorageDatabaseHelper.COLUMN_CONFIDENCE, confidence)
            put(SleepStorageDatabaseHelper.COLUMN_CREATED_AT_EPOCH_MS, createdAtEpochMs)
        }
    }

    private fun AiEvidenceLinkRecord.toContentValues(): ContentValues {
        return ContentValues().apply {
            put(SleepStorageDatabaseHelper.COLUMN_REPORT_ID, reportId)
            put(SleepStorageDatabaseHelper.COLUMN_EVIDENCE_TYPE, evidenceType.name)
            put(SleepStorageDatabaseHelper.COLUMN_EVIDENCE_REF_ID, evidenceRefId)
            put(SleepStorageDatabaseHelper.COLUMN_NOTE, note)
        }
    }

    private fun AnalysisJobRecord.toContentValues(): ContentValues {
        return ContentValues().apply {
            put(SleepStorageDatabaseHelper.COLUMN_JOB_ID, jobId)
            put(SleepStorageDatabaseHelper.COLUMN_SESSION_ID, sessionId)
            put(SleepStorageDatabaseHelper.COLUMN_JOB_TYPE, jobType.name)
            put(SleepStorageDatabaseHelper.COLUMN_JOB_STATUS, status.name)
            put(SleepStorageDatabaseHelper.COLUMN_ALGORITHM_VERSION, algorithmVersion)
            put(SleepStorageDatabaseHelper.COLUMN_STARTED_AT, startedAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_FINISHED_AT, finishedAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_ERROR_MESSAGE, errorMessage)
            put(SleepStorageDatabaseHelper.COLUMN_PAYLOAD_JSON, payloadJson)
            put(SleepStorageDatabaseHelper.COLUMN_CREATED_AT_EPOCH_MS, createdAtEpochMs)
            put(SleepStorageDatabaseHelper.COLUMN_UPDATED_AT_EPOCH_MS, updatedAtEpochMs)
        }
    }

    private fun android.database.Cursor.toSleepSessionRecord(): SleepSessionRecord {
        return SleepSessionRecord(
            sessionId = getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_SESSION_ID)),
            sourceType = SleepDataSource.valueOf(
                getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_SOURCE_TYPE))
            ),
            status = SleepSessionStatus.valueOf(
                getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_STATUS))
            ),
            analysisStatus = SleepAnalysisStatus.valueOf(
                getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_ANALYSIS_STATUS))
            ),
            analysisVersion = getStringOrNull(SleepStorageDatabaseHelper.COLUMN_ANALYSIS_VERSION),
            lastAnalyzedAtEpochMs = getLongOrNull(SleepStorageDatabaseHelper.COLUMN_LAST_ANALYZED_AT_EPOCH_MS),
            deviceId = getStringOrNull(SleepStorageDatabaseHelper.COLUMN_DEVICE_ID),
            deviceName = getStringOrNull(SleepStorageDatabaseHelper.COLUMN_DEVICE_NAME),
            deviceAddress = getStringOrNull(SleepStorageDatabaseHelper.COLUMN_DEVICE_ADDRESS),
            startedAtEpochMs = getLong(
                getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_STARTED_AT_EPOCH_MS)
            ),
            endedAtEpochMs = getLongOrNull(SleepStorageDatabaseHelper.COLUMN_ENDED_AT_EPOCH_MS),
            samplingRateHz = getInt(
                getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_SAMPLING_RATE_HZ)
            ),
            channelCount = getInt(
                getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_CHANNEL_COUNT)
            ),
            rawFormat = getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_RAW_FORMAT)),
            rawFilePath = getString(
                getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_RAW_FILE_PATH)
            ),
            packetCount = getLong(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_PACKET_COUNT)),
            createdAtEpochMs = getLong(
                getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_CREATED_AT_EPOCH_MS)
            ),
            updatedAtEpochMs = getLong(
                getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_UPDATED_AT_EPOCH_MS)
            )
        )
    }

    private fun android.database.Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getString(index)
    }

    private fun android.database.Cursor.getLongOrNull(columnName: String): Long? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getLong(index)
    }

    private fun android.database.Cursor.getIntOrNull(columnName: String): Int? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getInt(index)
    }

    private fun android.database.Cursor.getFloatOrNull(columnName: String): Float? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getFloat(index)
    }

    private fun android.database.Cursor.toSleepEpochRecord(): SleepEpochRecord {
        return SleepEpochRecord(
            id = getLong(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_EPOCH_ID)),
            sessionId = getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_SESSION_ID)),
            epochIndex = getInt(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_EPOCH_INDEX)),
            startAtEpochMs = getLong(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_EPOCH_START_AT_EPOCH_MS)),
            endAtEpochMs = getLong(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_EPOCH_END_AT_EPOCH_MS)),
            stage = SleepStage.valueOf(getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_STAGE))),
            confidence = getFloatOrNull(SleepStorageDatabaseHelper.COLUMN_CONFIDENCE),
            avgSignalQuality = getFloatOrNull(SleepStorageDatabaseHelper.COLUMN_AVG_SIGNAL_QUALITY),
            source = SleepStageSource.valueOf(getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_STAGE_SOURCE))),
            featuresJson = getStringOrNull(SleepStorageDatabaseHelper.COLUMN_FEATURES_JSON)
        )
    }

    private fun android.database.Cursor.toSleepNightlySummaryRecord(): SleepNightlySummaryRecord {
        return SleepNightlySummaryRecord(
            summaryId = getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_SUMMARY_ID)),
            sessionId = getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_SESSION_ID)),
            summaryVersion = getStringOrNull(SleepStorageDatabaseHelper.COLUMN_SUMMARY_VERSION),
            sleepDurationMs = getLongOrNull(SleepStorageDatabaseHelper.COLUMN_SLEEP_DURATION_MS),
            sleepEfficiency = getFloatOrNull(SleepStorageDatabaseHelper.COLUMN_SLEEP_EFFICIENCY),
            sleepOnsetLatencyMs = getLongOrNull(SleepStorageDatabaseHelper.COLUMN_SLEEP_ONSET_LATENCY_MS),
            wakeAfterSleepOnsetMs = getLongOrNull(SleepStorageDatabaseHelper.COLUMN_WAKE_AFTER_SLEEP_ONSET_MS),
            wakeCount = getIntOrNull(SleepStorageDatabaseHelper.COLUMN_WAKE_COUNT),
            deepSleepMs = getLongOrNull(SleepStorageDatabaseHelper.COLUMN_DEEP_SLEEP_MS),
            remSleepMs = getLongOrNull(SleepStorageDatabaseHelper.COLUMN_REM_SLEEP_MS),
            lightSleepMs = getLongOrNull(SleepStorageDatabaseHelper.COLUMN_LIGHT_SLEEP_MS),
            awakeMs = getLongOrNull(SleepStorageDatabaseHelper.COLUMN_AWAKE_MS),
            avgSignalQuality = getFloatOrNull(SleepStorageDatabaseHelper.COLUMN_AVG_SIGNAL_QUALITY),
            dataQualityScore = getFloatOrNull(SleepStorageDatabaseHelper.COLUMN_DATA_QUALITY_SCORE),
            generatedAtEpochMs = getLong(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_CREATED_AT_EPOCH_MS)),
            payloadJson = getStringOrNull(SleepStorageDatabaseHelper.COLUMN_PAYLOAD_JSON)
        )
    }

    private fun android.database.Cursor.toSleepAiReportRecord(): SleepAiReportRecord {
        return SleepAiReportRecord(
            reportId = getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_REPORT_ID)),
            sessionId = getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_SESSION_ID)),
            reportType = SleepReportType.valueOf(getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_REPORT_TYPE))),
            modelName = getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_MODEL_NAME)),
            promptVersion = getStringOrNull(SleepStorageDatabaseHelper.COLUMN_PROMPT_VERSION),
            inputSnapshotId = getStringOrNull(SleepStorageDatabaseHelper.COLUMN_INPUT_SNAPSHOT_ID),
            summaryText = getString(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_SUMMARY_TEXT)),
            structuredJson = getStringOrNull(SleepStorageDatabaseHelper.COLUMN_STRUCTURED_JSON),
            confidence = getFloatOrNull(SleepStorageDatabaseHelper.COLUMN_CONFIDENCE),
            createdAtEpochMs = getLong(getColumnIndexOrThrow(SleepStorageDatabaseHelper.COLUMN_CREATED_AT_EPOCH_MS))
        )
    }

    companion object {
        private const val DEFAULT_SAMPLING_RATE_HZ = 250
        private const val DEFAULT_CHANNEL_COUNT = 8
        private const val RAW_FORMAT_CSV = "csv"
        private const val TABLE_SLEEP_ANALYSIS_JOB = "sleep_analysis_job"
        private const val TABLE_SLEEP_FEATURE_WINDOW = "sleep_feature_window"
        private const val TABLE_SLEEP_NIGHTLY_SUMMARY = "sleep_nightly_summary"
        private const val TABLE_SLEEP_EVENT = "sleep_event"
        private const val TABLE_SLEEP_AI_REPORT = "sleep_ai_report"
        private const val TABLE_AI_EVIDENCE_LINK = "ai_evidence_link"
    }
}
