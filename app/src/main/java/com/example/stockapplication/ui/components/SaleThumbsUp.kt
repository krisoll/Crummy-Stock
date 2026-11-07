package com.example.stockapplication.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.stockapplication.data.SaleAnimation
import com.example.stockapplication.ui.theme.AppMotion
import kotlinx.coroutines.delay

/**
 * The success flourish shown on the receipt: a glyph that lands, settles, then ticks upwards.
 *
 * **Why a bespoke animation rather than an icon swap.** The receipt is the one moment in a
 * transaction the cashier looks up for, and a static glyph reads as another row of data. A short,
 * physical arrival — overshoot, rebound, rest — is what makes it read as a *gesture* rather than
 * as information, which is exactly the job the old "Sale complete" heading could not do.
 *
 * **The motion is fixed; only the glyph is configurable.** [animation] chooses *which* picture is
 * thrown, not how it is thrown. That split is deliberate: the landing, the two rings and the float
 * are one choreographed piece, and exposing them as separate knobs would let a user assemble a
 * combination that reads badly (a long ring over a fast arrival, say) with no way to describe it.
 *
 * **The rings are drawn, not scaled.** Two of them are drawn on a [Canvas] and animated on
 * separate clocks, so the burst can go out while a second, slower ring follows it. Scaling a single
 * circle would have produced one concentric pulse, which reads as a loading spinner — the opposite
 * of the intended message.
 *
 * Everything here is decorative: the caller supplies the label on [modifier], and a zero-duration
 * motion setting collapses this to its resting frame automatically (see [AppMotion]).
 */
