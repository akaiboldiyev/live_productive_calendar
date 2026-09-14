package com.example

import com.example.model.DotState
import com.example.model.GoalData
import com.example.model.GoalProgressCalculator
import com.example.model.GoalStatus
import com.example.render.GridCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalProgressCalculatorTest {

    @Test
    fun `test total days inclusive calculation`() {
        val start = LocalDate.of(2026, 1, 1)
        val sameDay = LocalDate.of(2026, 1, 1)
        assertEquals(1, GoalProgressCalculator.calculateTotalDays(start, sameDay))

        val nextDay = LocalDate.of(2026, 1, 2)
        assertEquals(2, GoalProgressCalculator.calculateTotalDays(start, nextDay))

        val halfYear = LocalDate.of(2026, 6, 29)
        assertEquals(180, GoalProgressCalculator.calculateTotalDays(start, halfYear))
    }

    @Test
    fun `test NOT_STARTED state`() {
        val start = LocalDate.of(2026, 5, 1)
        val end = LocalDate.of(2026, 5, 30) // 30 days
        val today = LocalDate.of(2026, 4, 25) // 6 days before start

        val goal = GoalData(name = "Startup", startDate = start, endDate = end)
        val snapshot = GoalProgressCalculator.computeSnapshot(goal, today)

        assertTrue(snapshot.status is GoalStatus.NotStarted)
        assertEquals("DAY 0 / 30", snapshot.headerText)
        assertEquals(0, snapshot.progressPercent)
        assertEquals(30, snapshot.dotStates.size)
        assertTrue(snapshot.dotStates.all { it == DotState.FUTURE })
    }

    @Test
    fun `test ACTIVE state day 1`() {
        val start = LocalDate.of(2026, 5, 1)
        val end = LocalDate.of(2026, 5, 10) // 10 days
        val today = LocalDate.of(2026, 5, 1)

        val goal = GoalData(name = "Fitness", startDate = start, endDate = end)
        val snapshot = GoalProgressCalculator.computeSnapshot(goal, today)

        assertTrue(snapshot.status is GoalStatus.Active)
        val active = snapshot.status as GoalStatus.Active
        assertEquals(1, active.currentDay)
        assertEquals(10, active.totalDays)
        assertEquals(9, active.daysRemaining)
        assertEquals(10, snapshot.progressPercent)
        assertEquals("DAY 1 / 10", snapshot.headerText)

        assertEquals(DotState.CURRENT, snapshot.dotStates[0])
        assertTrue(snapshot.dotStates.drop(1).all { it == DotState.FUTURE })
    }

    @Test
    fun `test ACTIVE state mid-point`() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 1, 10) // 10 days
        val today = LocalDate.of(2026, 1, 5) // Day 5

        val goal = GoalData(name = "Habit", startDate = start, endDate = end)
        val snapshot = GoalProgressCalculator.computeSnapshot(goal, today)

        assertTrue(snapshot.status is GoalStatus.Active)
        assertEquals("DAY 5 / 10", snapshot.headerText)
        assertEquals(50, snapshot.progressPercent)

        // Days 1..4 (indices 0..3) must be COMPLETED
        for (i in 0..3) {
            assertEquals(DotState.COMPLETED, snapshot.dotStates[i])
        }
        // Day 5 (index 4) must be CURRENT
        assertEquals(DotState.CURRENT, snapshot.dotStates[4])
        // Days 6..10 (indices 5..9) must be FUTURE
        for (i in 5..9) {
            assertEquals(DotState.FUTURE, snapshot.dotStates[i])
        }
    }

    @Test
    fun `test LAST_DAY state`() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 1, 10) // 10 days
        val today = LocalDate.of(2026, 1, 10) // Last day

        val goal = GoalData(name = "Exam", startDate = start, endDate = end)
        val snapshot = GoalProgressCalculator.computeSnapshot(goal, today)

        assertTrue(snapshot.status is GoalStatus.LastDay)
        assertEquals("DAY 10 / 10", snapshot.headerText)
        assertEquals(100, snapshot.progressPercent)

        for (i in 0..8) {
            assertEquals(DotState.COMPLETED, snapshot.dotStates[i])
        }
        assertEquals(DotState.CURRENT, snapshot.dotStates[9])
    }

    @Test
    fun `test COMPLETED state`() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 1, 10) // 10 days
        val today = LocalDate.of(2026, 1, 15) // after end

        val goal = GoalData(name = "Finished", startDate = start, endDate = end)
        val snapshot = GoalProgressCalculator.computeSnapshot(goal, today)

        assertTrue(snapshot.status is GoalStatus.Completed)
        assertEquals("DAY 10 / 10", snapshot.headerText)
        assertEquals(100, snapshot.progressPercent)
        assertTrue(snapshot.dotStates.all { it == DotState.COMPLETED })
    }

    @Test
    fun `test GridCalculator with various dot counts`() {
        val dotCounts = listOf(1, 7, 30, 90, 180, 365)
        for (count in dotCounts) {
            val result = GridCalculator.calculateGrid(
                totalDots = count,
                availableWidth = 1080f,
                availableHeight = 1600f
            )
            assertEquals(count, result.dotPositions.size)
            assertTrue(result.dotRadius > 0f)
            assertTrue(result.gridWidth <= 1080f)
            assertTrue(result.gridHeight <= 1600f)
        }
    }
}
