package io.github.yighy.orgwhal.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/**
 * A swipe started while the screen opens or closes scrolls at once, and the shared elements
 * still moving (cards, titles) stop and show in place, so none is left floating over the list.
 */
@Composable
fun Modifier.cutTransitionOnScroll(): Modifier {
    val running by rememberUpdatedState(screenTransitionRunning())
    val cut = LocalSharedCut.current
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!running) return@awaitEachGesture
            var moved = 0f
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                moved += change.positionChange().y
                if (abs(moved) > viewConfiguration.touchSlop) {
                    cut.value = true
                    break
                }
            }
        }
    }
}
