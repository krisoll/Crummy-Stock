package com.example.stockapplication.ui.screens.personalize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.MaxScanVibrationStrength
import com.example.stockapplication.data.SaleSound
import com.example.stockapplication.data.ScanBeep
import com.example.stockapplication.data.ScanVibration
import com.example.stockapplication.data.ScanVibrationStore
import kotlin.math.roundToInt

/**
 * Everything the app plays or buzzes: the scan beep, the sale chime and the scan vibration.
 *
 * Every control here is a switch, a tap or a short slider, and every one of them is judged by
 * trying it — which is why the sound lists play themselves when tapped instead of waiting for the
 * user to leave the page and scan something, and why the vibration list buzzes on tap.
 *
 * The scan **delay** deliberately does not live here. It is a timing value, not feedback: it
 * decides how long the scanner ignores a code it has already read, and nothing about it is judged
 * by ear. It sits on the Config tab with the other day-to-day settings.
 */
@Composable
internal fun SoundsPage(
    settings: AppSettings,
    onBack: () -> Unit,
    onToggleBeep: (Boolean) -> Unit,
    onPickBeep: (ScanBeep) -> Unit,
    onPreviewBeep: (ScanBeep) -> Unit,
    /** Opens the system audio picker. */
    onOpenBeepPicker: () -> Unit,
    onClearBeepFile: () -> Unit,
    onToggleSaleSound: (Boolean) -> Unit,
    onSaleSoundChange: (SaleSound) -> Unit,
    onPreviewSaleSound: (SaleSound) -> Unit,
    onOpenSaleSoundPicker: () -> Unit,
    onClearSaleSoundFile: () -> Unit,
    onResetScanner: () -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onPickVibration: (ScanVibration) -> Unit,
    onPreviewVibration: (ScanVibration, Int) -> Unit,
    onVibrationStrengthChange: (Int) -> Unit,
    /** False on hardware with no vibration motor, so the section can say so. */
    vibrationSupported: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PersonalizeHeader(
            title = stringResource(R.string.settings_sounds),
            description = stringResource(R.string.settings_sounds_description),
            backLabel = stringResource(R.string.settings_back_to_personalize),
            onBack = onBack,
        )

        // ---- Scan beep -------------------------------------------------------
        PersonalizeSection(
            title = stringResource(R.string.settings_scan_beep),
            description = stringResource(R.string.settings_scan_beep_description)
        ) {
            ToggleRow(
                label = stringResource(R.string.settings_scan_beep_enabled),
                checked = settings.scannerBeepEnabled,
                onCheckedChange = onToggleBeep
            )

            // Collapsed rather than merely disabled: a muted scanner does not need a tone list,
            // and leaving it visible would invite the user to "fix" a beep that is switched off.
            if (settings.scannerBeepEnabled) {
                val pickedSound = settings.scannerBeepFileName?.isNotBlank() == true
                ScanBeep.entries.forEach { beep ->
                    ChoiceRow(
                        label = stringResource(beep.labelRes),
                        // A picked file overrides every tone, so none of them can look selected
                        // while the user is hearing something else.
                        selected = !pickedSound && settings.scannerBeep == beep,
                        // Selecting and hearing are deliberately one action: a list of named
                        // sounds the user has to leave the page to audition is unusable.
                        onSelect = {
                            onPickBeep(beep)
                            onPreviewBeep(beep)
                        },
                    )
                }

                HorizontalDivider()
                PersonalSoundPicker(
                    label = stringResource(R.string.settings_scan_beep_own),
                    hasPickedFile = pickedSound,
                    // The store keeps only its own copy of the file, so there is no name to show:
                    // the confirmation is the fact that one is in use, not which.
                    activeLabel = stringResource(R.string.settings_scan_beep_custom_active),
                    chooseLabel = stringResource(
                        if (pickedSound) {
                            R.string.settings_scan_beep_change
                        } else {
                            R.string.settings_scan_beep_choose
                        }
                    ),
                    removeLabel = stringResource(R.string.settings_scan_beep_remove),
                    onChoose = onOpenBeepPicker,
                    onRemove = onClearBeepFile,
                )
            }
        }

        // ---- Sale sound ------------------------------------------------------
        // Same shape as the beep above, and deliberately a copy rather than one shared composable
        // for the list: the two have different defaults and one is off out of the box, so
        // generalising the whole block would make either one harder to change on its own. The
        // file-picking half *is* shared, because that part really is identical.
        PersonalizeSection(
            title = stringResource(R.string.settings_sale_sound),
            description = stringResource(R.string.settings_sale_sound_description)
        ) {
            ToggleRow(
                label = stringResource(R.string.settings_sale_sound_enabled),
                checked = settings.saleSoundEnabled,
                onCheckedChange = onToggleSaleSound
            )

            // Collapsed for the same reason as the beep list: an off switch makes a sound list
            // look broken rather than merely disabled.
            if (settings.saleSoundEnabled) {
                val pickedSaleSound = settings.saleSoundFileName?.isNotBlank() == true
                SaleSound.entries.forEach { sound ->
                    ChoiceRow(
                        label = stringResource(sound.labelRes),
                        selected = !pickedSaleSound &&
                            SaleSound.fromId(settings.saleSoundId) == sound,
                        // Selecting and hearing as one action, exactly like the beep list.
                        onSelect = {
                            onSaleSoundChange(sound)
                            onPreviewSaleSound(sound)
                        },
                    )
                }

                HorizontalDivider()
                PersonalSoundPicker(
                    label = stringResource(R.string.settings_sale_sound_own),
                    hasPickedFile = pickedSaleSound,
                    activeLabel = stringResource(R.string.settings_sale_sound_custom_active),
                    chooseLabel = stringResource(
                        if (pickedSaleSound) {
                            R.string.settings_sale_sound_change
                        } else {
                            R.string.settings_sale_sound_choose
                        }
                    ),
                    removeLabel = stringResource(R.string.settings_sale_sound_remove),
                    onChoose = onOpenSaleSoundPicker,
                    onRemove = onClearSaleSoundFile,
                )
            }
        }

        // The scan popup is deliberately *not* here: it is a working aid rather than feedback
        // tuned by ear, and it now lives in Config → Scanner next to the scan delay.

        // ---- Vibration -------------------------------------------------------
        // Separate from the beep rather than a second "feedback" switch: a cashier may well want
        // the buzz and no beep (a quiet shop) or the beep and no buzz (a phone on a stand).
        PersonalizeSection(
            title = stringResource(R.string.settings_scan_vibration),
            description = stringResource(R.string.settings_scan_vibration_description)
        ) {
            ToggleRow(
                label = stringResource(R.string.settings_scan_vibration_enabled),
                checked = settings.scanVibrationEnabled,
                onCheckedChange = onToggleVibration
            )

            if (!vibrationSupported) {
                // Said plainly rather than by hiding the control: an owner comparing settings
                // across two devices should be able to see *why* this one does nothing.
                Text(
                    stringResource(R.string.settings_scan_vibration_unsupported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (settings.scanVibrationEnabled) {
                FieldLabel(stringResource(R.string.settings_scan_vibration_pattern))

                ScanVibration.entries.forEach { pattern ->
                    ChoiceRow(
                        label = stringResource(pattern.labelRes),
                        selected = settings.scanVibration == pattern,
                        // Select and feel in one action, for the same reason the tones work that
                        // way: a pattern the user has to leave the page to feel is impossible to
                        // choose between.
                        onSelect = {
                            onPickVibration(pattern)
                            if (vibrationSupported) {
                                onPreviewVibration(
                                    pattern,
                                    settings.effectiveScanVibrationStrength
                                )
                            }
                        },
                        // The timings, as a duration: this is the one property that tells
                        // "Double tap" from "Heartbeat" without feeling both.
                        supporting = stringResource(
                            R.string.settings_scan_vibration_duration,
                            formatVibrationDuration(pattern)
                        ),
                    )
                }

                HorizontalDivider()
                VibrationStrengthSlider(
                    value = settings.effectiveScanVibrationStrength,
                    onValueChange = onVibrationStrengthChange,
                )
            }
        }

        TextButton(onClick = onResetScanner) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
            Text(stringResource(R.string.settings_scanner_reset))
        }
    }
}

/**
 * The "your own file" half of a sound list: a caption, what is currently in use, and the choose /
 * remove buttons.
 *
 * Shared by the beep and the sale chime because this part genuinely is the same in both — same two
 * buttons, same layout, same rule that "remove" only exists when there is something to remove.
 * Only the strings and the callbacks differ.
 */
@Composable
private fun PersonalSoundPicker(
    label: String,
    hasPickedFile: Boolean,
    activeLabel: String,
    chooseLabel: String,
    removeLabel: String,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
) {
    FieldLabel(label)
    if (hasPickedFile) {
        Text(
            activeLabel,
            style = MaterialTheme.typography.bodySmall,
            // A status label is text, so it follows the font colour.
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onChoose, modifier = Modifier.weight(1f)) {
            Text(chooseLabel)
        }
        if (hasPickedFile) {
            OutlinedButton(onClick = onRemove) {
                Text(removeLabel)
            }
        }
    }
}

/**
 * How hard the phone buzzes, as a slider from 1 to [MaxScanVibrationStrength].
 *
 * Starts at 1, not 0: amplitude 0 means "hardware default", which is not a strength the user can
 * feel or predict, so it is not offered as a choice.
 */
@Composable
private fun VibrationStrengthSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.settings_scan_vibration_strength),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                stringResource(
                    R.string.settings_scan_vibration_strength_value,
                    vibrationPercent(value)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HintText(stringResource(R.string.settings_scan_vibration_strength_description))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 1f..MaxScanVibrationStrength.toFloat(),
            modifier = Modifier.fillMaxWidth(),
            colors = appSliderColors(),
        )
    }
}

/**
 * Formats a vibration pattern's total on-time in milliseconds, e.g. 35 -> "35 ms".
 *
 * Deliberately in milliseconds rather than seconds: every pattern is under a quarter of a second,
 * and "0.04 s" tells the user less than "40 ms".
 */
internal fun formatVibrationDuration(pattern: ScanVibration): String =
    "${ScanVibrationStore.onTimeMillis(pattern)} ms"

/** Amplitude as a percentage, rounded, for the strength readout. */
internal fun vibrationPercent(amplitude: Int): Int {
    val clamped = amplitude.coerceIn(0, MaxScanVibrationStrength)
    return (clamped * 100.0 / MaxScanVibrationStrength).roundToInt()
}
