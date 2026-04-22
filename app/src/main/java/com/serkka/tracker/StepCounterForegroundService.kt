package com.serkka.tracker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate

class StepCounterForegroundService : Service(), SensorEventListener {

    private val channelId = "steps_tracking_channel"
    private val notifId = 44

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    private val prefs by lazy {
        getSharedPreferences("step_counter", MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (stepSensor == null || !hasActivityRecognitionPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        startForeground(notifId, buildNotification())
        sensorManager.unregisterListener(this)
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val sensorSteps = event.values[0].toLong()
        val today = LocalDate.now().toString()
        val baselineDate = prefs.getString("baseline_date", null)
        var baseline = prefs.getLong("baseline", -1L)

        if (baselineDate != today) {
            if (baselineDate != null && baseline >= 0) {
                val prevDaySteps = prefs.getLong("today_steps", 0L)
                prefs.edit().putLong("steps_$baselineDate", prevDaySteps).apply()
            }
            baseline = sensorSteps
            prefs.edit()
                .putString("baseline_date", today)
                .putLong("baseline", baseline)
                .putLong("today_steps", 0L)
                .apply()
        }

        if (sensorSteps < baseline) {
            baseline = sensorSteps
            prefs.edit().putLong("baseline", baseline).apply()
        }

        val steps = sensorSteps - baseline
        prefs.edit().putLong("today_steps", steps).apply()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Step tracking active")
            .setContentText("Counting your steps throughout the day")
            .setSmallIcon(R.drawable.ic_stat_steps)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openIntent)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            channelId,
            "Step Tracking",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps step counting running in the background"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    companion object {
        fun hasActivityRecognitionPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        }

        fun start(context: Context) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            if (sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) == null) return
            if (!hasActivityRecognitionPermission(context)) return
            val intent = Intent(context, StepCounterForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepCounterForegroundService::class.java))
        }
    }
}
