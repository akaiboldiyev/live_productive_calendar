package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GoalLoadState
import com.example.data.GoalRepository
import com.example.model.GoalData
import com.example.model.GoalProgressCalculator
import com.example.model.GoalStatus
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoalEngineLifecycleTest {

    @Test
    fun testDataStorePersistsAndSurvivesRepositoryRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Initial repository saves goal
        val repo1 = GoalRepository.getInstance(context)
        val testGoal = GoalData(
            name = "Test Lifecycle Goal",
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 31)
        )
        repo1.saveGoal(testGoal)

        // 2. Simulate process/engine recreation by creating a fresh repo instance
        val repo2 = GoalRepository(context.applicationContext)
        val loadedState = repo2.goalStateFlow
            .filter { it !is GoalLoadState.Loading }
            .first()

        assertTrue("Expected Loaded state, but got $loadedState", loadedState is GoalLoadState.Loaded)
        val loaded = (loadedState as GoalLoadState.Loaded).goal
        assertEquals("Test Lifecycle Goal", loaded.name)
        assertEquals(LocalDate.of(2026, 1, 1), loaded.startDate)
        assertEquals(LocalDate.of(2026, 1, 31), loaded.endDate)

        // 3. Compute snapshot on the restored goal data
        val today = LocalDate.of(2026, 1, 15)
        val snapshot = GoalProgressCalculator.computeSnapshot(loaded, today)
        assertTrue(snapshot.status is GoalStatus.Active)
        assertEquals(31, snapshot.totalDots)
        assertEquals("DAY 15 / 31", snapshot.headerText)
    }

    @Test
    fun testNullGoalSafelyProducesNoGoalSnapshotWithoutCrashing() {
        val snapshot = GoalProgressCalculator.computeSnapshot(null, LocalDate.now())
        assertNotNull(snapshot)
        assertTrue(snapshot.status is GoalStatus.NoGoal)
        assertEquals(0, snapshot.totalDots)
    }
}
