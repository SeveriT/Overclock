package com.serkka.tracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class TimerForegroundService : Service() {

    private val handler   = Handler(Looper.getMainLooper())
    private val channelId = "timer_channel_v2"
    private val notifId   = 42

    private var elapsedSeconds = 0L
    private var isRunning      = false

    // Wall-clock anchors for accurate timing
    private var wallClockBase = 0L
    private var elapsedAtBase = 0L

    // Current lap, mirrored from WorkoutTimerViewModel (ACTION_LAP) with the same anchors, so the
    // notification shows the same countdown / "+m:ss over" as the ring.
    private var lapSeconds = 0L
    private var lapAtBase = 0L
    private var lapWallBase = 0L          // 0 = lap not ticking (paused)
    private var lapDuration = 60L

    private fun currentLap(now: Long = System.currentTimeMillis()) =
        if (isRunning && lapWallBase > 0L) lapAtBase + (now - lapWallBase) / 1000 else lapAtBase

    private val timerPrefs by lazy {
        getSharedPreferences("timer_state", MODE_PRIVATE)
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (isRunning) {
                elapsedSeconds = elapsedAtBase + (System.currentTimeMillis() - wallClockBase) / 1000
                lapSeconds = currentLap()
                updateNotification()
                saveTimerState()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                elapsedSeconds = intent.getLongExtra(EXTRA_ELAPSED, 0L)
                elapsedAtBase = elapsedSeconds
                wallClockBase = System.currentTimeMillis()
                isRunning = true
                lapSeconds = 0L; lapAtBase = 0L; lapWallBase = wallClockBase   // ACTION_LAP follows with exact anchors
                createChannel()
                startForeground(notifId, buildNotification())
                handler.removeCallbacks(ticker)
                handler.postDelayed(ticker, 1000)
                saveTimerState()
            }
            ACTION_PAUSE -> {
                // Snapshot accurate elapsed before pausing
                if (isRunning && wallClockBase > 0L) {
                    elapsedSeconds = elapsedAtBase + (System.currentTimeMillis() - wallClockBase) / 1000
                    lapSeconds = currentLap()
                    lapAtBase = lapSeconds
                    lapWallBase = 0L
                }
                isRunning = false
                handler.removeCallbacks(ticker)
                updateNotification()
                saveTimerState()
            }
            ACTION_RESUME -> {
                elapsedSeconds = intent.getLongExtra(EXTRA_ELAPSED, elapsedSeconds)
                elapsedAtBase = elapsedSeconds
                wallClockBase = System.currentTimeMillis()
                isRunning = true
                handler.removeCallbacks(ticker)
                handler.postDelayed(ticker, 1000)
                updateNotification()
                saveTimerState()
            }
            ACTION_LAP -> {
                // A fresh instance (the old one was killed, or the workout already ended): pick the
                // saved workout back up first, or stop if there isn't one, so a late lap update
                // can't bring back a timer that was already stopped.
                if (wallClockBase == 0L && !recoverFromPrefs()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                lapAtBase = intent.getLongExtra(EXTRA_LAP_AT_BASE, 0L)
                lapWallBase = intent.getLongExtra(EXTRA_LAP_WALL_BASE, 0L)
                lapDuration = intent.getLongExtra(EXTRA_LAP_DURATION, lapDuration).coerceAtLeast(1L)
                lapSeconds = currentLap()
                updateNotification()
                saveTimerState()
            }
            ACTION_STOP -> {
                isRunning = false
                handler.removeCallbacks(ticker)
                clearTimerState()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            null -> {
                // Service restarted by the system — recover state
                if (!recoverFromPrefs()) stopSelf()
            }
        }
        return START_STICKY
    }

    /** Restores a workout saved by [saveTimerState] and shows its notification. False if none. */
    private fun recoverFromPrefs(): Boolean {
        val saved = timerPrefs
        if (!saved.getBoolean("active", false)) return false
        val savedElapsed = saved.getLong("elapsed_seconds", 0L)
        val savedTimestamp = saved.getLong("timestamp", 0L)
        val wasRunning = saved.getBoolean("running", false)
        val drift = if (wasRunning && savedTimestamp > 0L)
            (System.currentTimeMillis() - savedTimestamp) / 1000 else 0L
        elapsedSeconds = savedElapsed + drift
        elapsedAtBase = elapsedSeconds
        wallClockBase = System.currentTimeMillis()
        isRunning = wasRunning
        lapDuration = saved.getLong("lap_duration", lapDuration)
        lapSeconds = saved.getLong("lap_seconds", 0L) + drift
        lapAtBase = lapSeconds
        lapWallBase = if (wasRunning) wallClockBase else 0L
        createChannel()
        startForeground(notifId, buildNotification())
        if (isRunning) {
            handler.removeCallbacks(ticker)
            handler.postDelayed(ticker, 1000)
        }
        return true
    }

    private fun saveTimerState() {
        timerPrefs.edit()
            .putBoolean("active", true)
            .putBoolean("running", isRunning)
            .putLong("elapsed_seconds", elapsedSeconds)
            .putLong("lap_seconds", lapSeconds)          // WorkoutTimerViewModel recovers the lap from these
            .putLong("lap_duration", lapDuration)
            .putLong("timestamp", System.currentTimeMillis())
            .apply()
    }

    private fun clearTimerState() {
        timerPrefs.edit().clear().apply()
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val timeString = formatElapsed(elapsedSeconds)
        // This base time is used by the Chronometer to show live ticking
        val baseTime = System.currentTimeMillis() - (elapsedSeconds * 1000)
        // Same as the ring: count down the set time, then count up how long past it you've rested
        val lapText = if (lapSeconds >= lapDuration) "+${formatElapsed(lapSeconds - lapDuration)} over"
                      else "${formatElapsed(lapDuration - lapSeconds)} left"

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(lapText)
            .setContentText((if (isRunning) "" else "Paused · ") + "Total $timeString")
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setTicker("Workout")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(baseTime)
            .setShowWhen(isRunning)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notifId, buildNotification())
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        // Remove the legacy LOW-importance channel from older installs
        manager.deleteNotificationChannel("timer_channel")
        val channel = NotificationChannel(
            channelId,
            "Workout Timer",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Shows elapsed workout time"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(ticker)
    }

    companion object {
        const val ACTION_START  = "com.serkka.tracker.TIMER_START"
        const val ACTION_PAUSE  = "com.serkka.tracker.TIMER_PAUSE"
        const val ACTION_RESUME = "com.serkka.tracker.TIMER_RESUME"
        const val ACTION_STOP   = "com.serkka.tracker.TIMER_STOP"
        const val ACTION_LAP    = "com.serkka.tracker.TIMER_LAP"
        const val EXTRA_ELAPSED = "elapsed"
        const val EXTRA_LAP_AT_BASE    = "lap_at_base"
        const val EXTRA_LAP_WALL_BASE  = "lap_wall_base"
        const val EXTRA_LAP_DURATION   = "lap_duration"
    }
}
