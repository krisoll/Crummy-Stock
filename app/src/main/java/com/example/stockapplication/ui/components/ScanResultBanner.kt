package com.example.stockapplication.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.Product
import com.example.stockapplication.ui.theme.AppMotion

/**
 * What the scanner last read, as a value the UI can render.
 *
 * A sealed type rather than "name + image + failed flag" because the two outcomes are genuinely
 * different things: a known product shows its photo and current stock, an unknown code shows the
 * code itself. A half-filled trio would force the banner to guess which one it is looking at.
 */
sealed interface ScanResult {

    /** A code that matched a product in the catalogue. */
    data class ProductFound(
        val product: Product,
        /** How many units of it are now in the cart, or 0 when the scan did not touch a cart. */
        val countInCart: Int = 0,
    ) : ScanResult

    /** A code the catalogue does not know. */
    data class UnknownCode(val code: String) : ScanResult
}

/**
 * The banner that drops over the top of the camera after a scan.
 *
 * Placement is the point: it sits at the **top** of the preview, well away from the reticle in the
 * middle, so it never covers the label the cashier is aiming at. It slides down — rather than
 * fading in — so it reads as "the app just did something", then leaves the same way.
 *
 * @param result what to show, or null to hide it.
 * @param remembered the last non-null result. The caller keeps it so the exit animation still has
 *   something to draw: `result` is already null by then, and an empty card would pop.
 */
@Composable
fun ScanResultBanner(
    result: ScanResult?,
    remembered: ScanResult?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shown = result ?: remembered

    AnimatedVisibility(
        visible = enabled && result != null,
        // Separate specs for the fade (Float) and the slide (IntOffset): they animate different
        // types, so they cannot share one spec even though both come from AppMotion.
        enter = fadeIn(animationSpec = AppMotion.enterFade()) +
            slideInVertically(
                animationSpec = tween(
                    durationMillis = AppMotion.screenEnterMillis(),
                    easing = AppMotion.Emphasised,
                ),
                initialOffsetY = { -it },
            ),
        exit = fadeOut(animationSpec = AppMotion.exitFade()) +
            slideOutVertically(
                animationSpec = tween(durationMillis = AppMotion.screenExitMillis()),
                targetOffsetY = { -it },
            ),
        modifier = modifier,
    ) {
        if (shown != null) ScanResultCard(shown)
    }
}

@Composable
private fun ScanResultCard(result: ScanResult) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        // primaryContainer rather than a hard-coded "success" green: the palette is fully
        // user-customisable, and a fixed green would fight every custom theme.
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The photo is what a cashier recognises fastest, so it leads the row.
            ProductThumbnail(
                fileName = (result as? ScanResult.ProductFound)?.product?.imagePath,
                size = 48.dp,
                cornerRadius = 10.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when (result) {
                    is ScanResult.ProductFound -> {
                        Text(
                            result.product.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            // The cart count is the useful second line when there is a cart;
                            // otherwise the stock on hand is.
                            if (result.countInCart > 0) {
                                stringResource(R.string.scanner_banner_in_cart, result.countInCart)
                            } else {
                                stringResource(R.string.inventory_quantity, result.product.quantity)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    is ScanResult.UnknownCode -> {
                        Text(
                            stringResource(R.string.scanner_banner_unknown),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.scanner_banner_unknown_barcode, result.code),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
