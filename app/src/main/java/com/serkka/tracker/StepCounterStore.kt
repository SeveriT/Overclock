package com.serkka.tracker

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ── Step recording (shared by the service, the in-app manager and StepSampleWorker) ─────────
// TYPE_STEP_COUNTER is a running total since boot, and readings arrive irregularly: the step
// service can be killed for days, and with the screen off readings may not arrive for hours. So
// every reading adds the steps taken since the PREVIOUS reading ("last_sensor"), and when that
// gap crosses midnight the steps are split over the days in proportion to the time each day
// covers. (The old code reset the count at the first reading of a new day and threw away
// everything since the last reading, which is how a day or two without opening the app lost steps.)
//
// Prefs ("step_counter"): today_steps + baseline_date = today's running total; steps_<date> = past
// days; last_sensor / last_sample_ms = the previous reading. "baseline" is kept up to date
// (= sensor - today_steps) for older code paths but no longer drives the count.

internal object StepCounterStore {
    private const val PREFS = "step_counter"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Records a cumulative step counter reading taken at [nowMs] and returns today's total.
     * Synchronized: the service and the manager (main thread) and the worker (background) all
     * call this, and each reading must be counted exactly once.
     */
    @Synchronized
    fun record(context: Context, sensorSteps: Long, nowMs: Long = System.currentTimeMillis()): Long {
        val prefs = prefs(context)
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

        val baselineDate = prefs.getString("baseline_date", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val runningTotal = prefs.getLong("today_steps", 0L)   // total for baselineDate
        val oldBaseline = prefs.getLong("baseline", -1L)

        // Previous reading. Installs from before this code only have "baseline": the last
        // reading was baseline + today_steps (true for negative, reboot-shifted baselines too).
        var lastSensor = prefs.getLong("last_sensor", Long.MIN_VALUE)
        var lastMs = prefs.getLong("last_sample_ms", 0L)
        if (lastSensor == Long.MIN_VALUE && baselineDate != null && oldBaseline != -1L) {
            lastSensor = oldBaseline + runningTotal
            // When is unknown: take the end of the day it was recorded on (or now, if that's today)
            lastMs = minOf(nowMs, baselineDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
        }

        // A reboot restarts the counter at 0. Only trust that when the phone really booted after
        // the previous reading: the service, the in-app counter and StepSampleWorker all listen,
        // and with the screen off the phone delivers held-back OLDER readings later, so a lower
        // value usually just means a late reading. Treating those as reboots added the whole
        // since-boot count again (20k steps on a quiet day), so late readings are now ignored.
        val bootMs = nowMs - SystemClock.elapsedRealtime()
        val rebooted = lastMs > 0L && bootMs > lastMs
        if (lastSensor != Long.MIN_VALUE && !rebooted && sensorSteps < lastSensor) {
            return if (baselineDate == today) runningTotal else 0L   // late reading: already counted
        }
        val delta = when {
            lastSensor == Long.MIN_VALUE -> 0L        // first reading ever (or after restore/demo): just anchor
            rebooted -> sensorSteps                    // rebooted: everything since boot is new
            else -> sensorSteps - lastSensor
        }

        // Split delta over the days between the previous reading and now
        val shares = HashMap<LocalDate, Long>()
        if (delta > 0) {
            val fromMs = if (lastMs in 1 until nowMs) lastMs else nowMs
            val fromDay = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate()
            if (fromDay >= today || fromMs == nowMs) {
                shares[today] = delta
            } else {
                val span = (nowMs - fromMs).toDouble()
                var given = 0L
                var day = fromDay
                while (day < today) {
                    val start = maxOf(fromMs, day.atStartOfDay(zone).toInstant().toEpochMilli())
                    val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val part = (delta * ((end - start) / span)).toLong()
                    if (part > 0) shares[day] = part
                    given += part
                    day = day.plusDays(1)
                }
                shares[today] = delta - given   // rounding leftovers go to today
            }
        }

        val edit = prefs.edit()
        val todayTotal: Long
        if (baselineDate == today) {
            todayTotal = runningTotal + (shares.remove(today) ?: 0L)
        } else {
            // The day being tracked has ended: store its total (plus its share of the gap)
            if (baselineDate != null) {
                edit.putLong("steps_$baselineDate", runningTotal + (shares.remove(baselineDate) ?: 0L))
            }
            todayTotal = shares.remove(today) ?: 0L
        }
        // Days in between (the app wasn't seen at all): add their share to whatever they hold
        shares.forEach { (day, part) ->
            if (day < today) edit.putLong("steps_$day", prefs.getLong("steps_$day", 0L) + part)
        }
        edit.putString("baseline_date", today.toString())
            .putLong("today_steps", todayTotal)
            .putLong("baseline", sensorSteps - todayTotal)
            .putLong("last_sensor", sensorSteps)
            .putLong("last_sample_ms", nowMs)
            .apply()
        return todayTotal
    }

    /** Forget the previous reading, so the next one only anchors (after a restore or demo data). */
    fun resetAnchor(context: Context) {
        prefs(context).edit().remove("last_sensor").remove("last_sample_ms").remove("baseline").apply()
    }
}
