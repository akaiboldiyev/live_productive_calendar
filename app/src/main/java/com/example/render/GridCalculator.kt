package com.example.render

import android.graphics.PointF
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class GridLayoutResult(
    val columns: Int,
    val rows: Int,
    val dotRadius: Float,
    val horizontalSpacing: Float,
    val verticalSpacing: Float,
    val gridWidth: Float,
    val gridHeight: Float,
    val startX: Float,
    val startY: Float,
    val dotPositions: List<PointF>
)

object GridCalculator {

    /**
     * Calculate optimal dot grid layout fitting inside [availableWidth] and [availableHeight].
     */
    fun calculateGrid(
        totalDots: Int,
        availableWidth: Float,
        availableHeight: Float,
        minDotRadius: Float = 4f,
        maxDotRadius: Float = 24f,
        spacingRatio: Float = 0.9f
    ): GridLayoutResult {
        if (totalDots <= 0 || availableWidth <= 0f || availableHeight <= 0f) {
            return GridLayoutResult(
                columns = 0,
                rows = 0,
                dotRadius = 0f,
                horizontalSpacing = 0f,
                verticalSpacing = 0f,
                gridWidth = 0f,
                gridHeight = 0f,
                startX = 0f,
                startY = 0f,
                dotPositions = emptyList()
            )
        }

        // Determine candidate range of columns to evaluate
        val minCols = when {
            totalDots <= 4 -> 1
            totalDots <= 10 -> 2
            totalDots <= 30 -> 3
            totalDots <= 100 -> 5
            else -> 7
        }
        val maxCols = min(totalDots, when {
            totalDots <= 10 -> totalDots
            totalDots <= 60 -> 12
            totalDots <= 180 -> 16
            else -> 22
        })

        var bestColumns = minCols
        var bestRows = ceil(totalDots.toFloat() / minCols).toInt()
        var bestDotRadius = minDotRadius
        var bestScore = Float.NEGATIVE_INFINITY

        val targetAspectRatio = (availableWidth / availableHeight).coerceIn(0.4f, 1.2f)

        for (c in minCols..maxCols) {
            val r = ceil(totalDots.toFloat() / c.toFloat()).toInt()

            // Dimensions multiplier: c dots + (c - 1) spaces
            val widthUnits = 2f * (c + (c - 1) * spacingRatio)
            val heightUnits = 2f * (r + (r - 1) * spacingRatio)

            val maxRadiusWidth = availableWidth / widthUnits
            val maxRadiusHeight = availableHeight / heightUnits

            val theoreticalRadius = min(maxRadiusWidth, maxRadiusHeight)
            val effectiveRadius = theoreticalRadius.coerceIn(minDotRadius, maxDotRadius)

            val actualGridWidth = widthUnits * effectiveRadius
            val actualGridHeight = heightUnits * effectiveRadius

            if (actualGridWidth > availableWidth || actualGridHeight > availableHeight) {
                // If it doesn't fit with minDotRadius, downscale slightly
                continue
            }

            // Evaluation score:
            // 1. Radius weight (we want readable, pleasant dots)
            val radiusScore = effectiveRadius * 10f

            // 2. Aspect ratio suitability (penalize tall stringy columns or flat wide lines)
            val gridAspect = actualGridWidth / actualGridHeight
            val aspectPenalty = kotlin.math.abs(gridAspect - targetAspectRatio) * 12f

            // 3. Penalty for ragged incomplete last row
            val remainder = totalDots % c
            val emptySlots = if (remainder == 0) 0 else (c - remainder)
            val emptySlotPenalty = (emptySlots.toFloat() / c.toFloat()) * 8f

            val score = radiusScore - aspectPenalty - emptySlotPenalty

            if (score > bestScore) {
                bestScore = score
                bestColumns = c
                bestRows = r
                bestDotRadius = effectiveRadius
            }
        }

        // If no candidate succeeded (e.g. extreme small available area), fallback
        if (bestScore == Float.NEGATIVE_INFINITY) {
            bestColumns = max(1, min(totalDots, 10))
            bestRows = ceil(totalDots.toFloat() / bestColumns).toInt()
            val widthUnits = 2f * (bestColumns + (bestColumns - 1) * spacingRatio)
            val heightUnits = 2f * (bestRows + (bestRows - 1) * spacingRatio)
            bestDotRadius = min(availableWidth / widthUnits, availableHeight / heightUnits).coerceAtLeast(2f)
        }

        val horizontalSpacing = bestDotRadius * 2f * spacingRatio
        val verticalSpacing = bestDotRadius * 2f * spacingRatio

        val gridWidth = 2f * bestDotRadius * bestColumns + (bestColumns - 1) * horizontalSpacing
        val gridHeight = 2f * bestDotRadius * bestRows + (bestRows - 1) * verticalSpacing

        val startX = (availableWidth - gridWidth) / 2f
        val startY = (availableHeight - gridHeight) / 2f

        // Compute individual dot coordinates, with centered incomplete last row
        val positions = ArrayList<PointF>(totalDots)
        for (rIndex in 0 until bestRows) {
            val isLastRow = (rIndex == bestRows - 1)
            val dotsInThisRow = if (isLastRow) {
                val rem = totalDots - (bestRows - 1) * bestColumns
                if (rem > 0) rem else bestColumns
            } else {
                bestColumns
            }

            val rowWidth = dotsInThisRow * (2f * bestDotRadius) + (dotsInThisRow - 1) * horizontalSpacing
            // Center the row horizontally relative to grid bounds
            val rowStartX = startX + (gridWidth - rowWidth) / 2f
            val dotCenterY = startY + rIndex * (2f * bestDotRadius + verticalSpacing) + bestDotRadius

            for (colIndex in 0 until dotsInThisRow) {
                val dotCenterX = rowStartX + colIndex * (2f * bestDotRadius + horizontalSpacing) + bestDotRadius
                positions.add(PointF(dotCenterX, dotCenterY))
            }
        }

        return GridLayoutResult(
            columns = bestColumns,
            rows = bestRows,
            dotRadius = bestDotRadius,
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            startX = startX,
            startY = startY,
            dotPositions = positions
        )
    }
}
