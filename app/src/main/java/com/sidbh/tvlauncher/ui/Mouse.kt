package com.sidbh.tvlauncher.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

// The TV widgets only react to the D-pad, so a mouse needs its own handling.
// Hover moves focus (so the hero, glow and tint follow the cursor), left click activates,
// right click acts as a long press, and the wheel scrolls rows sideways.

private const val CLICK_SLOP_PX = 24f
private const val WHEEL_PX_PER_NOTCH = 90f

private const val REAL_MOVE_WINDOW_NS = 200_000_000L

// When focus (or the wheel) scrolls a list, the content slides under a still cursor and Compose sends
// synthetic hover events for whatever is now beneath it. Focusing on those scrolls again, which is the
// runaway scrolling. Synthetic events keep the cursor's position, so only a changed position counts as a move.
private var lastCursor: Offset? = null
private var lastRealMoveNs = 0L

/** Place once at the root, so every hover can tell a real cursor move from content moving under it. */
fun Modifier.trackMouseMoves(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Move && event.type != PointerEventType.Enter) continue
                val pos = event.changes.firstOrNull()?.position ?: continue
                val prev = lastCursor
                if (prev == null || (pos - prev).getDistance() > 1f) lastRealMoveNs = System.nanoTime()
                lastCursor = pos
            }
        }
    }

private fun cursorJustMoved() = System.nanoTime() - lastRealMoveNs < REAL_MOVE_WINDOW_NS

/** Moving the cursor onto the element focuses it. Content scrolling under a still cursor does not. */
fun Modifier.hoverFocus(requester: FocusRequester): Modifier =
    pointerInput(requester) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val hover = event.type == PointerEventType.Enter || event.type == PointerEventType.Move
                if (hover && cursorJustMoved()) runCatching { requester.requestFocus() }
            }
        }
    }

/** Left click (or touch tap) calls [onClick]; right click calls [onLongClick] when given. Drags are ignored. */
fun Modifier.mouseClick(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier =
    pointerInput(onClick, onLongClick) {
        awaitPointerEventScope {
            var down: Offset? = null
            var secondary = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                when (event.type) {
                    PointerEventType.Press -> {
                        down = change.position
                        secondary = event.buttons.isSecondaryPressed
                    }
                    PointerEventType.Release -> {
                        val start = down
                        if (start != null && (change.position - start).getDistance() <= CLICK_SLOP_PX) {
                            if (secondary && onLongClick != null) onLongClick() else onClick()
                        }
                        down = null
                        secondary = false
                    }
                }
            }
        }
    }

/** A plain vertical wheel notch scrolls a horizontal row; sideways wheels already work natively. */
fun Modifier.wheelScrollsRow(state: LazyListState): Modifier = composed {
    val scope = rememberCoroutineScope()
    pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val delta = event.changes.firstOrNull()?.scrollDelta ?: continue
                // The row's own scrollable has already handled any sideways delta by now.
                if (delta.x == 0f && delta.y != 0f) {
                    scope.launch { state.scrollBy(delta.y * WHEEL_PX_PER_NOTCH) }
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
}
