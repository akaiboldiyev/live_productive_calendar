package com.example.render

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.model.OverlayReadabilityMode
import com.example.model.ReadabilityModeSelector

data class OverlayContentColors(
    val completedDot: Int,
    val futureDot: Int,
    val currentDot: Int,
    val currentDotRing: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val accentText: Int,
    /** A small opposite-colour halo for individual text and dots, never a backdrop shape. */
    val contrastHalo: Int
)
data class OverlayReadabilityStyle(val useLightContent: Boolean, val colors: OverlayContentColors)

/** Caches a contrast decision on bitmap/config changes; drawing itself never samples pixels. */
class OverlayReadabilityRenderer {
    companion object { private const val TAG = "GOAL_READABILITY" }
    private var style = lightStyle()

    fun update(bitmap: Bitmap?, mode: OverlayReadabilityMode): OverlayReadabilityStyle {
        val useLight = ReadabilityModeSelector.useLightContent(mode, bitmap?.let(::averageLuminance))
        style = if (useLight) lightStyle() else darkStyle()
        Log.i(TAG, "Contrast mode selected: ${if (useLight) "light content" else "dark content"}, requested=$mode")
        return style
    }

    fun currentStyle() = style

    private fun averageLuminance(bitmap: Bitmap): Float {
        val xStart = bitmap.width / 6; val xEnd = bitmap.width * 5 / 6
        val yStart = bitmap.height / 8; val yEnd = bitmap.height * 7 / 8
        var total = 0f; var samples = 0
        for (y in yStart until yEnd step ((yEnd - yStart).coerceAtLeast(1) / 18).coerceAtLeast(1)) for (x in xStart until xEnd step ((xEnd - xStart).coerceAtLeast(1) / 14).coerceAtLeast(1)) {
            val color = bitmap.getPixel(x, y)
            total += (.2126f * Color.red(color) + .7152f * Color.green(color) + .0722f * Color.blue(color)) / 255f; samples++
        }
        return if (samples == 0) 0f else total / samples
    }

    private fun lightStyle() = OverlayReadabilityStyle(true, OverlayContentColors(
        completedDot = Color.WHITE, futureDot = Color.rgb(184, 192, 204), currentDot = Color.rgb(255, 150, 105), currentDotRing = Color.argb(180, 255, 170, 125), primaryText = Color.WHITE, secondaryText = Color.rgb(222, 228, 238), accentText = Color.rgb(255, 176, 130), contrastHalo = Color.argb(185, 0, 0, 0)
    ))
    private fun darkStyle() = OverlayReadabilityStyle(false, OverlayContentColors(
        completedDot = Color.rgb(26, 30, 34), futureDot = Color.rgb(82, 88, 94), currentDot = Color.rgb(130, 54, 28), currentDotRing = Color.argb(175, 130, 54, 28), primaryText = Color.rgb(18, 22, 26), secondaryText = Color.rgb(58, 63, 68), accentText = Color.rgb(112, 43, 20), contrastHalo = Color.argb(145, 255, 255, 255)
    ))
}
