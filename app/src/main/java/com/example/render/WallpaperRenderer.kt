package com.example.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.model.AppearanceSettings
import com.example.model.ColorTheme
import com.example.model.DotState
import com.example.model.GoalSnapshot
import com.example.model.GoalStatus
import kotlin.math.max
import kotlin.math.min

/**
 * Pure, high-performance canvas renderer for the Goal Dots wallpaper.
 * Reused identically in WallpaperService and Compose preview.
 */
class WallpaperRenderer {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val completedDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val futureDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val currentDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val currentDotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val headerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        letterSpacing = 0.12f
    }

    private val titleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val footerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        letterSpacing = 0.05f
    }

    private val emptySubtextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    /**
     * Render the goal snapshot onto the provided [canvas].
     */
    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        snapshot: GoalSnapshot,
        density: Float = 2.5f
    ) {
        if (width <= 0 || height <= 0) return

        val theme = snapshot.goalData?.settings?.theme ?: ColorTheme.OBSIDIAN_CORAL

        // 1. Draw Background
        bgPaint.color = theme.backgroundColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // If no goal set, render placeholder
        if (snapshot.status is GoalStatus.NoGoal) {
            renderEmptyState(canvas, width, height, theme, density)
            return
        }

        val settings = snapshot.goalData?.settings ?: AppearanceSettings()

        // Configure theme paints
        completedDotPaint.color = theme.completedDotColor
        futureDotPaint.color = theme.futureDotColor
        currentDotPaint.color = theme.currentDotColor
        currentDotRingPaint.color = theme.currentDotRingColor
        currentDotRingPaint.strokeWidth = (2f * density).coerceAtLeast(1.5f)

        headerTextPaint.color = theme.accentTextColor
        titleTextPaint.color = theme.primaryTextColor
        footerTextPaint.color = theme.secondaryTextColor

        // Responsive font sizing
        val headerSize = (width * 0.038f).coerceIn(12f * density, 18f * density)
        val titleSize = (width * 0.052f).coerceIn(16f * density, 26f * density)
        val footerSize = (width * 0.036f).coerceIn(12f * density, 16f * density)

        headerTextPaint.textSize = headerSize
        titleTextPaint.textSize = titleSize
        footerTextPaint.textSize = footerSize

        // Safe area definition:
        // Top margin ~20% of canvas height (reserves space for lockscreen clock / notifications)
        // Bottom margin ~16% of canvas height (reserves space for lockscreen shortcuts / dock)
        // Vertical bias allows user to shift content up/down
        val minTopMargin = height * 0.16f
        val maxTopMargin = height * 0.30f
        val topMargin = minTopMargin + (maxTopMargin - minTopMargin) * settings.verticalBias

        val bottomMargin = height * 0.15f
        val horizontalMargin = width * 0.08f
        val availableContentWidth = width - (horizontalMargin * 2f)

        var cursorY = topMargin

        // 2. Render Header (e.g. "DAY 47 / 180")
        if (settings.showStatusHeader && snapshot.headerText.isNotEmpty()) {
            val headerFontMetrics = headerTextPaint.fontMetrics
            val headerHeight = headerFontMetrics.descent - headerFontMetrics.ascent
            canvas.drawText(
                snapshot.headerText,
                width / 2f,
                cursorY - headerFontMetrics.ascent,
                headerTextPaint
            )
            cursorY += headerHeight + (10f * density)
        }

        // 3. Render Goal Title (with StaticLayout for multi-line support)
        val titleLayout = buildTextLayout(
            text = snapshot.titleText,
            paint = titleTextPaint,
            width = availableContentWidth.toInt(),
            maxLines = 3
        )
        canvas.save()
        canvas.translate(horizontalMargin, cursorY)
        titleLayout.draw(canvas)
        canvas.restore()
        cursorY += titleLayout.height + (16f * density)

        // 4. Determine Remaining Height for Grid and Footer
        val footerFontMetrics = footerTextPaint.fontMetrics
        val footerHeight = footerFontMetrics.descent - footerFontMetrics.ascent
        val footerSpacing = 16f * density
        val totalFooterAlloc = if (settings.showPercentage || settings.showRemainingDays) {
            footerHeight + footerSpacing
        } else {
            0f
        }

        val availableGridHeight = (height - bottomMargin - totalFooterAlloc - cursorY).coerceAtLeast(60f * density)

        // 5. Calculate Grid Layout
        val gridResult = GridCalculator.calculateGrid(
            totalDots = snapshot.totalDots,
            availableWidth = availableContentWidth,
            availableHeight = availableGridHeight,
            minDotRadius = 3f * density,
            maxDotRadius = 14f * density,
            spacingRatio = 0.95f
        )

        // Render Dots
        val gridOffsetX = horizontalMargin + gridResult.startX
        val gridOffsetY = cursorY + gridResult.startY

        val radius = gridResult.dotRadius
        val ringRadius = radius * 1.45f

        for (i in 0 until min(snapshot.totalDots, gridResult.dotPositions.size)) {
            val pos = gridResult.dotPositions[i]
            val dotX = horizontalMargin + pos.x
            val dotY = cursorY + pos.y
            val state = if (i < snapshot.dotStates.size) snapshot.dotStates[i] else DotState.FUTURE

            when (state) {
                DotState.COMPLETED -> {
                    canvas.drawCircle(dotX, dotY, radius, completedDotPaint)
                }
                DotState.FUTURE -> {
                    canvas.drawCircle(dotX, dotY, radius, futureDotPaint)
                }
                DotState.CURRENT -> {
                    // Draw glowing outer ring first
                    canvas.drawCircle(dotX, dotY, ringRadius, currentDotRingPaint)
                    // Draw filled accent center
                    canvas.drawCircle(dotX, dotY, radius, currentDotPaint)
                }
            }
        }

        // 6. Render Footer
        if (totalFooterAlloc > 0f && snapshot.footerText.isNotEmpty()) {
            val footerY = cursorY + gridResult.startY + gridResult.gridHeight + footerSpacing
            canvas.drawText(
                snapshot.footerText,
                width / 2f,
                footerY - footerFontMetrics.ascent,
                footerTextPaint
            )
        }
    }

    private fun renderEmptyState(
        canvas: Canvas,
        width: Int,
        height: Int,
        theme: ColorTheme,
        density: Float
    ) {
        // Render welcoming placeholder layout
        headerTextPaint.color = theme.accentTextColor
        headerTextPaint.textSize = (width * 0.042f).coerceIn(14f * density, 20f * density)

        titleTextPaint.color = theme.primaryTextColor
        titleTextPaint.textSize = (width * 0.06f).coerceIn(18f * density, 28f * density)

        emptySubtextPaint.color = theme.secondaryTextColor
        emptySubtextPaint.textSize = (width * 0.038f).coerceIn(12f * density, 16f * density)

        val centerY = height * 0.46f

        // Draw 5x5 decorative mini matrix
        val cols = 5
        val rows = 5
        val dotRadius = 4f * density
        val spacing = 7f * density
        val matrixWidth = cols * (2 * dotRadius) + (cols - 1) * spacing
        val matrixHeight = rows * (2 * dotRadius) + (rows - 1) * spacing
        val matrixStartX = (width - matrixWidth) / 2f
        val matrixStartY = centerY - matrixHeight - (28f * density)

        futureDotPaint.color = theme.futureDotColor
        currentDotPaint.color = theme.currentDotColor
        currentDotRingPaint.color = theme.currentDotRingColor
        currentDotRingPaint.strokeWidth = 2f * density

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cx = matrixStartX + c * (2 * dotRadius + spacing) + dotRadius
                val cy = matrixStartY + r * (2 * dotRadius + spacing) + dotRadius
                if (r == 2 && c == 2) {
                    canvas.drawCircle(cx, cy, dotRadius * 1.5f, currentDotRingPaint)
                    canvas.drawCircle(cx, cy, dotRadius, currentDotPaint)
                } else {
                    canvas.drawCircle(cx, cy, dotRadius, futureDotPaint)
                }
            }
        }

        canvas.drawText("GOAL DOTS", width / 2f, centerY + (10f * density), headerTextPaint)
        canvas.drawText("No Goal Active", width / 2f, centerY + (36f * density), titleTextPaint)
        canvas.drawText("Open app to create your countdown", width / 2f, centerY + (60f * density), emptySubtextPaint)
    }

    private fun buildTextLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int
    ): StaticLayout {
        val safeWidth = max(10, width)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
    }
}
