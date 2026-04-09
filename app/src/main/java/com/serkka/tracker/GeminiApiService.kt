package com.serkka.tracker

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class AiWorkoutResponse(
    val workouts: List<AiWorkoutEntry>,
    val summary: String
)

data class AiWorkoutEntry(
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weight: Float,
    val weightUnit: String = "kg",
    val notes: String = ""
)

class GeminiApiService(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun generateWorkout(
        prompt: String,
        recentWorkouts: List<Workout>
    ): AiWorkoutResponse = withContext(Dispatchers.IO) {
        val systemPrompt = buildSystemPrompt(recentWorkouts)

        val requestBody = gson.toJson(
            mapOf(
                "systemInstruction" to mapOf(
                    "parts" to listOf(mapOf("text" to systemPrompt))
                ),
                "contents" to listOf(
                    mapOf(
                        "parts" to listOf(mapOf("text" to prompt))
                    )
                ),
                "generationConfig" to mapOf(
                    "responseMimeType" to "application/json",
                    "maxOutputTokens" to 4096
                )
            )
        )

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .addHeader("content-type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            val errorMsg = try {
                JsonParser.parseString(body).asJsonObject
                    .getAsJsonObject("error")?.get("message")?.asString
            } catch (_: Exception) { null }
            throw Exception(errorMsg ?: "API error ${response.code}")
        }

        parseResponse(body)
    }

    private fun buildSystemPrompt(recentWorkouts: List<Workout>): String {
        val historySection = if (recentWorkouts.isNotEmpty()) {
            val summary = recentWorkouts
                .sortedByDescending { it.date }
                .take(50)
                .groupBy { it.exerciseName }
                .map { (name, workouts) ->
                    val latest = workouts.first()
                    "$name: ${latest.sets}x${latest.reps}@${formatWeight(latest.weight)}${latest.weightUnit}"
                }
                .joinToString(", ")
            "\nUSER'S RECENT EXERCISES:\n$summary\n"
        } else {
            "\nNo workout history available yet.\n"
        }

        return """
You are a fitness coach AI for a workout tracking app. Generate workout plans based on the user's request and their training history.
$historySection
RULES:
- Suggest realistic weights based on the user's history when available
- For new exercises without history, suggest conservative starting weights
- Include warm-up sets where appropriate (mark with "warm-up" in notes)
- Always use kg for weight unit
- For multi-week programs, group workouts by day/session in the summary

RESPONSE FORMAT:
Return ONLY valid JSON with this exact schema:
{
  "workouts": [
    {
      "exerciseName": "Exercise Name",
      "sets": 3,
      "reps": 10,
      "weight": 60.0,
      "weightUnit": "kg",
      "notes": "optional notes"
    }
  ],
  "summary": "Brief description of the workout plan"
}
        """.trimIndent()
    }

    private fun parseResponse(body: String): AiWorkoutResponse {
        val json = JsonParser.parseString(body).asJsonObject
        val candidates = json.getAsJsonArray("candidates")
        val content = candidates[0].asJsonObject
            .getAsJsonObject("content")
            .getAsJsonArray("parts")[0].asJsonObject
            .get("text").asString

        // Strip markdown code fences if present
        val cleanJson = content
            .replace(Regex("^```json\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^```\\s*", RegexOption.MULTILINE), "")
            .trim()

        return gson.fromJson(cleanJson, AiWorkoutResponse::class.java)
    }
}
