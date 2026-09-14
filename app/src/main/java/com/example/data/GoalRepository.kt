package com.example.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.createMultiProcessCoordinator
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.model.AppearanceSettings
import com.example.model.ColorTheme
import com.example.model.GoalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException
import java.time.LocalDate

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
        const val ACTION_GOAL_UPDATED = "com.aistudio.goaldots.ACTION_GOAL_UPDATED"

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

        @Volatile
        private var multiProcessDataStore: DataStore<Preferences>? = null

        /**
         * Creates a true AndroidX Multi-Process DataStore using OkioStorage with
         * createMultiProcessCoordinator and PreferencesSerializer.
         * Guarantees file locks, inter-process update notifications, and cross-process
         * transactional read/write consistency between the main app process and :wallpaper process.
         */
        fun getDataStore(context: Context): DataStore<Preferences> {
            return multiProcessDataStore ?: synchronized(this) {
                multiProcessDataStore ?: run {
                    val appContext = context.applicationContext
                    val file = File(appContext.filesDir, "datastore/goal_preferences.preferences_pb")
                    file.parentFile?.mkdirs()

                    val storage = OkioStorage<Preferences>(
                        fileSystem = FileSystem.SYSTEM,
                        serializer = PreferencesSerializer,
                        coordinatorProducer = { _, _ ->
                            createMultiProcessCoordinator(
                                context = Dispatchers.IO,
                                file = file
                            )
                        },
                        producePath = { file.toOkioPath() }
                    )

                    PreferenceDataStoreFactory.create(
                        storage = storage
                    ).also { multiProcessDataStore = it }
                }
            }
        }

        fun getInstance(context: Context): GoalRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GoalRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val dataStore = getDataStore(context)

    /**
     * Rich state flow explicitly distinguishing Loading, NoGoal, Loaded, and Error states.
     * Prevents false "No Goal Active" flashes on initial app startup or wallpaper engine creation.
     */
    val goalStateFlow: Flow<GoalLoadState> = flow {
        com.example.diagnostics.DiagnosticRecorder.log("DATASTORE_FLOW_START", "reading preferences")
        Log.d("GOAL_TIMELINE", "[+${com.example.GoalApplication.elapsedSinceProcessStart()}ms] DataStore flow collection started -> loading preferences")
        Log.d(TAG_DATASTORE, "DataStore flow collection started -> loading preferences")
        com.example.diagnostics.DiagnosticRecorder.log("DATASTORE_EMIT_LOADING")
        Log.d("GOAL_TIMELINE", "[+${com.example.GoalApplication.elapsedSinceProcessStart()}ms] DataStore emitting GoalLoadState.Loading")
        emit(GoalLoadState.Loading)
        emitAll(
            dataStore.data
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
                        com.example.diagnostics.DiagnosticRecorder.log("DATASTORE_EMIT_NO_GOAL")
                        Log.d("GOAL_TIMELINE", "[+${com.example.GoalApplication.elapsedSinceProcessStart()}ms] DataStore emitted GoalLoadState.NoGoal")
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

                    com.example.diagnostics.DiagnosticRecorder.log("DATASTORE_EMIT_LOADED", "goalName='${goal.name}'")
                    Log.d("GOAL_TIMELINE", "[+${com.example.GoalApplication.elapsedSinceProcessStart()}ms] DataStore emitted GoalLoadState.Loaded: name='${goal.name}'")
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
        dataStore.edit { preferences ->
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
        try {
            val intent = Intent(ACTION_GOAL_UPDATED).setPackage(context.packageName)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG_SAVE, "Failed to send update broadcast", e)
        }
    }

    suspend fun clearGoal() {
        Log.d(TAG_SAVE, "clearGoal called - clearing all preferences")
        dataStore.edit { preferences ->
            preferences.clear()
        }
        Log.d(TAG_SAVE, "clearGoal completed")
        try {
            val intent = Intent(ACTION_GOAL_UPDATED).setPackage(context.packageName)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG_SAVE, "Failed to send update broadcast", e)
        }
    }

    suspend fun getGoalSnapshotSync(): GoalData? {
        return runCatching { goalFlow.first() }.getOrNull()
    }
}
