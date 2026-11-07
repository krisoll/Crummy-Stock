package com.example.stockapplication.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.stockapplication.data.LoadingCircle
import com.example.stockapplication.data.LoadingIcon
import com.example.stockapplication.data.LoadingImage
import com.example.stockapplication.data.LoadingImageStore
import com.example.stockapplication.data.LoadingSpin

/**
 * The image that moves while the app starts.
 *
 * Shared between the startup screen and the Config preview so the two can never drift: the preview
 * the shopkeeper tunes is literally the animation they will see on launch.
 *
 * Picked images are rendered through Coil rather than [Image]. Coil is the only way to get an
 * animated GIF or WebP to actually **play** — a `Bitmap` holds a single frame, so decoding a GIF
 * with BitmapFactory silently gives you the first frame and loses the animation.
 *
 * All the transform motion runs inside `graphicsLayer`, which keeps it in the draw phase — the
 * animation costs no recomposition and no relayout, so it stays cheap even behind a busy main
 * thread. Note that this transform is applied to the *animated* image as well, so picking "Spin"
 * with a GIF rotates the already-playing animation.
 *
 * @param spin one of [LoadingSpin]'s five behaviours; [LoadingSpin.NONE] applies no transform.
 * @param periodMillis how long one full cycle of the transform takes.
 */
@Composable
fun SpinningImage(
    image: LoadingImage,
    spin: LoadingSpin,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    periodMillis: Int = 1400,
    /** Drawn behind the image. [LoadingCircle.NONE] draws nothing. */
    circle: LoadingCircle = LoadingCircle.NONE,
    /** Size of the circle, when one is drawn. */
    circleSize: Dp = size + 72.dp,
) {
    // A Lottie animation is a complete, self-animating picture: rotating it would spin the whole
    // artwork, and framing it in the disc would put a circle behind something that already has its
    // own composition. It is drawn as-is, and the caller hides the spin and disc controls so the
    // settings are never silently ignored.
    if (image is LoadingImage.Lottie) {
        LoadingLottie(
            fileName = image.fileName,
            size = size,
            modifier = modifier.size(size),
            fallbackColor = color,
        )
        return
    }

    // Only spin up the transition when something actually moves. `rememberInfiniteTransition`
    // keeps a frame clock running; with NONE there is nothing to drive it.
    val transform = if (spin == LoadingSpin.NONE) Stationary else rememberTransform(spin, periodMillis)

    // Both slide variants need to leave the disc to read as travelling across the screen, so they
    // get the full available width and no framing. Every other motion is a local wobble that the
    // disc contains, so it keeps the fixed circle size.
    //
    // Asked of the enum rather than restated here, so a new travelling motion cannot be added
    // without this path picking it up.
    val travelling = spin.travelsAcrossScreen

    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    Box(
        modifier = if (travelling) {
            // Fill the width but keep a sane height, so the caller's layout is not disrupted.
            modifier.fillMaxWidth().height(circleSize)
        } else {
            modifier.size(circleSize)
        }.onSizeChanged { containerWidthPx = it.width.toFloat() },
        contentAlignment = Alignment.Center,
    ) {
        // No disc while travelling: a circle sitting in the middle would pin the eye to the centre
        // while the image sails past it, which reads as a bug rather than as decoration.
        if (!travelling) {
            LoadingCircleBackdrop(circle = circle, modifier = Modifier.fillMaxSize())
        }

        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    rotationZ = transform.rotation
                    // Split axes so the bounce can squash without shrinking overall.
                    scaleX = transform.scaleX
                    scaleY = transform.scaleY
                    // Only the slides set `offsetX`, and both of them are travelling, so this is
                    // always resolved against the container.
                    //
                    // One travel unit is the distance from the image's rest position (the centre
                    // of the container) to the point where its leading edge clears the far side:
                    // containerWidth/2 to reach the edge, plus imageWidth/2 to fully exit it.
                    translationX = transform.offsetX * (containerWidthPx + size.toPx()) / 2f
                    translationY = transform.offsetY * density.density
                },
            contentAlignment = Alignment.Center,
        ) {
            when (image) {
                is LoadingImage.BuiltIn -> Icon(
                    imageVector = image.icon.icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(size),
                )

                is LoadingImage.Picked -> PickedImage(
                    fileName = image.fileName,
                    size = size,
                    fallbackColor = color,
                )

                // Unreachable: the Lottie case returns above, because an animation that is
                // already moving must not also be rotated or framed. Listed so the compiler
                // keeps this exhaustive when a new source is added.
                is LoadingImage.Lottie -> Unit
            }
        }
    }
}

