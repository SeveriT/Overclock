package com.serkka.tracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String,       // e.g. "WeightTraining", "Workout"
    val date: Long,         // epoch ms (session start time)
    val durationSeconds: Int,
    val notes: String = "",
    // Heart rate from a Bluetooth strap (null = not recorded). DB v10, MIGRATION_9_10.
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    /** BPM every HeartRateMonitor.SAMPLE_SECONDS of active time, comma-separated; 0 = no reading. */
    val hrSamples: String? = null
) {
    /** [hrSamples] as a list (empty when not recorded). */
    fun hrSampleList(): List<Int> =
        hrSamples?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
}
