package com.serkka.tracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "body_weights")
data class BodyWeight(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val weight: Float,
    val notes: String = ""
)
