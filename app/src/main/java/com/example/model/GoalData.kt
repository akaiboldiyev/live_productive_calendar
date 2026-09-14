package com.example.model

import java.time.LocalDate

/**
 * Visual color theme configuration for the Goal Dots wallpaper.
 */
data class ColorTheme(
    val id: String,
    val name: String,
    val backgroundColor: Int,
    val completedDotColor: Int,
    val currentDotColor: Int,
    val currentDotRingColor: Int,
    val futureDotColor: Int,
    val primaryTextColor: Int,
    val secondaryTextColor: Int,
    val accentTextColor: Int
) {
    companion object {
        val OBSIDIAN_CORAL = ColorTheme(
            id = "obsidian_coral",
            name = "Obsidian Coral",
            backgroundColor = 0xFF111215.toInt(),
            completedDotColor = 0xFFE2E8F0.toInt(),
            currentDotColor = 0xFFFF6B4A.toInt(),
            currentDotRingColor = 0x66FF6B4A.toInt(),
            futureDotColor = 0xFF2A2D34.toInt(),
            primaryTextColor = 0xFFF1F5F9.toInt(),
            secondaryTextColor = 0xFF94A3B8.toInt(),
            accentTextColor = 0xFFFF7A59.toInt()
        )

        val DEEP_SLATE_MINT = ColorTheme(
            id = "slate_mint",
            name = "Slate Mint",
            backgroundColor = 0xFF0D1117.toInt(),
            completedDotColor = 0xFFF0FDF4.toInt(),
            currentDotColor = 0xFF10B981.toInt(),
            currentDotRingColor = 0x6610B981.toInt(),
            futureDotColor = 0xFF21262D.toInt(),
            primaryTextColor = 0xFFF0F6FC.toInt(),
            secondaryTextColor = 0xFF8B949E.toInt(),
            accentTextColor = 0xFF34D399.toInt()
        )

        val MIDNIGHT_AMBER = ColorTheme(
            id = "midnight_amber",
            name = "Midnight Amber",
            backgroundColor = 0xFF100F0D.toInt(),
            completedDotColor = 0xFFFEF3C7.toInt(),
            currentDotColor = 0xFFF59E0B.toInt(),
            currentDotRingColor = 0x66F59E0B.toInt(),
            futureDotColor = 0xFF2B2823.toInt(),
            primaryTextColor = 0xFFFFFBEB.toInt(),
            secondaryTextColor = 0xFFD97706.toInt(),
            accentTextColor = 0xFFFBBF24.toInt()
        )

        val CYBER_VIOLET = ColorTheme(
            id = "cyber_violet",
            name = "Cyber Violet",
            backgroundColor = 0xFF0F0B15.toInt(),
            completedDotColor = 0xFFF5F3FF.toInt(),
            currentDotColor = 0xFFA855F7.toInt(),
            currentDotRingColor = 0x66A855F7.toInt(),
            futureDotColor = 0xFF292233.toInt(),
            primaryTextColor = 0xFFFAF5FF.toInt(),
            secondaryTextColor = 0xFFC084FC.toInt(),
            accentTextColor = 0xFFC084FC.toInt()
        )

        val MONOCHROME_SILVER = ColorTheme(
            id = "monochrome",
            name = "Monochrome",
            backgroundColor = 0xFF0A0A0A.toInt(),
            completedDotColor = 0xFFFFFFFF.toInt(),
            currentDotColor = 0xFFCCCCCC.toInt(),
            currentDotRingColor = 0x66FFFFFF.toInt(),
            futureDotColor = 0xFF262626.toInt(),
            primaryTextColor = 0xFFEEEEEE.toInt(),
            secondaryTextColor = 0xFF737373.toInt(),
            accentTextColor = 0xFFFFFFFF.toInt()
        )

        val ALL_THEMES = listOf(
            OBSIDIAN_CORAL,
            DEEP_SLATE_MINT,
            MIDNIGHT_AMBER,
            CYBER_VIOLET,
            MONOCHROME_SILVER
        )

        fun findById(id: String): ColorTheme {
            return ALL_THEMES.find { it.id == id } ?: OBSIDIAN_CORAL
        }
    }
}

/**
 * Visual layout and typography settings.
 */
data class AppearanceSettings(
    val themeId: String = ColorTheme.OBSIDIAN_CORAL.id,
    val showPercentage: Boolean = true,
    val showRemainingDays: Boolean = true,
    val showStatusHeader: Boolean = true,
    val verticalBias: Float = 0.5f // 0.0 = higher, 1.0 = lower
) {
    val theme: ColorTheme
        get() = ColorTheme.findById(themeId)
}

/**
 * Domain entity representing a user's goal with calendar start and end dates.
 */
data class GoalData(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val settings: AppearanceSettings = AppearanceSettings()
)
