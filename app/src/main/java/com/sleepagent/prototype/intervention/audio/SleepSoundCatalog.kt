package com.sleepagent.prototype.intervention.audio

import com.sleepagent.prototype.R

enum class SleepSoundCategory {
    NATURE,
    NOISE,
    WAKE,
    ALARM
}

enum class SleepSoundKey(
    val title: String,
    val category: SleepSoundCategory,
    val rawResId: Int,
    val canLoop: Boolean,
    val description: String
) {
    RAIN_GENTLE(
        title = "柔和雨声",
        category = SleepSoundCategory.NATURE,
        rawResId = R.raw.sleep_rain_gentle,
        canLoop = true,
        description = "轻柔的雨水声，帮助放松入睡"
    ),
    RAIN_LONG(
        title = "绵长雨声",
        category = SleepSoundCategory.NATURE,
        rawResId = R.raw.sleep_rain_long,
        canLoop = true,
        description = "持续的绵长雨声，营造安静氛围"
    ),
    OCEAN_WAVES(
        title = "舒缓海浪",
        category = SleepSoundCategory.NATURE,
        rawResId = R.raw.sleep_ocean_waves,
        canLoop = true,
        description = "舒缓的海浪声，带来宁静感"
    ),
    RIVER(
        title = "自然河流",
        category = SleepSoundCategory.NATURE,
        rawResId = R.raw.sleep_river,
        canLoop = true,
        description = "潺潺流水声，自然放松"
    ),
    NIGHT_FOREST(
        title = "夜间森林",
        category = SleepSoundCategory.NATURE,
        rawResId = R.raw.sleep_night_forest,
        canLoop = true,
        description = "夜晚森林中的自然声音"
    ),
    PINK_NOISE(
        title = "粉红噪声",
        category = SleepSoundCategory.NOISE,
        rawResId = R.raw.sleep_pink_noise,
        canLoop = true,
        description = "柔和稳定的粉红噪声，帮助入睡"
    ),
    BROWN_NOISE(
        title = "棕色噪声",
        category = SleepSoundCategory.NOISE,
        rawResId = R.raw.sleep_brown_noise,
        canLoop = true,
        description = "低频棕色噪声，深沉的背景音"
    ),
    WHITE_NOISE(
        title = "白噪声",
        category = SleepSoundCategory.NOISE,
        rawResId = R.raw.sleep_white_noise,
        canLoop = true,
        description = "均匀的白噪声，屏蔽环境干扰"
    ),
    MORNING_BIRDS(
        title = "晨间鸟鸣",
        category = SleepSoundCategory.WAKE,
        rawResId = R.raw.wake_morning_birds,
        canLoop = false,
        description = "自然的晨间鸟鸣声，轻柔唤醒"
    ),
    SOFT_PLUCKS_ALARM(
        title = "柔和铃音",
        category = SleepSoundCategory.ALARM,
        rawResId = R.raw.alarm_soft_plucks,
        canLoop = true,
        description = "柔和的拨弦铃音，作为兜底闹钟"
    );

    companion object {
        /**
         * Resolve a [SleepSoundKey] from its name string.
         * Falls back to a default based on the category if the key is invalid.
         */
        fun resolve(keyValue: String): SleepSoundKey {
            return try {
                valueOf(keyValue)
            } catch (_: IllegalArgumentException) {
                RAIN_GENTLE
            }
        }

        fun resolveWake(keyValue: String): SleepSoundKey {
            return try {
                val key = valueOf(keyValue)
                if (key.category == SleepSoundCategory.WAKE) key else MORNING_BIRDS
            } catch (_: IllegalArgumentException) {
                MORNING_BIRDS
            }
        }

        fun resolveAlarm(keyValue: String): SleepSoundKey {
            return try {
                val key = valueOf(keyValue)
                if (key.category == SleepSoundCategory.ALARM) key else SOFT_PLUCKS_ALARM
            } catch (_: IllegalArgumentException) {
                SOFT_PLUCKS_ALARM
            }
        }
    }
}

data class SleepSoundItem(
    val key: SleepSoundKey,
    val title: String = key.title,
    val category: SleepSoundCategory = key.category,
    val rawResId: Int = key.rawResId,
    val canLoop: Boolean = key.canLoop,
    val description: String = key.description
)

fun resolveSound(keyValue: String): SleepSoundItem {
    val key = SleepSoundKey.resolve(keyValue)
    return SleepSoundItem(key = key)
}
