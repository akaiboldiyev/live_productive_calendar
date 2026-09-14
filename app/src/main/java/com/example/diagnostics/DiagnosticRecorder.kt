package com.example.diagnostics

import android.app.WallpaperManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe, persistent in-app diagnostic recorder for debugging lifecycle,
 * render events, and wallpaper binder state across process deaths/restarts.
 */
object DiagnosticRecorder {

    private const val TAG = "GOAL_DIAGNOSTIC"
    private const val LOG_FILE_NAME = "goal_dots_diagnostics.log"
    private val executor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val lastValidFrameMs = AtomicLong(0L)
    private val lastRenderMs = AtomicLong(0L)
    private val lastStateSnapshot = AtomicReference<String>("INITIALIZING")

    fun log(event: String, details: String = "") {
        val nowMs = SystemClock.elapsedRealtime()
        val wallTime = dateFormat.format(Date())
        val pid = Process.myPid()
        val procName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            android.app.Application.getProcessName()
        } else {
            "PID:$pid"
        }
        val line = "[$wallTime | +${nowMs}ms | proc:$procName | PID:$pid] $event ${if (details.isNotEmpty()) ":: $details" else ""}"

        Log.d(TAG, line)

        // Write asynchronously to app internal file so it survives activity teardown
        executor.execute {
            try {
                val app = com.example.GoalApplication.instance ?: return@execute
                val file = File(app.filesDir, LOG_FILE_NAME)
                FileWriter(file, true).use { writer ->
                    writer.appendLine(line)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing to diagnostic log file", e)
            }
        }
    }

    fun markValidFrame() {
        lastValidFrameMs.set(SystemClock.elapsedRealtime())
    }

    fun markRender() {
        lastRenderMs.set(SystemClock.elapsedRealtime())
    }

    fun updateState(state: String) {
        lastStateSnapshot.set(state)
    }

    fun markState(context: Context, stateTag: String): String {
        val wm = WallpaperManager.getInstance(context.applicationContext)
        val info = try { wm.wallpaperInfo } catch (e: Exception) { null }
        val isServiceActive = info?.packageName == context.packageName
        val pid = Process.myPid()
        val elapsed = SystemClock.elapsedRealtime()
        val nowWall = dateFormat.format(Date())
        val lastFrame = lastValidFrameMs.get()
        val lastRender = lastRenderMs.get()

        val summary = buildString {
            appendLine("=== $stateTag ===")
            appendLine("Timestamp: $nowWall (uptime: ${elapsed}ms)")
            appendLine("Current PID: $pid")
            appendLine("Active Live Wallpaper Component: ${info?.component?.flattenToString() ?: "NONE / NULL / STATIC"}")
            appendLine("Is GoalDots Active Wallpaper: $isServiceActive")
            appendLine("Last Recorded State: ${lastStateSnapshot.get()}")
            appendLine("Last Render: ${if (lastRender > 0L) "+${lastRender}ms (${elapsed - lastRender}ms ago)" else "NEVER"}")
            appendLine("Last Valid Frame Posted: ${if (lastFrame > 0L) "+${lastFrame}ms (${elapsed - lastFrame}ms ago)" else "NEVER"}")
            appendLine("===================")
        }

        log("USER_STATE_MARK: $stateTag", summary.replace("\n", " | "))
        return summary
    }

    fun getLogContent(context: Context): String {
        return try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) file.readText() else "No diagnostic logs recorded yet."
        } catch (e: Exception) {
            "Error reading diagnostic file: ${e.message}"
        }
    }

    fun clearLogs(context: Context) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) file.delete()
            log("LOGS_CLEARED", "Diagnostic log file reset")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs", e)
        }
    }
}
