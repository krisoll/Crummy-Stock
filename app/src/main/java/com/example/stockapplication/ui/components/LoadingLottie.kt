package com.example.stockapplication.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionResult
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.stockapplication.data.LoadingIcon
import com.example.stockapplication.data.MotionSpeed
import com.example.stockapplication.data.SaleAnimationStore
import com.example.stockapplication.ui.theme.LocalMotionSpeed

/**
 * The startup screen's Lottie animation.
 *
 * Two sources, resolved from a single nullable name:
 *
 *  - **null** → the animation bundled in the app's assets, which is what a fresh install shows;
 *  - **a file name** → an animation the user imported, copied into app storage on pick.
 *
 * ## Always falls back
 *
 * A missing or unparseable file draws the built-in cart icon instead. The startup screen is the
 * first thing the user sees, and a blank one does not read as "your animation is gone" — it reads
 * as "the app is broken".
 *
 * @param size the box the animation is drawn into. A Lottie carries its own dimensions, so the
 *   startup screen's size setting has to be applied as the frame rather than left to the file.
 * @param fallbackColor tint for the icon drawn when the animation cannot be played.
 */
@Composable
fun LoadingLottie(
    fileName: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    fallbackColor: Color = MaterialTheme.colorScheme.primary,
) {
    val context = LocalContext.current

    // Resolved once per source: the bundled asset never changes, and an imported file's name is
    // unique per pick, so it is safe to key on.
    // `playableFile` rather than `loadingAnimationFile`: a `.lottie` import is a folder, and the
    // player needs the `animation.json` inside it rather than the container.
    val path = remember(fileName) {
        fileName?.let { name ->
            SaleAnimationStore.playableFile(context, name, forLoadingScreen = true)
                ?.absolutePath
        }
    }

    val composition: LottieCompositionResult = rememberLottieComposition(
        spec = if (fileName == null) {
            // The bundled animation is an *assets* animation, not a `res/raw` one, because it has
            // twelve bitmap layers: a raw resource is a single file with no way to reach its
            // images, so they would have to be base64-inlined into the JSON. `LottieCompositionSpec.Asset`
            // resolves them out of `assets/crummy_stock/i/` instead, which is the folder the
            // document's own `"u"` base path points at.
            LottieCompositionSpec.Asset(BundledAsset)
        } else {
            // An empty path resolves to nothing, which `composition.value` reports as null and
            // sends us down the fallback branch below.
            LottieCompositionSpec.File(path.orEmpty())
        }
    )

    val loaded = composition.value
    val fileMissing = fileName != null && path == null

    if (loaded == null) {
        // Two very different situations share this branch, and conflating them is what caused the
        // cart icon to flash for a frame on every launch:
        //
        //  - **Still parsing** (or not started yet). Drawing the icon here means the startup screen
        //    opens on the fallback and swaps to the animation a frame later — a visible flicker on
        //    every single launch.
        //  - **Genuinely unavailable** — the file is gone or unreadable. Then the icon is the right
        //    answer, because a blank startup screen reads as a crash.
        //
        // So the icon is drawn *only* when we know the animation will never arrive. While it is
        // still on its way, the box keeps its full size but paints nothing, which is
        // indistinguishable from the animation's first transparent frame.
        if (fileMissing) {
            Icon(
                imageVector = LoadingIcon.CART.icon,
                contentDescription = null,
                tint = fallbackColor,
                modifier = modifier.size(size),
            )
        } else {
            // Reserves the layout so nothing shifts when the composition resolves.
            Spacer(modifier = modifier.size(size))
        }
        return
    }

    val motion = LocalMotionSpeed.current
    LottieAnimation(
        composition = loaded,
        modifier = modifier.size(size),
        // Animations off holds the first frame rather than looping, matching how the rest of the
        // app treats that setting.
        isPlaying = motion != MotionSpeed.OFF,
        // Guarded against OFF's 0 multiplier, which would divide the frame rate by zero.
        speed = if (motion.multiplier <= 0f) 1f else motion.multiplier,
        iterations = LottieConstants.IterateForever,
    )
}

/**
 * The bundled animation's document, relative to the assets root.
 *
 * The images it references sit in `assets/crummy_stock/i/`, which the document names as its base
 * path. Both are copied from the `CrummyStock.lottie` bundle and are keyed by file name inside the
 * Lottie cache, so keeping the names stable keeps the decrypt-and-parse work to once per process.
 */
private const val BundledAsset = "crummy_stock.json"
