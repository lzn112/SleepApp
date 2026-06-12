package com.sleepagent.prototype.data

import android.content.Context

object UserPreferences {
    private const val PREFS_NAME = "sleepagent_user"
    private const val KEY_DISPLAY_NAME = "display_name"

    fun getDisplayName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DISPLAY_NAME, null) ?: "用户"
    }
}
