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
    val notes: String = ""
)
