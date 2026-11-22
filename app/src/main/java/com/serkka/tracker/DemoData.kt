package com.serkka.tracker

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Seeds the database with ~4 weeks of realistic mock data so new users can see
 * what the app looks like populated. Dates are relative to today.
 */
object DemoData {

    private const val PREFS = "app_prefs"
    private const val KEY_SEEDED = "demo_seeded"

    fun isSeeded(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SEEDED, false)

    suspend fun seed(context: Context, repository: WorkoutRepository) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
        seedInternal(repository)
        seedSteps(context)
        // Height, so the Weight page shows BMI
        PreferencesManager.getInstance(context).tracker.edit().putFloat("height_cm", 180f).apply()
    }

    private fun seedSteps(context: Context) {
        val stepPrefs = context.getSharedPreferences("step_counter", Context.MODE_PRIVATE)
        val today = LocalDate.now()
        // Realistic-looking 7-day history ending today
        val dailySteps = listOf(7820L, 9110L, 10245L, 6480L, 11340L, 8275L, 8L)
        val editor = stepPrefs.edit()
        // Previous 6 days
        (6 downTo 1).forEachIndexed { idx, daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            editor.putLong("steps_$date", dailySteps[idx])
        }
        // Today's total; the next real sensor reading only anchors on it (StepCounterStore)
        val todaySteps = dailySteps.last()
        editor.putString("baseline_date", today.toString())
        editor.putLong("today_steps", todaySteps)
        editor.apply()
        StepCounterStore.resetAnchor(context)
    }

    /**
     * A believable heart-rate trace for [session]: a warm-up climb, then work bouts and rests (lifting)
     * or a steadier effort (cardio), sampled every HeartRateMonitor.SAMPLE_SECONDS like a real strap.
     */
    private fun heartRateFor(session: WorkoutSession, seed: Int): WorkoutSession {
        val random = kotlin.random.Random(seed)
        val count = session.durationSeconds / HeartRateMonitor.SAMPLE_SECONDS
        val cardio = session.type == "Cardio"
        val samples = List(count) { i ->
            val minute = i * HeartRateMonitor.SAMPLE_SECONDS / 60.0
            val warmUp = (minute / 6.0).coerceAtMost(1.0)
            val base = if (cardio) 128 + 22 * warmUp else 98 + 22 * warmUp
            // Lifting: ~45 s of work then ~2 min of rest, over and over
            val bout = if (cardio) 8 * kotlin.math.sin(minute / 3.0) else {
                val phase = (i * HeartRateMonitor.SAMPLE_SECONDS) % 165
                if (phase < 45) 28.0 * phase / 45 else 28.0 * (1 - (phase - 45) / 120.0).coerceAtLeast(0.0)
            }
            (base + bout + random.nextInt(-3, 4)).toInt().coerceIn(60, 190)
        }
        return session.copy(
            avgHr = samples.average().toInt(),
            maxHr = samples.max(),
            hrSamples = samples.joinToString(",")
        )
    }

    private suspend fun seedInternal(repository: WorkoutRepository) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        fun dayMs(daysAgo: Int, hour: Int = 18, minute: Int = 0): Long =
            today.minusDays(daysAgo.toLong())
                .atTime(LocalTime.of(hour, minute))
                .atZone(zone).toInstant().toEpochMilli()

        // ── Workouts (exercise logs) ───────────────────────────────────────────
        val workouts = mutableListOf<Workout>()

        // Bench Press progression: 70 → 80, one PB
        workouts += Workout(date = dayMs(27), exerciseName = "Bench Press", sets = 4, reps = 8, weight = 70f)
        workouts += Workout(date = dayMs(24), exerciseName = "Bench Press", sets = 4, reps = 8, weight = 72.5f)
        workouts += Workout(date = dayMs(20), exerciseName = "Bench Press", sets = 4, reps = 6, weight = 75f)
        workouts += Workout(date = dayMs(17), exerciseName = "Bench Press", sets = 4, reps = 6, weight = 77.5f)
        workouts += Workout(date = dayMs(13), exerciseName = "Bench Press", sets = 4, reps = 5, weight = 80f, isPersonalBest = true, notes = "Finally hit 80kg!")
        workouts += Workout(date = dayMs(9), exerciseName = "Bench Press", sets = 4, reps = 6, weight = 77.5f)
        workouts += Workout(date = dayMs(5), exerciseName = "Bench Press", sets = 5, reps = 5, weight = 77.5f)
        workouts += Workout(date = dayMs(2), exerciseName = "Bench Press", sets = 4, reps = 5, weight = 80f)

        // Squats
        workouts += Workout(date = dayMs(26), exerciseName = "Squat", sets = 4, reps = 8, weight = 90f)
        workouts += Workout(date = dayMs(22), exerciseName = "Squat", sets = 4, reps = 8, weight = 95f)
        workouts += Workout(date = dayMs(18), exerciseName = "Squat", sets = 5, reps = 5, weight = 100f, isPersonalBest = true, notes = "Triple-digit club")
        workouts += Workout(date = dayMs(13), exerciseName = "Squat", sets = 5, reps = 5, weight = 100f)
        workouts += Workout(date = dayMs(9), exerciseName = "Squat", sets = 4, reps = 6, weight = 102.5f)
        workouts += Workout(date = dayMs(4), exerciseName = "Squat", sets = 4, reps = 5, weight = 105f, isPersonalBest = true)

        // Deadlifts
        workouts += Workout(date = dayMs(25), exerciseName = "Deadlift", sets = 3, reps = 5, weight = 110f)
        workouts += Workout(date = dayMs(18), exerciseName = "Deadlift", sets = 3, reps = 5, weight = 115f)
        workouts += Workout(date = dayMs(11), exerciseName = "Deadlift", sets = 3, reps = 3, weight = 125f, isPersonalBest = true)
        workouts += Workout(date = dayMs(4), exerciseName = "Deadlift", sets = 3, reps = 5, weight = 120f)

        // Pull-ups (bodyweight)
        workouts += Workout(date = dayMs(24), exerciseName = "Pull-up", sets = 4, reps = 6, weight = 0f)
        workouts += Workout(date = dayMs(17), exerciseName = "Pull-up", sets = 4, reps = 8, weight = 0f)
        workouts += Workout(date = dayMs(10), exerciseName = "Pull-up", sets = 5, reps = 8, weight = 0f)
        workouts += Workout(date = dayMs(3), exerciseName = "Pull-up", sets = 4, reps = 10, weight = 0f, isPersonalBest = true)

        // Cardio (uses weight field as minutes)
        workouts += Workout(date = dayMs(26), exerciseName = "Cardio", sets = 0, reps = 0, weight = 25f, notes = "Treadmill, easy pace")
        workouts += Workout(date = dayMs(19), exerciseName = "Cardio", sets = 0, reps = 0, weight = 30f)
        workouts += Workout(date = dayMs(12), exerciseName = "Cardio", sets = 0, reps = 0, weight = 35f)
        workouts += Workout(date = dayMs(6), exerciseName = "Cardio", sets = 0, reps = 0, weight = 40f, notes = "Intervals")

        // Holds: sets × seconds (seconds go in reps)
        workouts += Workout(date = dayMs(20), exerciseName = "Plank", sets = 3, reps = 60, weight = 0f)
        workouts += Workout(date = dayMs(13), exerciseName = "Plank", sets = 3, reps = 75, weight = 0f)
        workouts += Workout(date = dayMs(6), exerciseName = "Plank", sets = 3, reps = 90, weight = 0f, isPersonalBest = true)
        workouts += Workout(date = dayMs(17), exerciseName = "Dead Hang", sets = 3, reps = 40, weight = 0f)
        workouts += Workout(date = dayMs(3), exerciseName = "Dead Hang", sets = 3, reps = 50, weight = 0f)

        // The month before: lighter and a little less often, so Home's Last 30 Days has something to
        // compare against (and charts show a longer trend)
        val earlier = listOf(
            Triple(55, "Bench Press", 62.5f), Triple(54, "Squat", 80f), Triple(52, "Deadlift", 100f),
            Triple(51, "Bench Press", 65f), Triple(48, "Squat", 82.5f), Triple(47, "Pull-up", 0f),
            Triple(44, "Bench Press", 65f), Triple(43, "Deadlift", 102.5f), Triple(40, "Squat", 85f),
            Triple(37, "Bench Press", 67.5f), Triple(36, "Pull-up", 0f), Triple(33, "Squat", 87.5f),
            Triple(32, "Deadlift", 105f), Triple(30, "Bench Press", 67.5f),
        )
        earlier.forEach { (daysAgo, name, weight) ->
            workouts += Workout(
                date = dayMs(daysAgo), exerciseName = name, sets = 3,
                reps = if (name == "Pull-up") 5 else 8, weight = weight
            )
        }

        // RPE on the lifts (not cardio or holds): harder sets near the PBs, easier the month before
        val rpeSteps = listOf(7f, 7.5f, 8f, 8.5f, 8f, 9f)
        val rated = workouts.mapIndexed { i, w ->
            val lift = w.sets > 0 && w.exerciseName != "Plank" && w.exerciseName != "Dead Hang"
            when {
                !lift -> w
                w.isPersonalBest -> w.copy(rpe = 9.5f)
                w.date < dayMs(29) -> w.copy(rpe = rpeSteps[i % rpeSteps.size] - 0.5f)
                else -> w.copy(rpe = rpeSteps[i % rpeSteps.size])
            }
        }
        rated.forEach { repository.addWorkout(it) }

        // ── Body weight (gentle downward trend) ────────────────────────────────
        val bodyWeights = listOf(
            BodyWeight(date = dayMs(55, 8), weight = 83.6f),
            BodyWeight(date = dayMs(48, 8), weight = 83.3f),
            BodyWeight(date = dayMs(41, 8), weight = 83.1f),
            BodyWeight(date = dayMs(34, 8), weight = 82.7f),
            BodyWeight(date = dayMs(28, 8), weight = 82.4f),
            BodyWeight(date = dayMs(24, 8), weight = 82.1f),
            BodyWeight(date = dayMs(21, 8), weight = 81.9f),
            BodyWeight(date = dayMs(17, 8), weight = 81.6f),
            BodyWeight(date = dayMs(14, 8), weight = 81.3f),
            BodyWeight(date = dayMs(10, 8), weight = 81.0f),
            BodyWeight(date = dayMs(7, 8), weight = 80.7f),
            BodyWeight(date = dayMs(3, 8), weight = 80.5f),
            BodyWeight(date = dayMs(1, 8), weight = 80.3f, notes = "Feeling lighter"),
        )
        bodyWeights.forEach { repository.addBodyWeight(it) }

        // ── Workout sessions (timer-logged) ────────────────────────────────────
        val sessions = listOf(
            WorkoutSession(name = "Push Day", type = "WeightTraining", date = dayMs(27, 18), durationSeconds = 62 * 60),
            WorkoutSession(name = "Treadmill Intervals", type = "Cardio", date = dayMs(26, 7), durationSeconds = 28 * 60),
            WorkoutSession(name = "Leg Day", type = "WeightTraining", date = dayMs(22, 17, 30), durationSeconds = 71 * 60),
            WorkoutSession(name = "Pull Day", type = "WeightTraining", date = dayMs(20, 18), durationSeconds = 58 * 60),
            WorkoutSession(name = "Mobility & Core", type = "Workout", date = dayMs(15, 10), durationSeconds = 40 * 60),
            WorkoutSession(name = "Push Day", type = "WeightTraining", date = dayMs(13, 18), durationSeconds = 65 * 60),
            WorkoutSession(name = "Rowing", type = "Cardio", date = dayMs(12, 7), durationSeconds = 34 * 60),
            WorkoutSession(name = "Leg Day", type = "WeightTraining", date = dayMs(9, 18), durationSeconds = 74 * 60),
            WorkoutSession(name = "Pull Day", type = "WeightTraining", date = dayMs(6, 18), durationSeconds = 60 * 60),
            WorkoutSession(name = "Stairmaster", type = "Cardio", date = dayMs(5, 8), durationSeconds = 45 * 60),
            WorkoutSession(name = "Push Day", type = "WeightTraining", date = dayMs(2, 18), durationSeconds = 63 * 60),
        )
        // Heart rate from a strap on most of the recent sessions (graph, average, max, TCX export)
        val withHr = sessions.mapIndexed { i, session ->
            if (session.date < dayMs(16) || session.type == "Workout") session
            else heartRateFor(session, seed = i)
        }
        withHr.forEach { repository.addSession(it) }

        // ── Schedule checklist (Tools) ─────────────────────────────────────────
        listOf("Push: bench, OHP, dips", "Pull: deadlift, rows, pull-ups", "Legs: squat, lunges, calves", "Cardio 30 min")
            .forEach { repository.addScheduleItem(it) }

        // ── Notes ──────────────────────────────────────────────────────────────
        val notes = listOf(
            Note(title = "Program", content = "Upper/lower split, 4 days a week. Deload every 5th week.", date = dayMs(27, 20)),
            Note(title = "Bench cues", content = "- Leg drive\n- Elbows ~60°\n- Touch just below nipples", date = dayMs(14, 20)),
            Note(title = "Recovery", content = "Sleep 8h, protein 1.8g/kg bodyweight.", date = dayMs(3, 20)),
        )
        notes.forEach { repository.addNote(it) }
    }
}
