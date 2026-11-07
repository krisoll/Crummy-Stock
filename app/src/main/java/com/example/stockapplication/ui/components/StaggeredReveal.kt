package com.example.stockapplication.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.stockapplication.ui.theme.AppMotion

/** Upper bound on the stagger steps, so a long list never keeps the user waiting to see it. */
private const val MaxStaggerSteps = 8

/**
 * Fades and lifts an item into place, delayed by its position in the list.
 *
 * Applied to list rows so a screen does not assemble itself all at once. The cap on
 * [MaxStaggerSteps] is load-bearing: without it the 40th product in a catalogue would wait almost
 * two seconds before appearing, which reads as jank rather than polish.
 *
 * The animation is driven by an [Animatable] started from [LaunchedEffect] and rendered through
 * `graphicsLayer`, so it runs entirely in the draw phase.
 *
 * Returns [Modifier] unchanged when [staggerMillis] is zero, which is how "animations off" turns
 * the effect into a no-op.
 *
 * @param index the item's position, used only to derive the delay.
 * @param staggerMillis delay between consecutive items.
 */
@Composable
fun rememberRevealModifier(index: Int, staggerMillis: Int): Modifier {
    if (staggerMillis <= 0) return Modifier

    val progress = remember { Animatable(0f) }
    // Resolved here, in composable scope, so the per-item delay reads the user's speed setting.
    val revealMillis = AppMotion.itemRevealMillis()
    val delayMillis = staggerMillis * index.coerceAtMost(MaxStaggerSteps)

    LaunchedEffect(index, revealMillis) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = revealMillis, delayMillis = delayMillis),
        )
    }

    return Modifier.graphicsLayer {
        alpha = progress.value
        // 16dp of travel: enough to read as motion, small enough never to collide with the row above.
        translationY = (1f - progress.value) * 16.dp.toPx()
    }
}
