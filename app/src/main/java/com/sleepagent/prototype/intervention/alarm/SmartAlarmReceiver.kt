package com.sleepagent.prototype.intervention.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Broadcast receiver for smart wake window start and fallback alarm.
 *
 * Received actions:
 * - SMART_WAKE_WINDOW: Notifies that smart wake window has begun.
 * - FALLBACK_ALARM: Triggers the fallback alarm (unconditional wake-up).
 */
class SmartAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmartAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received alarm: ${intent.action}")

        when (intent.action) {
            SmartAlarmScheduler.ACTION_SMART_WAKE_WINDOW -> {
                handleSmartWakeWindowStart(context)
            }
            SmartAlarmScheduler.ACTION_FALLBACK_ALARM -> {
                handleFallbackAlarm(context)
            }
        }
    }

    private fun handleSmartWakeWindowStart(context: Context) {
        // Smart wake window start: signal the service if running.
        // If the service is stopped/not running, this is informational only.
        Log.i(TAG, "Smart wake window started")
    }

    private fun handleFallbackAlarm(context: Context) {
        // Launch the full-screen alarm activity
        val alarmIntent = Intent(context, WakeAlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(alarmIntent)
    }
}
