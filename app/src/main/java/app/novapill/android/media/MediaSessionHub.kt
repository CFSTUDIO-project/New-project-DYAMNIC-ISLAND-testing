package app.novapill.android.media

import android.graphics.Bitmap
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

 data class MediaUiState(
    val title: String,
    val artist: String,
    val album: String,
    val packageName: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val canPlayPause: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val canSeek: Boolean,
    val artwork: Bitmap?,
    val playbackSpeed: Float = 1f,
    val sampledAtElapsed: Long = SystemClock.elapsedRealtime()
) {
    fun estimatedPosition(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        if (!isPlaying) return positionMs.coerceAtLeast(0L)
        val elapsed = (nowElapsed - sampledAtElapsed).coerceAtLeast(0L)
        val progressed = positionMs + (elapsed * playbackSpeed.coerceIn(0f, 4f)).toLong()
        return progressed.coerceAtMost(durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
    }
}

object MediaSessionHub {
    private val _media = MutableStateFlow<MediaUiState?>(null)
    val media: StateFlow<MediaUiState?> = _media.asStateFlow()

    private val _notificationPeek = MutableStateFlow<String?>(null)
    val notificationPeek: StateFlow<String?> = _notificationPeek.asStateFlow()

    @Volatile private var controller: MediaController? = null

    fun setController(value: MediaController?) {
        controller = value
    }

    fun publish(value: MediaUiState?) {
        _media.value = value
    }

    fun publishNotificationPeek(appName: String?) {
        _notificationPeek.value = appName
    }

    fun togglePlayPause() {
        val active = controller ?: return
        val playback = active.playbackState ?: return
        val actions = playback.actions
        val isPlaying = playback.state == PlaybackState.STATE_PLAYING ||
            playback.state == PlaybackState.STATE_FAST_FORWARDING ||
            playback.state == PlaybackState.STATE_REWINDING
        if (isPlaying) {
            if ((actions and (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)) != 0L) {
                active.transportControls.pause()
            }
        } else if ((actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE)) != 0L) {
            active.transportControls.play()
        }
    }

    fun next() {
        val active = controller ?: return
        val actions = active.playbackState?.actions ?: 0L
        if ((actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L) active.transportControls.skipToNext()
    }

    fun previous() {
        val active = controller ?: return
        val actions = active.playbackState?.actions ?: 0L
        if ((actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L) active.transportControls.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        val active = controller ?: return
        val actions = active.playbackState?.actions ?: 0L
        if ((actions and PlaybackState.ACTION_SEEK_TO) != 0L) {
            active.transportControls.seekTo(positionMs.coerceAtLeast(0L))
        }
    }
}
