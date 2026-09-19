package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GoalLoadState
import com.example.data.GoalRepository
import com.example.model.AppearanceSettings
import com.example.model.ColorTheme
import com.example.model.GoalData
import com.example.model.GoalProgressCalculator
import com.example.model.GoalSnapshot
import com.example.model.WallpaperBackgroundConfig
import com.example.model.WallpaperBackgroundMode
import com.example.model.WallpaperImageSlot
import com.example.model.OverlayReadabilityMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GoalUiState(
    val goalName: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now().plusDays(179), // 180 days default
    val themeId: String = ColorTheme.OBSIDIAN_CORAL.id,
    val showPercentage: Boolean = true,
    val showRemainingDays: Boolean = true,
    val showStatusHeader: Boolean = true,
    val verticalBias: Float = 0.5f,
    val isSaved: Boolean = false,
    val validationError: String? = null,
    val showSaveSuccessMessage: Boolean = false,
    val showLockscreenOverlay: Boolean = false,
    val backgroundConfig: WallpaperBackgroundConfig = WallpaperBackgroundConfig(),
    val isBackgroundImporting: Boolean = false,
    val backgroundError: String? = null
) {
    val totalDays: Int
        get() = GoalProgressCalculator.calculateTotalDays(startDate, endDate)

    val currentGoalData: GoalData?
        get() = if (goalName.trim().isNotEmpty() && totalDays in GoalProgressCalculator.MIN_DAYS..GoalProgressCalculator.MAX_DAYS) {
            GoalData(
                name = goalName.trim(),
                startDate = startDate,
                endDate = endDate,
                settings = AppearanceSettings(
                    themeId = themeId,
                    showPercentage = showPercentage,
                    showRemainingDays = showRemainingDays,
                    showStatusHeader = showStatusHeader,
                    verticalBias = verticalBias
                )
            )
        } else null

    val snapshot: GoalSnapshot
        get() = GoalProgressCalculator.computeSnapshot(currentGoalData, LocalDate.now())
}

class GoalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GoalRepository.getInstance(application)

    private val _uiState = MutableStateFlow(GoalUiState())
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.goalStateFlow.collect { state ->
                Log.d("GOAL_DATASTORE", "ViewModel collected goalState: $state")
                when (state) {
                    is GoalLoadState.Loaded -> {
                        val savedGoal = state.goal
                        Log.d("GOAL_DATASTORE", "ViewModel updating UI with savedGoal: '${savedGoal.name}'")
                        _uiState.update { current ->
                            current.copy(
                                goalName = savedGoal.name,
                                startDate = savedGoal.startDate,
                                endDate = savedGoal.endDate,
                                themeId = savedGoal.settings.themeId,
                                showPercentage = savedGoal.settings.showPercentage,
                                showRemainingDays = savedGoal.settings.showRemainingDays,
                                showStatusHeader = savedGoal.settings.showStatusHeader,
                                verticalBias = savedGoal.settings.verticalBias,
                                isSaved = true,
                                validationError = null
                            )
                        }
                    }
                    is GoalLoadState.NoGoal -> {
                        _uiState.update { current ->
                            if (current.goalName.isEmpty()) {
                                current.copy(
                                    goalName = "Build a profitable startup",
                                    startDate = LocalDate.now(),
                                    endDate = LocalDate.now().plusDays(179),
                                    isSaved = false
                                )
                            } else {
                                current.copy(isSaved = false)
                            }
                        }
                    }
                    is GoalLoadState.Loading -> {
                        // Keep current state during initial async load to avoid UI flicker
                    }
                    is GoalLoadState.Error -> {
                        Log.e("GOAL_DATASTORE", "ViewModel error loading goal: ${state.message}")
                        _uiState.update { it.copy(validationError = state.message) }
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.backgroundConfigFlow.collect { config ->
                _uiState.update { current ->
                    current.copy(backgroundConfig = config, backgroundError = null)
                }
            }
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(goalName = name, showSaveSuccessMessage = false) }
        validate()
    }

    fun updateStartDate(newStart: LocalDate) {
        _uiState.update { current ->
            var newEnd = current.endDate
            if (newEnd.isBefore(newStart)) {
                newEnd = newStart
            }
            // Max allowed 365 inclusive days: start + 364 days
            val maxAllowedEnd = GoalProgressCalculator.getMaxEndDate(newStart)
            if (newEnd.isAfter(maxAllowedEnd)) {
                newEnd = maxAllowedEnd
            }
            current.copy(
                startDate = newStart,
                endDate = newEnd,
                showSaveSuccessMessage = false
            )
        }
        validate()
    }

    fun updateEndDate(newEnd: LocalDate) {
        _uiState.update { it.copy(endDate = newEnd, showSaveSuccessMessage = false) }
        validate()
    }

    fun setQuickDuration(days: Int) {
        val clampedDays = days.coerceIn(1, GoalProgressCalculator.MAX_DAYS)
        _uiState.update { current ->
            current.copy(
                endDate = current.startDate.plusDays((clampedDays - 1).toLong()),
                showSaveSuccessMessage = false
            )
        }
        validate()
    }

    fun updateTheme(themeId: String) {
        _uiState.update { it.copy(themeId = themeId, showSaveSuccessMessage = false) }
        autoSaveIfAlreadyConfigured()
    }

    fun updateVerticalBias(bias: Float) {
        _uiState.update { it.copy(verticalBias = bias.coerceIn(0f, 1f), showSaveSuccessMessage = false) }
        autoSaveIfAlreadyConfigured()
    }

    fun toggleShowHeader(enabled: Boolean) {
        _uiState.update { it.copy(showStatusHeader = enabled, showSaveSuccessMessage = false) }
        autoSaveIfAlreadyConfigured()
    }

    fun toggleShowPercentage(enabled: Boolean) {
        _uiState.update { it.copy(showPercentage = enabled, showSaveSuccessMessage = false) }
        autoSaveIfAlreadyConfigured()
    }

    fun toggleShowRemaining(enabled: Boolean) {
        _uiState.update { it.copy(showRemainingDays = enabled, showSaveSuccessMessage = false) }
        autoSaveIfAlreadyConfigured()
    }

    fun toggleLockscreenOverlay() {
        _uiState.update { it.copy(showLockscreenOverlay = !it.showLockscreenOverlay) }
    }

    fun dismissSaveSuccess() {
        _uiState.update { it.copy(showSaveSuccessMessage = false) }
    }

    fun selectBackground(slot: WallpaperImageSlot, uri: Uri) {
        _uiState.update { it.copy(isBackgroundImporting = true, backgroundError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.setImageBackground(slot, uri) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isBackgroundImporting = false,
                            backgroundError = null
                        )
                    }
                }
                .onFailure { error ->
                    Log.e("GOAL_BACKGROUND", "Background import failed", error)
                    _uiState.update {
                        it.copy(
                            isBackgroundImporting = false,
                            backgroundError = "Could not import this photo. Please choose another image."
                        )
                    }
                }
        }
    }

    fun removeBackground(slot: WallpaperImageSlot) {
        _uiState.update { it.copy(isBackgroundImporting = true, backgroundError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.removeImageBackground(slot) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isBackgroundImporting = false
                        )
                    }
                }
                .onFailure { error ->
                    Log.e("GOAL_BACKGROUND", "Removing background failed", error)
                    _uiState.update {
                        it.copy(
                            isBackgroundImporting = false,
                            backgroundError = "Could not remove the background."
                        )
                    }
                }
        }
    }

    fun setBackgroundMode(mode: WallpaperBackgroundMode) = updateBackground { repository.setBackgroundMode(mode) }
    fun updateSchedule(dayStartMinutes: Int, eveningStartMinutes: Int) = updateBackground { repository.updateSchedule(dayStartMinutes, eveningStartMinutes) }
    fun setReadabilityMode(mode: OverlayReadabilityMode) = updateBackground { repository.setReadabilityMode(mode) }

    private fun updateBackground(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { block() }.onFailure { error ->
                Log.e("GOAL_BACKGROUND", "Background configuration update failed", error)
                _uiState.update { it.copy(backgroundError = "Could not save background settings.") }
            }
        }
    }

    private fun validate(): Boolean {
        val state = _uiState.value
        val result = GoalProgressCalculator.validateGoal(
            state.goalName,
            state.startDate,
            state.endDate
        )
        val errorMsg = when (result) {
            is GoalProgressCalculator.ValidationResult.Valid -> null
            is GoalProgressCalculator.ValidationResult.Error -> result.message
        }
        _uiState.update { it.copy(validationError = errorMsg) }
        return errorMsg == null
    }

    fun saveGoal(onComplete: (() -> Unit)? = null): Boolean {
        Log.d(
            "GOAL_SAVE",
            "saveGoal requested: name='${_uiState.value.goalName}', start=${_uiState.value.startDate}, end=${_uiState.value.endDate}"
        )
        if (!validate()) {
            Log.w("GOAL_SAVE", "saveGoal validation failed: ${_uiState.value.validationError}")
            return false
        }
        val state = _uiState.value
        val goal = state.currentGoalData ?: run {
            Log.w("GOAL_SAVE", "currentGoalData was null despite validation passing")
            return false
        }

        viewModelScope.launch {
            Log.d("GOAL_SAVE", "Persisting goal to DataStore...")
            repository.saveGoal(goal)
            _uiState.update { it.copy(isSaved = true, showSaveSuccessMessage = true) }
            Log.d("GOAL_SAVE", "Goal successfully saved in ViewModel state. Invoking onComplete.")
            onComplete?.invoke()
        }
        return true
    }

    private fun autoSaveIfAlreadyConfigured() {
        if (_uiState.value.isSaved) {
            val goal = _uiState.value.currentGoalData ?: return
            viewModelScope.launch {
                Log.d("GOAL_SAVE", "autoSave triggered for configured goal: ${goal.name}")
                repository.saveGoal(goal)
            }
        }
    }

    fun resetGoal() {
        viewModelScope.launch {
            repository.clearGoal()
            _uiState.update { current ->
                GoalUiState(
                    goalName = "",
                    startDate = LocalDate.now(),
                    endDate = LocalDate.now().plusDays(179),
                    isSaved = false,
                    backgroundConfig = current.backgroundConfig
                )
            }
        }
    }
}
