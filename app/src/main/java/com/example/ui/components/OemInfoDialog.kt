package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OemInfoDialog(
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Live Wallpaper Setup & Tips",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                InfoSection(
                    title = "Lock Screen & Home Screen Support",
                    content = "When you tap 'Set as Wallpaper', Android opens the system wallpaper preview. Tap 'Apply' or 'Set Wallpaper'. On most devices, choose 'Home and Lock Screen' so your goal is visible whenever you glance at your phone."
                )

                Spacer(modifier = Modifier.height(10.dp))

                InfoSection(
                    title = "Device-Specific Instructions",
                    content = "• Samsung Galaxy: In the preview, select 'Set on Lock and Home screens'.\n• Xiaomi / HyperOS / MIUI: Ensure you select 'Both screens' when applying.\n• Google Pixel: Choose 'Home and lock screens'."
                )

                Spacer(modifier = Modifier.height(10.dp))

                InfoSection(
                    title = "Zero Battery Drain Architecture",
                    content = "Goal Dots does not run any background loops, animations, or foreground services. It renders only when your screen turns on and updates cleanly at midnight. Battery consumption is effectively 0%."
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("dismiss_info_dialog")
            ) {
                Text("Got it")
            }
        }
    )
}

@Composable
private fun InfoSection(title: String, content: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = content,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 18.sp
    )
}