@Composable
fun SaleThumbsUp(
    modifier: Modifier = Modifier,
    /** Which glyph to throw. Defaults to the thumb the function is named after. */
    animation: SaleAnimation = SaleAnimation.Default,
    /** Diameter of the glyph itself; the rings and halo are sized relative to it. */
    thumbSize: Dp = 56.dp,
    /**
     * Colour of the glyph and its burst rings.
     *
     * Defaults to `primary` — the button accent — because that is what the receipt wants: the
     * flourish lands on a dark scrim, where the accent is the loudest thing available. Override it
     * when the glyph is drawn **on** an accent-coloured surface, where the accent would be the same
     * colour as the fill behind it. The animation picker's chips are exactly that case: they sit on
     * the app's light panel, so they pass the text colour instead.
     */
    glyphColor: Color = MaterialTheme.colorScheme.primary,
    /** Fill of the soft disc behind the glyph. */
    haloColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {


    // ---- The thumb itself --------------------------------------------------
    // `Animatable` rather than `animateFloatAsState` because the landing is a keyframed sequence,
    // not a transition between two values: it overshoots and comes back, and that has to be driven
    // from one launch rather than from a series of separate state changes.
    val punch = remember { Animatable(0f) }
    // Read outside the effect: the motion-speed lookup is @Composable, so it has to happen here
    // in composition rather than inside the coroutine that drives the animation.
    val enterMillis = AppMotion.screenEnterMillis()
    LaunchedEffect(enterMillis) {
        val enter = enterMillis.toFloat()
        punch.animateTo(
            targetValue = 1f,
            // 0 → overshoot → small rebound → rest. The overshoot is what sells the "stamped"
            // feel; without it the icon merely grows and looks like a resize.
            animationSpec = keyframes {
                durationMillis = (enter * 1.6f).toInt()
                0f at 0
                1.18f at (enter * 0.62f).toInt() using AppMotion.Emphasised
                0.94f at (enter * 0.82f).toInt() using AppMotion.Standard
                1f at (enter * 1.6f).toInt() using AppMotion.Standard
            }
        )
    }

    // A slow, permanent float once the landing is done. Subtle on purpose: a couple of dp of
    // travel at this size is enough to keep the eye, without making the dialog look unstable.
    val float = rememberInfiniteTransition(label = "thumbs-float")
    val bob by float.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        // Deliberately not tied to the motion-speed setting: this is ambient idle movement, and a
        // user who picked "fast" for screen transitions did not ask for a jittery resting icon.
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = AppMotion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thumbs-bob"
    )

    // ---- The burst ---------------------------------------------------------
    // Two rings on separate clocks. The first leaves immediately and is the "impact"; the second
    // is delayed so it reads as the echo of the first rather than part of the same pulse.
    val ringInner = remember { Animatable(0f) }
    val ringOuter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        ringInner.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 520, easing = AppMotion.Accelerate)
        )
    }
    LaunchedEffect(Unit) {
        delay(140)
        ringOuter.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 640, easing = AppMotion.Accelerate)
        )
    }

    // The whole flourish is drawn inside a box sized to fit the largest ring, so the dialog's
    // layout does not shift as the rings expand past the glyph.
    Box(
        // No semantics of its own: the drawing is decorative and the caller supplies the label on
        // this modifier (clearAndSetSemantics would have wiped it out).
        modifier = modifier.size(thumbSize * 2.2f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = thumbSize.toPx() * 0.42f

            fun drawRing(progress: Float, endRadiusFactor: Float, maxAlpha: Float) {
                if (progress <= 0f || progress >= 1f) return
                val radius = baseRadius + (thumbSize.toPx() * endRadiusFactor - baseRadius) * progress
                drawCircle(
                    color = glyphColor.copy(alpha = (1f - progress) * maxAlpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = thumbSize.toPx() * 0.06f * (1f - progress * 0.5f))
                )
            }

            drawRing(progress = ringInner.value, endRadiusFactor = 0.95f, maxAlpha = 0.55f)
            drawRing(progress = ringOuter.value, endRadiusFactor = 1.35f, maxAlpha = 0.35f)

            // The halo behind the glyph: a disc that gives the thumb something to sit on. No blur
            // (`Modifier.blur` needs API 31), so the softness comes from low alpha and a radius
            // that stays well inside the rings.
            //
            // Skipped entirely when fully transparent, rather than drawn with alpha 0 — the
            // caller that hides it (the animation picker's chips) wants the glyph on a plain
            // surface, and a hardcoded `0.9f` here would paint the disc over the glyph anyway.
            if (haloColor.alpha > 0f) {
                drawCircle(
                    color = haloColor.copy(alpha = haloColor.alpha * 0.9f),
                    radius = thumbSize.toPx() * 0.62f,
                    center = center
                )
            }
        }

        Icon(
            imageVector = animation.icon,
            contentDescription = null,
            tint = glyphColor,
            modifier = Modifier
                .size(thumbSize)
                .graphicsLayer {
                    // Vertical travel only: a thumbs-up bobbing sideways would read as a wobble,
                    // which is not what "money came in" should look like.
                    translationY = -bob * 2.dp.toPx()
                }
                .scale(punch.value)
        )
    }
}

@Preview(name = "Sale thumbs-up", showBackground = true, widthDp = 200, heightDp = 200)
@Composable
private fun SaleThumbsUpPreview() {
    SaleThumbsUp(animation = SaleAnimation.THUMBS_UP)
}

/**
 * The picker-chip configuration: a small glyph in the text colour, with no halo disc.
 *
 * Kept as a preview because the two settings that make it work are easy to break silently — the
 * halo is drawn at a fixed 0.9 alpha unless its colour is fully transparent, and at this size the
 * disc is large enough to paint straight over the glyph.
 */
@Preview(name = "Sale chip", showBackground = true, widthDp = 200, heightDp = 120)
@Composable
private fun SaleChipPreview() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(16.dp)
    ) {
        SaleAnimation.entries.take(4).forEach { animation ->
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                SaleThumbsUp(
                    animation = animation,
                    thumbSize = 22.dp,
                    glyphColor = MaterialTheme.colorScheme.onSurface,
                    haloColor = Color.Transparent,
                )
            }
        }
    }
}
