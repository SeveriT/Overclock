package com.serkka.tracker

import kotlin.math.roundToInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class WorkoutViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    // Automatically updates the UI whenever the database changes
    val allWorkouts: StateFlow<List<Workout>> = repository.getAllWorkouts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allBodyWeights: StateFlow<List<BodyWeight>> = repository.getAllBodyWeights()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allNotes: StateFlow<List<Note>> = repository.getAllNotes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allSessions: StateFlow<List<WorkoutSession>> = repository.getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allScheduleItems: StateFlow<List<ScheduleItem>> = repository.getAllScheduleItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addWorkout(exercise: String, sets: Int, reps: Int, weight: Float, dateMillis: Long, isPersonalBest: Boolean, weightUnit: String = "kg", notes: String = "", rpe: Float? = null) {
        viewModelScope.launch {
            val newWorkout = Workout(
                exerciseName = exercise,
                sets = sets,
                reps = reps,
                weight = weight,
                date = dateMillis,
                isPersonalBest = isPersonalBest,
                weightUnit = weightUnit,
                notes = notes,
                rpe = rpe
            )
            repository.addWorkout(newWorkout)
        }
    }

    /** Bulk rename; [onDone] gets the changed ids so the caller can offer Undo. */
    fun renameExercise(oldName: String, newName: String, onDone: (List<Int>) -> Unit = {}) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return
        viewModelScope.launch { onDone(repository.renameExercise(oldName, trimmed)) }
    }

    fun undoRenameExercise(ids: List<Int>, oldName: String) {
        viewModelScope.launch { repository.setExerciseName(ids, oldName) }
    }

    fun updateWorkout(workout: Workout) {
        viewModelScope.launch {
            repository.updateWorkout(workout)
        }
    }

    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch {
            repository.deleteWorkout(workout)
        }
    }

    fun restoreWorkout(workout: Workout) {
        viewModelScope.launch {
            repository.addWorkout(workout)
        }
    }

    // Body Weight operations

    var weightInput by mutableStateOf("") // The state tied to your TextField
    fun prepareNewEntry() {
        weightInput = ""
        viewModelScope.launch {
            val lastEntry = repository.getLastWeight()
            // If a previous entry exists, set the input to that value, else keep it empty
            weightInput = lastEntry?.weight?.let { w ->
                if (w % 1 == 0f) w.toInt().toString() else w.toString()
            } ?: ""
        }
    }

    fun addBodyWeight(weight: Float, dateMillis: Long, notes: String = "") {
        viewModelScope.launch {
            repository.addBodyWeight(BodyWeight(date = dateMillis, weight = weight, notes = notes))
        }
    }

    fun updateBodyWeight(bodyWeight: BodyWeight) {
        viewModelScope.launch {
            repository.updateBodyWeight(bodyWeight)
        }
    }

    fun deleteBodyWeight(bodyWeight: BodyWeight) {
        viewModelScope.launch {
            repository.deleteBodyWeight(bodyWeight)
        }
    }

    fun restoreBodyWeight(bodyWeight: BodyWeight) {
        viewModelScope.launch {
            repository.addBodyWeight(bodyWeight)
        }
    }

    // Note operations
    fun addNote(title: String, content: String, dateMillis: Long) {
        viewModelScope.launch {
            repository.addNote(Note(title = title, content = content, date = dateMillis))
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun restoreNote(note: Note) {
        viewModelScope.launch {
            repository.addNote(note)
        }
    }

    // WorkoutSession operations
    /** [hrSamples]: BPM per HeartRateMonitor.SAMPLE_SECONDS from the timer (empty = no strap). */
    fun addWorkoutSession(
        name: String,
        type: String,
        startEpochMs: Long,
        durationSeconds: Int,
        hrSamples: List<Int> = emptyList()
    ) {
        val readings = hrSamples.filter { it > 0 }
        viewModelScope.launch {
            repository.addSession(
                WorkoutSession(
                    name = name, type = type, date = startEpochMs, durationSeconds = durationSeconds,
                    avgHr = readings.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                    maxHr = readings.maxOrNull(),
                    hrSamples = hrSamples.takeIf { readings.isNotEmpty() }?.joinToString(",")
                )
            )
        }
    }

    fun updateWorkoutSession(session: WorkoutSession) {
        viewModelScope.launch {
            repository.updateSession(session)
        }
    }

    fun deleteWorkoutSession(session: WorkoutSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }

    fun restoreWorkoutSession(session: WorkoutSession) {
        viewModelScope.launch {
            repository.addSession(session)
        }
    }

    // Schedule (workout checklist) operations
    fun addScheduleItem(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.addScheduleItem(trimmed) }
    }

    fun toggleScheduleItem(item: ScheduleItem) {
        viewModelScope.launch { repository.updateScheduleItem(item.copy(isDone = !item.isDone)) }
    }

    fun renameScheduleItem(item: ScheduleItem, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.updateScheduleItem(item.copy(text = trimmed)) }
    }

    fun deleteScheduleItem(item: ScheduleItem) {
        viewModelScope.launch { repository.deleteScheduleItem(item) }
    }

    fun resetSchedule() {
        viewModelScope.launch { repository.resetSchedule() }
    }

}
