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
import com.example.data.GoalLoadState
import com.example.data.GoalRepository
import com.example.model.GoalData
import com.example.model.GoalProgressCalculator
import com.example.render.ViewportBounds
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
import kotlin.math.min

/**
 * Android Live Wallpaper Service rendering the dynamic Goal Dots countdown.
 * Highly battery-efficient: only renders when visible, on real calendar date/settings changes,
 * and maintains exact visual screen centering regardless of launcher wallpaper scrolling.
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
        private var currentLoadState: GoalLoadState = GoalLoadState.Loading
        private var cachedGoal: GoalData? = null
        private var lastRenderedDate: LocalDate? = null

        private var currentXOffset: Float = 0f
        private var currentXPixelOffset: Int = 0

        private val mainHandler = Handler(Looper.getMainLooper())
        private var isReceiverRegistered = false

        // Midnight transition runnable (active only while wallpaper is visibly displayed)
        private val midnightRunnable = object : Runnable {
            override fun run() {
                if (isVisibleState) {
                    val today = LocalDate.now()
                    if (today != lastRenderedDate) {
                        Log.d("GOAL_WALLPAPER", "Midnight triggered: redrawing frame for new date $today")
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
                    Log.d("GOAL_WALLPAPER", "Time/Date broadcast received (${intent?.action}) - redrawing frame")
                    drawFrame()
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            Log.d("GOAL_WALLPAPER", "Engine onCreate: subscribing to goalStateFlow")
            // Observe goal changes from DataStore
            dataCollectJob = scope.launch {
                repository.goalStateFlow.collect { state ->
                    Log.d(
                        "GOAL_WALLPAPER",
                        "Engine collected goalState: $state (isVisible=$isVisibleState, isPreview=$isPreview)"
                    )
                    currentLoadState = state
                    when (state) {
                        is GoalLoadState.Loaded -> cachedGoal = state.goal
                        is GoalLoadState.NoGoal -> cachedGoal = null
                        else -> { /* retain previous or remain null while Loading/Error */ }
                    }

                    // Always redraw when goal updates
                    if (isVisibleState || isPreview) {
                        drawFrame()
                    }
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            Log.d("GOAL_WALLPAPER", "Engine onDestroy")
            unregisterTimeReceiver()
            mainHandler.removeCallbacksAndMessages(null)
            dataCollectJob?.cancel()
            scope.cancel()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible
            Log.d(
                "GOAL_WALLPAPER",
                "onVisibilityChanged: visible=$visible, cachedGoal='${cachedGoal?.name}', loadState=$currentLoadState"
            )

            if (visible) {
                registerTimeReceiver()
                // Always draw a fresh frame when the screen unlocks / becomes visible
                drawFrame()
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
            Log.d(
                "GOAL_WALLPAPER",
                "onSurfaceChanged: surfaceWidth=$width, surfaceHeight=$height, screenWidth=${resources.displayMetrics.widthPixels}"
            )
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            Log.d("GOAL_WALLPAPER", "onSurfaceDestroyed")
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
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            val changed = (currentXOffset != xOffset || currentXPixelOffset != xPixelOffset)
            currentXOffset = xOffset
            currentXPixelOffset = xPixelOffset

            // If surface is wider than screen, update content position so it remains visually centered on screen
            val screenW = resources.displayMetrics.widthPixels
            if (changed && surfaceWidth > screenW && (isVisibleState || isPreview)) {
                Log.d(
                    "GOAL_WALLPAPER",
                    "onOffsetsChanged triggered redraw: xOffset=$xOffset, xPixelOffset=$xPixelOffset"
                )
                drawFrame()
            }
        }

        private fun computeVisibleViewport(surfaceW: Int, surfaceH: Int): ViewportBounds {
            val screenW = resources.displayMetrics.widthPixels.coerceAtLeast(1)
            val screenH = resources.displayMetrics.heightPixels.coerceAtLeast(1)

            val effectiveSurfaceW = surfaceW.coerceAtLeast(1)
            val effectiveSurfaceH = surfaceH.coerceAtLeast(1)

            val visibleWidth = min(effectiveSurfaceW, screenW).toFloat()
            val visibleHeight = effectiveSurfaceH.toFloat()

            // When launcher surface is wider than physical screen (e.g. Samsung One UI multi-page wallpaper),
            // calculate the visible window inside the surface canvas so content stays centered on the physical screen.
            val visibleLeft = if (effectiveSurfaceW > screenW) {
                val extraWidth = (effectiveSurfaceW - screenW).toFloat()
                if (currentXPixelOffset != 0) {
                    (-currentXPixelOffset.toFloat()).coerceIn(0f, extraWidth)
                } else {
                    (currentXOffset.coerceIn(0f, 1f) * extraWidth)
                }
            } else {
                0f
            }

            return ViewportBounds(
                left = visibleLeft,
                top = 0f,
                right = visibleLeft + visibleWidth,
                bottom = visibleHeight
            )
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return

            val today = LocalDate.now()
            lastRenderedDate = today

            val snapshot = GoalProgressCalculator.computeSnapshot(cachedGoal, today)
            val density = resources.displayMetrics.density
            val viewport = computeVisibleViewport(surfaceWidth, surfaceHeight)

            Log.d(
                "GOAL_WALLPAPER",
                "drawFrame: title='${snapshot.titleText}', status=${snapshot.status::class.simpleName}, dots=${snapshot.totalDots}, loadState=${currentLoadState::class.simpleName}"
            )

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
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight,
                        viewport = viewport,
                        loadState = currentLoadState,
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
