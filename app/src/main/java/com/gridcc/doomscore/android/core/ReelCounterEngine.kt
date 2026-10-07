package com.gridcc.doomscore.android.core

data class Observation(val app: SourceApp, val fingerprint: String, val ad: Boolean = false)
enum class CounterEvent { COUNT, AD_SKIPPED, REWATCH_SKIPPED }
interface CounterSink {
    fun recentlySeen(app: SourceApp, fingerprint: String, wall: Long): Boolean
    fun remember(app: SourceApp, fingerprint: String, wall: Long)
    fun event(kind: CounterEvent, app: SourceApp, wall: Long)
    fun watch(app: SourceApp, from: Long, to: Long)
}

/** Pure state machine. Content must be stable for 750ms; only qualifying views enter history. */
class ReelCounterEngine(private val sink: CounterSink, private val dwellMs: Long = 750) {
    private var candidate: Observation? = null
    private var started = 0L
    private var lastWall = 0L
    private var resolved = false
    private var organic = false
    var status = "Waiting for a reel"; private set

    fun observe(next: Observation?, monotonic: Long, wall: Long) {
        if (next != candidate) {
            // Never infer elapsed viewing through gaps: previous observations were flushed at each poll.
            candidate = next; started = monotonic; lastWall = wall
            resolved = false; organic = false
        }
        val current = candidate ?: run { status = "Waiting for a reel"; return }
        if (monotonic - started < dwellMs) { status = "Checking reel"; return }
        if (!resolved) {
            resolved = true
            when {
                current.ad -> { sink.event(CounterEvent.AD_SKIPPED, current.app, wall); status = "Ad skipped" }
                sink.recentlySeen(current.app, current.fingerprint, wall) -> {
                    organic = true; sink.event(CounterEvent.REWATCH_SKIPPED, current.app, wall); status = "Rewatch skipped"
                }
                else -> { organic = true; sink.event(CounterEvent.COUNT, current.app, wall); status = "Counting reels" }
            }
            if (organic) {
                sink.remember(current.app, current.fingerprint, wall)
                // Attribute only elapsed monotonic time, even if the wall clock changes during dwell.
                val elapsed = (monotonic - started).coerceAtMost(2_000)
                if (elapsed > 0) sink.watch(current.app, wall - elapsed, wall)
            }
            lastWall = wall
        } else if (organic) {
            sink.remember(current.app, current.fingerprint, wall)
            // A changed system clock must not manufacture hours of viewing.
            if (wall - lastWall in 1..2_000) sink.watch(current.app, lastWall, wall)
            lastWall = wall
        }
    }
    fun stop() { candidate = null; resolved = false; organic = false; status = "Waiting for a reel" }
}
