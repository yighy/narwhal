package io.github.yighy.orgline.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SwipeAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * Row that reveals [actions] when swiped left, and runs [onSwipeRight] when swiped right
 * past a threshold. Only the row whose [key] equals [openKey] stays open.
 */
@Composable
fun SwipeRow(
    key: String,
    openKey: String?,
    onOpenChange: (Boolean) -> Unit,
    swipeRightIcon: ImageVector,
    onSwipeRight: () -> Unit,
    actions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    val revealPx = with(density) { (56.dp * actions.size + 12.dp).toPx() }
    val triggerPx = with(density) { 96.dp.toPx() }
    val offset = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }

    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val currentOnOpenChange by rememberUpdatedState(onOpenChange)

    // Close when another row opens
    LaunchedEffect(openKey) {
        if (openKey != key && offset.value != 0f) offset.animateTo(0f, spatial)
    }

    Box(modifier.fillMaxWidth()) {
        val x = offset.value
        if (x > 0f) {
            // Swipe right: quick action, highlighted once past the threshold
            Box(
                Modifier
                    .matchParentSize()
                    .background(if (armed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    swipeRightIcon,
                    null,
                    tint = if (armed) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp),
                )
            }
        } else if (x < 0f) {
            Row(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    FilledTonalIconButton(onClick = {
                        scope.launch { offset.animateTo(0f, spatial) }
                        currentOnOpenChange(false)
                        action.onClick()
                    }) { Icon(action.icon, action.label) }
                }
            }
        }

        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(revealPx, triggerPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    offset.value > triggerPx -> {
                                        currentOnSwipeRight()
                                        offset.animateTo(0f, spatial)
                                    }
                                    offset.value < -revealPx / 2 -> {
                                        currentOnOpenChange(true)
                                        offset.animateTo(-revealPx, spatial)
                                    }
                                    else -> {
                                        if (offset.value < 0f) currentOnOpenChange(false)
                                        offset.animateTo(0f, spatial)
                                    }
                                }
                                armed = false
                            }
                        },
                        onDragCancel = {
                            scope.launch { offset.animateTo(0f, spatial) }
                            armed = false
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            val next = (offset.value + delta).coerceIn(-revealPx * 1.15f, triggerPx * 1.5f)
                            val nowArmed = next > triggerPx
                            if (nowArmed != armed) {
                                armed = nowArmed
                                if (nowArmed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            scope.launch { offset.snapTo(next) }
                        },
                    )
                },
        ) {
            content()
        }
    }
}

/** Icons used by the swipe actions (Material Symbols paths), to avoid the large extended icon set. */
object OrglineIcons {
    val Check: ImageVector get() = Icons.Default.Check

    val SubNote: ImageVector by lazy {
        icon("SubNote", "M19,15l-6,6l-1.42,-1.42L15.17,16H4V4h2v10h9.17l-3.59,-3.58L13,9L19,15z")
    }

    val Undo: ImageVector by lazy {
        icon(
            "Undo",
            "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 " +
                "3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z",
        )
    }

    val UnfoldMore: ImageVector by lazy {
        icon(
            "UnfoldMore",
            "M12,5.83L15.17,9l1.41,-1.41L12,3 7.41,7.59 8.83,9 12,5.83zM12,18.17L8.83,15l-1.41,1.41L12,21l4.59,-4.59L15.17,15 12,18.17z",
        )
    }

    val UnfoldLess: ImageVector by lazy {
        icon(
            "UnfoldLess",
            "M7.41,18.59L8.83,20 12,16.41 15.17,20l1.41,-1.41L12,14l-4.59,4.59zM16.59,5.41L15.17,4 12,7.59 8.83,4 7.41,5.41 12,10l4.59,-4.59z",
        )
    }

    private fun icon(name: String, path: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
}
