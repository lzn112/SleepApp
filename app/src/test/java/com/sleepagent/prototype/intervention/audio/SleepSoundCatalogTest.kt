package com.sleepagent.prototype.intervention.audio

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SleepSoundCatalog key resolution and fallback logic.
 */
class SleepSoundCatalogTest {

    @Test
    fun `valid keys resolve correctly`() {
        assertEquals(SleepSoundKey.RAIN_GENTLE, SleepSoundKey.resolve("RAIN_GENTLE"))
        assertEquals(SleepSoundKey.RAIN_LONG, SleepSoundKey.resolve("RAIN_LONG"))
        assertEquals(SleepSoundKey.OCEAN_WAVES, SleepSoundKey.resolve("OCEAN_WAVES"))
        assertEquals(SleepSoundKey.RIVER, SleepSoundKey.resolve("RIVER"))
        assertEquals(SleepSoundKey.NIGHT_FOREST, SleepSoundKey.resolve("NIGHT_FOREST"))
        assertEquals(SleepSoundKey.PINK_NOISE, SleepSoundKey.resolve("PINK_NOISE"))
        assertEquals(SleepSoundKey.BROWN_NOISE, SleepSoundKey.resolve("BROWN_NOISE"))
        assertEquals(SleepSoundKey.WHITE_NOISE, SleepSoundKey.resolve("WHITE_NOISE"))
        assertEquals(SleepSoundKey.MORNING_BIRDS, SleepSoundKey.resolve("MORNING_BIRDS"))
        assertEquals(SleepSoundKey.SOFT_PLUCKS_ALARM, SleepSoundKey.resolve("SOFT_PLUCKS_ALARM"))
    }

    @Test
    fun `invalid key falls back to RAIN_GENTLE`() {
        assertEquals(SleepSoundKey.RAIN_GENTLE, SleepSoundKey.resolve("INVALID_KEY"))
        assertEquals(SleepSoundKey.RAIN_GENTLE, SleepSoundKey.resolve(""))
        assertEquals(SleepSoundKey.RAIN_GENTLE, SleepSoundKey.resolve("random_text"))
    }

    @Test
    fun `wake key resolution falls back to MORNING_BIRDS`() {
        assertEquals(SleepSoundKey.MORNING_BIRDS, SleepSoundKey.resolveWake("MORNING_BIRDS"))
        assertEquals(SleepSoundKey.MORNING_BIRDS, SleepSoundKey.resolveWake("INVALID"))
        // Non-wake key falls back to MORNING_BIRDS
        assertEquals(SleepSoundKey.MORNING_BIRDS, SleepSoundKey.resolveWake("RAIN_GENTLE"))
    }

    @Test
    fun `alarm key resolution falls back to SOFT_PLUCKS_ALARM`() {
        assertEquals(SleepSoundKey.SOFT_PLUCKS_ALARM, SleepSoundKey.resolveAlarm("SOFT_PLUCKS_ALARM"))
        assertEquals(SleepSoundKey.SOFT_PLUCKS_ALARM, SleepSoundKey.resolveAlarm("INVALID"))
        // Non-alarm key falls back to SOFT_PLUCKS_ALARM
        assertEquals(SleepSoundKey.SOFT_PLUCKS_ALARM, SleepSoundKey.resolveAlarm("RAIN_GENTLE"))
    }

    @Test
    fun `resolveSound function returns correct SleepSoundItem`() {
        val item = resolveSound("RAIN_GENTLE")
        assertEquals(SleepSoundKey.RAIN_GENTLE, item.key)
        assertEquals("柔和雨声", item.title)
        assertEquals(SleepSoundCategory.NATURE, item.category)
        assertTrue(item.canLoop)
    }

    @Test
    fun `resolveSound with invalid key falls back to RAIN_GENTLE`() {
        val item = resolveSound("INVALID")
        assertEquals(SleepSoundKey.RAIN_GENTLE, item.key)
    }

    @Test
    fun `all sound keys have unique resource IDs`() {
        val ids = SleepSoundKey.entries.map { it.rawResId }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `sound keys have correct categories`() {
        assertEquals(SleepSoundCategory.NATURE, SleepSoundKey.RAIN_GENTLE.category)
        assertEquals(SleepSoundCategory.NATURE, SleepSoundKey.OCEAN_WAVES.category)
        assertEquals(SleepSoundCategory.NOISE, SleepSoundKey.PINK_NOISE.category)
        assertEquals(SleepSoundCategory.NOISE, SleepSoundKey.BROWN_NOISE.category)
        assertEquals(SleepSoundCategory.WAKE, SleepSoundKey.MORNING_BIRDS.category)
        assertEquals(SleepSoundCategory.ALARM, SleepSoundKey.SOFT_PLUCKS_ALARM.category)
    }
}
