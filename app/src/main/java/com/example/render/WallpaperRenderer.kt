package com.example.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.example.data.GoalLoadState
import com.example.model.AppearanceSettings
import com.example.model.ColorTheme
import com.example.model.DotState
import com.example.model.GoalSnapshot
import com.example.model.GoalStatus
import kotlin.math.max
import kotlin.math.min

/**
 * Geometric definition of the visible on-screen viewport within the wallpaper canvas.
 * Accommodates wallpaper scrolling offsets and displays content precisely in the visual center.
 */
data class ViewportBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = max(1f, right - left)
    val height: Float get() = max(1f, bottom - top)
    val centerX: Float get() = left + (width / 2f)
    val centerY: Float get() = top + (height / 2f)
}

/**
 * Pure, high-performance canvas renderer for the Goal Dots wallpaper.
 * Reused identically in WallpaperService and Compose preview.
 */
class WallpaperRenderer {

    companion object {
        private const val TAG_RENDER = "GOAL_RENDER"
    }

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

    // Paint for StaticLayout multi-line title.
    // NOTE: For StaticLayout, textAlign MUST be LEFT because Alignment.ALIGN_CENTER handles horizontal centering.
    private val titleLayoutPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    // Paint for single-line canvas.drawText (e.g. empty state title)
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
     * Backward-compatible convenience overload for Compose Preview.
     */
    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        snapshot: GoalSnapshot,
        density: Float = 2.5f
    ) {
        val viewport = ViewportBounds(0f, 0f, width.toFloat(), height.toFloat())
        val loadState = if (snapshot.status is GoalStatus.NoGoal) {
            GoalLoadState.NoGoal
        } else {
            GoalLoadState.Loaded(snapshot.goalData ?: return)
        }
        render(canvas, width, height, viewport, loadState, snapshot, density)
    }

    /**
     * Full viewport-aware render method.
     * Guarantees that all content (dots, title, header, footer) shares ONE coherent
     * coordinate system centered at [viewport.centerX].
     */
    fun render(
        canvas: Canvas,
        surfaceWidth: Int,
        surfaceHeight: Int,
        viewport: ViewportBounds,
        loadState: GoalLoadState,
        snapshot: GoalSnapshot,
        density: Float = 2.5f
    ) {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return

        Log.d(
            TAG_RENDER,
            "render requested: surface=(${surfaceWidth}x$surfaceHeight), viewport=(${viewport.left.toInt()}..${viewport.right.toInt()}), loadState=${loadState::class.simpleName}, status=${snapshot.status::class.simpleName}, dots=${snapshot.totalDots}"
        )

        // Prevent false "No Goal Active" flash while async DataStore is loading
        if (loadState is GoalLoadState.Loading) {
            Log.d(TAG_RENDER, "Skipping render: DataStore is in Loading state")
            return
        }

        val theme = snapshot.goalData?.settings?.theme ?: ColorTheme.OBSIDIAN_CORAL

        // 1. Draw Background covering the FULL surface canvas
        bgPaint.color = theme.backgroundColor
        canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), bgPaint)

        // If explicitly confirmed that no goal is set or Error, render placeholder
        if (loadState is GoalLoadState.NoGoal || snapshot.status is GoalStatus.NoGoal) {
            renderEmptyState(canvas, viewport, theme, density)
            Log.d(TAG_RENDER, "render completed: empty state drawn")
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
        titleLayoutPaint.color = theme.primaryTextColor
        footerTextPaint.color = theme.secondaryTextColor

        // Responsive font sizing based on visible viewport width
        val headerSize = (viewport.width * 0.038f).coerceIn(12f * density, 18f * density)
        val titleSize = (viewport.width * 0.052f).coerceIn(16f * density, 26f * density)
        val footerSize = (viewport.width * 0.036f).coerceIn(12f * density, 16f * density)

        headerTextPaint.textSize = headerSize
        titleLayoutPaint.textSize = titleSize
        footerTextPaint.textSize = footerSize

        // Safe area margins relative to visible viewport
        val minTopMargin = viewport.height * 0.16f
        val maxTopMargin = viewport.height * 0.30f
        val topMargin = minTopMargin + (maxTopMargin - minTopMargin) * settings.verticalBias

        val bottomMargin = viewport.height * 0.15f
        val horizontalMargin = viewport.width * 0.08f
        val availableContentWidth = viewport.width - (horizontalMargin * 2f)

        var cursorY = topMargin

        // 2. Render Header (e.g. "DAY 47 / 180")
        if (settings.showStatusHeader && snapshot.headerText.isNotEmpty()) {
            val headerFontMetrics = headerTextPaint.fontMetrics
            val headerHeight = headerFontMetrics.descent - headerFontMetrics.ascent
            canvas.drawText(
                snapshot.headerText,
                viewport.centerX,
                cursorY - headerFontMetrics.ascent,
                headerTextPaint
            )
            cursorY += headerHeight + (10f * density)
        }

        // 3. Render Goal Title (with StaticLayout for multi-line support)
        val titleLayout = buildCenteredTextLayout(
            text = snapshot.titleText,
            paint = titleLayoutPaint,
            width = availableContentWidth.toInt(),
            maxLines = 3
        )
        val contentLeft = viewport.left + horizontalMargin
        canvas.save()
        canvas.translate(contentLeft, cursorY)
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

        val availableGridHeight = (viewport.height - bottomMargin - totalFooterAlloc - cursorY).coerceAtLeast(60f * density)

        // 5. Calculate Grid Layout
        val gridResult = GridCalculator.calculateGrid(
            totalDots = snapshot.totalDots,
            availableWidth = availableContentWidth,
            availableHeight = availableGridHeight,
            minDotRadius = 3f * density,
            maxDotRadius = 14f * density,
            spacingRatio = 0.95f
        )

        // Render Dots in the exact same coordinate system
        val radius = gridResult.dotRadius
        val ringRadius = radius * 1.45f

        for (i in 0 until min(snapshot.totalDots, gridResult.dotPositions.size)) {
            val pos = gridResult.dotPositions[i]
            val dotX = contentLeft + pos.x
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
                    canvas.drawCircle(dotX, dotY, ringRadius, currentDotRingPaint)
                    canvas.drawCircle(dotX, dotY, radius, currentDotPaint)
                }
            }
        }

        // 6. Render Footer
        if (totalFooterAlloc > 0f && snapshot.footerText.isNotEmpty()) {
            val footerY = cursorY + gridResult.startY + gridResult.gridHeight + footerSpacing
            canvas.drawText(
                snapshot.footerText,
                viewport.centerX,
                footerY - footerFontMetrics.ascent,
                footerTextPaint
            )
        }
        Log.d(TAG_RENDER, "render completed successfully: ${snapshot.totalDots} dots rendered")
    }

    private fun renderEmptyState(
        canvas: Canvas,
        viewport: ViewportBounds,
        theme: ColorTheme,
        density: Float
    ) {
        headerTextPaint.color = theme.accentTextColor
        headerTextPaint.textSize = (viewport.width * 0.042f).coerceIn(14f * density, 20f * density)

        titleTextPaint.color = theme.primaryTextColor
        titleTextPaint.textSize = (viewport.width * 0.06f).coerceIn(18f * density, 28f * density)

        emptySubtextPaint.color = theme.secondaryTextColor
        emptySubtextPaint.textSize = (viewport.width * 0.038f).coerceIn(12f * density, 16f * density)

        val centerY = viewport.centerY

        // Draw 5x5 decorative mini matrix centered at viewport.centerX
        val cols = 5
        val rows = 5
        val dotRadius = 4f * density
        val spacing = 7f * density
        val matrixWidth = cols * (2 * dotRadius) + (cols - 1) * spacing
        val matrixHeight = rows * (2 * dotRadius) + (rows - 1) * spacing
        val matrixStartX = viewport.centerX - (matrixWidth / 2f)
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

        canvas.drawText("GOAL DOTS", viewport.centerX, centerY + (10f * density), headerTextPaint)
        canvas.drawText("No Goal Active", viewport.centerX, centerY + (36f * density), titleTextPaint)
        canvas.drawText("Open app to create your countdown", viewport.centerX, centerY + (60f * density), emptySubtextPaint)
    }

    private fun buildCenteredTextLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int
    ): StaticLayout {
        val safeWidth = max(10, width)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
    }
}
