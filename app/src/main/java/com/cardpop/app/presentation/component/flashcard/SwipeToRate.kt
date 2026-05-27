/*
 * Copyright (C) 2026 FloFla Dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.cardpop.app.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardpop.app.R
import com.cardpop.app.domain.model.FlashcardRating
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter

private const val COMMIT_THRESHOLD_DP = 120f

enum class SwipeDirection {
    LEFT, RIGHT, UP, DOWN;

    fun toRating(): FlashcardRating = when (this) {
        LEFT  -> FlashcardRating.WRONG
        RIGHT -> FlashcardRating.EASY
        UP    -> FlashcardRating.GOOD
        DOWN  -> FlashcardRating.HARD
    }

    fun color(): Color = toRating().ratingColor()

    fun icon(): ImageVector = when (this) {
        LEFT  -> Icons.AutoMirrored.Filled.ArrowBack
        RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
        UP    -> Icons.Default.ArrowUpward
        DOWN  -> Icons.Default.ArrowDownward
    }
}

/**
 * State holder for the swipe gesture. Offset drives the card translation;
 * direction and fraction drive the tint overlay.
 */
class SwipeState {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    var direction by mutableStateOf<SwipeDirection?>(null)
    var fraction by mutableFloatStateOf(0f)
}

@Composable
fun rememberSwipeState(): SwipeState = remember { SwipeState() }

/**
 * Applies a drag gesture to the surface. [enabled] is used as the pointerInput key so
 * the block restarts when swipe mode is toggled. Pass the composable's
 * [rememberCoroutineScope] and density (from [LocalDensity]) to avoid @Composable constraints.
 */
fun Modifier.swipeToRateGesture(
    state: SwipeState,
    scope: CoroutineScope,
    thresholdPx: Float,
    enabled: Boolean = true,
    onRating: (FlashcardRating) -> Unit
): Modifier = this.pointerInput(enabled) {
    if (!enabled) return@pointerInput
    detectDragGestures(
        onDragStart = {
            state.direction = null
            state.fraction = 0f
        },
        onDrag = { change, dragAmount ->
            change.consume()
            scope.launch {
                state.offset.snapTo(state.offset.value + dragAmount)
            }
            val x = state.offset.value.x
            val y = state.offset.value.y
            state.direction = when {
                abs(x) >= abs(y) -> if (x < 0) SwipeDirection.LEFT else SwipeDirection.RIGHT
                else             -> if (y < 0) SwipeDirection.UP   else SwipeDirection.DOWN
            }
            val dominant = if (abs(x) >= abs(y)) abs(x) else abs(y)
            state.fraction = (dominant / thresholdPx).coerceIn(0f, 1f)
        },
        onDragEnd = {
            val rating = if (state.fraction >= 1f) state.direction?.toRating() else null
            scope.launch { state.offset.snapTo(Offset.Zero) }
            state.direction = null
            state.fraction = 0f
            if (rating != null) onRating(rating)
        },
        onDragCancel = {
            scope.launch { state.offset.snapTo(Offset.Zero) }
            state.direction = null
            state.fraction = 0f
        }
    )
}

/**
 * Semi-transparent color wash + centered arrow/label that grows as the user pulls.
 * Has no pointer-input — all touches pass through to the gesture handler below.
 */
@Composable
fun SwipeTintOverlay(direction: SwipeDirection?, fraction: Float, modifier: Modifier = Modifier) {
    if (direction == null || fraction <= 0f) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(direction.color().copy(alpha = (fraction * 0.88f).coerceIn(0f, 0.88f))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(fraction.coerceIn(0f, 1f))
        ) {
            Icon(
                imageVector = direction.icon(),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = direction.toRating().displayName,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * One-time tutorial scrim. Shows the four directions with their rating colors.
 * Tapping anywhere dismisses it; the caller sets the persistent "seen" flag.
 */
@Composable
fun SwipeCoachmark(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss)
    ) {
        CoachmarkArrow(SwipeDirection.UP,    Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
        CoachmarkArrow(SwipeDirection.DOWN,  Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
        CoachmarkArrow(SwipeDirection.LEFT,  Modifier.align(Alignment.CenterStart).padding(start = 24.dp))
        CoachmarkArrow(SwipeDirection.RIGHT, Modifier.align(Alignment.CenterEnd).padding(end = 24.dp))

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.swipe_onboarding_caption),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.swipe_onboarding_dismiss),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun CoachmarkArrow(direction: SwipeDirection, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = direction.icon(),
            contentDescription = null,
            tint = direction.color(),
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = direction.toRating().displayName,
            color = direction.color(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
