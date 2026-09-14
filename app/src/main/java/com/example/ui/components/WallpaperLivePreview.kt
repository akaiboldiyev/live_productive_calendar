package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GoalSnapshot
import com.example.render.WallpaperRenderer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WallpaperLivePreview(
    snapshot: GoalSnapshot,
    showLockscreenOverlay: Boolean,
    modifier: Modifier = Modifier
) {
    val renderer = remember { WallpaperRenderer() }
    val density = LocalDensity.current.density

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0D0E12))
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .testTag("wallpaper_live_preview")
    ) {
        // Core Live Wallpaper Canvas (identical renderer to Live Wallpaper Service)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("wallpaper_canvas")
        ) {
            drawIntoCanvas { canvas ->
                renderer.render(
                    canvas = canvas.nativeCanvas,
                    width = size.width.toInt(),
                    height = size.height.toInt(),
                    snapshot = snapshot,
                    density = density
                )
            }
        }

        // Optional Lock Screen Simulator Overlay
        if (showLockscreenOverlay) {
            LockscreenOverlay()
        }
    }
}

@Composable
private fun LockscreenOverlay() {
    val today = remember { LocalDate.now() }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("d.M EEEE", Locale.getDefault())
    }
    val dateText = remember(today) { today.format(dateFormatter) }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Top lockscreen clock simulation
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "00:11",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.SansSerif
            )
        }

        // Top date
        Text(
            text = dateText,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 58.dp, start = 4.dp)
        )

        // Bottom lockscreen shortcuts
        Icon(
            imageVector = Icons.Default.FlashlightOn,
            contentDescription = "Flashlight shortcut",
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 6.dp, start = 6.dp)
        )

        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = "Camera shortcut",
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 6.dp, end = 6.dp)
        )
    }
}
