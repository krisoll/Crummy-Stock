package com.example.stockapplication.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.MinProductChangeDelayMillis
import com.example.stockapplication.data.MinScanDelayMillis
import com.example.stockapplication.data.ScanBeepStore
import com.example.stockapplication.data.ScanVibrationStore
import com.example.stockapplication.ui.scanner.QrScanner

/**
 * A modal camera scanner, used where a code is needed but there is no room for an always-on
 * preview — the product editor and the checkout cart.
 *
 * **Cooldown.** After an *accepted* scan, every further code is ignored for
 * [AppSettings.scanDelayMillis] — not just a repeat of the same one. The camera re-reads whichever
 * label is in view many times a second, so a scan is accepted at most once per cooldown regardless
 * of whether the cashier moved to a different product.
 *
 * **The beep and the buzz live here, not in the callers.** This is the only place that knows a
 * scan was *accepted* rather than merely seen, so a camera that keeps reading a label still in
 * frame cannot produce a machine-gun of beeps or a continuous rumble. The listeners below get a
 * clean callback per accepted scan.
 *
 * @param onCodeScanned called for each accepted code.
 * @param onScanResult called with the same code, for a caller that can describe what it meant.
 *   Runs after [onCodeScanned] so the caller's own state (a cart, say) has already been updated.
 * @param banner overlay drawn on top of the preview. Rendered only when the user has not turned
 *   the popup off in Personalize.
 */
@Composable
fun ScannerDialog(
    onCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit,
    settings: AppSettings = AppSettings(),
    onScanResult: (String) -> Unit = {},
    banner: @Composable (result: String?) -> Unit = {},
) {
    val context = LocalContext.current

    // The cooldown needs to know which code was accepted last, because the two delays differ:
    // re-reading the label in frame is suppressed for the long one, moving to the next product for
    // the short one.
    var lastCode by remember { mutableStateOf<String?>(null) }
    var lastScanAt by remember { mutableLongStateOf(0L) }

    // The code the banner should describe, or null when it is dismissed.
    var bannerCode by remember { mutableStateOf<String?>(null) }

    // The seam between the scanner (re-created only when the camera rebinds) and the settings,
    // which the user can change while the dialog is open. Without this the callback would keep the
    // values captured on first composition.
    val currentSettings by rememberUpdatedState(settings)
    val currentOnResult by rememberUpdatedState(onScanResult)

    // Free the decoded beep samples when the dialog closes: a SoundPool left alive for a scanner
    // that may not be reopened on this screen is a small but pointless leak.
    DisposableEffect(Unit) {
        onDispose { ScanBeepStore.release() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_scan)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    QrScanner(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        onCodeScanned = { code ->
                            val active = currentSettings
                            val now = System.currentTimeMillis()
                            // ---- Which wait applies -------------------------------------
                            //
                            // Two different jobs, so two different delays. Seeing the same code
                            // again is the camera re-reading a label still in frame — a misfire,
                            // suppressed hard by the long delay. Seeing a *different* code is the
                            // cashier moving on, which only needs to be slower than the camera's
                            // own repeat rate.
                            //
                            // A single cooldown cannot serve both: set it short and a stationary
                            // label floods the cart, set it long and working through a basket is
                            // slow for no reason.
                            val isSameCode = code == lastCode
                            val delay = if (isSameCode) {
                                active.scanDelayMillis.toLong()
                                    .coerceAtLeast(MinScanDelayMillis.toLong())
                            } else {
                                active.productChangeDelayMillis.toLong()
                                    .coerceAtLeast(MinProductChangeDelayMillis.toLong())
                            }
                            val isTooSoon = (now - lastScanAt) < delay
                            if (!isTooSoon) {
                                lastCode = code
                                lastScanAt = now
                                if (active.playsScanBeep) {
                                    ScanBeepStore.play(context, active.scannerBeepSound)
                                }
                                // Fired from the same accepted-scan branch as the beep. Haptics
                                // deliberately do NOT wait for the product lookup: the buzz is
                                // "the camera read something", and delaying it by a database
                                // round trip would make it feel laggy.
                                if (active.scanVibrationEnabled) {
                                    ScanVibrationStore.vibrate(
                                        context = context,
                                        pattern = active.scanVibration,
                                        amplitude = active.effectiveScanVibrationStrength,
                                    )
                                }
                                onCodeScanned(code)
                                currentOnResult(code)
                                bannerCode = code
                            }
                        }
                    )
                }
                // Below the preview rather than over it: a modal dialog is small, and a banner on
                // top of a 220dp preview would cover the reticle the user is aiming with. In the
                // cart this list of recent scans doubles as the "did it register?" feedback.
                if (settings.showScanPopup) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        banner(bannerCode)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