/**
 * The resolved transform for one frame.
 *
 * @param offsetX horizontal travel in **pixels**, resolved from the container width so the slide
 *   spans the screen regardless of density or window size.
 * @param scaleX/scaleY split rather than shared so the bounce can squash (wider than tall) on
 *   contact without shrinking overall.
 */
private data class Transform(
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val scaleX: Float = scale,
    val scaleY: Float = scale,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

private val Stationary = Transform()

/** Drives the six moving behaviours from one pair of infinite transitions. */
@Composable
private fun rememberTransform(spin: LoadingSpin, periodMillis: Int): Transform {
    val transition = rememberInfiniteTransition(label = "loading-image")

    // 0f..1f, restarting each cycle. Only the behaviours that travel read this.
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "progress",
    )

    // 0..1..0, for the behaviours that breathe rather than travel.
    val oscillation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "oscillation",
    )

    return when (spin) {
        LoadingSpin.NONE -> Stationary
        LoadingSpin.CLOCKWISE -> Transform(rotation = progress * 360f)
        LoadingSpin.COUNTER_CLOCKWISE -> Transform(rotation = -progress * 360f)
        // A quarter-turn rock stays readable even when the image is drawn small.
        LoadingSpin.PENDULUM -> Transform(rotation = -45f + oscillation * 90f)
        LoadingSpin.PULSE -> Transform(scale = 0.75f + oscillation * 0.25f)
        LoadingSpin.BOUNCE -> bounceTransform(oscillation)
        LoadingSpin.SLIDE -> slideTransform(progress)
        LoadingSpin.SLIDE_PAUSE -> slidePauseTransform(progress)
    }
}

/** Peak height of a bounce, in pixels at 1x — scaled below by the caller's density. */
private const val BounceHeightDp = 22f

/**
 * Hops up and down, squashing as it lands.
 *
 * Returns an offset in "dp units" that [SpinningImage] multiplies by its density. Keeping the
 * maths here free of `LocalDensity` makes it a pure function, and keeps the call site readable.
 *
 * The squash is keyed off how close the image is to the floor: full squash at the bottom of the
 * arc, none at the apex. Scaling X up while Y goes down preserves the visual mass, which is the
 * trick that sells it as a solid object.
 */
private fun bounceTransform(oscillation: Float): Transform {
    // `oscillation` runs 0 at the floor up to 1 at the apex and back.
    val height = oscillation
    val squash = (1f - height) * 0.12f
    return Transform(
        // Negative because Y grows downward, and we want to travel upward.
        offsetY = -height * BounceHeightDp,
        scaleX = 1f + squash,
        scaleY = 1f - squash,
    )
}

/**
 * Enters from the left, crosses, and leaves on the right.
 *
 * The image is off-screen at both ends of the cycle, so the reset from "gone on the right" back to
 * "arriving on the left" is never visible — which is why this can use `Restart` rather than needing
 * a deliberately blanked hand-off.
 *
 * @return an offset in "dp units", out of a nominal [-1, 1] travel that the caller scales to the
 *   container width.
 */
private fun slideTransform(progress: Float): Transform {
    // -1.25..1.25 leaves a margin so the image is fully clear of the edges at both ends.
    val travel = -1.25f + progress * 2.5f
    return Transform(offsetX = travel)
}

/**
 * Fraction of each cycle spent **moving**. The remainder is the hold in the centre.
 *
 * At the 3.2s period this is ~2.7s of travel and a ~0.45s hold. Deliberately a *fraction of a
 * second*: the pause is meant to read as a beat, not as a stall. Anything approaching a full
 * second starts to look like the animation has hung.
 */
const val SlidePauseTravelFraction = 0.86f

/**
 * How far off-centre the image starts and finishes, in **travel units** — not dp.
 *
 * One travel unit is `(containerWidth + imageWidth) / 2`, measured from the image's rest position
 * at the centre of the container. Clearing the far edge requires the image's centre to move
 * `(containerWidth + imageWidth) / 2` — i.e. **exactly 1.0** — leaving no margin at all.
 *
 * 1.25 adds a quarter-unit of breathing room past each edge. This was 1.25 before as well, but the
 * scaling it multiplies was wrong, so the image stopped short of the right edge; see
 * `SpinningImage`. The value is the same, the meaning is now correct.
 */
