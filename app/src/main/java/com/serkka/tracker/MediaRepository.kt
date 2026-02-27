package com.serkka.tracker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MediaRepository private constructor() {
    private val _currentSong = MutableStateFlow(SongInfo())
    val currentSong = _currentSong.asStateFlow()

    private var nextTrackAction: (() -> Unit)? = null
    private var previousTrackAction: (() -> Unit)? = null
    private var togglePlayPauseAction: (() -> Unit)? = null

    fun updateSong(info: SongInfo) {
        _currentSong.value = info
    }

    fun setNextTrackAction(action: () -> Unit) {
        nextTrackAction = action
    }

    fun setTogglePlayPauseAction(action: () -> Unit) {
        togglePlayPauseAction = action
    }

    fun setPreviousTrackAction(action: () -> Unit) {
        previousTrackAction = action
    }

    fun nextTrack() {
        nextTrackAction?.invoke()
    }

    fun previousTrack() {
        previousTrackAction?.invoke()
    }

    fun togglePlayPause() {
        togglePlayPauseAction?.invoke()
    }

    companion object {
        @Volatile
        private var instance: MediaRepository? = null

        fun getInstance(): MediaRepository {
            return instance ?: synchronized(this) {
                instance ?: MediaRepository().also { instance = it }
            }
        }
    }
}
