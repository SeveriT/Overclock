package com.serkka.tracker

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class SongInfo(
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val packageName: String? = null,
    val position: Long?,
    val duration: Long?,
    val albumArt: Bitmap? = null
)

class MediaNotificationListener : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private val mediaRepository = MediaRepository.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var isUpdatingPosition = false

    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            if (isUpdatingPosition) {
                updateSongInfo()
                handler.postDelayed(this, 1000) // Update every second
            }
        }
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateSongInfo()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateSongInfo()
        }
    }

    private var activeController: MediaController? = null
    private var lastKnownTitle: String? = null
    private var lastKnownArtist: String? = null
    private var lastKnownAlbumArt: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaRepository.setNextTrackAction { skipToNext() }
        mediaRepository.setPreviousTrackAction { skipToPrevious() }
        mediaRepository.setTogglePlayPauseAction { togglePlayPause() }
        mediaRepository.setOpenAppAction { openApp() }
        mediaRepository.setRefreshAction { updateController() }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateController()
    }

    private fun updateController() {
        val controllers = try {
            mediaSessionManager?.getActiveSessions(ComponentName(this, MediaNotificationListener::class.java))
        } catch (_: SecurityException) { null }
        // Prefer the music app chosen in Settings (MusicApps.kt), so e.g. a video in another app
        // doesn't take over the widget while that app has a song loaded
        val chosen = MusicApps.selected(this).value.packageName
        val newController = controllers?.firstOrNull { it.packageName == chosen }
            ?: controllers?.firstOrNull()

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
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)

        // Preserve last known metadata during track transitions (e.g. previous track)
        if (!title.isNullOrBlank()) lastKnownTitle = title
        if (!artist.isNullOrBlank()) lastKnownArtist = artist

        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (albumArt != null) lastKnownAlbumArt = albumArt

        val info = SongInfo(
            title = lastKnownTitle,
            artist = lastKnownArtist,
            isPlaying = isPlaying,
            packageName = activeController?.packageName,
            position = playbackState?.position,
            duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION),
            albumArt = lastKnownAlbumArt
        )
        mediaRepository.updateSong(info)

        if (isPlaying && !isUpdatingPosition) {
            isUpdatingPosition = true
            handler.postDelayed(positionUpdateRunnable, 1000)
        } else if (!isPlaying && isUpdatingPosition) {
            isUpdatingPosition = false
            handler.removeCallbacks(positionUpdateRunnable)
        }
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

    private fun skipToPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    private fun togglePlayPause() {
        val playbackState = activeController?.playbackState
        if (playbackState?.state == PlaybackState.STATE_PLAYING) {
            activeController?.transportControls?.pause()
        } else {
            activeController?.transportControls?.play()
        }
    }

    private fun openApp() {
        if (activeController?.packageName == MusicApp.Spotify.packageName) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(MusicApp.Spotify.packageName, "androidx.compose.ui.tooling.PreviewActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback to default behavior if special activity fails
            }
        }
        
        // Other apps (Metrolist): open them through their launcher entry. The app is already
        // running, so this brings it back where it was. The session's own PendingIntent used to come
        // first, but since Android 14 sending another app's PendingIntent silently fails to open an
        // activity unless the sender opts in, so it's only the fallback now (with that opt-in).
        val pkg = activeController?.packageName ?: return
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            try {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                startActivity(launch)
                return
            } catch (_: Exception) { }
        }
        val session = activeController?.sessionActivity ?: return
        try {
            val options = if (android.os.Build.VERSION.SDK_INT >= 34) {
                android.app.ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    .toBundle()
            } else null
            session.send(this, 0, null, null, null, null, options)
        } catch (_: Exception) { }
    }
    override fun onDestroy() {
        super.onDestroy()
        isUpdatingPosition = false
        handler.removeCallbacks(positionUpdateRunnable)
        activeController?.unregisterCallback(callback)
    }
}
