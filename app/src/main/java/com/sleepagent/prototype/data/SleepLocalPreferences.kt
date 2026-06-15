package com.sleepagent.prototype.data

import android.content.Context

data class UserProfilePreference(
    val nickname: String = "用户昵称",
    val avatarEmoji: String = "🌙",
    val gender: String = "未设置",
    val ageRange: String = "未设置",
    val currentGoal: String = "更快入睡"
)

data class SleepPreference(
    val targetSleepHours: Float = 7.5f,
    val defaultBedtime: String = "23:30",
    val defaultWakeTime: String = "07:30",
    val bedtimeReminder: String = "23:00",
    val smartWakeEnabled: Boolean = true,
    val soundAidPreference: String = "呼吸放松 + 白噪音",
    val aiCompanionEnabled: Boolean = true
)

data class SleepPlanPreference(
    val bedtime: String = "23:30",
    val wakeTime: String = "07:30",
    val smartWakeEnabled: Boolean = true,
    val smartWakeStart: String = "07:00",
    val smartWakeEnd: String = "07:30",
    val soundAidEnabled: Boolean = true,
    val soundAidName: String = "雨声",
    val soundDurationMin: Int = 30,
    val fadeOutEnabled: Boolean = true,
    val aiCompanionEnabled: Boolean = true,
    val aiCompanionMode: String = "呼吸放松",
    val aiCompanionDurationMin: Int = 5,
    val sleepGuardEnabled: Boolean = true
)

object SleepLocalPreferences {
    private const val PREFS_NAME = "sleepagent_local_preferences"

    private const val KEY_PROFILE_NICKNAME = "profile_nickname"
    private const val KEY_PROFILE_AVATAR_EMOJI = "profile_avatar_emoji"
    private const val KEY_PROFILE_GENDER = "profile_gender"
    private const val KEY_PROFILE_AGE_RANGE = "profile_age_range"
    private const val KEY_PROFILE_CURRENT_GOAL = "profile_current_goal"

    private const val KEY_PREF_TARGET_SLEEP_HOURS = "pref_target_sleep_hours"
    private const val KEY_PREF_DEFAULT_BEDTIME = "pref_default_bedtime"
    private const val KEY_PREF_DEFAULT_WAKE_TIME = "pref_default_wake_time"
    private const val KEY_PREF_BEDTIME_REMINDER = "pref_bedtime_reminder"
    private const val KEY_PREF_SMART_WAKE_ENABLED = "pref_smart_wake_enabled"
    private const val KEY_PREF_SOUND_AID = "pref_sound_aid"
    private const val KEY_PREF_AI_COMPANION_ENABLED = "pref_ai_companion_enabled"

    private const val KEY_PLAN_EXISTS = "plan_exists"
    private const val KEY_PLAN_BEDTIME = "plan_bedtime"
    private const val KEY_PLAN_WAKE_TIME = "plan_wake_time"
    private const val KEY_PLAN_SMART_WAKE_ENABLED = "plan_smart_wake_enabled"
    private const val KEY_PLAN_SMART_WAKE_START = "plan_smart_wake_start"
    private const val KEY_PLAN_SMART_WAKE_END = "plan_smart_wake_end"
    private const val KEY_PLAN_SOUND_AID_ENABLED = "plan_sound_aid_enabled"
    private const val KEY_PLAN_SOUND_AID_NAME = "plan_sound_aid_name"
    private const val KEY_PLAN_SOUND_DURATION_MIN = "plan_sound_duration_min"
    private const val KEY_PLAN_FADE_OUT_ENABLED = "plan_fade_out_enabled"
    private const val KEY_PLAN_AI_COMPANION_ENABLED = "plan_ai_companion_enabled"
    private const val KEY_PLAN_AI_COMPANION_MODE = "plan_ai_companion_mode"
    private const val KEY_PLAN_AI_COMPANION_DURATION_MIN = "plan_ai_companion_duration_min"
    private const val KEY_PLAN_SLEEP_GUARD_ENABLED = "plan_sleep_guard_enabled"

    fun loadUserProfile(context: Context): UserProfilePreference {
        val prefs = prefs(context)
        return UserProfilePreference(
            nickname = prefs.getString(KEY_PROFILE_NICKNAME, null) ?: "用户昵称",
            avatarEmoji = prefs.getString(KEY_PROFILE_AVATAR_EMOJI, null) ?: "🌙",
            gender = prefs.getString(KEY_PROFILE_GENDER, null) ?: "未设置",
            ageRange = prefs.getString(KEY_PROFILE_AGE_RANGE, null) ?: "未设置",
            currentGoal = prefs.getString(KEY_PROFILE_CURRENT_GOAL, null) ?: "更快入睡"
        )
    }

    fun saveUserProfile(context: Context, profile: UserProfilePreference) {
        prefs(context).edit()
            .putString(KEY_PROFILE_NICKNAME, profile.nickname)
            .putString(KEY_PROFILE_AVATAR_EMOJI, profile.avatarEmoji)
            .putString(KEY_PROFILE_GENDER, profile.gender)
            .putString(KEY_PROFILE_AGE_RANGE, profile.ageRange)
            .putString(KEY_PROFILE_CURRENT_GOAL, profile.currentGoal)
            .apply()
    }

