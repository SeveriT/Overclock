package com.serkka.tracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed class AiUiState {
    data object Idle : AiUiState()
    data object Loading : AiUiState()
    data object Success : AiUiState()
    data class Error(val message: String) : AiUiState()
}

class AiViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val DAILY_LIMIT = 20
        private const val DAILY_LIMIT_PREMIUM = 40
        private const val KEY_REQUEST_COUNT = "ai_daily_request_count"
        private const val KEY_REQUEST_DATE = "ai_request_date"
    }

    private val prefs = PreferencesManager.getInstance(application).ai
    private val billing = BillingManager.getInstance(application)
    private val dailyLimit get() = if (billing.isWhitelisted.value) DAILY_LIMIT_PREMIUM else DAILY_LIMIT

    private val _uiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val uiState: StateFlow<AiUiState> = _uiState

    private val _generatedWorkouts = MutableStateFlow<List<AiWorkoutEntry>>(emptyList())
    val generatedWorkouts: StateFlow<List<AiWorkoutEntry>> = _generatedWorkouts

    private val _summary = MutableStateFlow("")
    val summary: StateFlow<String> = _summary

    private val _remainingRequests = MutableStateFlow(getRemainingRequests())
    val remainingRequests: StateFlow<Int> = _remainingRequests

    val dailyRequestLimit: Int get() = dailyLimit

    init {
        viewModelScope.launch {
            billing.isWhitelisted.collect {
                _remainingRequests.value = getRemainingRequests()
            }
        }
    }

    private fun getTodayString(): String = LocalDate.now().toString()

    private fun getRemainingRequests(): Int {
        val savedDate = prefs.getString(KEY_REQUEST_DATE, null)
        return if (savedDate == getTodayString()) {
            (dailyLimit - prefs.getInt(KEY_REQUEST_COUNT, 0)).coerceAtLeast(0)
        } else {
            dailyLimit
        }
    }

    private fun recordRequest() {
        val today = getTodayString()
        val savedDate = prefs.getString(KEY_REQUEST_DATE, null)
        val count = if (savedDate == today) prefs.getInt(KEY_REQUEST_COUNT, 0) + 1 else 1
        prefs.edit()
            .putString(KEY_REQUEST_DATE, today)
            .putInt(KEY_REQUEST_COUNT, count)
            .apply()
        _remainingRequests.value = (dailyLimit - count).coerceAtLeast(0)
    }

    fun generateWorkout(prompt: String, recentWorkouts: List<Workout>) {
        viewModelScope.launch {
            if (getRemainingRequests() <= 0) {
                _uiState.value = AiUiState.Error("Daily limit reached ($dailyLimit requests/day). Try again tomorrow.")
                return@launch
            }
            _uiState.value = AiUiState.Loading
            try {
                val service = GeminiApiService(billing.geminiApiKey)
                val response = service.generateWorkout(prompt, recentWorkouts)
                recordRequest()
                _generatedWorkouts.value = response.workouts
                _summary.value = response.summary
                _uiState.value = AiUiState.Success
            } catch (e: Exception) {
                _uiState.value = AiUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun clearState() {
        _uiState.value = AiUiState.Idle
        _generatedWorkouts.value = emptyList()
        _summary.value = ""
    }
}
