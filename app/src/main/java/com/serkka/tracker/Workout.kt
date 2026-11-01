package com.serkka.tracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weight: Float,
    val isPersonalBest: Boolean = false,
    val weightUnit: String = "kg",
    val notes: String = "",
    /** Rate of perceived exertion, 5–10 in 0.5 steps; null = not recorded. Added in DB v9. */
    val rpe: Float? = null
)
