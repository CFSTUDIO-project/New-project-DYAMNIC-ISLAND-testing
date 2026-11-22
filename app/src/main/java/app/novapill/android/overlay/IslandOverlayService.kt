package app.novapill.android.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.view.animation.OvershootInterpolator
import android.animation.ValueAnimator
import androidx.core.app.NotificationCompat
import app.novapill.android.MainActivity
import app.novapill.android.core.AppSettings
import app.novapill.android.core.AppSettingsStore
import app.novapill.android.R
import app.novapill.android.media.MediaSessionHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class IslandOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var islandView: IslandOverlayView? = null
    private var layoutParams: LayoutParams? = null
    private var sizeAnimator: ValueAnimator? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentSettings = AppSettings()
    private var mediaObserverJob: Job? = null
    private var idleHideJob: Job? = null
    private var latestMedia: app.novapill.android.media.MediaUiState? = null
    private var latestPeek: String? = null
    private val density get() = resources.displayMetrics.density

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startSessionNotification()
        showOverlay()
        islandView?.visibility = android.view.View.VISIBLE
        observeMedia()
        scheduleIdleVisibility(latestMedia, latestPeek, currentSettings)
        return START_NOT_STICKY
    }

    private fun startSessionNotification() {
        val stopIntent = Intent(this, IslandOverlayService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 77, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPending = PendingIntent.getActivity(
            this, 78, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("NOVA PILL is active")
            .setContentText("Tap to manage your live island")
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showOverlay() {
        if (islandView != null) return
        val view = IslandOverlayView(
            context = this,
            onExpandedChanged = { expanded -> animateSize(expanded) },
            onDismiss = { islandView?.setExpanded(false) }
        )
        val params = LayoutParams(
            dp(currentSettings.islandWidthDp),
            dp(42),
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCH_MODAL or LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = safeTopInset() + dp(currentSettings.topOffsetDp)
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            windowAnimations = 0
        }
        islandView = view
        layoutParams = params
        try {
            windowManager.addView(view, params)
        } catch (error: WindowManager.BadTokenException) {
            islandView = null
            layoutParams = null
            stopSelf()
        } catch (error: SecurityException) {
            islandView = null
            layoutParams = null
            stopSelf()
        }
    }

    private fun observeMedia() {
        if (mediaObserverJob?.isActive == true) return
        mediaObserverJob = serviceScope.launch {
            val settingsFlow = AppSettingsStore(this@IslandOverlayService).settings
            combine(MediaSessionHub.media, MediaSessionHub.notificationPeek, settingsFlow) { media, peek, settings -> Triple(media, peek, settings) }
                .collect { (media, peek, settings) ->
                    currentSettings = settings
                    latestMedia = media
            latestPeek = if (settings.notificationPeek) peek else null
            islandView?.updateState(media, latestPeek, settings)
            layoutParams?.let { params ->
                params.y = safeTopInset() + dp(settings.topOffsetDp)
                if (islandView?.isExpanded != true) params.width = dp(settings.islandWidthDp)
                islandView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
            }
            scheduleIdleVisibility(media, latestPeek, settings)
                }
        }
    }

    private fun scheduleIdleVisibility(
        media: app.novapill.android.media.MediaUiState?,
        peek: String?,
        settings: AppSettings
    ) {
        idleHideJob?.cancel()
        val active = media?.isPlaying == true || peek != null || settings.manualRecordingIndicator
        val view = islandView ?: return
        if (active || !settings.hideWhenIdle) {
            view.visibility = android.view.View.VISIBLE
            return
        }
        val gracePeriod = if (media != null) 8_000L else 6_000L
        idleHideJob = serviceScope.launch {
            kotlinx.coroutines.delay(gracePeriod)
            if (latestMedia?.isPlaying != true && latestPeek == null && !currentSettings.manualRecordingIndicator) {
                islandView?.visibility = android.view.View.INVISIBLE
            }
        }
    }

    private fun animateSize(expanded: Boolean) {
        val view = islandView ?: return
        val params = layoutParams ?: return
        sizeAnimator?.cancel()
        val targetWidth = if (expanded) expandedWidth() else dp(currentSettings.islandWidthDp)
        val targetHeight = if (expanded) dp(166) else dp(42)
        val startWidth = params.width
        val startHeight = params.height
        val powerSaver = getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        if (currentSettings.reducedMotion || powerSaver) {
            params.width = targetWidth
            params.height = targetHeight
            runCatching { windowManager.updateViewLayout(view, params) }
            return
        }
        sizeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250L
            interpolator = OvershootInterpolator(0.32f)
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                params.width = (startWidth + (targetWidth - startWidth) * t).toInt()
                params.height = (startHeight + (targetHeight - startHeight) * t).toInt()
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            start()
        }
    }

    private fun expandedWidth(): Int {
        val screenWidth = if (Build.VERSION.SDK_INT >= 30) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            resources.displayMetrics.widthPixels
        }
        return minOf(dp(350), (screenWidth * 0.92f).toInt())
    }

    private fun safeTopInset(): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            val safeInsets = windowManager.currentWindowMetrics.windowInsets.getInsets(
                android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.displayCutout()
            )
            return safeInsets.top
        }
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(24)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_channel_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        layoutParams?.let { params ->
            params.y = safeTopInset() + dp(currentSettings.topOffsetDp)
            val view = islandView
            if (view != null) {
                params.width = if (view.isExpanded) expandedWidth() else dp(currentSettings.islandWidthDp)
                params.height = if (view.isExpanded) dp(166) else dp(42)
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }
    }

    override fun onDestroy() {
        sizeAnimator?.cancel()
        mediaObserverJob?.cancel()
        idleHideJob?.cancel()
        serviceScope.cancel()
        islandView?.let { view -> runCatching { windowManager.removeView(view) } }
        islandView = null
        layoutParams = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int = (value * density).toInt()

    companion object {
        private const val CHANNEL_ID = "nova_pill_overlay"
        private const val NOTIFICATION_ID = 501
        private const val ACTION_STOP = "app.novapill.android.action.STOP_ISLAND"

        fun start(context: Context) {
            val intent = Intent(context, IslandOverlayService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IslandOverlayService::class.java))
        }
    }
}