    fun loadSleepPreference(context: Context): SleepPreference {
        val prefs = prefs(context)
        return SleepPreference(
            targetSleepHours = prefs.getFloat(KEY_PREF_TARGET_SLEEP_HOURS, 7.5f),
            defaultBedtime = prefs.getString(KEY_PREF_DEFAULT_BEDTIME, null) ?: "23:30",
            defaultWakeTime = prefs.getString(KEY_PREF_DEFAULT_WAKE_TIME, null) ?: "07:30",
            bedtimeReminder = prefs.getString(KEY_PREF_BEDTIME_REMINDER, null) ?: "23:00",
            smartWakeEnabled = prefs.getBoolean(KEY_PREF_SMART_WAKE_ENABLED, true),
            soundAidPreference = prefs.getString(KEY_PREF_SOUND_AID, null) ?: "呼吸放松 + 白噪音",
            aiCompanionEnabled = prefs.getBoolean(KEY_PREF_AI_COMPANION_ENABLED, true)
        )
    }

    fun saveSleepPreference(context: Context, preference: SleepPreference) {
        prefs(context).edit()
            .putFloat(KEY_PREF_TARGET_SLEEP_HOURS, preference.targetSleepHours)
            .putString(KEY_PREF_DEFAULT_BEDTIME, preference.defaultBedtime)
            .putString(KEY_PREF_DEFAULT_WAKE_TIME, preference.defaultWakeTime)
            .putString(KEY_PREF_BEDTIME_REMINDER, preference.bedtimeReminder)
            .putBoolean(KEY_PREF_SMART_WAKE_ENABLED, preference.smartWakeEnabled)
            .putString(KEY_PREF_SOUND_AID, preference.soundAidPreference)
            .putBoolean(KEY_PREF_AI_COMPANION_ENABLED, preference.aiCompanionEnabled)
            .apply()
    }

    fun loadSleepPlan(context: Context): SleepPlanPreference? {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_PLAN_EXISTS, false)) return null
        return SleepPlanPreference(
            bedtime = prefs.getString(KEY_PLAN_BEDTIME, null) ?: "23:30",
            wakeTime = prefs.getString(KEY_PLAN_WAKE_TIME, null) ?: "07:30",
            smartWakeEnabled = prefs.getBoolean(KEY_PLAN_SMART_WAKE_ENABLED, true),
            smartWakeStart = prefs.getString(KEY_PLAN_SMART_WAKE_START, null) ?: "07:00",
            smartWakeEnd = prefs.getString(KEY_PLAN_SMART_WAKE_END, null) ?: "07:30",
            soundAidEnabled = prefs.getBoolean(KEY_PLAN_SOUND_AID_ENABLED, true),
            soundAidName = prefs.getString(KEY_PLAN_SOUND_AID_NAME, null) ?: "雨声",
            soundDurationMin = prefs.getInt(KEY_PLAN_SOUND_DURATION_MIN, 30),
            fadeOutEnabled = prefs.getBoolean(KEY_PLAN_FADE_OUT_ENABLED, true),
            aiCompanionEnabled = prefs.getBoolean(KEY_PLAN_AI_COMPANION_ENABLED, true),
            aiCompanionMode = prefs.getString(KEY_PLAN_AI_COMPANION_MODE, null) ?: "呼吸放松",
            aiCompanionDurationMin = prefs.getInt(KEY_PLAN_AI_COMPANION_DURATION_MIN, 5),
            sleepGuardEnabled = prefs.getBoolean(KEY_PLAN_SLEEP_GUARD_ENABLED, true)
        )
    }

    fun saveSleepPlan(context: Context, plan: SleepPlanPreference) {
        prefs(context).edit()
            .putBoolean(KEY_PLAN_EXISTS, true)
            .putString(KEY_PLAN_BEDTIME, plan.bedtime)
            .putString(KEY_PLAN_WAKE_TIME, plan.wakeTime)
            .putBoolean(KEY_PLAN_SMART_WAKE_ENABLED, plan.smartWakeEnabled)
            .putString(KEY_PLAN_SMART_WAKE_START, plan.smartWakeStart)
            .putString(KEY_PLAN_SMART_WAKE_END, plan.smartWakeEnd)
            .putBoolean(KEY_PLAN_SOUND_AID_ENABLED, plan.soundAidEnabled)
            .putString(KEY_PLAN_SOUND_AID_NAME, plan.soundAidName)
            .putInt(KEY_PLAN_SOUND_DURATION_MIN, plan.soundDurationMin)
            .putBoolean(KEY_PLAN_FADE_OUT_ENABLED, plan.fadeOutEnabled)
            .putBoolean(KEY_PLAN_AI_COMPANION_ENABLED, plan.aiCompanionEnabled)
            .putString(KEY_PLAN_AI_COMPANION_MODE, plan.aiCompanionMode)
            .putInt(KEY_PLAN_AI_COMPANION_DURATION_MIN, plan.aiCompanionDurationMin)
            .putBoolean(KEY_PLAN_SLEEP_GUARD_ENABLED, plan.sleepGuardEnabled)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
