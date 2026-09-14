package com.example.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

enum class DotState {
    COMPLETED,
    CURRENT,
    FUTURE
}

sealed interface GoalStatus {
    object NoGoal : GoalStatus

    data class NotStarted(
        val totalDays: Int,
        val daysUntilStart: Long
    ) : GoalStatus

    data class Active(
        val totalDays: Int,
        val currentDay: Int,
        val daysRemaining: Long,
        val progressPercent: Int
    ) : GoalStatus

    data class LastDay(
        val totalDays: Int,
        val progressPercent: Int = 100
    ) : GoalStatus

    data class Completed(
        val totalDays: Int,
        val progressPercent: Int = 100
    ) : GoalStatus
}

data class GoalSnapshot(
    val goalData: GoalData?,
    val status: GoalStatus,
    val headerText: String,
    val titleText: String,
    val footerText: String,
    val totalDots: Int,
    val dotStates: List<DotState>,
    val progressPercent: Int
)

object GoalProgressCalculator {

    const val MIN_DAYS = 1
    const val MAX_DAYS = 365

    sealed interface ValidationResult {
        object Valid : ValidationResult
        data class Error(val message: String) : ValidationResult
    }

    /**
     * Calculate total inclusive days between start and end date.
     * 01.01 -> 01.01 = 1 day
     * 01.01 -> 02.01 = 2 days
     */
    fun calculateTotalDays(startDate: LocalDate, endDate: LocalDate): Int {
        val diff = ChronoUnit.DAYS.between(startDate, endDate)
        return (diff + 1).toInt()
    }

    /**
     * Calculates the maximum permissible end date for a given start date.
     * Start date + 364 days inclusive = 365 days.
     */
    fun getMaxEndDate(startDate: LocalDate): LocalDate {
        return startDate.plusDays((MAX_DAYS - 1).toLong())
    }

    /**
     * Validates goal input parameters.
     */
    fun validateGoal(name: String, startDate: LocalDate, endDate: LocalDate): ValidationResult {
        if (name.trim().isEmpty()) {
            return ValidationResult.Error("Goal name cannot be empty")
        }
        if (endDate.isBefore(startDate)) {
            return ValidationResult.Error("End date cannot be earlier than start date")
        }
        val total = calculateTotalDays(startDate, endDate)
        if (total < MIN_DAYS) {
            return ValidationResult.Error("Duration must be at least 1 day")
        }
        if (total > MAX_DAYS) {
            return ValidationResult.Error("Duration cannot exceed 365 days (selected $total days)")
        }
        return ValidationResult.Valid
    }

    /**
     * Pure function returning an immutable snapshot of the goal state based strictly on
     * [goalData] and current calendar [today].
     */
    fun computeSnapshot(goalData: GoalData?, today: LocalDate = LocalDate.now()): GoalSnapshot {
        if (goalData == null) {
            return GoalSnapshot(
                goalData = null,
                status = GoalStatus.NoGoal,
                headerText = "NO GOAL SET",
                titleText = "Open Goal Dots to set target",
                footerText = "Tap app to begin",
                totalDots = 0,
                dotStates = emptyList(),
                progressPercent = 0
            )
        }

        val startDate = goalData.startDate
        val endDate = goalData.endDate
        val totalDays = calculateTotalDays(startDate, endDate).coerceIn(MIN_DAYS, MAX_DAYS)

        val status: GoalStatus
        val headerText: String
        val footerText: String
        val progressPercent: Int
        val dotStates = ArrayList<DotState>(totalDays)

        when {
            // Case 1: NOT_STARTED
            today.isBefore(startDate) -> {
                val daysUntilStart = ChronoUnit.DAYS.between(today, startDate)
                status = GoalStatus.NotStarted(
                    totalDays = totalDays,
                    daysUntilStart = daysUntilStart
                )
                headerText = "DAY 0 / $totalDays"
                val pluralDays = if (daysUntilStart == 1L) "day" else "days"
                footerText = "Starts in $daysUntilStart $pluralDays"
                progressPercent = 0
                // All dots are future
                for (i in 0 until totalDays) {
                    dotStates.add(DotState.FUTURE)
                }
            }

            // Case 4: COMPLETED (strictly after endDate)
            today.isAfter(endDate) -> {
                status = GoalStatus.Completed(
                    totalDays = totalDays,
                    progressPercent = 100
                )
                headerText = "DAY $totalDays / $totalDays"
                footerText = "Goal completed • 100%"
                progressPercent = 100
                // All dots are completed
                for (i in 0 until totalDays) {
                    dotStates.add(DotState.COMPLETED)
                }
            }

            // Case 3: LAST_DAY (today == endDate)
            today.isEqual(endDate) -> {
                status = GoalStatus.LastDay(
                    totalDays = totalDays,
                    progressPercent = 100
                )
                headerText = "DAY $totalDays / $totalDays"
                footerText = "Final day • 100%"
                progressPercent = 100
                // Days 0 until totalDays - 2 are completed, last one is CURRENT
                for (i in 0 until totalDays) {
                    if (i == totalDays - 1) {
                        dotStates.add(DotState.CURRENT)
                    } else {
                        dotStates.add(DotState.COMPLETED)
                    }
                }
            }

            // Case 2: ACTIVE (startDate <= today < endDate)
            else -> {
                val currentDay = (ChronoUnit.DAYS.between(startDate, today) + 1).toInt()
                val daysRemaining = ChronoUnit.DAYS.between(today, endDate)
                val calculatedProgress = ((currentDay.toFloat() / totalDays.toFloat()) * 100f)
                    .roundToInt()
                    .coerceIn(0, 100)

                status = GoalStatus.Active(
                    totalDays = totalDays,
                    currentDay = currentDay,
                    daysRemaining = daysRemaining,
                    progressPercent = calculatedProgress
                )
                headerText = "DAY $currentDay / $totalDays"
                progressPercent = calculatedProgress

                val remainingStr = if (daysRemaining == 1L) "1 day left" else "$daysRemaining days left"
                footerText = "$calculatedProgress% complete • $remainingStr"

                // Dot allocation:
                // days 0 until currentDay - 2 => COMPLETED
                // day currentDay - 1 => CURRENT
                // days currentDay until totalDays - 1 => FUTURE
                val currentIndex = currentDay - 1
                for (i in 0 until totalDays) {
                    when {
                        i < currentIndex -> dotStates.add(DotState.COMPLETED)
                        i == currentIndex -> dotStates.add(DotState.CURRENT)
                        else -> dotStates.add(DotState.FUTURE)
                    }
                }
            }
        }

        return GoalSnapshot(
            goalData = goalData,
            status = status,
            headerText = headerText,
            titleText = goalData.name.trim(),
            footerText = footerText,
            totalDots = totalDays,
            dotStates = dotStates,
            progressPercent = progressPercent
        )
    }
}
