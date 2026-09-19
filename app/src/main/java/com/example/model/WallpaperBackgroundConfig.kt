package com.example.model

import java.time.LocalTime

/** The durable wallpaper choice. Image bytes deliberately stay in app-private files. */
enum class WallpaperBackgroundMode { DEFAULT_BLACK, SINGLE_IMAGE, SCHEDULED_IMAGES }
enum class WallpaperImageSlot { SINGLE, DAY, EVENING }
enum class OverlayReadabilityMode { AUTO, LIGHT, DARK }
enum class BackgroundPeriod { DAY, EVENING }

object ReadabilityModeSelector {
    /** Light content needs a dark scrim; dark content needs a light scrim. */
    fun useLightContent(mode: OverlayReadabilityMode, averageLuminance: Float?): Boolean = when (mode) {
        OverlayReadabilityMode.LIGHT -> true
        OverlayReadabilityMode.DARK -> false
        OverlayReadabilityMode.AUTO -> (averageLuminance ?: 0f) < 0.52f
    }
}

data class WallpaperBackgroundConfig(
    val mode: WallpaperBackgroundMode = WallpaperBackgroundMode.DEFAULT_BLACK,
    val dayStartMinutes: Int = 7 * 60,
    val eveningStartMinutes: Int = 19 * 60,
    val hasSingleImage: Boolean = false,
    val hasDayImage: Boolean = false,
    val hasEveningImage: Boolean = false,
    val readabilityMode: OverlayReadabilityMode = OverlayReadabilityMode.AUTO,
    /** Changes only when the bytes or availability of a photo change. */
    val imageRevision: Long = 0L,
    /** Changes whenever a file/configuration changes, so another process invalidates its cache. */
    val revision: Long = 0L
) {
    fun hasImage(slot: WallpaperImageSlot): Boolean = when (slot) {
        WallpaperImageSlot.SINGLE -> hasSingleImage
        WallpaperImageSlot.DAY -> hasDayImage
        WallpaperImageSlot.EVENING -> hasEveningImage
    }
}

data class BackgroundSelection(val period: BackgroundPeriod? = null, val slot: WallpaperImageSlot? = null) {
    val hasImage: Boolean get() = slot != null
}

/** Pure schedule and fallback logic; it deliberately has no Android or file-system dependency. */
object WallpaperSchedule {
    fun activePeriod(dayStartMinutes: Int, eveningStartMinutes: Int, now: LocalTime): BackgroundPeriod {
        val minute = now.hour * 60 + now.minute
        val dayStart = dayStartMinutes.coerceIn(0, 1_439)
        val eveningStart = eveningStartMinutes.coerceIn(0, 1_439)
        if (dayStart == eveningStart) return BackgroundPeriod.DAY
        val isDay = if (dayStart < eveningStart) minute >= dayStart && minute < eveningStart else minute >= dayStart || minute < eveningStart
        return if (isDay) BackgroundPeriod.DAY else BackgroundPeriod.EVENING
    }

    fun select(config: WallpaperBackgroundConfig, now: LocalTime): BackgroundSelection = when (config.mode) {
        WallpaperBackgroundMode.DEFAULT_BLACK -> BackgroundSelection()
        WallpaperBackgroundMode.SINGLE_IMAGE -> BackgroundSelection(slot = WallpaperImageSlot.SINGLE.takeIf { config.hasSingleImage })
        WallpaperBackgroundMode.SCHEDULED_IMAGES -> {
            val period = activePeriod(config.dayStartMinutes, config.eveningStartMinutes, now)
            val preferred = if (period == BackgroundPeriod.DAY) WallpaperImageSlot.DAY else WallpaperImageSlot.EVENING
            val fallback = if (preferred == WallpaperImageSlot.DAY) WallpaperImageSlot.EVENING else WallpaperImageSlot.DAY
            BackgroundSelection(period, preferred.takeIf(config::hasImage) ?: fallback.takeIf(config::hasImage))
        }
    }

}
