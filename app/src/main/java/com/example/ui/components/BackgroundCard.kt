package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BackgroundType
import com.example.model.WallpaperBackgroundConfig

@Composable
fun BackgroundCard(
    config: WallpaperBackgroundConfig,
    isImporting: Boolean,
    error: String?,
    onChoosePhoto: () -> Unit,
    onUseDefaultBlack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasPhoto = config.type == BackgroundType.IMAGE
    Card(
        modifier = modifier.fillMaxWidth().testTag("background_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "BACKGROUND",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasPhoto) "Custom photo" else "Default black",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Your photo stays inside the app and is shown behind Goal Dots.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onUseDefaultBlack,
                    enabled = hasPhoto && !isImporting,
                    modifier = Modifier.weight(1f).testTag("default_black_button")
                ) {
                    Text("Default black")
                }
                Button(
                    onClick = onChoosePhoto,
                    enabled = !isImporting,
                    modifier = Modifier.weight(1f).testTag("choose_photo_button")
                ) {
                    Text(if (isImporting) "Importing…" else "Choose photo")
                }
            }
            if (hasPhoto) {
                TextButton(
                    onClick = onUseDefaultBlack,
                    enabled = !isImporting,
                    modifier = Modifier.testTag("remove_background_button")
                ) {
                    Text("Remove photo")
                }
            }
            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
