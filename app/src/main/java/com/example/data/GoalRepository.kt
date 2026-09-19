package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.model.AppearanceSettings
import com.example.model.ColorTheme
import com.example.model.OverlayReadabilityMode
import com.example.model.OnboardingDecision
import com.example.model.WallpaperBackgroundMode
import com.example.model.WallpaperImageSlot
import com.example.model.GoalData
import com.example.model.WallpaperBackgroundConfig
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
        const val ACTION_BACKGROUND_UPDATED = "com.aistudio.goaldots.ACTION_BACKGROUND_UPDATED"
        /** A transient, package-scoped hint; all durable state still lives in DataStore. */
        const val ACTION_APP_MOVED_TO_BACKGROUND = "com.aistudio.goaldots.ACTION_APP_MOVED_TO_BACKGROUND"

        private val KEY_GOAL_NAME = stringPreferencesKey("goal_name")
        private val KEY_START_DATE = stringPreferencesKey("start_date")
        private val KEY_END_DATE = stringPreferencesKey("end_date")
        private val KEY_THEME_ID = stringPreferencesKey("theme_id")
        private val KEY_SHOW_PERCENTAGE = booleanPreferencesKey("show_percentage")
        private val KEY_SHOW_REMAINING = booleanPreferencesKey("show_remaining")
        private val KEY_SHOW_HEADER = booleanPreferencesKey("show_header")
        private val KEY_VERTICAL_BIAS = floatPreferencesKey("vertical_bias")
        private val KEY_ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        private val KEY_BACKGROUND_TYPE = stringPreferencesKey("background_type")
        private val KEY_BACKGROUND_MODE = stringPreferencesKey("background_mode")
        private val KEY_DAY_START_MINUTES = longPreferencesKey("background_day_start_minutes")
        private val KEY_EVENING_START_MINUTES = longPreferencesKey("background_evening_start_minutes")
        private val KEY_HAS_SINGLE_IMAGE = booleanPreferencesKey("background_has_single_image")
        private val KEY_HAS_DAY_IMAGE = booleanPreferencesKey("background_has_day_image")
        private val KEY_HAS_EVENING_IMAGE = booleanPreferencesKey("background_has_evening_image")
        private val KEY_READABILITY_MODE = stringPreferencesKey("background_readability_mode")
        private val KEY_BACKGROUND_REVISION = longPreferencesKey("background_revision")
        private val KEY_BACKGROUND_IMAGE_REVISION = longPreferencesKey("background_image_revision")

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

    /**
     * Stored beside the goal, rather than in Activity state. A pre-existing complete goal
     * is treated as having seen onboarding so upgrades never interrupt an existing user.
     */
    val shouldShowOnboardingFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            OnboardingDecision.shouldShow(
                onboardingSeen = preferences[KEY_ONBOARDING_SEEN] ?: false,
                hasSavedGoal = hasCompleteGoal(preferences)
            )
        }
        .catch { exception ->
            // Never interrupt an established user with onboarding if storage is briefly unavailable.
            Log.e(TAG_DATASTORE, "Unable to read onboarding state", exception)
            emit(false)
        }

    suspend fun markOnboardingSeen() {
        dataStore.edit { preferences -> preferences[KEY_ONBOARDING_SEEN] = true }
    }

    suspend fun markOnboardingSeenForExistingGoal() {
        dataStore.edit { preferences ->
            if (!(preferences[KEY_ONBOARDING_SEEN] ?: false) && hasCompleteGoal(preferences)) {
                preferences[KEY_ONBOARDING_SEEN] = true
            }
        }
    }

    /** Uses the same multi-process DataStore as the goal configuration. */
    val backgroundConfigFlow: Flow<WallpaperBackgroundConfig> = dataStore.data
        .catch { exception ->
            Log.e(TAG_DATASTORE, "Unable to read wallpaper background configuration", exception)
            emit(emptyPreferences())
        }
        .map { preferences ->
            val revision = preferences[KEY_BACKGROUND_REVISION] ?: 0L
            val oldImage = preferences[KEY_BACKGROUND_TYPE] == "IMAGE"
            val mode = preferences[KEY_BACKGROUND_MODE].asEnumOrNull<WallpaperBackgroundMode>()
                ?: if (oldImage) WallpaperBackgroundMode.SINGLE_IMAGE else WallpaperBackgroundMode.DEFAULT_BLACK
            WallpaperBackgroundConfig(
                mode = mode,
                dayStartMinutes = (preferences[KEY_DAY_START_MINUTES] ?: (7 * 60).toLong()).toInt().coerceIn(0, 1439),
                eveningStartMinutes = (preferences[KEY_EVENING_START_MINUTES] ?: (19 * 60).toLong()).toInt().coerceIn(0, 1439),
                hasSingleImage = preferences[KEY_HAS_SINGLE_IMAGE] ?: oldImage,
                hasDayImage = preferences[KEY_HAS_DAY_IMAGE] ?: false,
                hasEveningImage = preferences[KEY_HAS_EVENING_IMAGE] ?: false,
                readabilityMode = preferences[KEY_READABILITY_MODE].asEnumOrNull<OverlayReadabilityMode>() ?: OverlayReadabilityMode.AUTO,
                imageRevision = preferences[KEY_BACKGROUND_IMAGE_REVISION] ?: revision,
                revision = revision
            )
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
            preferences[KEY_ONBOARDING_SEEN] = true
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
        Log.d(TAG_SAVE, "clearGoal called - clearing goal preferences")
        dataStore.edit { preferences ->
            // Background selection is independent from a goal and must survive a goal reset.
            preferences.remove(KEY_GOAL_NAME)
            preferences.remove(KEY_START_DATE)
            preferences.remove(KEY_END_DATE)
            preferences.remove(KEY_THEME_ID)
            preferences.remove(KEY_SHOW_PERCENTAGE)
            preferences.remove(KEY_SHOW_REMAINING)
            preferences.remove(KEY_SHOW_HEADER)
            preferences.remove(KEY_VERTICAL_BIAS)
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

    /**
     * Imports user-selected media into app-private storage before publishing IMAGE to the
     * multi-process configuration. The wallpaper service never depends on the picker Uri.
     */
    suspend fun setImageBackground(slot: WallpaperImageSlot, uri: Uri) {
        Log.i("GOAL_BACKGROUND", "Background selected for $slot")
        WallpaperBackgroundStorage.copyFromUri(context, uri, slot)
        dataStore.edit { preferences ->
            setImageFlag(preferences, slot, true)
            incrementImageRevision(preferences)
            incrementBackgroundRevision(preferences)
        }
        sendUpdateBroadcast(ACTION_BACKGROUND_UPDATED)
    }

    suspend fun removeImageBackground(slot: WallpaperImageSlot) {
        dataStore.edit { preferences ->
            setImageFlag(preferences, slot, false)
            incrementImageRevision(preferences)
            incrementBackgroundRevision(preferences)
        }
        WallpaperBackgroundStorage.delete(context, slot)
        sendUpdateBroadcast(ACTION_BACKGROUND_UPDATED)
    }

    suspend fun setBackgroundMode(mode: WallpaperBackgroundMode) {
        dataStore.edit { preferences ->
            preferences[KEY_BACKGROUND_MODE] = mode.name
            incrementBackgroundRevision(preferences)
        }
        Log.i("GOAL_BACKGROUND", "Background mode changed to $mode")
        sendUpdateBroadcast(ACTION_BACKGROUND_UPDATED)
    }

    suspend fun updateSchedule(dayStartMinutes: Int, eveningStartMinutes: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_DAY_START_MINUTES] = dayStartMinutes.coerceIn(0, 1439).toLong()
            preferences[KEY_EVENING_START_MINUTES] = eveningStartMinutes.coerceIn(0, 1439).toLong()
            incrementBackgroundRevision(preferences)
        }
        Log.i("GOAL_BACKGROUND", "Time schedule changed")
        sendUpdateBroadcast(ACTION_BACKGROUND_UPDATED)
    }

    suspend fun setReadabilityMode(mode: OverlayReadabilityMode) {
        dataStore.edit { preferences ->
            preferences[KEY_READABILITY_MODE] = mode.name
            incrementBackgroundRevision(preferences)
        }
        sendUpdateBroadcast(ACTION_BACKGROUND_UPDATED)
    }

    private fun setImageFlag(preferences: androidx.datastore.preferences.core.MutablePreferences, slot: WallpaperImageSlot, value: Boolean) {
        when (slot) {
            WallpaperImageSlot.SINGLE -> preferences[KEY_HAS_SINGLE_IMAGE] = value
            WallpaperImageSlot.DAY -> preferences[KEY_HAS_DAY_IMAGE] = value
            WallpaperImageSlot.EVENING -> preferences[KEY_HAS_EVENING_IMAGE] = value
        }
    }

    private fun hasCompleteGoal(preferences: Preferences): Boolean =
        !preferences[KEY_GOAL_NAME].isNullOrBlank() &&
            !preferences[KEY_START_DATE].isNullOrBlank() &&
            !preferences[KEY_END_DATE].isNullOrBlank()

    private fun incrementBackgroundRevision(preferences: androidx.datastore.preferences.core.MutablePreferences) {
        preferences[KEY_BACKGROUND_REVISION] = (preferences[KEY_BACKGROUND_REVISION] ?: 0L) + 1L
    }

    private fun incrementImageRevision(preferences: androidx.datastore.preferences.core.MutablePreferences) {
        preferences[KEY_BACKGROUND_IMAGE_REVISION] = (preferences[KEY_BACKGROUND_IMAGE_REVISION] ?: 0L) + 1L
    }

    private fun sendUpdateBroadcast(action: String) {
        try {
            context.sendBroadcast(Intent(action).setPackage(context.packageName))
        } catch (e: Exception) {
            Log.e(TAG_SAVE, "Failed to send update broadcast for $action", e)
        }
    }
}

private inline fun <reified T : Enum<T>> String?.asEnumOrNull(): T? = this?.let { value ->
    enumValues<T>().firstOrNull { it.name == value }
}
