package com.serkka.tracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY date DESC, id DESC")
    fun getAllWorkouts(): Flow<List<Workout>>

    @Insert
    suspend fun insertWorkout(workout: Workout)

    @Update
    suspend fun updateWorkout(workout: Workout)

    @Delete
    suspend fun deleteWorkout(workout: Workout)

    // Bulk exercise rename (Tools → Exercise names). Works on ids so a rename that merges
    // into an existing name can still be undone exactly.
    @Query("SELECT id FROM workouts WHERE exerciseName = :name")
    suspend fun getWorkoutIdsForExercise(name: String): List<Int>

    @Query("UPDATE workouts SET exerciseName = :newName WHERE id IN (:ids)")
    suspend fun setExerciseName(ids: List<Int>, newName: String)

    // Notes
    @Query("SELECT * FROM notes ORDER BY date DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Insert
    suspend fun insertNote(note: Note)

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)
}
