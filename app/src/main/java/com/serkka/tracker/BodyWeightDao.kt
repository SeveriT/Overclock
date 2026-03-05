package com.serkka.tracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weights ORDER BY date DESC")
    fun getAllBodyWeights(): Flow<List<BodyWeight>>

    @Insert
    suspend fun insert(bodyWeight: BodyWeight)

    @Update
    suspend fun update(bodyWeight: BodyWeight)

    @Delete
    suspend fun delete(bodyWeight: BodyWeight)
}
