package com.example.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import com.example.model.OverlayReadabilityMode
import com.example.model.ReadabilityModeSelector

data class OverlayContentColors(val completedDot: Int, val futureDot: Int, val currentDot: Int, val currentDotRing: Int, val primaryText: Int, val secondaryText: Int, val accentText: Int)
data class OverlayReadabilityStyle(val useLightContent: Boolean, val scrimColor: Int, val colors: OverlayContentColors)

/** Caches a contrast decision on bitmap/config changes; drawing itself never samples pixels. */
class OverlayReadabilityRenderer {
    companion object { private const val TAG = "GOAL_READABILITY" }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var style = lightStyle()

    fun update(bitmap: Bitmap?, mode: OverlayReadabilityMode): OverlayReadabilityStyle {
        val useLight = ReadabilityModeSelector.useLightContent(mode, bitmap?.let(::averageLuminance))
        style = if (useLight) lightStyle() else darkStyle()
        Log.i(TAG, "Contrast mode selected: ${if (useLight) "light content" else "dark content"}, requested=$mode")
        return style
    }

    fun currentStyle() = style

    fun drawOverlayReadabilityLayer(canvas: Canvas, viewport: ViewportBounds) {
        val inset = viewport.width * 0.045f
        val rect = RectF(viewport.left + inset, viewport.top + viewport.height * 0.11f, viewport.right - inset, viewport.top + viewport.height * 0.89f)
        paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, intArrayOf(withAlpha(style.scrimColor, .35f), style.scrimColor, withAlpha(style.scrimColor, .35f)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, viewport.width * .055f, viewport.width * .055f, paint)
        paint.shader = null
    }

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

    private fun lightStyle() = OverlayReadabilityStyle(true, Color.argb(150, 8, 10, 14), OverlayContentColors(Color.WHITE, Color.rgb(184,192,204), Color.rgb(255,150,105), Color.argb(180,255,170,125), Color.WHITE, Color.rgb(222,228,238), Color.rgb(255,176,130)))
    private fun darkStyle() = OverlayReadabilityStyle(false, Color.argb(160, 246,232,210), OverlayContentColors(Color.rgb(26,30,34), Color.rgb(82,88,94), Color.rgb(130,54,28), Color.argb(175,130,54,28), Color.rgb(18,22,26), Color.rgb(58,63,68), Color.rgb(112,43,20)))
    private fun withAlpha(color: Int, factor: Float) = Color.argb((Color.alpha(color) * factor).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
