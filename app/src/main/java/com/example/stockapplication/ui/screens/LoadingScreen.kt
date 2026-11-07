package com.example.stockapplication.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.DefaultLoadingCircleSizeDp
import com.example.stockapplication.data.DefaultLoadingImageSizeDp
import com.example.stockapplication.data.LoadingCircle
import com.example.stockapplication.data.LoadingImage
import com.example.stockapplication.data.LoadingSpin
import com.example.stockapplication.ui.components.SpinningImage
import com.example.stockapplication.ui.theme.AppMotion
import com.example.stockapplication.ui.theme.LocalMotionSpeed

/**
 * The app's busy screen: the user's chosen image over an optional disc, with a caption.
 *
 * Used for two things, which is why it is parameterised rather than duplicated:
 *
 *  - **Startup**, full-bleed on the app background, captioned with the app name.
 *  - **Working**, as a scrim over the current screen while a slow export runs. There the
 *    background is translucent, the disc is opaque (so it reads against whatever is behind it),
 *    and the caption says what is being worked on.
 *
 * Deliberately dumb — it never decides *when* to disappear. Callers own that: [com.example.stockapplication.ui.StockApp]
 * keeps the startup variant up until the catalogue has loaded, and the export variant until the
 * write finishes. That keeps every timer in one place instead of split across a screen and here.
 *
 * The image is either a built-in icon or a picture the user picked from the device; both come from
 * settings and move with the chosen [spin].
 *
 * @param scrim when true, the surface is translucent and drops a shadow behind the disc, so the
 *   content underneath stays dimly visible — a modal "working" look rather than a new screen.
 * @param label overrides the caption under the image. Null uses the app name (startup behaviour).
 * @param color tints all three layers (image, disc, caption) except in scrim mode, where the disc
 *   and caption follow the theme so they stay legible over arbitrary content.
 * @param imageSize width/height of the image, in dp. Callers pass the user's configured size.
 * @param circleSize width/height of the disc, in dp.
 */
@Composable
fun LoadingScreen(
    image: LoadingImage,
    spin: LoadingSpin,
    circle: LoadingCircle = LoadingCircle.SOFT,
    modifier: Modifier = Modifier,
    scrim: Boolean = false,
    label: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    imageSize: Dp = DefaultLoadingImageSizeDp.dp,
    circleSize: Dp = DefaultLoadingCircleSizeDp.dp,
) {
    val speed = LocalMotionSpeed.current
    // Collapse the entrance entirely when motion is off; otherwise fade the disc in behind the image.
    var visible by remember { mutableStateOf(speed.multiplier == 0f) }
    LaunchedEffect(speed) { visible = true }

    val caption = label ?: stringResource(R.string.app_name)
    val contentDescription = label ?: stringResource(R.string.loading_screen_skip)

    Surface(
        modifier = modifier
            .fillMaxSize()
            .semantics { this.contentDescription = contentDescription },
        // A scrim lets the frozen UI show through; the startup screen covers it completely.
        color = if (scrim) {
            MaterialTheme.colorScheme.background.copy(alpha = 0.82f)
        } else {
            MaterialTheme.colorScheme.background
        },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(speed.scale(320))) +
                        scaleIn(
                            animationSpec = tween(speed.scale(420), easing = AppMotion.Emphasised),
                            initialScale = 0.6f,
                        ),
                ) {
                    // No drop shadow in either mode. `Modifier.shadow` assumes an opaque shape on a
                    // solid background; over the translucent scrim it bled a grey halo out from
                    // the circle and darkened the disc itself, since the shadow is drawn under an
                    // alpha-blended fill. The scrim already separates the overlay from the content
                    // behind it, so the shadow was doing no work — only adding dirt.
                    //
                    // The disc is drawn by SpinningImage itself, so choosing "none" collapses the
                    // framing entirely rather than leaving an empty 168dp hole.
                    SpinningImage(
                        image = image,
                        spin = spin,
                        size = imageSize,
                        circle = circle,
                        circleSize = circleSize,
                        color = color,
                        // Each motion carries its own ideal cycle length, stretched or compressed
                        // by the user's Animations setting — the same expression the Config
                        // preview uses, so the two cannot drift.
                        periodMillis = spin.periodMillisAt(speed),
                    )
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(speed.scale(500))),
                ) {
                    Text(
                        text = caption,
                        style = if (scrim) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}
