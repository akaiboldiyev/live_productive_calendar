package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A short, optional introduction which always leads back to the existing app screens. */
@Composable
fun OnboardingFlow(
    step: Int,
    onGetStarted: () -> Unit,
    onSkip: () -> Unit,
    onCreateGoal: () -> Unit,
    onExploreOptions: () -> Unit,
    onChoosePhotos: () -> Unit,
    onUseDefaultBlack: () -> Unit,
    onSetWallpaper: () -> Unit,
    onFinishLater: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "${step.coerceIn(1, 4)} of 4",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                when (step) {
                    1 -> WelcomeStep(onGetStarted, onSkip)
                    2 -> GoalStep(onCreateGoal, onExploreOptions)
                    3 -> BackgroundStep(onChoosePhotos, onUseDefaultBlack)
                    else -> WallpaperStep(onSetWallpaper, onFinishLater)
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onGetStarted: () -> Unit, onSkip: () -> Unit) {
    OnboardingTitle("Your progress, on your wallpaper")
    OnboardingBody("Goal Dots turns your goal into a live wallpaper. Every day is one dot.")
    PrimaryOnboardingButton("Get started", "onboarding_get_started", onGetStarted)
    TextButton(onClick = onSkip, modifier = Modifier.testTag("onboarding_skip")) { Text("Skip for now") }
}

@Composable
private fun GoalStep(onCreateGoal: () -> Unit, onExploreOptions: () -> Unit) {
    OnboardingTitle("Create your first goal")
    OnboardingBody("Choose a goal and the dates. Goal Dots will show your progress every day.")
    PrimaryOnboardingButton("Create goal", "onboarding_create_goal", onCreateGoal)
    TextButton(onClick = onExploreOptions, modifier = Modifier.testTag("onboarding_explore_options")) { Text("Explore options first") }
}

@Composable
private fun BackgroundStep(onChoosePhotos: () -> Unit, onUseDefaultBlack: () -> Unit) {
    OnboardingTitle("Make it yours")
    OnboardingBody("You can use a black background, one photo, or day and evening photos.")
    PrimaryOnboardingButton("Choose photos", "onboarding_choose_photos", onChoosePhotos)
    OutlinedButton(onClick = onUseDefaultBlack, modifier = Modifier.fillMaxWidth().testTag("onboarding_default_black")) { Text("Use default black") }
}

@Composable
private fun WallpaperStep(onSetWallpaper: () -> Unit, onFinishLater: () -> Unit) {
    OnboardingTitle("Set your live wallpaper")
    OnboardingBody("Tap the button below, then choose Set wallpaper in Android settings.")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Text(
            text = "Goal Dots keeps a small notification while the wallpaper is active. This helps prevent HyperOS from removing your wallpaper after you close the app.",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    PrimaryOnboardingButton("Set as Live Wallpaper", "onboarding_set_wallpaper", onSetWallpaper)
    TextButton(onClick = onFinishLater, modifier = Modifier.testTag("onboarding_finish_later")) { Text("Finish later") }
}

@Composable
private fun OnboardingTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
private fun OnboardingBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun PrimaryOnboardingButton(text: String, tag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(14.dp)
    ) { Text(text) }
}
