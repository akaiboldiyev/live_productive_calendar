package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.example.data.GoalRepository
import com.example.model.GoalData
import com.example.model.GoalProgressCalculator
import com.example.render.WallpaperRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Android Live Wallpaper Service rendering the dynamic Goal Dots countdown.
 * Highly battery-efficient: only renders when visible and on real calendar date/settings changes.
 */
class GoalWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return GoalEngine()
    }

    inner class GoalEngine : Engine() {

        private val tag = "GoalWallpaperEngine"
        private val renderer = WallpaperRenderer()
        private val repository by lazy { GoalRepository.getInstance(applicationContext) }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var dataCollectJob: Job? = null

        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var isVisibleState = false
        private var cachedGoal: GoalData? = null
        private var lastRenderedDate: LocalDate? = null

        private val mainHandler = Handler(Looper.getMainLooper())
        private var isReceiverRegistered = false

        // Midnight transition runnable (active only while wallpaper is visibly displayed)
        private val midnightRunnable = object : Runnable {
            override fun run() {
                if (isVisibleState) {
                    val today = LocalDate.now()
                    if (today != lastRenderedDate) {
                        drawFrame()
                    }
                    scheduleMidnightUpdate()
                }
            }
        }

        // Broadcast receiver for system date, time, and timezone changes
        private val timeChangedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (isVisibleState) {
                    drawFrame()
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            // Observe goal changes from DataStore
            dataCollectJob = scope.launch {
                repository.goalFlow.collect { newGoal ->
                    cachedGoal = newGoal
                    if (isVisibleState) {
                        drawFrame()
                    }
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            unregisterTimeReceiver()
            mainHandler.removeCallbacksAndMessages(null)
            dataCollectJob?.cancel()
            scope.cancel()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible

            if (visible) {
                registerTimeReceiver()
                // Check if calendar day transitioned while wallpaper was invisible
                val today = LocalDate.now()
                if (today != lastRenderedDate || cachedGoal == null) {
                    drawFrame()
                }
                scheduleMidnightUpdate()
            } else {
                unregisterTimeReceiver()
                mainHandler.removeCallbacks(midnightRunnable)
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder?,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            isVisibleState = false
            unregisterTimeReceiver()
            mainHandler.removeCallbacks(midnightRunnable)
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            // Deliberately keep content centered and stable across home screen paging.
            // Do NOT re-render on offset changes to save battery and maintain visual stability.
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return

            val today = LocalDate.now()
            lastRenderedDate = today

            val snapshot = GoalProgressCalculator.computeSnapshot(cachedGoal, today)
            val density = resources.displayMetrics.density

            var canvas = try {
                holder.lockCanvas()
            } catch (e: Exception) {
                Log.e(tag, "Failed to lock canvas", e)
                null
            }

            if (canvas != null) {
                try {
                    renderer.render(
                        canvas = canvas,
                        width = surfaceWidth,
                        height = surfaceHeight,
                        snapshot = snapshot,
                        density = density
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Error during wallpaper render", e)
                } finally {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to unlockCanvasAndPost", e)
                    }
                }
            }
        }

        private fun registerTimeReceiver() {
            if (!isReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_DATE_CHANGED)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                    addAction(Intent.ACTION_LOCALE_CHANGED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(timeChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(timeChangedReceiver, filter)
                }
                isReceiverRegistered = true
            }
        }

        private fun unregisterTimeReceiver() {
            if (isReceiverRegistered) {
                try {
                    unregisterReceiver(timeChangedReceiver)
                } catch (e: Exception) {
                    Log.w(tag, "Error unregistering time receiver", e)
                }
                isReceiverRegistered = false
            }
        }

        private fun scheduleMidnightUpdate() {
            mainHandler.removeCallbacks(midnightRunnable)
            val now = LocalDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
            val millisUntilMidnight = Duration.between(now, nextMidnight).toMillis() + 150L
            mainHandler.postDelayed(midnightRunnable, millisUntilMidnight.coerceAtLeast(1000L))
        }
    }
}
