package com.example.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppearanceSettings
import com.example.model.ColorTheme
import com.example.model.GoalData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

val Context.goalDataStore: DataStore<Preferences> by preferencesDataStore(name = "goal_preferences")

sealed interface GoalLoadState {
    object Loading : GoalLoadState
    object NoGoal : GoalLoadState
    data class Loaded(val goal: GoalData) : GoalLoadState
    data class Error(val message: String) : GoalLoadState
}

class GoalRepository(private val context: Context) {

    companion object {
        private const val TAG_DATASTORE = "GOAL_DATASTORE"
        private const val TAG_SAVE = "GOAL_SAVE"

        private val KEY_GOAL_NAME = stringPreferencesKey("goal_name")
        private val KEY_START_DATE = stringPreferencesKey("start_date")
        private val KEY_END_DATE = stringPreferencesKey("end_date")
        private val KEY_THEME_ID = stringPreferencesKey("theme_id")
        private val KEY_SHOW_PERCENTAGE = booleanPreferencesKey("show_percentage")
        private val KEY_SHOW_REMAINING = booleanPreferencesKey("show_remaining")
        private val KEY_SHOW_HEADER = booleanPreferencesKey("show_header")
        private val KEY_VERTICAL_BIAS = floatPreferencesKey("vertical_bias")

        @Volatile
        private var INSTANCE: GoalRepository? = null

        fun getInstance(context: Context): GoalRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GoalRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Rich state flow explicitly distinguishing Loading, NoGoal, Loaded, and Error states.
     * Prevents false "No Goal Active" flashes on initial app startup or wallpaper engine creation.
     */
    val goalStateFlow: Flow<GoalLoadState> = flow {
        Log.d(TAG_DATASTORE, "DataStore flow collection started -> loading preferences")
        emit(GoalLoadState.Loading)
        emitAll(
            context.goalDataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        Log.e(TAG_DATASTORE, "IOException reading preferences, emitting emptyPreferences", exception)
                        emit(emptyPreferences())
                    } else {
                        Log.e(TAG_DATASTORE, "Unexpected exception reading preferences", exception)
                        emit(emptyPreferences())
                    }
                }
                .map { preferences ->
                    val rawName = preferences[KEY_GOAL_NAME]
                    val rawStart = preferences[KEY_START_DATE]
                    val rawEnd = preferences[KEY_END_DATE]

                    Log.d(
                        TAG_DATASTORE,
                        "Read preferences: name='$rawName', start='$rawStart', end='$rawEnd'"
                    )

                    if (rawName.isNullOrBlank() || rawStart.isNullOrBlank() || rawEnd.isNullOrBlank()) {
                        Log.d(TAG_DATASTORE, "DataStore has no complete goal -> emitting GoalLoadState.NoGoal")
                        return@map GoalLoadState.NoGoal
                    }

                    val startDate = runCatching { LocalDate.parse(rawStart) }.getOrNull()
                    val endDate = runCatching { LocalDate.parse(rawEnd) }.getOrNull()

                    if (startDate == null || endDate == null) {
                        Log.e(TAG_DATASTORE, "Failed to parse dates from DataStore: start='$rawStart', end='$rawEnd'")
                        return@map GoalLoadState.Error("Invalid dates stored in DataStore")
                    }

                    val themeId = preferences[KEY_THEME_ID] ?: ColorTheme.OBSIDIAN_CORAL.id
                    val showPercentage = preferences[KEY_SHOW_PERCENTAGE] ?: true
                    val showRemaining = preferences[KEY_SHOW_REMAINING] ?: true
                    val showHeader = preferences[KEY_SHOW_HEADER] ?: true
                    val verticalBias = preferences[KEY_VERTICAL_BIAS] ?: 0.5f

                    val goal = GoalData(
                        name = rawName.trim(),
                        startDate = startDate,
                        endDate = endDate,
                        settings = AppearanceSettings(
                            themeId = themeId,
                            showPercentage = showPercentage,
                            showRemainingDays = showRemaining,
                            showStatusHeader = showHeader,
                            verticalBias = verticalBias
                        )
                    )

                    Log.d(
                        TAG_DATASTORE,
                        "DataStore loaded: goalName='${goal.name}', startDate='${goal.startDate}', endDate='${goal.endDate}', theme='${goal.settings.themeId}'"
                    )
                    GoalLoadState.Loaded(goal)
                }
        )
    }

    /**
     * Backward-compatible nullable GoalData Flow.
     */
    val goalFlow: Flow<GoalData?> = goalStateFlow.map { state ->
        when (state) {
            is GoalLoadState.Loaded -> state.goal
            else -> null
        }
    }

    suspend fun saveGoal(goal: GoalData) {
        Log.d(
            TAG_SAVE,
            "saveGoal starting DataStore edit: name='${goal.name}', start=${goal.startDate}, end=${goal.endDate}, theme=${goal.settings.themeId}"
        )
        context.goalDataStore.edit { preferences ->
            preferences[KEY_GOAL_NAME] = goal.name.trim()
            preferences[KEY_START_DATE] = goal.startDate.toString()
            preferences[KEY_END_DATE] = goal.endDate.toString()
            preferences[KEY_THEME_ID] = goal.settings.themeId
            preferences[KEY_SHOW_PERCENTAGE] = goal.settings.showPercentage
            preferences[KEY_SHOW_REMAINING] = goal.settings.showRemainingDays
            preferences[KEY_SHOW_HEADER] = goal.settings.showStatusHeader
            preferences[KEY_VERTICAL_BIAS] = goal.settings.verticalBias
        }
        Log.d(TAG_SAVE, "saveGoal DataStore edit completed successfully for '${goal.name}'")
    }

    suspend fun clearGoal() {
        Log.d(TAG_SAVE, "clearGoal called - clearing all preferences")
        context.goalDataStore.edit { preferences ->
            preferences.clear()
        }
        Log.d(TAG_SAVE, "clearGoal completed")
    }

    suspend fun getGoalSnapshotSync(): GoalData? {
        return runCatching { goalFlow.first() }.getOrNull()
    }
}
