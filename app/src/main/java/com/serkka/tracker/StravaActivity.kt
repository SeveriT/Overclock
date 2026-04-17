package com.serkka.tracker

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.google.gson.annotations.SerializedName

data class StravaActivity(
    val id: Long,
    val name: String,
    @SerializedName("start_date_local") val startDate: String, // ISO 8601 format
    val type: String,
    val distance: Float,
    val calories: Float = 0f,
    @SerializedName("moving_time") val movingTime: Int
)

fun Context.openStravaActivity(activityId: Long) {
    val appIntent = Intent(Intent.ACTION_VIEW, "strava://activities/$activityId".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(appIntent)
    } catch (_: ActivityNotFoundException) {
        val webIntent = Intent(Intent.ACTION_VIEW, "https://www.strava.com/activities/$activityId".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(webIntent)
    }
}
