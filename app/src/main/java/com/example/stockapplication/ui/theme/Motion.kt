package com.example.stockapplication.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntSize
import com.example.stockapplication.data.DefaultMotionSpeedId
import com.example.stockapplication.data.MotionSpeed

/**
 * The app's motion language, in one place.
 *
 * Every screen transition, expansion and staggered reveal reads its timings from here, so
 * "make the app feel faster/slower" becomes a single enum lookup rather than a hunt through the
 * UI tree. When the user turns motion off we still hand out real specs — just zero-duration
 * ones — so callers never need an `if (animationsEnabled)` branch.
 */
object AppMotion {

    /** Emphasised deceleration: content arriving on screen. */
    val Emphasised: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Emphasised acceleration: content leaving the screen. */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Standard curve for size/layout changes, where both ends should feel symmetric. */
    val Standard: Easing = FastOutSlowInEasing

    /** Gentle arrival, used by the staggered list reveal. */
    val Decelerate: Easing = LinearOutSlowInEasing

    // ---- Base durations ----------------------------------------------------
    // Expressed at MotionSpeed.NORMAL; [MotionSpeed.scale] stretches or compresses them.

    private const val FastMillis = 180
    private const val MediumMillis = 260
    private const val SlowMillis = 340
    private const val ExpressiveMillis = 420

    /** How far apart consecutive items in a list stagger, in milliseconds. */
    @Composable
    fun stagger(): Int = currentSpeed().scale(45)

    private fun duration(base: Int, speed: MotionSpeed): Int = speed.scale(base)

    /** Theme colour/font/language changes cross-fade. Pure colour and opacity, no geometry. */
    @Composable
    fun themeFadeMillis(): Int = duration(ExpressiveMillis, currentSpeed())

    /** The product editor sliding over the tab content. */
    @Composable
    fun screenEnterMillis(): Int = duration(MediumMillis, currentSpeed())

    /** Leaving a screen is quicker than arriving; it feels more responsive. */
    @Composable
    fun screenExitMillis(): Int = duration(FastMillis, currentSpeed())

    /** A newly composed list row fading up into place. */
    @Composable
    fun itemRevealMillis(): Int = duration(SlowMillis, currentSpeed())

    @Composable
    fun expandMillis(): Int = duration(MediumMillis, currentSpeed())

    /** Reading the speed from the environment instead of threading it through every call. */
    @Composable
    private fun currentSpeed(): MotionSpeed = LocalMotionSpeed.current

    // ---- Ready-made specs --------------------------------------------------

    @Composable
    fun themeFade(): androidx.compose.animation.core.FiniteAnimationSpec<Float> =
        tween(durationMillis = themeFadeMillis(), easing = Standard)

    @Composable
    fun enterFade(): androidx.compose.animation.core.FiniteAnimationSpec<Float> =
        tween(durationMillis = screenEnterMillis(), easing = Emphasised)

    @Composable
    fun exitFade(): androidx.compose.animation.core.FiniteAnimationSpec<Float> =
        tween(durationMillis = screenExitMillis(), easing = Accelerate)

    @Composable
    fun expand(): androidx.compose.animation.core.FiniteAnimationSpec<IntSize> =
        tween(durationMillis = expandMillis(), easing = Standard)

    @Composable
    fun itemReveal(): androidx.compose.animation.core.FiniteAnimationSpec<Float> =
        tween(durationMillis = itemRevealMillis(), easing = Decelerate)
}

/**
 * Publishes the user's animation-speed choice down the tree.
 *
 * A CompositionLocal rather than a parameter because motion is consulted from a dozen leaf
 * composables; threading the settings through every screen would be pure noise.
 */
val LocalMotionSpeed = staticCompositionLocalOf { MotionSpeed.fromId(DefaultMotionSpeedId) }
