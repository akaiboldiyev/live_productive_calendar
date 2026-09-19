package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.example.GoalApplication
import com.example.data.GoalLoadState
import com.example.data.GoalRepository
import com.example.data.WallpaperBackgroundStorage
import com.example.model.BackgroundType
import com.example.model.GoalData
import com.example.model.GoalProgressCalculator
import com.example.model.WallpaperBackgroundConfig
import com.example.render.ViewportBounds
import com.example.render.WallpaperBackgroundRenderer
import com.example.render.WallpaperRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        private const val TAG_TIMELINE = "GOAL_TIMELINE"
        private const val TAG_SERVICE = "GOAL_SERVICE"
        private const val TAG_ENGINE = "GOAL_ENGINE"
        private const val FOREGROUND_NOTIFICATION_ID = 4201
        private const val FOREGROUND_CHANNEL_ID = "goal_dots_wallpaper"
        private val engineSequence = AtomicInteger(0)

        private fun t(): String = "[+${GoalApplication.elapsedSinceProcessStart()}ms]"
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundProtection()
        val wm = WallpaperManager.getInstance(applicationContext)
        val info = wm.wallpaperInfo
        com.example.diagnostics.DiagnosticRecorder.log("WALLPAPERSERVICE_ONCREATE", "activeComponent=${info?.component}")
        Log.d(TAG_TIMELINE, "${t()} WallpaperService.onCreate: currentActiveWallpaper=${info?.component}")
        Log.d(TAG_SERVICE, "GoalWallpaperService onCreate: process restarted/created")
    }

    override fun onDestroy() {
        stopForegroundProtection()
        super.onDestroy()
        com.example.diagnostics.DiagnosticRecorder.log("WALLPAPERSERVICE_ONDESTROY")
        Log.d(TAG_TIMELINE, "${t()} WallpaperService.onDestroy")
        Log.d(TAG_SERVICE, "GoalWallpaperService onDestroy")
    }

    /**
     * HyperOS' recents cleaner explicitly kills every process belonging to the swiped task,
     * including a bound wallpaper service. Keeping the *existing* service foreground gives the
     * system a user-visible, higher-priority service rather than adding a second APK or a
     * parallel watchdog. Any OEM refusal is deliberately non-fatal: WallpaperService still
     * follows its normal system-managed lifecycle.
     */
    private fun startForegroundProtection() {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        FOREGROUND_CHANNEL_ID,
                        getString(com.example.R.string.wallpaper_notification_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = getString(com.example.R.string.wallpaper_notification_channel_description)
                        setShowBadge(false)
                    }
                )
            }

            val notification = Notification.Builder(this, FOREGROUND_CHANNEL_ID)
                .setSmallIcon(com.example.R.drawable.ic_stat_goal_dots)
                .setContentTitle(getString(com.example.R.string.wallpaper_notification_title))
                .setContentText(getString(com.example.R.string.wallpaper_notification_text))
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
            Log.i(TAG_SERVICE, "Wallpaper foreground protection started")
        } catch (error: Exception) {
            Log.e(TAG_SERVICE, "Wallpaper foreground protection could not start", error)
        }
    }

    private fun stopForegroundProtection() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (error: Exception) {
            Log.w(TAG_SERVICE, "Wallpaper foreground protection could not stop cleanly", error)
        }
    }

    override fun onCreateEngine(): Engine {
        val id = engineSequence.incrementAndGet()
        com.example.diagnostics.DiagnosticRecorder.log("WALLPAPERSERVICE_ONCREATEENGINE", "engineId=$id")
        Log.d(TAG_TIMELINE, "${t()} WallpaperService.onCreateEngine: assigned engineId=$id")
        Log.d(TAG_SERVICE, "GoalWallpaperService onCreateEngine: assigned engineId=$id")
        return GoalEngine(id)
    }

    inner class GoalEngine(private val engineId: Int) : Engine() {

        private val renderer = WallpaperRenderer()
        private val backgroundRenderer = WallpaperBackgroundRenderer()
        private val repository by lazy { GoalRepository.getInstance(applicationContext) }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var dataCollectJob: Job? = null
        private var backgroundConfigCollectJob: Job? = null
        private var backgroundLoadJob: Job? = null

        // Lifecycle-managed surface and visibility states
        private var isSurfaceReady = false
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var isVisibleState = false
        private var surfaceCreatedTimestamp: Long = 0L
        private var hasPostedFirstValidFrame = false

        // State machine for asynchronous DataStore flow
        private var currentLoadState: GoalLoadState = GoalLoadState.Loading
        private var cachedGoal: GoalData? = null
        private var lastRenderedDate: LocalDate? = null
        private var currentBackgroundConfig = WallpaperBackgroundConfig()

        // Parallax and viewport bounds
        private var currentXOffset: Float = 0f
        private var currentXPixelOffset: Int = 0

        private val mainHandler = Handler(Looper.getMainLooper())
        private var isReceiverRegistered = false
        private var isAppStateReceiverRegistered = false
        private var recoveryRenderPending = false

        // A bounded one-shot recovery for an OEM visibility-callback race, never a loop.
        private val recoveryRenderRunnable = Runnable { renderRecoveryFrame() }

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

        // Broadcast receiver for system date, time, and timezone changes + multi-process goal updates
        private val timeChangedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                com.example.diagnostics.DiagnosticRecorder.log("BROADCAST_RECEIVED", "action=$action, visible=$isVisibleState")
                if (action == GoalRepository.ACTION_BACKGROUND_UPDATED) {
                    Log.d(TAG_ENGINE, "Engine[$engineId] Background update broadcast received from UI process")
                    backgroundRenderer.invalidateCache()
                    scheduleBackgroundLoadIfNeeded()
                    requestRender("backgroundUpdatedBroadcast")
                } else if (action == GoalRepository.ACTION_GOAL_UPDATED) {
                    Log.d(TAG_ENGINE, "Engine[$engineId] Goal updated broadcast received from UI process")
                    requestRender("goalUpdatedBroadcast")
                } else if (isVisibleState) {
                    Log.d(TAG_ENGINE, "Engine[$engineId] Time/Date broadcast received ($action)")
                    requestRender("systemTimeChanged")
                }
            }
        }

        // Unlike date/time updates, this receiver remains available while the Engine is hidden.
        private val appStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == GoalRepository.ACTION_APP_MOVED_TO_BACKGROUND) {
                    com.example.diagnostics.DiagnosticRecorder.log(
                        "UI_MOVED_TO_BACKGROUND",
                        "engineId=$engineId, ready=$isSurfaceReady, visible=$isVisibleState"
                    )
                    Log.d(TAG_ENGINE, "Engine[$engineId] UI moved to background; scheduling recovery frame")
                    scheduleRecoveryRender()
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            registerAppStateReceiver()
            com.example.diagnostics.DiagnosticRecorder.log("ENGINE_ONCREATE", "engineId=$engineId, isPreview=$isPreview")
            Log.d(TAG_TIMELINE, "${t()} Engine[$engineId].onCreate: isPreview=$isPreview")
            Log.d(TAG_ENGINE, "Engine[$engineId] onCreate: isPreview=$isPreview")

            // Subscribe to DataStore flow. The Engine is completely decoupled from Activity lifecycle
            dataCollectJob = scope.launch {
                Log.d(TAG_TIMELINE, "${t()} Engine[$engineId] DataStore collection started")
                com.example.diagnostics.DiagnosticRecorder.log("DATASTORE_COLLECT_START", "engineId=$engineId")
                repository.goalStateFlow.collect { state ->
                    com.example.diagnostics.DiagnosticRecorder.log(
                        "DATASTORE_EMIT",
                        "engineId=$engineId, state=${state::class.simpleName}, isReady=$isSurfaceReady, isVisible=$isVisibleState"
                    )
                    com.example.diagnostics.DiagnosticRecorder.updateState("Engine[$engineId]: ${state::class.simpleName}")
                    Log.d(
                        TAG_TIMELINE,
                        "${t()} Engine[$engineId] DataStore ${state::class.simpleName} (ready=$isSurfaceReady, visible=$isVisibleState)"
                    )
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
                    if (recoveryRenderPending && state !is GoalLoadState.Loading) {
                        scheduleRecoveryRender()
                    }
                }
            }

            // Background configuration shares the existing multi-process DataStore. The
            // bitmap itself remains app-private and is independently rebuilt after process death.
            backgroundConfigCollectJob = scope.launch {
                repository.backgroundConfigFlow.collect { config ->
                    val changed = currentBackgroundConfig != config
                    currentBackgroundConfig = config
                    if (changed) {
                        backgroundRenderer.invalidateCache()
                    }
                    scheduleBackgroundLoadIfNeeded()
                    requestRender("backgroundConfigUpdated")
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceCreatedTimestamp = SystemClock.elapsedRealtime()
            isSurfaceReady = true
            val frame = holder.surfaceFrame
            if (frame != null && frame.width() > 0 && frame.height() > 0) {
                surfaceWidth = frame.width()
                surfaceHeight = frame.height()
            }
            com.example.diagnostics.DiagnosticRecorder.log(
                "SURFACE_CREATED",
                "engineId=$engineId, dim=(${surfaceWidth}x$surfaceHeight), visible=$isVisibleState, preview=$isPreview"
            )
            Log.d(
                TAG_TIMELINE,
                "${t()} Engine[$engineId].onSurfaceCreated: surface=(${surfaceWidth}x$surfaceHeight), visible=$isVisibleState, preview=$isPreview"
            )
            Log.d(
                TAG_ENGINE,
                "Engine[$engineId] onSurfaceCreated: surface=(${surfaceWidth}x$surfaceHeight), isVisible=$isVisibleState, isPreview=$isPreview"
            )
            scheduleBackgroundLoadIfNeeded()
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
            val dimensionsChanged = surfaceWidth != width || surfaceHeight != height
            surfaceWidth = width
            surfaceHeight = height
            com.example.diagnostics.DiagnosticRecorder.log(
                "SURFACE_CHANGED",
                "engineId=$engineId, format=$format, dim=(${width}x$height), visible=$isVisibleState"
            )
            Log.d(
                TAG_TIMELINE,
                "${t()} Engine[$engineId].onSurfaceChanged: format=$format, surface=(${width}x$height), visible=$isVisibleState"
            )
            Log.d(
                TAG_ENGINE,
                "Engine[$engineId] onSurfaceChanged: format=$format, surface=(${width}x$height), visible=$isVisibleState"
            )
            if (dimensionsChanged) {
                backgroundRenderer.invalidateCache()
            }
            scheduleBackgroundLoadIfNeeded()
            requestRender("onSurfaceChanged")
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible
            val wm = WallpaperManager.getInstance(applicationContext)
            val info = wm.wallpaperInfo
            com.example.diagnostics.DiagnosticRecorder.log(
                "VISIBILITY_CHANGED",
                "engineId=$engineId, visible=$visible, activeWall=${info?.component}, ready=$isSurfaceReady, loadState=${currentLoadState::class.simpleName}"
            )
            Log.d(
                TAG_TIMELINE,
                "${t()} Engine[$engineId].onVisibilityChanged: visible=$visible, wallpaperComponent=${info?.component}, ready=$isSurfaceReady, loadState=${currentLoadState::class.simpleName}"
            )
            Log.d(
                TAG_ENGINE,
                "Engine[$engineId] onVisibilityChanged: visible=$visible, ready=$isSurfaceReady, loadState=${currentLoadState::class.simpleName}, goal='${cachedGoal?.name}'"
            )

            if (visible) {
                registerTimeReceiver()
                scheduleMidnightUpdate()
                scheduleBackgroundLoadIfNeeded()
                requestRender("onVisibilityChanged(true)")
            } else {
                unregisterTimeReceiver()
                mainHandler.removeCallbacks(midnightRunnable)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            isSurfaceReady = false
            com.example.diagnostics.DiagnosticRecorder.log("SURFACE_DESTROYED", "engineId=$engineId")
            Log.d(TAG_TIMELINE, "${t()} Engine[$engineId].onSurfaceDestroyed")
            Log.d(TAG_ENGINE, "Engine[$engineId] onSurfaceDestroyed")
            unregisterTimeReceiver()
            mainHandler.removeCallbacks(midnightRunnable)
        }

        override fun onDestroy() {
            super.onDestroy()
            isSurfaceReady = false
            com.example.diagnostics.DiagnosticRecorder.log("ENGINE_ONDESTROY", "engineId=$engineId")
            Log.d(TAG_TIMELINE, "${t()} Engine[$engineId].onDestroy")
            Log.d(TAG_ENGINE, "Engine[$engineId] onDestroy")
            unregisterTimeReceiver()
            unregisterAppStateReceiver()
            mainHandler.removeCallbacksAndMessages(null)
            dataCollectJob?.cancel()
            backgroundConfigCollectJob?.cancel()
            backgroundLoadJob?.cancel()
            backgroundRenderer.release()
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
                TAG_TIMELINE,
                "${t()} Engine[$engineId] requestRender: reason='$reason', ready=$isSurfaceReady, visible=$isVisibleState, loadState=${currentLoadState::class.simpleName}"
            )
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

        private fun scheduleRecoveryRender() {
            recoveryRenderPending = true
            mainHandler.removeCallbacks(recoveryRenderRunnable)
            // Let the Recents/launcher transition release its Surface transaction first.
            mainHandler.postDelayed(recoveryRenderRunnable, 300L)
        }

        /**
         * Bypasses only the visibility gate once after the UI goes away. Surface readiness
         * and durable DataStore state are still required, so this cannot become a render loop.
         */
        private fun renderRecoveryFrame() {
            recoveryRenderPending = false
            if (!isSurfaceReady) {
                Log.d(TAG_ENGINE, "Engine[$engineId] Recovery frame skipped: Surface is not ready")
                com.example.diagnostics.DiagnosticRecorder.log("RECOVERY_RENDER_SKIPPED", "engineId=$engineId, noSurface")
                return
            }
            if (currentLoadState is GoalLoadState.Loading) {
                Log.d(TAG_ENGINE, "Engine[$engineId] Recovery frame deferred: DataStore is loading")
                recoveryRenderPending = true
                return
            }

            // A new process can have a valid Surface but an OEM-provided false visibility flag.
            scheduleBackgroundLoadIfNeeded(allowWhenHidden = true)
            com.example.diagnostics.DiagnosticRecorder.log(
                "RECOVERY_RENDER",
                "engineId=$engineId, visible=$isVisibleState, preview=$isPreview"
            )
            Log.d(TAG_ENGINE, "Engine[$engineId] Drawing one recovery frame (visible=$isVisibleState)")
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

            Log.d(TAG_TIMELINE, "${t()} Engine[$engineId] lockCanvas START (attempt $attempt)")
            com.example.diagnostics.DiagnosticRecorder.markRender()
            com.example.diagnostics.DiagnosticRecorder.log("LOCK_CANVAS_START", "engineId=$engineId, attempt=$attempt")
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
            } catch (e: Exception) {
                com.example.diagnostics.DiagnosticRecorder.log("LOCK_CANVAS_EXCEPTION", "engineId=$engineId, attempt=$attempt, err=${e.message}")
                Log.e(TAG_TIMELINE, "${t()} Engine[$engineId] lockCanvas EXCEPTION on attempt $attempt: ${e.message}")
                Log.e(TAG_ENGINE, "Engine[$engineId] Failed to lockCanvas on attempt $attempt", e)
            }

            if (canvas != null) {
                com.example.diagnostics.DiagnosticRecorder.log("LOCK_CANVAS_SUCCESS", "engineId=$engineId, attempt=$attempt")
                Log.d(TAG_TIMELINE, "${t()} Engine[$engineId] lockCanvas SUCCESS (attempt $attempt)")
                try {
                    backgroundRenderer.drawBackground(
                        canvas = canvas,
                        config = currentBackgroundConfig,
                        width = surfaceWidth,
                        height = surfaceHeight
                    )
                    Log.d(TAG_TIMELINE, "${t()} Engine[$engineId] WallpaperRenderer.render START (loadState=${currentLoadState::class.simpleName}, dots=${snapshot.totalDots})")
                    renderer.render(
                        canvas = canvas,
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight,
                        viewport = viewport,
                        loadState = currentLoadState,
                        snapshot = snapshot,
                        density = density,
                        drawDefaultBackground = false
                    )
                    Log.d(TAG_TIMELINE, "${t()} Engine[$engineId] WallpaperRenderer.render END")
                } catch (e: Exception) {
                    com.example.diagnostics.DiagnosticRecorder.log("RENDER_EXCEPTION", "engineId=$engineId, err=${e.message}")
                    Log.e(TAG_ENGINE, "Engine[$engineId] Error during wallpaper render", e)
                } finally {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                        com.example.diagnostics.DiagnosticRecorder.log("UNLOCK_CANVAS_POST", "engineId=$engineId")
                        Log.d(TAG_TIMELINE, "${t()} Engine[$engineId] unlockCanvasAndPost completed")
                        if (!hasPostedFirstValidFrame && currentLoadState is GoalLoadState.Loaded) {
                            hasPostedFirstValidFrame = true
                            val now = SystemClock.elapsedRealtime()
                            val fromSurface = if (surfaceCreatedTimestamp > 0L) "${now - surfaceCreatedTimestamp}ms" else "N/A"
                            com.example.diagnostics.DiagnosticRecorder.markValidFrame()
                            com.example.diagnostics.DiagnosticRecorder.log(
                                "FIRST_VALID_FRAME_POSTED",
                                "engineId=$engineId, timeFromSurface=$fromSurface, title='${snapshot.titleText}'"
                            )
                            Log.d(TAG_TIMELINE, "${t()} Engine[$engineId] FIRST VALID FRAME POSTED (timeFromSurfaceCreated=$fromSurface)")
                        }
                    } catch (e: Exception) {
                        com.example.diagnostics.DiagnosticRecorder.log("UNLOCK_CANVAS_FAILED", "engineId=$engineId, err=${e.message}")
                        Log.e(TAG_TIMELINE, "${t()} Engine[$engineId] unlockCanvasAndPost FAILED: ${e.message}")
                        Log.e(TAG_ENGINE, "Engine[$engineId] Failed to unlockCanvasAndPost", e)
                    }
                }
            } else {
                com.example.diagnostics.DiagnosticRecorder.log("LOCK_CANVAS_NULL", "engineId=$engineId, attempt=$attempt")
                Log.w(TAG_TIMELINE, "${t()} Engine[$engineId] lockCanvas NULL (attempt $attempt)")
                // If lockCanvas failed (e.g. keyguard transition or surface allocation race), retry
                if (attempt < 3 && isSurfaceReady && (isVisibleState || isPreview)) {
                    Log.d(TAG_TIMELINE, "${t()} Engine[$engineId] Scheduling lockCanvas retry ${attempt + 1} in 100ms")
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

        /**
         * Starts a sampled decode only when an image is configured and the Engine has a drawable
         * surface. Canvas drawing stays fast: until decoding finishes it safely shows black.
         */
        private fun scheduleBackgroundLoadIfNeeded(allowWhenHidden: Boolean = false) {
            if (
                !isSurfaceReady || surfaceWidth <= 0 || surfaceHeight <= 0 ||
                (!allowWhenHidden && !isVisibleState && !isPreview)
            ) {
                return
            }
            if (currentBackgroundConfig.type != BackgroundType.IMAGE) {
                backgroundLoadJob?.cancel()
                return
            }
            if (backgroundRenderer.isCachedFor(currentBackgroundConfig, surfaceWidth, surfaceHeight)) {
                return
            }

            backgroundLoadJob?.cancel()
            val requestedConfig = currentBackgroundConfig
            val requestedWidth = surfaceWidth
            val requestedHeight = surfaceHeight
            backgroundLoadJob = scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    WallpaperBackgroundRenderer.decodeBitmap(
                        WallpaperBackgroundStorage.backgroundFile(applicationContext),
                        requestedWidth,
                        requestedHeight
                    )
                }
                if (
                    isSurfaceReady &&
                    currentBackgroundConfig == requestedConfig &&
                    surfaceWidth == requestedWidth &&
                    surfaceHeight == requestedHeight
                ) {
                    backgroundRenderer.setBitmap(bitmap, requestedWidth, requestedHeight)
                    if (isVisibleState || isPreview) {
                        requestRender("backgroundBitmapLoaded")
                    } else {
                        // This decode was explicitly allowed by the one-shot recovery path.
                        // Post the sampled image once; the cache makes the next draw cheap.
                        scheduleRecoveryRender()
                    }
                } else {
                    bitmap?.takeUnless { it.isRecycled }?.recycle()
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
                    addAction(GoalRepository.ACTION_GOAL_UPDATED)
                    addAction(GoalRepository.ACTION_BACKGROUND_UPDATED)
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

        private fun registerAppStateReceiver() {
            if (isAppStateReceiverRegistered) return

            val filter = IntentFilter(GoalRepository.ACTION_APP_MOVED_TO_BACKGROUND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(appStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(appStateReceiver, filter)
            }
            isAppStateReceiverRegistered = true
        }

        private fun unregisterAppStateReceiver() {
            if (!isAppStateReceiverRegistered) return
            try {
                unregisterReceiver(appStateReceiver)
            } catch (e: Exception) {
                Log.w(TAG_ENGINE, "Error unregistering app-state receiver", e)
            }
            isAppStateReceiverRegistered = false
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
