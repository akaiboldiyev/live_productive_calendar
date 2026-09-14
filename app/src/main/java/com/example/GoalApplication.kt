package com.example

import android.app.Application
import android.os.Process
import android.os.SystemClock
import android.util.Log

class GoalApplication : Application() {

    companion object {
        var processStartTime: Long = 0L
            private set

        fun elapsedSinceProcessStart(): Long {
            val start = processStartTime
            return if (start > 0L) {
                SystemClock.elapsedRealtime() - start
            } else {
                0L
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        processStartTime = SystemClock.elapsedRealtime()
        Log.d("GOAL_TIMELINE", "[+0ms] PROCESS START: pid=${Process.myPid()}, time=$processStartTime")
    }
}
