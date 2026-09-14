package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppearanceSettings
import com.example.model.ColorTheme
import com.example.model.GoalData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

val Context.goalDataStore: DataStore<Preferences> by preferencesDataStore(name = "goal_preferences")

class GoalRepository(private val context: Context) {

    companion object {
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

    val goalFlow: Flow<GoalData?> = context.goalDataStore.data.map { preferences ->
        val name = preferences[KEY_GOAL_NAME] ?: return@map null
        val startStr = preferences[KEY_START_DATE] ?: return@map null
        val endStr = preferences[KEY_END_DATE] ?: return@map null

        val startDate = runCatching { LocalDate.parse(startStr) }.getOrNull() ?: return@map null
        val endDate = runCatching { LocalDate.parse(endStr) }.getOrNull() ?: return@map null

        val themeId = preferences[KEY_THEME_ID] ?: ColorTheme.OBSIDIAN_CORAL.id
        val showPercentage = preferences[KEY_SHOW_PERCENTAGE] ?: true
        val showRemaining = preferences[KEY_SHOW_REMAINING] ?: true
        val showHeader = preferences[KEY_SHOW_HEADER] ?: true
        val verticalBias = preferences[KEY_VERTICAL_BIAS] ?: 0.5f

        GoalData(
            name = name,
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
    }

    suspend fun saveGoal(goal: GoalData) {
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
    }

    suspend fun clearGoal() {
        context.goalDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun getGoalSnapshotSync(): GoalData? {
        return runCatching { goalFlow.first() }.getOrNull()
    }
}
