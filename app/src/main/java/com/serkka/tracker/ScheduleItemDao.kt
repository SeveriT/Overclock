package com.serkka.tracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleItemDao {
    @Query("SELECT * FROM schedule_items ORDER BY position ASC, id ASC")
    fun getAll(): Flow<List<ScheduleItem>>

    @Insert
    suspend fun insert(item: ScheduleItem)

    @Update
    suspend fun update(item: ScheduleItem)

    @Delete
    suspend fun delete(item: ScheduleItem)

    /** Untick every item (manual "reset" action). */
    @Query("UPDATE schedule_items SET isDone = 0")
    suspend fun resetAll()

    /** Highest current position, or -1 when the list is empty (used to append). */
    @Query("SELECT COALESCE(MAX(position), -1) FROM schedule_items")
    suspend fun maxPosition(): Int
}
