package com.sleepagent.prototype.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SleepSessionExportWriter(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun exportSessionBundle(session: SleepSessionRecord): SleepSessionExportResult {
        return withContext(ioDispatcher) {
            val rawFile = File(session.rawFilePath)
            require(rawFile.exists()) {
                "Raw session file not found: ${rawFile.absolutePath}"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportToDownloads(session, rawFile)
            } else {
                exportToAppExternal(session, rawFile)
            }
        }
    }

    private fun exportToDownloads(
        session: SleepSessionRecord,
        rawFile: File
    ): SleepSessionExportResult {
        val displayName = buildExportFileName(session.sessionId)
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/SleepAgent"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: error("Failed to create export entry in MediaStore")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                writeBundleZip(output, session, rawFile)
            } ?: error("Failed to open export output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishedValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, publishedValues, null, null)
            }

            return SleepSessionExportResult(
                fileName = displayName,
                locationHint = "Downloads/SleepAgent/$displayName",
                uri = uri.toString()
            )
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun exportToAppExternal(
        session: SleepSessionRecord,
        rawFile: File
    ): SleepSessionExportResult {
        val exportDir = File(
            requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)),
            "SleepAgent"
        )
        exportDir.mkdirs()
        val displayName = buildExportFileName(session.sessionId)
        val exportFile = File(exportDir, displayName)
        exportFile.outputStream().use { output ->
            writeBundleZip(output, session, rawFile)
        }
        return SleepSessionExportResult(
            fileName = displayName,
            locationHint = exportFile.absolutePath,
            uri = null
        )
    }

    private fun writeBundleZip(
        outputStream: OutputStream,
        session: SleepSessionRecord,
        rawFile: File
    ) {
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
            putZipTextEntry(
                zip = zip,
                entryName = "manifest.json",
                content = buildManifest(session)
            )

            zip.putNextEntry(ZipEntry(SleepSessionCsvWriter.RAW_CSV_FILE_NAME))
            BufferedInputStream(rawFile.inputStream()).use { input ->
                input.copyTo(zip)
            }
            zip.closeEntry()
        }
    }

    private fun putZipTextEntry(
        zip: ZipOutputStream,
        entryName: String,
        content: String
    ) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildManifest(session: SleepSessionRecord): String {
        val exportedAt = System.currentTimeMillis()
        return JSONObject().apply {
            put("schema_version", 1)
            put("exported_at_epoch_ms", exportedAt)
            put(
                "session",
                JSONObject().apply {
                    put("session_id", session.sessionId)
                    put("source_type", session.sourceType.name)
                    put("status", session.status.name)
                    put("analysis_status", session.analysisStatus.name)
                    put("analysis_version", session.analysisVersion)
                    put("last_analyzed_at_epoch_ms", session.lastAnalyzedAtEpochMs)
                    put("device_id", session.deviceId)
                    put("device_name", session.deviceName)
                    put("device_address", session.deviceAddress)
                    put("started_at_epoch_ms", session.startedAtEpochMs)
                    put("ended_at_epoch_ms", session.endedAtEpochMs)
                    put("sampling_rate_hz", session.samplingRateHz)
                    put("channel_count", session.channelCount)
                    put("raw_format", session.rawFormat)
                    put("packet_count", session.packetCount)
                    put("created_at_epoch_ms", session.createdAtEpochMs)
                    put("updated_at_epoch_ms", session.updatedAtEpochMs)
                }
            )
            put(
                "raw_csv",
                JSONObject().apply {
                    put("file_name", SleepSessionCsvWriter.RAW_CSV_FILE_NAME)
                    put(
                        "columns",
                        JSONArray(
                            SleepSessionCsvWriter.RAW_CSV_HEADER.split(",")
                        )
                    )
                    put("eeg_storage_unit", "adc_counts")
                    put("derived_microvolts_included", false)
                    put("row_granularity", "one packet per row")
                }
            )
        }.toString(2)
    }

    private fun buildExportFileName(sessionId: String): String {
        return "sleep_session_${sessionId}.zip"
    }
}

data class SleepSessionExportResult(
    val fileName: String,
    val locationHint: String,
    val uri: String?
)
