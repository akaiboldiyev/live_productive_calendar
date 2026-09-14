package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Android Live Wallpaper Service rendering the dynamic Goal Dots countdown.
 * Highly battery-efficient: only renders when visible, on real calendar date/settings changes,
 * and maintains exact visual screen centering regardless of launcher wallpaper scrolling.
 *
 * Designed to be 100% resilient across process death, Activity destruction, Recents dismissal,
 * and OEM-specific (HyperOS/MIUI/OneUI) surface recreation cycles.
 */
class GoalWallpaperService : WallpaperService() {

    companion object {
        private const val TAG_SERVICE = "GOAL_SERVICE"
        private const val TAG_ENGINE = "GOAL_ENGINE"
        private val engineSequence = AtomicInteger(0)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG_SERVICE, "GoalWallpaperService onCreate: process restarted/created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG_SERVICE, "GoalWallpaperService onDestroy")
    }

    override fun onCreateEngine(): Engine {
        val id = engineSequence.incrementAndGet()
        Log.d(TAG_SERVICE, "GoalWallpaperService onCreateEngine: assigned engineId=$id")
        return GoalEngine(id)
    }

    inner class GoalEngine(private val engineId: Int) : Engine() {

        private val renderer = WallpaperRenderer()
        private val repository by lazy { GoalRepository.getInstance(applicationContext) }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var dataCollectJob: Job? = null

        // Lifecycle-managed surface and visibility states
        private var isSurfaceReady = false
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var isVisibleState = false

        // State machine for asynchronous DataStore flow
        private var currentLoadState: GoalLoadState = GoalLoadState.Loading
        private var cachedGoal: GoalData? = null
        private var lastRenderedDate: LocalDate? = null

        // Parallax and viewport bounds
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
                        Log.d(TAG_ENGINE, "Engine[$engineId] Midnight triggered: redrawing frame for new date $today")
                        requestRender("midnightDateChange")
                    }
                    scheduleMidnightUpdate()
                }
            }
        }

        // Broadcast receiver for system date, time, and timezone changes
        private val timeChangedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (isVisibleState) {
                    Log.d(TAG_ENGINE, "Engine[$engineId] Time/Date broadcast received (${intent?.action})")
                    requestRender("systemTimeChanged")
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            Log.d(TAG_ENGINE, "Engine[$engineId] onCreate: isPreview=$isPreview")

            // Subscribe to DataStore flow. The Engine is completely decoupled from Activity lifecycle
            dataCollectJob = scope.launch {
                repository.goalStateFlow.collect { state ->
                    Log.d(
                        TAG_ENGINE,
                        "Engine[$engineId] DataStore emitted: ${state::class.simpleName} (ready=$isSurfaceReady, visible=$isVisibleState)"
                    )
                    currentLoadState = state
                    when (state) {
                        is GoalLoadState.Loaded -> cachedGoal = state.goal
                        is GoalLoadState.NoGoal -> cachedGoal = null
                        else -> { /* retain previous or remain null while Loading/Error */ }
                    }

                    // Always request render when DataStore updates, regardless of arrival order
                    requestRender("dataStoreUpdated")
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            isSurfaceReady = true
            val frame = holder.surfaceFrame
            if (frame != null && frame.width() > 0 && frame.height() > 0) {
                surfaceWidth = frame.width()
                surfaceHeight = frame.height()
            }
            Log.d(
                TAG_ENGINE,
                "Engine[$engineId] onSurfaceCreated: surface=(${surfaceWidth}x$surfaceHeight), isVisible=$isVisibleState, isPreview=$isPreview"
            )
            requestRender("onSurfaceCreated")
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder?,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            isSurfaceReady = true
            surfaceWidth = width
            surfaceHeight = height
            Log.d(
                TAG_ENGINE,
                "Engine[$engineId] onSurfaceChanged: format=$format, surface=(${width}x$height), isVisible=$isVisibleState"
            )
            requestRender("onSurfaceChanged")
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible
            Log.d(
                TAG_ENGINE,
                "Engine[$engineId] onVisibilityChanged: visible=$visible, ready=$isSurfaceReady, loadState=${currentLoadState::class.simpleName}, goal='${cachedGoal?.name}'"
            )

            if (visible) {
                registerTimeReceiver()
                scheduleMidnightUpdate()
                requestRender("onVisibilityChanged(true)")
            } else {
                unregisterTimeReceiver()
                mainHandler.removeCallbacks(midnightRunnable)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            isSurfaceReady = false
            Log.d(TAG_ENGINE, "Engine[$engineId] onSurfaceDestroyed")
            unregisterTimeReceiver()
            mainHandler.removeCallbacks(midnightRunnable)
        }

        override fun onDestroy() {
            super.onDestroy()
            isSurfaceReady = false
            Log.d(TAG_ENGINE, "Engine[$engineId] onDestroy")
            unregisterTimeReceiver()
            mainHandler.removeCallbacksAndMessages(null)
            dataCollectJob?.cancel()
            scope.cancel()
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
                requestRender("onOffsetsChanged")
            }
        }

        /**
         * Consolidated state machine gate for rendering.
         * Ensures that regardless of whether DataStore loads first or Surface is created first,
         * or if the screen was turned on before/after surface initialization, the latest
         * immutable state will always be drawn as soon as preconditions are satisfied.
         */
        private fun requestRender(reason: String) {
            Log.d(
                TAG_ENGINE,
                "Engine[$engineId] requestRender triggered by '$reason' [ready=$isSurfaceReady, visible=$isVisibleState, preview=$isPreview, loadState=${currentLoadState::class.simpleName}, surface=(${surfaceWidth}x$surfaceHeight)]"
            )

            if (!isSurfaceReady) {
                Log.d(TAG_ENGINE, "Engine[$engineId] Surface not ready -> deferring render")
                return
            }

            if (!isVisibleState && !isPreview) {
                Log.d(TAG_ENGINE, "Engine[$engineId] Wallpaper not visible -> deferring render until onVisibilityChanged(true)")
                return
            }

            if (currentLoadState is GoalLoadState.Loading) {
                Log.d(TAG_ENGINE, "Engine[$engineId] DataStore is loading -> deferring render until data arrival")
                return
            }

            // Ensure valid surface dimensions
            if (surfaceWidth <= 0 || surfaceHeight <= 0) {
                val frame = surfaceHolder?.surfaceFrame
                if (frame != null && frame.width() > 0 && frame.height() > 0) {
                    surfaceWidth = frame.width()
                    surfaceHeight = frame.height()
                } else {
                    Log.d(TAG_ENGINE, "Engine[$engineId] Surface dimensions 0 -> deferring render")
                    return
                }
            }

            drawFrame(attempt = 1)
        }

        private fun drawFrame(attempt: Int = 1) {
            val holder = surfaceHolder ?: return
            if (!isSurfaceReady || surfaceWidth <= 0 || surfaceHeight <= 0) return

            val today = LocalDate.now()
            lastRenderedDate = today

            val snapshot = GoalProgressCalculator.computeSnapshot(cachedGoal, today)
            val density = resources.displayMetrics.density
            val viewport = computeVisibleViewport(surfaceWidth, surfaceHeight)

            Log.d(
                TAG_ENGINE,
                "Engine[$engineId] drawFrame: attempt=$attempt, title='${snapshot.titleText}', status=${snapshot.status::class.simpleName}, dots=${snapshot.totalDots}, loadState=${currentLoadState::class.simpleName}"
            )

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
            } catch (e: Exception) {
                Log.e(TAG_ENGINE, "Engine[$engineId] Failed to lockCanvas on attempt $attempt", e)
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
                    Log.e(TAG_ENGINE, "Engine[$engineId] Error during wallpaper render", e)
                } finally {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e(TAG_ENGINE, "Engine[$engineId] Failed to unlockCanvasAndPost", e)
                    }
                }
            } else {
                // If lockCanvas failed (e.g. keyguard transition or surface allocation race), retry
                if (attempt < 3 && isSurfaceReady && (isVisibleState || isPreview)) {
                    Log.d(TAG_ENGINE, "Engine[$engineId] Scheduling retry $attempt in 100ms")
                    mainHandler.postDelayed({
                        if (isSurfaceReady && (isVisibleState || isPreview)) {
                            drawFrame(attempt + 1)
                        }
                    }, 100L)
                }
            }
        }

        private fun computeVisibleViewport(surfaceW: Int, surfaceH: Int): ViewportBounds {
            val screenW = resources.displayMetrics.widthPixels.coerceAtLeast(1)
            val screenH = resources.displayMetrics.heightPixels.coerceAtLeast(1)

            val effectiveSurfaceW = surfaceW.coerceAtLeast(1)
            val effectiveSurfaceH = surfaceH.coerceAtLeast(1)

            val visibleWidth = min(effectiveSurfaceW, screenW).toFloat()
            val visibleHeight = effectiveSurfaceH.toFloat()

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
                    Log.w(TAG_ENGINE, "Error unregistering time receiver", e)
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
