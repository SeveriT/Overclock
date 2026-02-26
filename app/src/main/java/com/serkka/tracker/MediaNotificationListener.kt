package com.serkka.tracker

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class SongInfo(
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false
)

class MediaNotificationListener : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private val mediaRepository = MediaRepository.getInstance()

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateSongInfo()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateSongInfo()
        }
    }

    private var activeController: MediaController? = null

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaRepository.setNextTrackAction { skipToNext() }
        mediaRepository.setTogglePlayPauseAction { togglePlayPause() }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateController()
    }

    private fun updateController() {
        val controllers = mediaSessionManager?.getActiveSessions(ComponentName(this, MediaNotificationListener::class.java))
        val newController = controllers?.firstOrNull()

        // Always register callback to the latest controller if it exists
        if (activeController != newController) {
            activeController?.unregisterCallback(callback)
            activeController = newController
            activeController?.registerCallback(callback)
        }
        updateSongInfo()
    }

    private fun updateSongInfo() {
        val metadata = activeController?.metadata
        val playbackState = activeController?.playbackState
        
        val info = SongInfo(
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        )
        mediaRepository.updateSong(info)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        updateController()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        updateController()
    }

    private fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    private fun togglePlayPause() {
        val playbackState = activeController?.playbackState
        if (playbackState?.state == PlaybackState.STATE_PLAYING) {
            activeController?.transportControls?.pause()
        } else {
            activeController?.transportControls?.play()
        }
    }
}
