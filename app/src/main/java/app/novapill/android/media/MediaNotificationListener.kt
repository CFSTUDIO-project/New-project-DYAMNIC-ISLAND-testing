package app.novapill.android.media

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.app.Notification
import android.util.Log
import app.novapill.android.core.AppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MediaNotificationListener : NotificationListenerService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var activeCallback: MediaController.Callback? = null
    private var sessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var peekJob: Job? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        sessionManager = getSystemService(MediaSessionManager::class.java)
        val listenerComponent = ComponentName(this, MediaNotificationListener::class.java)
        try {
            refreshControllers(sessionManager?.getActiveSessions(listenerComponent).orEmpty())
            val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                refreshControllers(controllers.orEmpty())
            }
            sessionListener = listener
            sessionManager?.addOnActiveSessionsChangedListener(listener, listenerComponent, mainHandler)
        } catch (error: SecurityException) {
            Log.w(TAG, "Media session access is not available yet", error)
            MediaSessionHub.setController(null)
            MediaSessionHub.publish(null)
        }
    }

    override fun onListenerDisconnected() {
        sessionListener?.let { listener ->
            runCatching { sessionManager?.removeOnActiveSessionsChangedListener(listener) }
        }
        sessionListener = null
        detachController()
        MediaSessionHub.publish(null)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        if (sbn.packageName == packageName) return
        val notification = sbn.notification ?: return
        if (notification.category == Notification.CATEGORY_TRANSPORT ||
            notification.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
        ) return
        val title = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        peekJob?.cancel()
        peekJob = serviceScope.launch {
            val prefs = runCatching { AppSettingsStore(applicationContext).settings.first() }.getOrNull()
            if (prefs?.notificationPeek != true) return@launch
            val safeLabel = if (prefs.privatePreview) {
                title
            } else {
                val shortTitle = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
                if (shortTitle.isBlank()) title else "$title · $shortTitle"
            }
            MediaSessionHub.publishNotificationPeek(safeLabel)
            delay(prefs.notificationDurationSeconds * 1_000L)
            if (MediaSessionHub.notificationPeek.value == safeLabel) MediaSessionHub.publishNotificationPeek(null)
            peekJob = null
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }

    private fun refreshControllers(controllers: List<MediaController>) {
        // Keep the provider's priority order, but prefer sessions with a real active state.
        val candidate = controllers.firstOrNull { controller ->
            val state = controller.playbackState?.state
            state == PlaybackState.STATE_PLAYING ||
                state == PlaybackState.STATE_BUFFERING ||
                state == PlaybackState.STATE_PAUSED ||
                state == PlaybackState.STATE_FAST_FORWARDING ||
                state == PlaybackState.STATE_REWINDING
        } ?: controllers.firstOrNull()
        if (candidate?.sessionToken != activeController?.sessionToken) {
            detachController()
            activeController = candidate
            MediaSessionHub.setController(candidate)
            if (candidate != null) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publish(candidate)
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish(candidate)
                    override fun onSessionDestroyed() {
                        if (activeController?.sessionToken == candidate.sessionToken) {
                            detachController()
                            MediaSessionHub.publish(null)
                        }
                    }
                }
                activeCallback = callback
                runCatching { candidate.registerCallback(callback, mainHandler) }
            }
        }
        candidate?.let(::publish) ?: run {
            MediaSessionHub.setController(null)
            MediaSessionHub.publish(null)
        }
    }

    private fun publish(controller: MediaController) {
        val metadata = controller.metadata
        val playback = controller.playbackState
        val actions = playback?.actions ?: 0L
        val state = playback?.state
        if (state == null || state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED || state == PlaybackState.STATE_ERROR) {
            MediaSessionHub.publish(null)
            return
        }
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val isPlaying = state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_FAST_FORWARDING || state == PlaybackState.STATE_REWINDING
        val canPlayPause = if (isPlaying) {
            (actions and (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)) != 0L
        } else {
            (actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE)) != 0L
        }
        val result = MediaUiState(
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().ifBlank { "Unknown title" },
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            packageName = controller.packageName,
            isPlaying = isPlaying,
            positionMs = playback?.position?.coerceAtLeast(0L) ?: 0L,
            durationMs = duration,
            canPlayPause = canPlayPause,
            canSkipNext = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L,
            canSkipPrevious = (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L,
            canSeek = (actions and PlaybackState.ACTION_SEEK_TO) != 0L,
            artwork = bitmap,
            playbackSpeed = playback?.playbackSpeed ?: 1f,
            sampledAtElapsed = SystemClock.elapsedRealtime()
        )
        MediaSessionHub.publish(result)
    }

    private fun detachController() {
        val old = activeController
        val callback = activeCallback
        if (old != null && callback != null) runCatching { old.unregisterCallback(callback) }
        activeController = null
        activeCallback = null
        MediaSessionHub.setController(null)
    }

    override fun onDestroy() {
        peekJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NovaMediaListener"
    }
}
