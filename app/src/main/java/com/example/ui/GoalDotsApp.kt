package com.example.ui

import android.app.WallpaperManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.GoalWallpaperService
import com.example.ui.components.AppearanceCard
import com.example.ui.components.BackgroundCard
import com.example.ui.components.DatePickerCards
import com.example.ui.components.GoalStatsCard
import com.example.ui.components.OemInfoDialog
import com.example.ui.components.OnboardingFlow
import com.example.ui.components.WallpaperLivePreview
import com.example.model.WallpaperImageSlot
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GoalDotsApp(
    viewModel: GoalViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val goalFormRequester = remember { BringIntoViewRequester() }
    var onboardingInitialized by rememberSaveable { mutableStateOf(false) }
    var showOnboarding by rememberSaveable { mutableStateOf(false) }
    var onboardingStep by rememberSaveable { mutableStateOf(1) }
    var focusGoalForm by rememberSaveable { mutableStateOf(false) }
    var pendingPhotoSlot by remember { mutableStateOf(WallpaperImageSlot.SINGLE) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.selectBackground(pendingPhotoSlot, uri)
            if (showOnboarding) onboardingStep = 4
        }
    }

    var showInfoDialog by remember { mutableStateOf(false) }
    var showDiagnosticDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.onboardingReady, uiState.shouldShowOnboarding) {
        if (uiState.onboardingReady && !onboardingInitialized) {
            onboardingInitialized = true
            showOnboarding = uiState.shouldShowOnboarding
            if (uiState.shouldShowOnboarding) viewModel.markOnboardingSeen()
        }
    }

    LaunchedEffect(showOnboarding, focusGoalForm) {
        if (!showOnboarding && focusGoalForm) {
            goalFormRequester.bringIntoView()
            focusGoalForm = false
        }
    }

    LaunchedEffect(uiState.showSaveSuccessMessage) {
        if (uiState.showSaveSuccessMessage) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Goal and wallpaper configuration saved!",
                    duration = SnackbarDuration.Short
                )
                viewModel.dismissSaveSuccess()
            }
        }
    }

    val setWallpaperAction: () -> Unit = {
        viewModel.saveGoal {
            launchWallpaperPicker(context)
        }
    }

    if (!onboardingInitialized) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (showOnboarding) {
        OnboardingFlow(
            step = onboardingStep,
            onGetStarted = { onboardingStep = 2 },
            onSkip = { showOnboarding = false },
            onCreateGoal = {
                showOnboarding = false
                focusGoalForm = true
            },
            onExploreOptions = { onboardingStep = 3 },
            onChoosePhotos = {
                pendingPhotoSlot = WallpaperImageSlot.SINGLE
                viewModel.setBackgroundMode(com.example.model.WallpaperBackgroundMode.SINGLE_IMAGE)
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onUseDefaultBlack = {
                viewModel.setBackgroundMode(com.example.model.WallpaperBackgroundMode.DEFAULT_BLACK)
                onboardingStep = 4
            },
            onSetWallpaper = {
                showOnboarding = false
                launchWallpaperPicker(context)
            },
            onFinishLater = { showOnboarding = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Goal Dots",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .padding(1.dp)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDiagnosticDialog = true },
                        modifier = Modifier.testTag("diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Diagnostics",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showInfoDialog = true },
                        modifier = Modifier.testTag("help_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Tips & Compatibility",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showResetConfirmDialog = true },
                        modifier = Modifier.testTag("reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Reset Goal",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live Interactive Wallpaper Preview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE WALLPAPER PREVIEW",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    FilterChip(
                        selected = uiState.showLockscreenOverlay,
                        onClick = { viewModel.toggleLockscreenOverlay() },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.LockClock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (uiState.showLockscreenOverlay) "Lock Screen UI" else "Pure Wallpaper",
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.testTag("toggle_overlay_chip")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Aspect ratio matching modern smartphone displays (9:16)
                WallpaperLivePreview(
                    snapshot = uiState.snapshot,
                    showLockscreenOverlay = uiState.showLockscreenOverlay,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .aspectRatio(9f / 16f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Primary CTA: Set as Live Wallpaper
                Button(
                    onClick = setWallpaperAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("set_wallpaper_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wallpaper,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Set as Live Wallpaper",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 2. Goal Setup Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(goalFormRequester)
                    .testTag("goal_setup_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "YOUR GOAL",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = uiState.goalName,
                        onValueChange = { viewModel.updateName(it) },
                        label = { Text("What is your goal?") },
                        placeholder = { Text("e.g. Run a half marathon") },
                        singleLine = true,
                        trailingIcon = {
                            if (uiState.goalName.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateName("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear input")
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goal_name_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Date pickers & Duration presets
                    DatePickerCards(
                        startDate = uiState.startDate,
                        endDate = uiState.endDate,
                        totalDays = uiState.totalDays,
                        validationError = uiState.validationError,
                        onStartDateSelected = { viewModel.updateStartDate(it) },
                        onEndDateSelected = { viewModel.updateEndDate(it) },
                        onQuickDurationSelected = { viewModel.setQuickDuration(it) }
                    )
                }
            }

            // 3. Goal Progress & Dot Breakdown
            GoalStatsCard(snapshot = uiState.snapshot)

            // 4. Appearance & Style Customization
            AppearanceCard(
                currentThemeId = uiState.themeId,
                verticalBias = uiState.verticalBias,
                showHeader = uiState.showStatusHeader,
                showPercentage = uiState.showPercentage,
                showRemainingDays = uiState.showRemainingDays,
                onThemeSelected = { viewModel.updateTheme(it) },
                onVerticalBiasChanged = { viewModel.updateVerticalBias(it) },
                onToggleHeader = { viewModel.toggleShowHeader(it) },
                onTogglePercentage = { viewModel.toggleShowPercentage(it) },
                onToggleRemainingDays = { viewModel.toggleShowRemaining(it) }
            )

            // 5. Background is intentionally independent from goal settings: importing uses
            // the system picker and copies the selected image into app-private storage.
            BackgroundCard(
                config = uiState.backgroundConfig,
                isImporting = uiState.isBackgroundImporting,
                error = uiState.backgroundError,
                onModeSelected = viewModel::setBackgroundMode,
                onChoosePhoto = { slot ->
                    pendingPhotoSlot = slot
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemovePhoto = viewModel::removeBackground,
                onChooseTime = { isDay ->
                    val current = if (isDay) uiState.backgroundConfig.dayStartMinutes else uiState.backgroundConfig.eveningStartMinutes
                    TimePickerDialog(context, { _, hour, minute ->
                        if (isDay) viewModel.updateSchedule(hour * 60 + minute, uiState.backgroundConfig.eveningStartMinutes)
                        else viewModel.updateSchedule(uiState.backgroundConfig.dayStartMinutes, hour * 60 + minute)
                    }, current / 60, current % 60, true).show()
                },
                onReadabilitySelected = viewModel::setReadabilityMode,
                onOpenDarkThemeSchedule = { openDarkThemeSchedule(context) },
                onOpenEyeComfortSchedule = { openEyeComfortSchedule(context) }
            )

            // 6. Save Changes Button
            Button(
                onClick = { viewModel.saveGoal() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_goal_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Goal Settings", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Info & OEM Guide Dialog
    if (showInfoDialog) {
        OemInfoDialog(onDismissRequest = { showInfoDialog = false })
    }

    // Diagnostic Recorder Dialog
    if (showDiagnosticDialog) {
        com.example.ui.components.DiagnosticDialog(
            onDismissRequest = { showDiagnosticDialog = false }
        )
    }

    // Reset Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset Goal?") },
            text = { Text("This will clear your current goal and dates from the live wallpaper.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetGoal()
                        showResetConfirmDialog = false
                    },
                    modifier = Modifier.testTag("confirm_reset_button")
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun launchWallpaperPicker(context: Context) {
    try {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, GoalWallpaperService::class.java)
            )
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
            context.startActivity(fallbackIntent)
        } catch (e2: Exception) {
            Toast.makeText(
                context,
                "Please enable Goal Dots in your device Wallpaper settings",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

private fun openDarkThemeSchedule(context: Context) {
    openSystemSettings(
        context = context,
        intent = Intent(Settings.ACTION_DISPLAY_SETTINGS),
        instructions = "Open Dark mode, then choose Schedule."
    )
}

private fun openEyeComfortSchedule(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS)
    } else {
        Intent(Settings.ACTION_DISPLAY_SETTINGS)
    }
    openSystemSettings(
        context = context,
        intent = intent,
        instructions = "Open Reading mode or Eye comfort, then choose a schedule."
    )
}

private fun openSystemSettings(context: Context, intent: Intent, instructions: String) {
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        if (intent.action != Settings.ACTION_DISPLAY_SETTINGS) {
            try {
                context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                return
            } catch (_: Exception) {
                // The message below gives a safe path when an OEM does not expose this intent.
            }
        }
        Toast.makeText(context, instructions, Toast.LENGTH_LONG).show()
    }
}
