package com.sleepagent.prototype.intervention.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sleepagent.prototype.data.SleepStorageDatabaseHelper
import com.sleepagent.prototype.data.SleepStorageRepository

/**
 * Re-registers fallback alarm after device reboot.
 * Reads the latest intervention config from the database and re-schedules.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed — re-registering alarm")

        val scheduler = SmartAlarmScheduler(context)
        if (!scheduler.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarms — permission not granted")
            return
        }

        // Load saved config
        val repository = SleepStorageRepository(context.applicationContext)
        val config = runCatching {
            // Read from database
            loadConfigFromDatabase(context)
        }.getOrElse {
            Log.e(TAG, "Failed to load config", it)
            null
        }

        if (config != null && config.smartWakeEnabled) {
            scheduler.schedule(
                config.latestWakeMinutesFromMidnight,
                config.wakeWindowMinutes
            )
            Log.i(TAG, "Alarm re-registered: wakeWindow=${config.wakeWindowMinutes}, latest=${config.latestWakeMinutesFromMidnight}")
        }
    }

    private fun loadConfigFromDatabase(context: Context): com.sleepagent.prototype.data.SoundInterventionConfigEntity? {
        return try {
            val db = SleepStorageDatabaseHelper(context.applicationContext).readableDatabase
            db.query(
                SleepStorageDatabaseHelper.TABLE_SOUND_INTERVENTION_CONFIG,
                null, null, null, null, null, null,
                "1"
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.toInterventionConfig()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun android.database.Cursor.toInterventionConfig(): com.sleepagent.prototype.data.SoundInterventionConfigEntity {
        return com.sleepagent.prototype.data.SoundInterventionConfigEntity(
            smartWakeEnabled = getInt(getColumnIndexOrThrow("smart_wake_enabled")) != 0,
            latestWakeMinutesFromMidnight = getInt(getColumnIndexOrThrow("latest_wake_minutes_from_midnight")),
            wakeWindowMinutes = getInt(getColumnIndexOrThrow("wake_window_minutes"))
        )
    }
}
