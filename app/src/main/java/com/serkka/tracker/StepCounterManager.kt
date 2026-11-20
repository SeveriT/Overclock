package com.serkka.tracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

class StepCounterManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val prefs = context.getSharedPreferences("step_counter", Context.MODE_PRIVATE)

    val isAvailable: Boolean = stepSensor != null

    private val _todaySteps = MutableStateFlow(loadTodaySteps())
    val todaySteps: StateFlow<Long> = _todaySteps

    private val _weeklySteps = MutableStateFlow(loadWeeklySteps())
    val weeklySteps: StateFlow<List<Pair<LocalDate, Long>>> = _weeklySteps

    private val _stepGoal = MutableStateFlow(prefs.getLong("step_goal", 10_000L))
    val stepGoal: StateFlow<Long> = _stepGoal

    private val _isCardVisible = MutableStateFlow(prefs.getBoolean("card_visible", true))
    val isCardVisible: StateFlow<Boolean> = _isCardVisible

    private val channelId = "steps_channel"
    private val notifId = 43

    fun start() {
        if (stepSensor == null) return
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** Re-register the listener to force the sensor to deliver a fresh cumulative count.
     *  Call on app resume so any steps taken while the app was killed/backgrounded are
     *  picked up — TYPE_STEP_COUNTER is cumulative since boot, so one event catches up. */
    fun refresh() {
        if (stepSensor == null) return
        sensorManager.unregisterListener(this)
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /** Reloads step values from shared preferences. Used after demo-data seeding
     *  so the UI reflects the written values without needing a sensor event. */
    fun reloadFromPrefs() {
        _todaySteps.value = loadTodaySteps()
        _weeklySteps.value = loadWeeklySteps()
    }

    fun setStepGoal(goal: Long) {
        _stepGoal.value = goal
        prefs.edit().putLong("step_goal", goal).apply()
        // Reset notification flag so it can fire again for new goal
        prefs.edit().putBoolean("goal_notified_${LocalDate.now()}", false).apply()
    }

    /**
     * Replaces [date]'s total with [total] (e.g. the number from another step app, to fix a wrong
     * day). For today the sensor anchor is left alone, so new steps keep adding on top.
     */
    fun setStepsManually(total: Long, date: LocalDate = LocalDate.now()) {
        if (total < 0) return
        val today = LocalDate.now()
        if (date == today) {
            val todayStr = today.toString()
            val edit = prefs.edit().putLong("today_steps", total)
            if (prefs.getString("baseline_date", null) != todayStr) edit.putString("baseline_date", todayStr)
            val lastSensor = prefs.getLong("last_sensor", Long.MIN_VALUE)
            if (lastSensor != Long.MIN_VALUE) edit.putLong("baseline", lastSensor - total)
            edit.apply()
            _todaySteps.value = total
        } else {
            prefs.edit().putLong("steps_$date", total).apply()
        }
        _weeklySteps.value = loadWeeklySteps()
    }

    fun setCardVisible(visible: Boolean) {
        _isCardVisible.value = visible
        prefs.edit().putBoolean("card_visible", visible).apply()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val today = LocalDate.now().toString()
        // Day rollover, gaps and reboots are handled in one place (StepCounterStore.kt)
        val steps = StepCounterStore.record(context, event.values[0].toLong())
        _todaySteps.value = steps
        _weeklySteps.value = loadWeeklySteps()

        // Check if goal reached and notify
        val goal = _stepGoal.value
        val notifiedKey = "goal_notified_$today"
        if (steps >= goal && !prefs.getBoolean(notifiedKey, false)) {
            prefs.edit().putBoolean(notifiedKey, true).apply()
            showGoalReachedNotification(steps, goal)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun loadTodaySteps(): Long {
        val today = LocalDate.now().toString()
        val baselineDate = prefs.getString("baseline_date", null)
        return if (baselineDate == today) prefs.getLong("today_steps", 0L) else 0L
    }

    private fun loadWeeklySteps(): List<Pair<LocalDate, Long>> {
        val today = LocalDate.now()
        val todayStr = today.toString()
        val baselineDate = prefs.getString("baseline_date", null)
        val currentTodaySteps = if (baselineDate == todayStr) prefs.getLong("today_steps", 0L) else 0L

        return (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val steps = if (date == today) currentTodaySteps
                        else prefs.getLong("steps_$date", 0L)
            date to steps
        }
    }

    private fun showGoalReachedNotification(steps: Long, goal: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Step Goal",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when daily step goal is reached"
        }
        notificationManager.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            context, 0,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val formattedSteps = String.format(java.util.Locale.getDefault(), "%,d", steps)
        val formattedGoal = String.format(java.util.Locale.getDefault(), "%,d", goal)

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Step goal reached!")
            .setContentText("$formattedSteps / $formattedGoal steps today")
            .setSmallIcon(R.drawable.ic_stat_steps)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        notificationManager.notify(notifId, notification)
    }
}
