package io.github.yighy.orgline.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first

/** A drag made while scrolling waits: finger moves, then the release with its speed. */
private sealed interface Move {
    data class Drag(val dy: Float) : Move
    data class Release(val velocity: Float) : Move
}

/** Way the finger went so far is played back this fast once scrolling is allowed. */
private const val CATCH_UP_MS = 150

/**
 * While [waiting] (a screen transition runs), a drag does not scroll [state] right away, so
 * shared elements land where they belong: it is kept, and once [waiting] ends the way the
 * finger went so far is played back quickly, then the rest of the drag and its fling follow.
 * [connection] (the app bar) gets the scroll as it would from the list.
 * Drags that start when not waiting are left to the list.
 */
@Composable
fun Modifier.deferScrollWhile(
    waiting: Boolean,
    state: ScrollableState,
    connection: NestedScrollConnection? = null,
): Modifier {
    val isWaiting by rememberUpdatedState(waiting)
    val fling by rememberUpdatedState(ScrollableDefaults.flingBehavior())
    val moves = remember { Channel<Move>(Channel.UNLIMITED) }

    LaunchedEffect(state, connection) {
        while (true) {
            val first = moves.receive()
            snapshotFlow { isWaiting }.first { !it }
            // Everything kept while waiting: its way is played back at once
            var kept = 0f
            var release: Move.Release? = null
            var next: Move? = first
            while (next != null) {
                when (next) {
                    is Move.Drag -> kept += next.dy
                    is Move.Release -> release = next
                }
                if (release != null) break
                next = moves.tryReceive().getOrNull()
            }
            if (kept != 0f) {
                state.scroll {
                    var done = 0f
                    animate(0f, kept, animationSpec = tween(CATCH_UP_MS)) { value, _ ->
                        nestedScrollBy(-(value - done), connection, NestedScrollSource.UserInput)
                        done = value
                    }
                }
            }
            // Then the drag goes on with the finger, until it is released
            while (release == null) {
                when (val move = moves.receive()) {
                    is Move.Drag -> state.scroll { nestedScrollBy(-move.dy, connection, NestedScrollSource.UserInput) }
                    is Move.Release -> release = move
                }
            }
            flingWithNestedScroll(state, fling, connection, release.velocity)
        }
    }

    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!isWaiting) return@awaitEachGesture
            // This drag is ours until the finger lifts: the list never sees it (no tap, no press)
            down.consume()
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPointerInputChange(change)
                val dy = change.positionChange().y
                change.consume()
                if (change.changedToUp()) break
                if (dy != 0f) moves.trySend(Move.Drag(dy))
            }
            moves.trySend(Move.Release(tracker.calculateVelocity().y))
        }
    }
}

/**
 * Scrolls by [pixels] (in the scroll direction) as a list would with nested scrolling: the
 * [connection] first, then the list, then the [connection] with what is left. Returns what
 * was used.
 */
private fun ScrollScope.nestedScrollBy(pixels: Float, connection: NestedScrollConnection?, source: NestedScrollSource): Float {
    // Nested scroll offsets go the way the finger does
    val finger = -pixels
    val before = connection?.onPreScroll(Offset(0f, finger), source)?.y ?: 0f
    val left = finger - before
    val byList = -scrollBy(-left)
    val after = connection?.onPostScroll(Offset(0f, byList), Offset(0f, left - byList), source)?.y ?: 0f
    return -(before + byList + after)
}

/** Flings like the list would on release, the app bar taking part (and settling after). */
private suspend fun flingWithNestedScroll(
    state: ScrollableState,
    fling: FlingBehavior,
    connection: NestedScrollConnection?,
    velocity: Float,
) {
    val before = connection?.onPreFling(Velocity(0f, velocity))?.y ?: 0f
    var left = velocity - before
    state.scroll {
        val outer = this
        val nested = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float = outer.nestedScrollBy(pixels, connection, NestedScrollSource.SideEffect)
        }
        left = -with(fling) { nested.performFling(-left) }
    }
    connection?.onPostFling(Velocity(0f, velocity - before - left), Velocity(0f, left))
}
