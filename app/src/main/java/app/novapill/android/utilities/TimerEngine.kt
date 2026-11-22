package app.novapill.android.utilities

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

 data class TimerSnapshot(
    val remainingMs: Long = 0L,
    val running: Boolean = false,
    val complete: Boolean = false
)

/** Monotonic-clock countdown. It runs while the process stays alive. */
object TimerEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(TimerSnapshot())
    val state: StateFlow<TimerSnapshot> = _state.asStateFlow()
    private var deadlineElapsed = 0L
    private var generation = 0L
    private var ticker: Job? = null

    @Synchronized
    fun start(durationMs: Long) {
        val safeDuration = durationMs.coerceIn(1_000L, 24L * 60L * 60L * 1_000L)
        generation += 1L
        val runId = generation
        ticker?.cancel()
        deadlineElapsed = SystemClock.elapsedRealtime() + safeDuration
        _state.value = TimerSnapshot(safeDuration, running = true)
        ticker = scope.launch {
            while (isActive) {
                val remaining = synchronized(this@TimerEngine) {
                    if (runId != generation || !_state.value.running) return@launch
                    (deadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                }
                synchronized(this@TimerEngine) {
                    if (runId != generation || !_state.value.running) return@launch
                    if (remaining == 0L) {
                        _state.value = TimerSnapshot(complete = true)
                        ticker = null
                        return@launch
                    }
                    _state.value = TimerSnapshot(remaining, running = true)
                }
                delay(200L)
            }
        }
    }

    @Synchronized
    fun pause() {
        if (!_state.value.running) return
        generation += 1L
        val current = (deadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        ticker?.cancel()
        ticker = null
        _state.value = TimerSnapshot(current, running = false, complete = current == 0L)
    }

    @Synchronized
    fun resume() {
        val remaining = _state.value.remainingMs
        if (remaining > 0L && !_state.value.running) start(remaining)
    }

    @Synchronized
    fun cancel() {
        generation += 1L
        ticker?.cancel()
        ticker = null
        deadlineElapsed = 0L
        _state.value = TimerSnapshot()
    }
}

 data class StopwatchSnapshot(val elapsedMs: Long = 0L, val running: Boolean = false)

object StopwatchEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(StopwatchSnapshot())
    val state: StateFlow<StopwatchSnapshot> = _state.asStateFlow()
    private var startedAt = 0L
    private var accumulated = 0L
    private var generation = 0L
    private var ticker: Job? = null

    @Synchronized
    fun start() {
        if (_state.value.running) return
        generation += 1L
        val runId = generation
        startedAt = SystemClock.elapsedRealtime()
        _state.value = StopwatchSnapshot(accumulated, true)
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                synchronized(this@StopwatchEngine) {
                    if (runId != generation || !_state.value.running) return@launch
                    _state.value = StopwatchSnapshot(
                        accumulated + (SystemClock.elapsedRealtime() - startedAt),
                        true
                    )
                }
                delay(100L)
            }
        }
    }

    @Synchronized
    fun pause() {
        if (!_state.value.running) return
        generation += 1L
        accumulated += SystemClock.elapsedRealtime() - startedAt
        ticker?.cancel()
        ticker = null
        _state.value = StopwatchSnapshot(accumulated, false)
    }

    @Synchronized
    fun reset() {
        generation += 1L
        ticker?.cancel()
        ticker = null
        accumulated = 0L
        startedAt = 0L
        _state.value = StopwatchSnapshot()
    }
}
