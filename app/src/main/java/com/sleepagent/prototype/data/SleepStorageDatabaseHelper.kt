package com.sleepagent.prototype.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SleepStorageDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_SLEEP_SESSION_TABLE)
        db.execSQL(CREATE_SLEEP_SESSION_STARTED_AT_INDEX)
        db.execSQL(CREATE_SLEEP_EPOCH_TABLE)
        db.execSQL(CREATE_SLEEP_EPOCH_SESSION_INDEX)
        db.execSQL(CREATE_SLEEP_EPOCH_UNIQUE_INDEX)
        db.execSQL(CREATE_SLEEP_ANALYSIS_JOB_TABLE)
        db.execSQL(CREATE_SLEEP_ANALYSIS_JOB_SESSION_INDEX)
        db.execSQL(CREATE_SLEEP_FEATURE_WINDOW_TABLE)
        db.execSQL(CREATE_SLEEP_FEATURE_WINDOW_SESSION_INDEX)
        db.execSQL(CREATE_SLEEP_FEATURE_WINDOW_UNIQUE_INDEX)
        db.execSQL(CREATE_SLEEP_NIGHTLY_SUMMARY_TABLE)
        db.execSQL(CREATE_SLEEP_NIGHTLY_SUMMARY_SESSION_INDEX)
        db.execSQL(CREATE_SLEEP_EVENT_TABLE)
        db.execSQL(CREATE_SLEEP_EVENT_SESSION_INDEX)
        db.execSQL(CREATE_SLEEP_AI_REPORT_TABLE)
        db.execSQL(CREATE_SLEEP_AI_REPORT_SESSION_INDEX)
        db.execSQL(CREATE_AI_EVIDENCE_LINK_TABLE)
        db.execSQL(CREATE_AI_EVIDENCE_LINK_REPORT_INDEX)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == newVersion) return
        var currentVersion = oldVersion

        if (currentVersion < 2) {
            migrateToVersion2(db)
            currentVersion = 2
        }

        if (currentVersion != newVersion) {
            rebuildAllTables(db)
        }
    }

    companion object {
        private const val DATABASE_NAME = "sleep_storage.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_SLEEP_SESSION = "sleep_session"
        const val TABLE_SLEEP_EPOCH = "sleep_epoch"

        const val COLUMN_SESSION_ID = "session_id"
        const val COLUMN_SOURCE_TYPE = "source_type"
        const val COLUMN_STATUS = "status"
        const val COLUMN_DEVICE_ID = "device_id"
        const val COLUMN_DEVICE_NAME = "device_name"
        const val COLUMN_DEVICE_ADDRESS = "device_address"
        const val COLUMN_ANALYSIS_STATUS = "analysis_status"
        const val COLUMN_ANALYSIS_VERSION = "analysis_version"
        const val COLUMN_LAST_ANALYZED_AT_EPOCH_MS = "last_analyzed_at_epoch_ms"
        const val COLUMN_STARTED_AT_EPOCH_MS = "started_at_epoch_ms"
        const val COLUMN_ENDED_AT_EPOCH_MS = "ended_at_epoch_ms"
        const val COLUMN_SAMPLING_RATE_HZ = "sampling_rate_hz"
        const val COLUMN_CHANNEL_COUNT = "channel_count"
        const val COLUMN_RAW_FORMAT = "raw_format"
        const val COLUMN_RAW_FILE_PATH = "raw_file_path"
        const val COLUMN_PACKET_COUNT = "packet_count"
        const val COLUMN_CREATED_AT_EPOCH_MS = "created_at_epoch_ms"
        const val COLUMN_UPDATED_AT_EPOCH_MS = "updated_at_epoch_ms"

        const val COLUMN_EPOCH_ID = "id"
        const val COLUMN_EPOCH_INDEX = "epoch_index"
        const val COLUMN_EPOCH_START_AT_EPOCH_MS = "start_at_epoch_ms"
        const val COLUMN_EPOCH_END_AT_EPOCH_MS = "end_at_epoch_ms"
        const val COLUMN_STAGE = "stage"
        const val COLUMN_CONFIDENCE = "confidence"
        const val COLUMN_AVG_SIGNAL_QUALITY = "avg_signal_quality"
        const val COLUMN_STAGE_SOURCE = "stage_source"
        const val COLUMN_FEATURES_JSON = "features_json"
        const val COLUMN_JOB_ID = "job_id"
        const val COLUMN_JOB_TYPE = "job_type"
        const val COLUMN_JOB_STATUS = "job_status"
        const val COLUMN_ALGORITHM_VERSION = "algorithm_version"
        const val COLUMN_STARTED_AT = "started_at"
        const val COLUMN_FINISHED_AT = "finished_at"
        const val COLUMN_ERROR_MESSAGE = "error_message"
        const val COLUMN_PAYLOAD_JSON = "payload_json"
        const val COLUMN_WINDOW_TYPE = "window_type"
        const val COLUMN_WINDOW_INDEX = "window_index"
        const val COLUMN_DELTA_POWER = "delta_power"
        const val COLUMN_THETA_POWER = "theta_power"
        const val COLUMN_ALPHA_POWER = "alpha_power"
        const val COLUMN_BETA_POWER = "beta_power"
        const val COLUMN_THETA_ALPHA_RATIO = "theta_alpha_ratio"
        const val COLUMN_ARTIFACT_RATIO = "artifact_ratio"
        const val COLUMN_FEATURE_VERSION = "feature_version"
        const val COLUMN_SUMMARY_ID = "summary_id"
        const val COLUMN_SUMMARY_VERSION = "summary_version"
        const val COLUMN_SLEEP_DURATION_MS = "sleep_duration_ms"
        const val COLUMN_SLEEP_EFFICIENCY = "sleep_efficiency"
        const val COLUMN_SLEEP_ONSET_LATENCY_MS = "sleep_onset_latency_ms"
        const val COLUMN_WAKE_AFTER_SLEEP_ONSET_MS = "wake_after_sleep_onset_ms"
        const val COLUMN_WAKE_COUNT = "wake_count"
        const val COLUMN_DEEP_SLEEP_MS = "deep_sleep_ms"
        const val COLUMN_REM_SLEEP_MS = "rem_sleep_ms"
        const val COLUMN_LIGHT_SLEEP_MS = "light_sleep_ms"
        const val COLUMN_AWAKE_MS = "awake_ms"
        const val COLUMN_DATA_QUALITY_SCORE = "data_quality_score"
        const val COLUMN_EVENT_ID = "event_id"
        const val COLUMN_EVENT_TYPE = "event_type"
        const val COLUMN_EVENT_START_AT_EPOCH_MS = "event_start_at_epoch_ms"
        const val COLUMN_EVENT_END_AT_EPOCH_MS = "event_end_at_epoch_ms"
        const val COLUMN_SEVERITY = "severity"
        const val COLUMN_EVENT_SOURCE = "event_source"
        const val COLUMN_REPORT_ID = "report_id"
        const val COLUMN_REPORT_TYPE = "report_type"
        const val COLUMN_MODEL_NAME = "model_name"
        const val COLUMN_PROMPT_VERSION = "prompt_version"
        const val COLUMN_INPUT_SNAPSHOT_ID = "input_snapshot_id"
        const val COLUMN_SUMMARY_TEXT = "summary_text"
        const val COLUMN_STRUCTURED_JSON = "structured_json"
        const val COLUMN_EVIDENCE_TYPE = "evidence_type"
        const val COLUMN_EVIDENCE_REF_ID = "evidence_ref_id"
        const val COLUMN_NOTE = "note"

        private val CREATE_SLEEP_SESSION_TABLE =
            """
            CREATE TABLE $TABLE_SLEEP_SESSION (
                $COLUMN_SESSION_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_SOURCE_TYPE TEXT NOT NULL,
                $COLUMN_STATUS TEXT NOT NULL,
                $COLUMN_ANALYSIS_STATUS TEXT NOT NULL DEFAULT 'NONE',
                $COLUMN_ANALYSIS_VERSION TEXT,
                $COLUMN_LAST_ANALYZED_AT_EPOCH_MS INTEGER,
                $COLUMN_DEVICE_ID TEXT,
                $COLUMN_DEVICE_NAME TEXT,
                $COLUMN_DEVICE_ADDRESS TEXT,
                $COLUMN_STARTED_AT_EPOCH_MS INTEGER NOT NULL,
                $COLUMN_ENDED_AT_EPOCH_MS INTEGER,
                $COLUMN_SAMPLING_RATE_HZ INTEGER NOT NULL,
                $COLUMN_CHANNEL_COUNT INTEGER NOT NULL,
                $COLUMN_RAW_FORMAT TEXT NOT NULL,
                $COLUMN_RAW_FILE_PATH TEXT NOT NULL,
                $COLUMN_PACKET_COUNT INTEGER NOT NULL DEFAULT 0,
                $COLUMN_CREATED_AT_EPOCH_MS INTEGER NOT NULL,
                $COLUMN_UPDATED_AT_EPOCH_MS INTEGER NOT NULL
            )
            """.trimIndent()

        private val CREATE_SLEEP_SESSION_STARTED_AT_INDEX =
            """
            CREATE INDEX IF NOT EXISTS idx_sleep_session_started_at
            ON $TABLE_SLEEP_SESSION ($COLUMN_STARTED_AT_EPOCH_MS DESC)
            """.trimIndent()

        private val CREATE_SLEEP_EPOCH_TABLE =
            """
            CREATE TABLE $TABLE_SLEEP_EPOCH (
                $COLUMN_EPOCH_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SESSION_ID TEXT NOT NULL,
                $COLUMN_EPOCH_INDEX INTEGER NOT NULL,
                $COLUMN_EPOCH_START_AT_EPOCH_MS INTEGER NOT NULL,
                $COLUMN_EPOCH_END_AT_EPOCH_MS INTEGER NOT NULL,
                $COLUMN_STAGE TEXT NOT NULL,
                $COLUMN_CONFIDENCE REAL,
                $COLUMN_AVG_SIGNAL_QUALITY REAL,
                $COLUMN_STAGE_SOURCE TEXT NOT NULL,
                $COLUMN_FEATURES_JSON TEXT,
                FOREIGN KEY($COLUMN_SESSION_ID)
                    REFERENCES $TABLE_SLEEP_SESSION($COLUMN_SESSION_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent()

        private val CREATE_SLEEP_EPOCH_SESSION_INDEX =
            """
            CREATE INDEX IF NOT EXISTS idx_sleep_epoch_session_start
            ON $TABLE_SLEEP_EPOCH ($COLUMN_SESSION_ID, $COLUMN_EPOCH_START_AT_EPOCH_MS)
            """.trimIndent()

        private val CREATE_SLEEP_EPOCH_UNIQUE_INDEX =
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_sleep_epoch_session_epoch
            ON $TABLE_SLEEP_EPOCH ($COLUMN_SESSION_ID, $COLUMN_EPOCH_INDEX)
            """.trimIndent()

        private val CREATE_SLEEP_ANALYSIS_JOB_TABLE =
            """
            CREATE TABLE IF NOT EXISTS sleep_analysis_job (
                $COLUMN_JOB_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_SESSION_ID TEXT NOT NULL,
                $COLUMN_JOB_TYPE TEXT NOT NULL,
                $COLUMN_JOB_STATUS TEXT NOT NULL,
                $COLUMN_ALGORITHM_VERSION TEXT,
                $COLUMN_STARTED_AT INTEGER,
                $COLUMN_FINISHED_AT INTEGER,
                $COLUMN_ERROR_MESSAGE TEXT,
                $COLUMN_PAYLOAD_JSON TEXT,
                $COLUMN_CREATED_AT_EPOCH_MS INTEGER NOT NULL,
                $COLUMN_UPDATED_AT_EPOCH_MS INTEGER NOT NULL,
                FOREIGN KEY($COLUMN_SESSION_ID)
                    REFERENCES $TABLE_SLEEP_SESSION($COLUMN_SESSION_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent()

        private val CREATE_SLEEP_ANALYSIS_JOB_SESSION_INDEX =
            """
            CREATE INDEX IF NOT EXISTS idx_sleep_analysis_job_session
            ON sleep_analysis_job ($COLUMN_SESSION_ID, $COLUMN_JOB_STATUS)
            """.trimIndent()

        private val CREATE_SLEEP_FEATURE_WINDOW_TABLE =
            """
            CREATE TABLE IF NOT EXISTS sleep_feature_window (
                $COLUMN_EPOCH_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SESSION_ID TEXT NOT NULL,
                $COLUMN_WINDOW_TYPE TEXT NOT NULL,
                $COLUMN_WINDOW_INDEX INTEGER NOT NULL,
                $COLUMN_EPOCH_START_AT_EPOCH_MS INTEGER NOT NULL,
                $COLUMN_EPOCH_END_AT_EPOCH_MS INTEGER NOT NULL,
                $COLUMN_DELTA_POWER REAL,
                $COLUMN_THETA_POWER REAL,
                $COLUMN_ALPHA_POWER REAL,
                $COLUMN_BETA_POWER REAL,
                $COLUMN_THETA_ALPHA_RATIO REAL,
                $COLUMN_AVG_SIGNAL_QUALITY REAL,
                $COLUMN_ARTIFACT_RATIO REAL,
                $COLUMN_FEATURE_VERSION TEXT,
                $COLUMN_FEATURES_JSON TEXT,
                $COLUMN_CREATED_AT_EPOCH_MS INTEGER NOT NULL,
                $COLUMN_UPDATED_AT_EPOCH_MS INTEGER NOT NULL,
                FOREIGN KEY($COLUMN_SESSION_ID)
                    REFERENCES $TABLE_SLEEP_SESSION($COLUMN_SESSION_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent()

        private val CREATE_SLEEP_FEATURE_WINDOW_SESSION_INDEX =
            """
            CREATE INDEX IF NOT EXISTS idx_sleep_feature_window_session
            ON sleep_feature_window ($COLUMN_SESSION_ID, $COLUMN_WINDOW_TYPE, $COLUMN_WINDOW_INDEX)
            """.trimIndent()

        private val CREATE_SLEEP_FEATURE_WINDOW_UNIQUE_INDEX =
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_sleep_feature_window_unique
            ON sleep_feature_window ($COLUMN_SESSION_ID, $COLUMN_WINDOW_TYPE, $COLUMN_WINDOW_INDEX)
            """.trimIndent()

        private val CREATE_SLEEP_NIGHTLY_SUMMARY_TABLE =
            """
            CREATE TABLE IF NOT EXISTS sleep_nightly_summary (
                $COLUMN_SUMMARY_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_SESSION_ID TEXT NOT NULL UNIQUE,
                $COLUMN_SUMMARY_VERSION TEXT,
                $COLUMN_SLEEP_DURATION_MS INTEGER,
                $COLUMN_SLEEP_EFFICIENCY REAL,
                $COLUMN_SLEEP_ONSET_LATENCY_MS INTEGER,
                $COLUMN_WAKE_AFTER_SLEEP_ONSET_MS INTEGER,
                $COLUMN_WAKE_COUNT INTEGER,
                $COLUMN_DEEP_SLEEP_MS INTEGER,
                $COLUMN_REM_SLEEP_MS INTEGER,
                $COLUMN_LIGHT_SLEEP_MS INTEGER,
                $COLUMN_AWAKE_MS INTEGER,
                $COLUMN_AVG_SIGNAL_QUALITY REAL,
                $COLUMN_DATA_QUALITY_SCORE REAL,
                $COLUMN_PAYLOAD_JSON TEXT,
                $COLUMN_CREATED_AT_EPOCH_MS INTEGER NOT NULL,
                $COLUMN_UPDATED_AT_EPOCH_MS INTEGER NOT NULL,
                FOREIGN KEY($COLUMN_SESSION_ID)
                    REFERENCES $TABLE_SLEEP_SESSION($COLUMN_SESSION_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent()

        private val CREATE_SLEEP_NIGHTLY_SUMMARY_SESSION_INDEX =
            """
            CREATE INDEX IF NOT EXISTS idx_sleep_nightly_summary_session
            ON sleep_nightly_summary ($COLUMN_SESSION_ID)
            """.trimIndent()

        private val CREATE_SLEEP_EVENT_TABLE =
            """
            CREATE TABLE IF NOT EXISTS sleep_event (
                $COLUMN_EVENT_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_SESSION_ID TEXT NOT NULL,
                $COLUMN_EVENT_TYPE TEXT NOT NULL,
                $COLUMN_EVENT_START_AT_EPOCH_MS INTEGER NOT NULL,
                $COLUMN_EVENT_END_AT_EPOCH_MS INTEGER,
                $COLUMN_SEVERITY INTEGER,
                $COLUMN_CONFIDENCE REAL,
                $COLUMN_EVENT_SOURCE TEXT NOT NULL,
                $COLUMN_PAYLOAD_JSON TEXT,
                $COLUMN_CREATED_AT_EPOCH_MS INTEGER NOT NULL,
                FOREIGN KEY($COLUMN_SESSION_ID)
                    REFERENCES $TABLE_SLEEP_SESSION($COLUMN_SESSION_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent()

        private val CREATE_SLEEP_EVENT_SESSION_INDEX =
            """
            CREATE INDEX IF NOT EXISTS idx_sleep_event_session_time
            ON sleep_event ($COLUMN_SESSION_ID, $COLUMN_EVENT_START_AT_EPOCH_MS)
            """.trimIndent()

        private val CREATE_SLEEP_AI_REPORT_TABLE =
            """
            CREATE TABLE IF NOT EXISTS sleep_ai_report (
                $COLUMN_REPORT_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_SESSION_ID TEXT NOT NULL,
                $COLUMN_REPORT_TYPE TEXT NOT NULL,
                $COLUMN_MODEL_NAME TEXT NOT NULL,
                $COLUMN_PROMPT_VERSION TEXT,
                $COLUMN_INPUT_SNAPSHOT_ID TEXT,
                $COLUMN_SUMMARY_TEXT TEXT NOT NULL,
                $COLUMN_STRUCTURED_JSON TEXT,
                $COLUMN_CONFIDENCE REAL,
                $COLUMN_CREATED_AT_EPOCH_MS INTEGER NOT NULL,
                FOREIGN KEY($COLUMN_SESSION_ID)
                    REFERENCES $TABLE_SLEEP_SESSION($COLUMN_SESSION_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent()

        private val CREATE_SLEEP_AI_REPORT_SESSION_INDEX =
            """
            CREATE INDEX IF NOT EXISTS idx_sleep_ai_report_session
            ON sleep_ai_report ($COLUMN_SESSION_ID, $COLUMN_REPORT_TYPE)
            """.trimIndent()

        private val CREATE_AI_EVIDENCE_LINK_TABLE =
            """
            CREATE TABLE IF NOT EXISTS ai_evidence_link (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_REPORT_ID TEXT NOT NULL,
                $COLUMN_EVIDENCE_TYPE TEXT NOT NULL,
                $COLUMN_EVIDENCE_REF_ID TEXT NOT NULL,
                $COLUMN_NOTE TEXT,
                FOREIGN KEY($COLUMN_REPORT_ID)
                    REFERENCES sleep_ai_report($COLUMN_REPORT_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent()

        private const val COLUMN_ID = "id"

        private val CREATE_AI_EVIDENCE_LINK_REPORT_INDEX =
            """
            CREATE INDEX IF NOT EXISTS idx_ai_evidence_link_report
            ON ai_evidence_link ($COLUMN_REPORT_ID, $COLUMN_EVIDENCE_TYPE)
            """.trimIndent()
    }

    private fun migrateToVersion2(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            ensureColumn(
                db = db,
                tableName = TABLE_SLEEP_SESSION,
                columnName = COLUMN_ANALYSIS_STATUS,
                definition = "$COLUMN_ANALYSIS_STATUS TEXT NOT NULL DEFAULT 'NONE'"
            )
            ensureColumn(
                db = db,
                tableName = TABLE_SLEEP_SESSION,
                columnName = COLUMN_ANALYSIS_VERSION,
                definition = "$COLUMN_ANALYSIS_VERSION TEXT"
            )
            ensureColumn(
                db = db,
                tableName = TABLE_SLEEP_SESSION,
                columnName = COLUMN_LAST_ANALYZED_AT_EPOCH_MS,
                definition = "$COLUMN_LAST_ANALYZED_AT_EPOCH_MS INTEGER"
            )

            db.execSQL(CREATE_SLEEP_ANALYSIS_JOB_TABLE)
            db.execSQL(CREATE_SLEEP_ANALYSIS_JOB_SESSION_INDEX)
            db.execSQL(CREATE_SLEEP_FEATURE_WINDOW_TABLE)
            db.execSQL(CREATE_SLEEP_FEATURE_WINDOW_SESSION_INDEX)
            db.execSQL(CREATE_SLEEP_FEATURE_WINDOW_UNIQUE_INDEX)
            db.execSQL(CREATE_SLEEP_NIGHTLY_SUMMARY_TABLE)
            db.execSQL(CREATE_SLEEP_NIGHTLY_SUMMARY_SESSION_INDEX)
            db.execSQL(CREATE_SLEEP_EVENT_TABLE)
            db.execSQL(CREATE_SLEEP_EVENT_SESSION_INDEX)
            db.execSQL(CREATE_SLEEP_AI_REPORT_TABLE)
            db.execSQL(CREATE_SLEEP_AI_REPORT_SESSION_INDEX)
            db.execSQL(CREATE_AI_EVIDENCE_LINK_TABLE)
            db.execSQL(CREATE_AI_EVIDENCE_LINK_REPORT_INDEX)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun rebuildAllTables(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS ai_evidence_link")
        db.execSQL("DROP TABLE IF EXISTS sleep_ai_report")
        db.execSQL("DROP TABLE IF EXISTS sleep_event")
        db.execSQL("DROP TABLE IF EXISTS sleep_nightly_summary")
        db.execSQL("DROP TABLE IF EXISTS sleep_feature_window")
        db.execSQL("DROP TABLE IF EXISTS sleep_analysis_job")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SLEEP_EPOCH")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SLEEP_SESSION")
        onCreate(db)
    }

    private fun ensureColumn(
        db: SQLiteDatabase,
        tableName: String,
        columnName: String,
        definition: String
    ) {
        if (hasColumn(db, tableName, columnName)) {
            return
        }
        db.execSQL("ALTER TABLE $tableName ADD COLUMN $definition")
    }

    private fun hasColumn(
        db: SQLiteDatabase,
        tableName: String,
        columnName: String
    ): Boolean {
        db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) {
                    return true
                }
            }
        }
        return false
    }
}
