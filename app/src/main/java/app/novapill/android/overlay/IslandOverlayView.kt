package app.novapill.android.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.os.PowerManager
import android.view.MotionEvent
import android.view.View
import app.novapill.android.media.MediaSessionHub
import app.novapill.android.media.MediaUiState
import app.novapill.android.core.AppSettings
import kotlin.math.abs
import kotlin.math.sin

/** Lightweight Canvas overlay. It draws only while visible; the mini waveform animates only during playback. */
class IslandOverlayView(
    context: Context,
    private val onExpandedChanged: (Boolean) -> Unit,
    private val onDismiss: () -> Unit
) : View(context) {
    private val density = resources.displayMetrics.density
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF28282D.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD94A59.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF5F3F2.toInt()
        textSize = dp(12.5f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val secondary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF929198.toInt()
        textSize = dp(10f)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val artworkPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE9E5E5.toInt() }
    private val capsule = RectF()
    private val artworkRect = RectF()
    private var media: MediaUiState? = null
    private var notificationLabel: String? = null
    private var visualizerEnabled = true
    private var reducedMotion = false
    private var manualRecording = false
    private var expanded = false
    val isExpanded: Boolean get() = expanded
    private var animating = false

    fun updateState(state: MediaUiState?, notification: String?, settings: AppSettings) {
        media = state
        notificationLabel = notification
        visualizerEnabled = settings.visualizerEnabled
        reducedMotion = settings.reducedMotion
        manualRecording = settings.manualRecordingIndicator
        contentDescription = when {
            notification != null -> "New notification from $notification"
            state != null -> "Now playing ${state.title} by ${state.artist}"
            else -> "NOVA PILL idle"
        }
        invalidate()
    }

    fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        animating = !reducedMotion && powerManager?.isPowerSaveMode != true
        onExpandedChanged(value)
        invalidate()
        if (animating) postDelayed({ animating = false; invalidate() }, 260L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = dp(1f)
        capsule.set(pad, pad, width.toFloat() - pad, height.toFloat() - pad)
        val radius = capsule.height() / 2f
        canvas.drawRoundRect(capsule, radius, radius, fill)
        canvas.drawRoundRect(capsule, radius, radius, border)

        val current = media
        val left = dp(13f)
        val artSize = if (expanded) dp(42f) else dp(30f)
        val artTop = (height - artSize) / 2f
        artworkRect.set(left, artTop, left + artSize, artTop + artSize)
        drawArtwork(canvas, current?.artwork, artworkRect)

        val titleText = notificationLabel?.let { "New notification · $it" }
            ?: current?.title?.takeIf { it.isNotBlank() }
            ?: "NOVA PILL"
        val artistText = notificationLabel?.let { "Private preview" }
            ?: current?.artist?.takeIf { it.isNotBlank() }
            ?: "Waiting for activity"
        val textLeft = artworkRect.right + dp(9f)
        val rightReserve = if (expanded) dp(94f) else dp(48f)
        val maxTextWidth = (width - textLeft - rightReserve).coerceAtLeast(dp(52f))

        text.textSize = dp(if (expanded) 13f else 11.8f)
        drawEllipsized(canvas, titleText, textLeft, height / 2f - dp(1f), maxTextWidth, text)
        secondary.textSize = dp(9.5f)
        drawEllipsized(canvas, artistText, textLeft, height / 2f + dp(13f), maxTextWidth, secondary)

        if (!expanded && visualizerEnabled) {
            drawWaveform(canvas, width - dp(36f), height / 2f, current?.isPlaying == true && !reducedMotion && powerManager?.isPowerSaveMode != true)
        } else if (expanded) {
            drawExpandedControls(canvas, current)
        }

        val motionSuppressed = reducedMotion || powerManager?.isPowerSaveMode == true
        if (current?.isPlaying == true && !motionSuppressed) {
            // Keep both the compact waveform and expanded progress position current,
            // without running a frame loop while idle or paused.
            if (!expanded && visualizerEnabled) postInvalidateDelayed(90L)
            else if (expanded) postInvalidateDelayed(250L)
        }
        if (manualRecording) canvas.drawCircle(width - dp(13f), dp(13f), dp(3f), accent)
        if (animating && !motionSuppressed) postInvalidateOnAnimation()
    }

    private fun drawArtwork(canvas: Canvas, bitmap: Bitmap?, bounds: RectF) {
        if (bitmap == null || bitmap.isRecycled) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF242329.toInt() }
            canvas.drawRoundRect(bounds, dp(9f), dp(9f), p)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD94A59.toInt() }
            canvas.drawCircle(bounds.centerX(), bounds.centerY(), dp(4f), dot)
            return
        }
        val path = Path().apply { addRoundRect(bounds, dp(8f), dp(8f), Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, null, bounds, artworkPaint)
        canvas.restore()
    }

    private fun drawWaveform(canvas: Canvas, centerX: Float, centerY: Float, playing: Boolean) {
        val time = SystemClock.uptimeMillis() / 210.0
        val heights = floatArrayOf(7f, 13f, 9f, 15f)
        for (i in heights.indices) {
            val h = if (playing) dp(5f + abs(sin(time + i * 0.9)) * heights[i]) else dp(3f)
            val x = centerX + dp(i * 5f)
            canvas.drawRoundRect(x, centerY - h / 2f, x + dp(2.2f), centerY + h / 2f, dp(2f), dp(2f), barPaint)
        }
    }

    private fun drawExpandedControls(canvas: Canvas, state: MediaUiState?) {
        val bottom = height - dp(19f)
        val progressLeft = dp(15f)
        val progressRight = width - dp(15f)
        val progressWidth = progressRight - progressLeft
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF34343A.toInt() }
        canvas.drawRoundRect(progressLeft, bottom - dp(3f), progressRight, bottom, dp(2f), dp(2f), track)
        if (state != null && state.durationMs > 0L) {
            val ratio = (state.estimatedPosition().toFloat() / state.durationMs).coerceIn(0f, 1f)
            canvas.drawRoundRect(progressLeft, bottom - dp(3f), progressLeft + progressWidth * ratio, bottom, dp(2f), dp(2f), accent)
        }
        val controlsY = height - dp(42f)
        fun controlPaint(enabled: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (enabled) 0xFFEDE9E9.toInt() else 0xFF5E5C62.toInt()
            strokeWidth = dp(1.8f)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        drawPrevious(canvas, width / 2f - dp(35f), controlsY, controlPaint(state?.canSkipPrevious == true))
        drawPlayPause(canvas, width / 2f, controlsY, state?.isPlaying == true, controlPaint(state?.canPlayPause == true))
        drawNext(canvas, width / 2f + dp(35f), controlsY, controlPaint(state?.canSkipNext == true))
        val closePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB9B5B7.toInt()
            strokeWidth = dp(1.7f)
            strokeCap = Paint.Cap.ROUND
        }
        val closeX = width - dp(18f)
        val closeY = dp(19f)
        canvas.drawLine(closeX - dp(4f), closeY - dp(4f), closeX + dp(4f), closeY + dp(4f), closePaint)
        canvas.drawLine(closeX + dp(4f), closeY - dp(4f), closeX - dp(4f), closeY + dp(4f), closePaint)
    }

    private fun drawPlayPause(canvas: Canvas, x: Float, y: Float, playing: Boolean, p: Paint) {
        if (playing) {
            canvas.drawRoundRect(x - dp(5f), y - dp(6f), x - dp(1f), y + dp(6f), dp(1f), dp(1f), p)
            canvas.drawRoundRect(x + dp(2f), y - dp(6f), x + dp(6f), y + dp(6f), dp(1f), dp(1f), p)
        } else {
            val path = Path().apply {
                moveTo(x - dp(4f), y - dp(7f)); lineTo(x + dp(7f), y); lineTo(x - dp(4f), y + dp(7f)); close()
            }
            canvas.drawPath(path, p)
        }
    }

    private fun drawPrevious(canvas: Canvas, x: Float, y: Float, p: Paint) {
        canvas.drawLine(x - dp(7f), y - dp(7f), x - dp(7f), y + dp(7f), p)
        val path = Path().apply { moveTo(x + dp(6f), y - dp(7f)); lineTo(x - dp(3f), y); lineTo(x + dp(6f), y + dp(7f)) }
        canvas.drawPath(path, p)
    }

    private fun drawNext(canvas: Canvas, x: Float, y: Float, p: Paint) {
        canvas.drawLine(x + dp(7f), y - dp(7f), x + dp(7f), y + dp(7f), p)
        val path = Path().apply { moveTo(x - dp(6f), y - dp(7f)); lineTo(x + dp(3f), y); lineTo(x - dp(6f), y + dp(7f)) }
        canvas.drawPath(path, p)
    }

    private fun drawEllipsized(canvas: Canvas, value: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        var label = value
        while (label.isNotEmpty() && paint.measureText(label) > maxWidth) label = label.dropLast(1)
        if (label != value) label = label.trimEnd() + "…"
        canvas.drawText(label, x, y, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val x = event.x
                val y = event.y
                if (expanded && x > width - dp(42f) && y < dp(40f)) {
                    onDismiss()
                } else if (expanded && y >= height - dp(28f)) {
                    val current = media
                    if (current?.canSeek == true && current.durationMs > 0L) {
                        val ratio = (x / width.toFloat()).coerceIn(0f, 1f)
                        MediaSessionHub.seekTo((current.durationMs * ratio).toLong())
                    }
                } else if (expanded && y > height * 0.62f) {
                    when {
                        x < width * 0.39f && media?.canSkipPrevious == true -> MediaSessionHub.previous()
                        x > width * 0.61f && media?.canSkipNext == true -> MediaSessionHub.next()
                        x >= width * 0.39f && x <= width * 0.61f && media?.canPlayPause == true -> MediaSessionHub.togglePlayPause()
                    }
                } else if (expanded && x < dp(58f) && y < height * 0.68f) {
                    openMediaSource()
                } else {
                    setExpanded(!expanded)
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun openMediaSource() {
        val packageName = media?.packageName ?: return
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launch) }
    }

    private fun dp(value: Float) = value * density
}
