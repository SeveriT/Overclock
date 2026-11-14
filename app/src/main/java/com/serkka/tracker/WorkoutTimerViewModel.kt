package com.serkka.tracker

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class WorkoutTimerViewModel(private val app: Application) : AndroidViewModel(app) {

    // ── Core timer state ──────────────────────────────────────────────────────
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds

    private val _currentLapSeconds = MutableStateFlow(0L)
    val currentLapSeconds: StateFlow<Long> = _currentLapSeconds

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _hasStarted = MutableStateFlow(false)
    val hasStarted: StateFlow<Boolean> = _hasStarted

    private val lapPrefs by lazy {
        app.getSharedPreferences("timer_prefs", android.content.Context.MODE_PRIVATE)
    }
    private val _lapDurationSeconds = MutableStateFlow(
        lapPrefs.getLong("lap_duration_seconds", 60L).coerceAtLeast(30L)
    )
    val lapDurationSeconds: StateFlow<Long> = _lapDurationSeconds

    init { recoverTimerState() }

    fun adjustLapDuration(deltaSeconds: Long) {
        val next = (_lapDurationSeconds.value + deltaSeconds).coerceAtLeast(30L)
        _lapDurationSeconds.value = next
        lapPrefs.edit().putLong("lap_duration_seconds", next).apply()
        if (_hasStarted.value) syncLapToService()
    }

    private val _startDateTime = MutableStateFlow<LocalDateTime?>(null)
    val startDateTime: StateFlow<LocalDateTime?> = _startDateTime

    private val _selectedType = MutableStateFlow(workoutActivityTypes[0])
    val selectedType: StateFlow<WorkoutActivityType> = _selectedType

    // ── Upload dialog state (also outlives navigation) ────────────────────────
    private val _showUploadDialog = MutableStateFlow(false)
    val showUploadDialog: StateFlow<Boolean> = _showUploadDialog

    private val _activityName = MutableStateFlow("")
    val activityName: StateFlow<String> = _activityName

    private val _distanceKm = MutableStateFlow("")
    val distanceKm: StateFlow<String> = _distanceKm

    // ── Tick job ──────────────────────────────────────────────────────────────
    private var tickJob: Job? = null

    // Wall-clock anchors for accurate timing
    private var wallClockBase = 0L        // System.currentTimeMillis() when timer started/resumed
    private var elapsedAtBase = 0L        // _elapsedSeconds snapshot at that moment
    private var lapWallClockBase = 0L
    private var lapElapsedAtBase = 0L

    // Start (or resume) the timer
    fun start() {
        val firstStart = !_hasStarted.value
        if (firstStart) {
            _startDateTime.value = LocalDateTime.now()
            _hasStarted.value = true
            _currentLapSeconds.value = 0L
            lapElapsedAtBase = 0L
            startTimerService()
        } else {
            resumeTimerService()
        }
        if (_isRunning.value) return          // already ticking
        _isRunning.value = true
        // Heart rate graph follows active time only (paused time isn't sampled)
        if (firstStart) HeartRateMonitor.startRecording() else HeartRateMonitor.resumeRecording()

        // Anchor wall clock (shared by both total and lap so second boundaries align)
        wallClockBase = System.currentTimeMillis()
        elapsedAtBase = _elapsedSeconds.value
        if (firstStart || lapWallClockBase == 0L) {
            lapWallClockBase = wallClockBase
            lapElapsedAtBase = _currentLapSeconds.value
        }
        syncLapToService()

        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(250L) // poll frequently for smooth updates
                val now = System.currentTimeMillis()
                _elapsedSeconds.value = elapsedAtBase + (now - wallClockBase) / 1000
                // The ring-end buzz + beep come from TimerForegroundService (RingAlert.kt)
                _currentLapSeconds.value = lapElapsedAtBase + (now - lapWallClockBase) / 1000
            }
        }
    }

    fun pause() {
        // Snapshot wall-clock elapsed before stopping
        if (_isRunning.value) {
            val now = System.currentTimeMillis()
            _elapsedSeconds.value = elapsedAtBase + (now - wallClockBase) / 1000
            _currentLapSeconds.value = lapElapsedAtBase + (now - lapWallClockBase) / 1000
            lapElapsedAtBase = _currentLapSeconds.value
        }
        _isRunning.value = false
        tickJob?.cancel()
        tickJob = null
        HeartRateMonitor.pauseRecording()
        pauseTimerService()
        if (_hasStarted.value) syncLapToService()
    }

    fun toggleRunning() {
        if (_isRunning.value) pause() else start()
    }

    fun lap() {
        vibrateLap()
        if (_isRunning.value) {
            _currentLapSeconds.value = 0L
            // Align sub-second offset with total-time anchor so both tick in lockstep
            val now = System.currentTimeMillis()
            val offset = (now - wallClockBase) % 1000L
            lapWallClockBase = now - offset
            lapElapsedAtBase = 0L
            syncLapToService()
        }
    }

    /** Buzz confirming a lap tap (ring ends buzz from the service, see RingAlert.kt). */
    private fun vibrateLap() = RingAlert.vibrate(app)

    // Stop tapping opens the upload dialog and pauses the timer
    fun requestStop() {
        pause()
        val dateStr = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("MMM d"))
        _activityName.value = "${_selectedType.value.label} – $dateStr"
        _showUploadDialog.value = true
    }

    // Cancel upload dialog — resume the timer, workout still in progress
    fun dismissUploadDialog() {
        _showUploadDialog.value = false
        start()
    }

    // Discard the workout entirely (from the dialog)
    fun discard() {
        reset()
    }

    // Reset everything after a successful upload
    fun reset() {
        pause()
        stopTimerService()
        HeartRateMonitor.discardRecording()   // a saved workout has already taken its samples
        _hasStarted.value       = false
        _elapsedSeconds.value   = 0L
        _currentLapSeconds.value = 0L
        _startDateTime.value    = null
        _activityName.value     = ""
        _distanceKm.value       = ""
        _showUploadDialog.value = false
        _selectedType.value     = workoutActivityTypes[0]
    }

    fun setActivityName(name: String) { _activityName.value = name }
    fun setSelectedType(type: WorkoutActivityType) {
        val previous = _selectedType.value
        _selectedType.value = type
        val currentName = _activityName.value
        if (currentName.isBlank() || isAutoGeneratedName(currentName, previous)) {
            val dateStr = (_startDateTime.value ?: LocalDateTime.now())
                .format(DateTimeFormatter.ofPattern("MMM d"))
            _activityName.value = "${type.label} – $dateStr"
        }
    }

    private fun isAutoGeneratedName(name: String, type: WorkoutActivityType): Boolean {
        return name.startsWith("${type.label} – ")
    }

    // ── Foreground service helpers ─────────────────────────────────────────────
    private fun serviceIntent(action: String) =
        Intent(app, TimerForegroundService::class.java).apply { this.action = action }

    private fun startTimerService() {
        val intent = serviceIntent(TimerForegroundService.ACTION_START)
            .putExtra(TimerForegroundService.EXTRA_ELAPSED, _elapsedSeconds.value)
        app.startForegroundService(intent)
    }

    private fun pauseTimerService() {
        app.startService(serviceIntent(TimerForegroundService.ACTION_PAUSE))
    }

    private fun resumeTimerService() {
        val intent = serviceIntent(TimerForegroundService.ACTION_RESUME)
            .putExtra(TimerForegroundService.EXTRA_ELAPSED, _elapsedSeconds.value)
        app.startService(intent)
    }

    /** Gives the notification the lap's anchors and ring length, so it counts exactly like the ring. */
    private fun syncLapToService() {
        val intent = serviceIntent(TimerForegroundService.ACTION_LAP)
            .putExtra(TimerForegroundService.EXTRA_LAP_AT_BASE, lapElapsedAtBase)
            .putExtra(TimerForegroundService.EXTRA_LAP_WALL_BASE, if (_isRunning.value) lapWallClockBase else 0L)
            .putExtra(TimerForegroundService.EXTRA_LAP_DURATION, _lapDurationSeconds.value)
        // startService can throw if the app is in the background; the next sync catches up
        try { app.startService(intent) } catch (_: IllegalStateException) { }
    }

    private fun stopTimerService() {
        app.startService(serviceIntent(TimerForegroundService.ACTION_STOP))
    }

    private fun recoverTimerState() {
        val prefs = app.getSharedPreferences("timer_state", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", false)) return

        val savedElapsed = prefs.getLong("elapsed_seconds", 0L)
        val savedTimestamp = prefs.getLong("timestamp", 0L)
        val wasRunning = prefs.getBoolean("running", false)

        val drift = if (wasRunning && savedTimestamp > 0L)
            (System.currentTimeMillis() - savedTimestamp) / 1000 else 0L
        val recovered = savedElapsed + drift
        // The service keeps the lap going while the app is closed, so pick it up instead of restarting at 0
        val recoveredLap = prefs.getLong("lap_seconds", 0L) + drift

        _elapsedSeconds.value = recovered
        _currentLapSeconds.value = recoveredLap
        lapElapsedAtBase = recoveredLap
        _hasStarted.value = true

        if (wasRunning) {
            _isRunning.value = true
            // Restarted mid-workout: keep sampling, padded so the graph lines up with elapsed time
            HeartRateMonitor.resumeRecording(padToSeconds = recovered)
            wallClockBase = System.currentTimeMillis()
            elapsedAtBase = recovered
            lapWallClockBase = wallClockBase
            lapElapsedAtBase = recoveredLap
            syncLapToService()
            tickJob = viewModelScope.launch {
                while (true) {
                    delay(250L)
                    val now = System.currentTimeMillis()
                    _elapsedSeconds.value = elapsedAtBase + (now - wallClockBase) / 1000
                    _currentLapSeconds.value = lapElapsedAtBase + (now - lapWallClockBase) / 1000
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickJob?.cancel()
    }
}
