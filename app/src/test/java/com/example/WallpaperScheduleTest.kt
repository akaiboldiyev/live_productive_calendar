package com.example

import com.example.model.BackgroundPeriod
import com.example.model.OverlayReadabilityMode
import com.example.model.ReadabilityModeSelector
import com.example.model.WallpaperBackgroundConfig
import com.example.model.WallpaperBackgroundMode
import com.example.model.WallpaperImageSlot
import com.example.model.WallpaperSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class WallpaperScheduleTest {
    @Test fun `day and evening selection uses normal schedule boundaries`() {
        assertEquals(BackgroundPeriod.DAY, WallpaperSchedule.activePeriod(7 * 60, 19 * 60, LocalTime.of(7, 0)))
        assertEquals(BackgroundPeriod.DAY, WallpaperSchedule.activePeriod(7 * 60, 19 * 60, LocalTime.of(18, 59)))
        assertEquals(BackgroundPeriod.EVENING, WallpaperSchedule.activePeriod(7 * 60, 19 * 60, LocalTime.of(19, 0)))
    }

    @Test fun `schedule correctly crosses midnight`() {
        assertEquals(BackgroundPeriod.DAY, WallpaperSchedule.activePeriod(22 * 60 + 30, 9 * 60, LocalTime.of(23, 0)))
        assertEquals(BackgroundPeriod.DAY, WallpaperSchedule.activePeriod(22 * 60 + 30, 9 * 60, LocalTime.of(8, 59)))
        assertEquals(BackgroundPeriod.EVENING, WallpaperSchedule.activePeriod(22 * 60 + 30, 9 * 60, LocalTime.of(12, 0)))
    }

    @Test fun `missing preferred scheduled image falls back to available image`() {
        val config = WallpaperBackgroundConfig(mode = WallpaperBackgroundMode.SCHEDULED_IMAGES, hasEveningImage = true)
        val selection = WallpaperSchedule.select(config, LocalTime.of(10, 0))
        assertEquals(BackgroundPeriod.DAY, selection.period)
        assertEquals(WallpaperImageSlot.EVENING, selection.slot)
    }

    @Test fun `readability choice is stable for auto and explicit modes`() {
        assertFalse(ReadabilityModeSelector.useLightContent(OverlayReadabilityMode.AUTO, .9f))
        assertTrue(ReadabilityModeSelector.useLightContent(OverlayReadabilityMode.AUTO, .1f))
        assertTrue(ReadabilityModeSelector.useLightContent(OverlayReadabilityMode.LIGHT, .9f))
        assertFalse(ReadabilityModeSelector.useLightContent(OverlayReadabilityMode.DARK, .1f))
    }

    @Test fun `scheduled mode without images always selects black fallback`() {
        val config = WallpaperBackgroundConfig(mode = WallpaperBackgroundMode.SCHEDULED_IMAGES)
        val selection = WallpaperSchedule.select(config, LocalTime.of(10, 0))
        assertEquals(BackgroundPeriod.DAY, selection.period)
        assertFalse(selection.hasImage)
    }
}