const val SlidePauseTravelExtent = 1.25f

/**
 * Smoothstep: eases in **and** out over 0..1, with zero velocity at both ends.
 *
 * `3t² − 2t³` rather than a cubic bezier because it needs to be a plain function of `t` — the
 * geometry here is unit-tested directly, and an easing that has to be solved numerically (as a
 * bezier's x→t inverse does) would make those tests approximate.
 *
 * Zero velocity at t=1 matters specifically for this animation: the image arrives at the centre
 * *decelerating to a stop*, which is what makes the hold read as a deliberate pause rather than
 * as a cut. With linear motion it would slam into the centre and then sit there.
 */
internal fun easeInOut(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

/**
 * Enters from the left, **eases to a stop in the centre**, then eases away and leaves on the right.
 *
 * Written as one mapping over three segments rather than with a `keyframes` spec. The transform has
 * to be produced per frame either way, and three explicit segments are easier to check than an
 * animation spec whose numbers only make sense read alongside this function.
 *
 * Each travel half is passed through [easeInOut], so the motion accelerates from rest at the
 * screen edge, decelerates into the hold, then repeats on the way out. Every eased segment starts
 * and ends at exactly the segment boundary, so easing cannot move the image off the pinned centre.
 *
 * During the hold the offset is pinned at exactly `0f`. A hold that is still creeping sideways
 * does not read as a pause — it reads as a stutter.
 *
 * Internal rather than private so the geometry can be unit-tested directly; it is the kind of
 * off-by-one-segment arithmetic that looks right and is not.
 */
internal fun slidePauseOffset(progress: Float): Float {
    // Two equal travel halves with the hold between them, so the image is centred exactly halfway
    // through the journey and stays there for the whole remainder of the cycle budget.
    val halfTravel = SlidePauseTravelFraction / 2f
    val hold = 1f - SlidePauseTravelFraction
    return when {
        progress < halfTravel -> {
            // Entry: -extent -> 0, easing out of rest at the edge and into rest at the centre.
            val t = easeInOut(progress / halfTravel)
            -SlidePauseTravelExtent + t * SlidePauseTravelExtent
        }

        progress < halfTravel + hold -> {
            // Hold, dead centre.
            0f
        }

        else -> {
            // Exit: 0 -> +extent, easing out of rest at the centre and into rest at the edge.
            val t = easeInOut((progress - halfTravel - hold) / halfTravel)
            t * SlidePauseTravelExtent
        }
    }
}

private fun slidePauseTransform(progress: Float): Transform =
    Transform(offsetX = slidePauseOffset(progress))

/**
 * A picked image, decoded and animated by Coil.
 *
 * `crossfade(false)` on purpose: the startup screen is already revealed with its own entrance
 * animation, and a crossfade on top of that reads as a flicker.
 */
@Composable
private fun PickedImage(
    fileName: String,
    size: Dp,
    fallbackColor: Color,
) {
    val context = LocalContext.current
    // Resolve to a File inside the app's private storage. Coil can also read content:// URIs, but
    // we deliberately copied the pick at save time, so this path is stable across reboots.
    val path = LoadingImageStore.imagePath(context, fileName)

    if (path == null) {
        // The file went missing (cleared storage, a botched restore). Fall back to the built-in
        // cart rather than leaving a hole where the icon should be.
        Icon(
            imageVector = LoadingIcon.CART.icon,
            contentDescription = null,
            tint = fallbackColor,
            modifier = Modifier.size(size),
        )
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(java.io.File(path))
            .crossfade(false)
            .build(),
        contentDescription = null,
        // `Fit` rather than `Crop`: a logo must never be clipped by the transform.
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(size),
    )
}

/** The optional disc behind the image. */
@Composable
private fun LoadingCircleBackdrop(circle: LoadingCircle, modifier: Modifier) {
    when (circle) {
        LoadingCircle.NONE -> Unit

        LoadingCircle.SOFT -> Box(
            modifier = modifier.background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = CircleShape,
            )
        )

        LoadingCircle.SOLID -> Box(
            modifier = modifier.background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
            )
        )

        LoadingCircle.OUTLINE -> Box(
            modifier = modifier.border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                shape = CircleShape,
            )
        )

        LoadingCircle.PANEL -> Box(
            modifier = modifier.background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
            )
        )
    }
}
