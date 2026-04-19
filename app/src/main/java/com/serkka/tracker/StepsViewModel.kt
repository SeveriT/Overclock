package com.serkka.tracker

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

class StepsViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = StepCounterManager(application)

    val isAvailable: Boolean = manager.isAvailable

    val todaySteps: StateFlow<Long> = manager.todaySteps
    val weeklySteps: StateFlow<List<Pair<LocalDate, Long>>> = manager.weeklySteps
    val stepGoal: StateFlow<Long> = manager.stepGoal
    val isCardVisible: StateFlow<Boolean> = manager.isCardVisible

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission

    init {
        checkAndStart()
    }

    private fun checkAndStart() {
        if (!isAvailable) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                getApplication(), Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            _hasPermission.value = granted
            if (granted) manager.start()
        } else {
            _hasPermission.value = true
            manager.start()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) manager.start()
    }

    fun refresh() {
        if (_hasPermission.value) manager.refresh()
    }

    fun reloadFromPrefs() {
        manager.reloadFromPrefs()
    }

    fun setStepGoal(goal: Long) {
        manager.setStepGoal(goal)
    }

    fun setCardVisible(visible: Boolean) {
        manager.setCardVisible(visible)
    }

    override fun onCleared() {
        super.onCleared()
        manager.stop()
    }
}
