package com.example.stockapplication.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.stockapplication.data.MotionSpeed
import com.example.stockapplication.data.SaleAnimation
import com.example.stockapplication.data.SaleAnimationStore
import com.example.stockapplication.ui.theme.LocalMotionSpeed

/**
 * Plays the user's imported Lottie animation on the receipt.
 *
 * ## Always falls back
 *
 * Every failure path here draws the built-in flourish instead: a missing file, an unreadable one,
 * or a composition Lottie refuses to parse. The receipt is the last screen of a transaction and
 * the one the cashier looks up for — rendering nothing, or a blank box, would be read as the sale
 * having failed. Falling back to [SaleThumbsUp] keeps the "it went through" message intact.
 *
 * @param fileName stored file name from the settings; null or blank falls back immediately.
 * @param sizeDp the box the animation is drawn into. A Lottie file carries its own dimensions, so
 *   the receipt's size slider would otherwise have no effect on it — the animation is fitted into
 *   this square regardless of its native size.
 */
@Composable
fun SaleLottieAnimation(
    fileName: String?,
    sizeDp: Int,
    modifier: Modifier = Modifier,
    /** Shown when the file cannot be played. */
    fallbackAnimation: SaleAnimation = SaleAnimation.Default,
) {
    val context = LocalContext.current
    // `playableFile` rather than `animationFile`: a `.lottie` import is a folder, and the player
    // needs the `animation.json` inside it rather than the container.
    val path = remember(fileName) {
        SaleAnimationStore.playableFile(context, fileName)?.absolutePath
    }

    if (path == null) {
        SaleThumbsUp(animation = fallbackAnimation, thumbSize = sizeDp.dp, modifier = modifier)
        return
    }

    // Resolved once: the composition is keyed on the file name, which is unique per pick.
    val composition by rememberLottieComposition(LottieCompositionSpec.File(path))
    val motion = LocalMotionSpeed.current

    val loaded = composition
    if (loaded == null) {
        // Also the state on the first frame, before the composition has been read — which is why
        // the fallback is the built-in glyph rather than an empty box.
        SaleThumbsUp(animation = fallbackAnimation, thumbSize = sizeDp.dp, modifier = modifier)
        return
    }

    LottieAnimation(
        composition = loaded,
        modifier = modifier.size(sizeDp.dp),
        // Animations off holds the resting state rather than looping — the same rule the built-in
        // flourish follows, so turning motion off does not leave one component still moving.
        //
        // Expressed as "paused" rather than as `iterations = 0`: that overload would render the
        // final frame and then stop, whereas freezing the first frame matches how a user reads
        // "animations off" — the component is still there, it just is not moving.
        isPlaying = motion != MotionSpeed.OFF,
        // A speed-matched playback rate, so "Slow" slows the imported animation too instead of
        // applying only to the app's own transitions. Guarded against OFF's 0 multiplier, which
        // would divide the frame rate by zero.
        speed = if (motion.multiplier <= 0f) 1f else motion.multiplier,
        iterations = LottieConstants.IterateForever,
    )
}
