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
                    title = "Quick start",
                    content = "1. Write your goal and choose the dates.\n2. Optionally choose a black background or photos.\n3. Tap 'Set as Live Wallpaper', then confirm it in Android settings."
                )

                Spacer(modifier = Modifier.height(10.dp))

                InfoSection(
                    title = "Lock Screen & Home Screen Support",
                    content = "When Android opens the wallpaper preview, tap 'Apply' or 'Set Wallpaper'. On most devices, choose 'Home and Lock Screen' so your goal is visible whenever you glance at your phone."
                )

                Spacer(modifier = Modifier.height(10.dp))

                InfoSection(
                    title = "Keeping your wallpaper active",
                    content = "While Goal Dots is active, it keeps a small notification. On HyperOS this helps keep your wallpaper in place after you close the app."
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
