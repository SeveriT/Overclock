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
        // Today: simulate sensor baseline so todaySteps resolves correctly
        val todaySteps = dailySteps.last()
        val fakeBaseline = 100_000L
        editor.putString("baseline_date", today.toString())
        editor.putLong("baseline", fakeBaseline)
        editor.putLong("today_steps", todaySteps)
        editor.apply()
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

        workouts.forEach { repository.addWorkout(it) }

        // ── Body weight (gentle downward trend) ────────────────────────────────
        val bodyWeights = listOf(
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
            WorkoutSession(name = "Morning Run", type = "Run", date = dayMs(26, 7), durationSeconds = 28 * 60),
            WorkoutSession(name = "Leg Day", type = "WeightTraining", date = dayMs(22, 17, 30), durationSeconds = 71 * 60),
            WorkoutSession(name = "Pull Day", type = "WeightTraining", date = dayMs(20, 18), durationSeconds = 58 * 60),
            WorkoutSession(name = "Weekend Hike", type = "Hike", date = dayMs(15, 10), durationSeconds = 95 * 60),
            WorkoutSession(name = "Push Day", type = "WeightTraining", date = dayMs(13, 18), durationSeconds = 65 * 60),
            WorkoutSession(name = "Tempo Run", type = "Run", date = dayMs(12, 7), durationSeconds = 34 * 60),
            WorkoutSession(name = "Leg Day", type = "WeightTraining", date = dayMs(9, 18), durationSeconds = 74 * 60),
            WorkoutSession(name = "Pull Day", type = "WeightTraining", date = dayMs(6, 18), durationSeconds = 60 * 60),
            WorkoutSession(name = "Long Run", type = "Run", date = dayMs(5, 8), durationSeconds = 52 * 60),
            WorkoutSession(name = "Push Day", type = "WeightTraining", date = dayMs(2, 18), durationSeconds = 63 * 60),
        )
        sessions.forEach { repository.addSession(it) }

        // ── Notes ──────────────────────────────────────────────────────────────
        val notes = listOf(
            Note(title = "Program", content = "Upper/lower split, 4 days a week. Deload every 5th week.", date = dayMs(27, 20)),
            Note(title = "Bench cues", content = "- Leg drive\n- Elbows ~60°\n- Touch just below nipples", date = dayMs(14, 20)),
            Note(title = "Recovery", content = "Sleep 8h, protein 1.8g/kg bodyweight.", date = dayMs(3, 20)),
        )
        notes.forEach { repository.addNote(it) }
    }
}
