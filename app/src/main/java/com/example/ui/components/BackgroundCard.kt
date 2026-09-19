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
import androidx.compose.material3.FilterChip
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
import com.example.model.EyeComfortMode
import com.example.model.OverlayReadabilityMode
import com.example.model.WallpaperBackgroundConfig
import com.example.model.WallpaperBackgroundMode
import com.example.model.WallpaperImageSlot
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun BackgroundCard(config: WallpaperBackgroundConfig, isImporting: Boolean, error: String?, onModeSelected: (WallpaperBackgroundMode) -> Unit, onChoosePhoto: (WallpaperImageSlot) -> Unit, onRemovePhoto: (WallpaperImageSlot) -> Unit, onChooseTime: (Boolean) -> Unit, onReadabilitySelected: (OverlayReadabilityMode) -> Unit, onEyeComfortSelected: (EyeComfortMode) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().testTag("background_card"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BACKGROUND", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Text("Photos are copied inside the app. Goal Dots never needs gallery access after import.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceRow(listOf("Default black" to WallpaperBackgroundMode.DEFAULT_BLACK, "One photo" to WallpaperBackgroundMode.SINGLE_IMAGE, "Automatic by time" to WallpaperBackgroundMode.SCHEDULED_IMAGES), config.mode, onModeSelected)
            when (config.mode) {
                WallpaperBackgroundMode.SINGLE_IMAGE -> PhotoRow("Photo", config.hasSingleImage, WallpaperImageSlot.SINGLE, isImporting, onChoosePhoto, onRemovePhoto)
                WallpaperBackgroundMode.SCHEDULED_IMAGES -> {
                    PhotoRow("Day photo", config.hasDayImage, WallpaperImageSlot.DAY, isImporting, onChoosePhoto, onRemovePhoto)
                    TimeRow("Day begins", config.dayStartMinutes, true, onChooseTime)
                    PhotoRow("Evening photo", config.hasEveningImage, WallpaperImageSlot.EVENING, isImporting, onChoosePhoto, onRemovePhoto)
                    TimeRow("Evening begins", config.eveningStartMinutes, false, onChooseTime)
                }
                WallpaperBackgroundMode.DEFAULT_BLACK -> Unit
            }
            Spacer(Modifier.height(4.dp))
            Text("OVERLAY READABILITY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            ChoiceRow(listOf("Auto" to OverlayReadabilityMode.AUTO, "Light" to OverlayReadabilityMode.LIGHT, "Dark" to OverlayReadabilityMode.DARK), config.readabilityMode, onReadabilitySelected)
            Text("Auto adds a soft contrast layer and colors that stay visible without hiding your photo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("EYE COMFORT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            ChoiceRow(listOf("Off" to EyeComfortMode.OFF, "Automatic" to EyeComfortMode.AUTOMATIC_BY_SCHEDULE, "Always on" to EyeComfortMode.ALWAYS_ON), config.eyeComfortMode, onEyeComfortSelected)
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun <T> ChoiceRow(options: List<Pair<String, T>>, selected: T, onSelected: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { (label, value) -> FilterChip(selected = selected == value, onClick = { onSelected(value) }, label = { Text(label, fontSize = 11.sp) }) }
    }
}

@Composable private fun PhotoRow(label: String, hasImage: Boolean, slot: WallpaperImageSlot, importing: Boolean, choose: (WallpaperImageSlot) -> Unit, remove: (WallpaperImageSlot) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.Medium); Text(if (hasImage) "Photo ready" else "No photo — black/fallback is used", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        OutlinedButton(onClick = { choose(slot) }, enabled = !importing) { Text(if (importing) "Importing…" else "Choose") }
        if (hasImage) TextButton(onClick = { remove(slot) }, enabled = !importing) { Text("Remove") }
    }
}

@Composable private fun TimeRow(label: String, minutes: Int, isDay: Boolean, onChooseTime: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.Medium)
        Button(onClick = { onChooseTime(isDay) }, modifier = Modifier.testTag(if (isDay) "day_time_button" else "evening_time_button")) { Text(LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofPattern("HH:mm"))) }
    }
}
