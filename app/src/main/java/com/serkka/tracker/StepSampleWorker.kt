package com.serkka.tracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Takes a step counter reading every ~15 minutes (WorkManager's minimum) and records it, so the
 * daily totals stay right even when the phone has killed StepCounterForegroundService or hasn't
 * delivered readings for hours with the screen off. WorkManager jobs run without the app being
 * opened and are rescheduled after a reboot.
 */
class StepSampleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!StepCounterForegroundService.hasActivityRecognitionPermission(context)) return Result.success()
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return Result.success()

        // Registering makes the sensor report its current total right away
        val reading = withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine<Long> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sensorManager.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values[0].toLong())
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
            }
        }
        if (reading != null) StepCounterStore.record(context, reading)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "StepSampleWork"

        /** Safe to call often: keeps the existing schedule if it's already there. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StepSampleWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
