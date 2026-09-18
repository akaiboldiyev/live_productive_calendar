package com.example.model

/**
 * Persisted wallpaper background choice. The image itself deliberately has no URI here:
 * selected media is copied to app-private storage before this configuration is saved.
 */
enum class BackgroundType {
    DEFAULT_BLACK,
    IMAGE
}

data class WallpaperBackgroundConfig(
    val type: BackgroundType = BackgroundType.DEFAULT_BLACK,
    /** Changes on every import so a same-type IMAGE update invalidates another process's cache. */
    val revision: Long = 0L
)
