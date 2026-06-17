package com.sleepagent.prototype.intervention.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules both smart wake window alarms and fallback alarm
 * using AlarmManager.setAlarmClock().
 *
 * Smart wake window: [latestWakeTime - wakeWindow, latestWakeTime]
 * Fallback alarm: latestWakeTime (unconditional)
 */
class SmartAlarmScheduler(private val context: Context) {

    companion object {
        const val ACTION_SMART_WAKE_WINDOW = "com.sleepagent.prototype.action.SMART_WAKE_WINDOW"
        const val ACTION_FALLBACK_ALARM = "com.sleepagent.prototype.action.FALLBACK_ALARM"
        private const val PI_REQUEST_WAKE_WINDOW = 2001
        private const val PI_REQUEST_FALLBACK = 2002
    }

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Check if the app can schedule exact alarms (Android 12+).
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Schedule smart wake window start and fallback alarm.
     *
     * @param latestWakeMinutesFromMidnight E.g., 7*60+30 = 07:30
     * @param wakeWindowMinutes Smart wake window duration (default 30)
     */
    fun schedule(latestWakeMinutesFromMidnight: Int, wakeWindowMinutes: Int) {
        if (!canScheduleExactAlarms()) return

        val latestWakeMs = minutesFromMidnightToMillis(latestWakeMinutesFromMidnight)
        val windowStartMs = latestWakeMs - (wakeWindowMinutes * 60_000L)

        // 1. Schedule smart wake window start notification
        val windowIntent = Intent(context, SmartAlarmReceiver::class.java).apply {
            action = ACTION_SMART_WAKE_WINDOW
        }
        val windowPendingIntent = PendingIntent.getBroadcast(
            context,
            PI_REQUEST_WAKE_WINDOW,
            windowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(windowStartMs, windowPendingIntent),
            windowPendingIntent
        )

        // 2. Schedule fallback alarm (unconditional)
        val fallbackIntent = Intent(context, SmartAlarmReceiver::class.java).apply {
            action = ACTION_FALLBACK_ALARM
        }
        val fallbackPendingIntent = PendingIntent.getBroadcast(
            context,
            PI_REQUEST_FALLBACK,
            fallbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(latestWakeMs, fallbackPendingIntent),
            fallbackPendingIntent
        )
    }

    /**
     * Cancel all scheduled alarms.
     */
    fun cancel() {
        val windowIntent = Intent(context, SmartAlarmReceiver::class.java).apply {
            action = ACTION_SMART_WAKE_WINDOW
        }
        val windowPendingIntent = PendingIntent.getBroadcast(
            context,
            PI_REQUEST_WAKE_WINDOW,
            windowIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        windowPendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }

        val fallbackIntent = Intent(context, SmartAlarmReceiver::class.java).apply {
            action = ACTION_FALLBACK_ALARM
        }
        val fallbackPendingIntent = PendingIntent.getBroadcast(
            context,
            PI_REQUEST_FALLBACK,
            fallbackIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        fallbackPendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    /**
     * Check if the scheduled fallback alarm is still pending.
     */
    fun hasPendingFallbackAlarm(): Boolean {
        val intent = Intent(context, SmartAlarmReceiver::class.java).apply {
            action = ACTION_FALLBACK_ALARM
        }
        val pending = PendingIntent.getBroadcast(
            context,
            PI_REQUEST_FALLBACK,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        return pending != null
    }

    private fun minutesFromMidnightToMillis(minutes: Int): Long {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If time has already passed today, schedule for tomorrow
        if (cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
