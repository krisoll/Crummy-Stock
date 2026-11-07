package com.example.stockapplication.ui.screens.personalize

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.ArgbColor
import com.example.stockapplication.data.BuiltInThemes
import com.example.stockapplication.data.ColorChannel
import com.example.stockapplication.data.ColorTheme
import com.example.stockapplication.data.DefaultTextArgb
import com.example.stockapplication.ui.components.HsvColorPicker
import com.example.stockapplication.ui.theme.AppMotion
import com.example.stockapplication.ui.viewmodel.ColorDraft

/**
 * The colour page: the five configurable channels, the live preview, and the confirmation panel
 * that turns a preview into a committed change.
 *
 * Keeping the preview and the pending bar next to the pickers is deliberate — they only mean
 * anything as an answer to what is above them, and stranding them on the landing page would leave
 * a pending change with nowhere to be seen, applied or thrown away.
 */
@Composable
internal fun ColourSettingsPage(
    settings: AppSettings,
    draft: ColorDraft,
    onBack: () -> Unit,
    onToggleBackground: (Boolean) -> Unit,
    onPreviewBackgroundColor: (ArgbColor) -> Unit,
    onToggleButton: (Boolean) -> Unit,
    onPreviewButtonColor: (ArgbColor) -> Unit,
    onTogglePanel: (Boolean) -> Unit,
    onPreviewPanelColor: (ArgbColor) -> Unit,
    onToggleText: (Boolean) -> Unit,
    onPreviewTextColor: (ArgbColor) -> Unit,
    onToggleHighlight: (Boolean) -> Unit,
    onPreviewHighlightColor: (ArgbColor) -> Unit,
    onApplyColors: () -> Unit,
    onDiscardColors: () -> Unit,
    onResetAppearance: () -> Unit,
    /** Applies a whole palette at once, bypassing the draft. */
    onApplyTheme: (ColorTheme) -> Unit,
    /** Saves the colours currently in effect under a new name. */
    onSaveTheme: (String, ArgbColor, ArgbColor, ArgbColor, ArgbColor, ArgbColor) -> Unit,
    onDeleteTheme: (String) -> Unit,
) {
    val hasPendingChanges = draft.hasPending

    // What the pickers are showing *right now*: a pending draft value if there is one, otherwise
    // the committed setting. The theme picker marks an "in use" theme from this, so a theme ticks
    // as active the moment it is applied, and stops ticking as soon as one channel is edited by
    // hand below — which is exactly what "not exactly this theme" means.
    val shownBackground = draft.backgroundArgb ?: settings.backgroundColorArgb
    val shownButton = draft.buttonArgb ?: settings.buttonColorArgb
    val shownPanel = draft.panelArgb ?: settings.panelColorArgb
    val shownHighlight = draft.highlightArgb ?: settings.highlightColorArgb
    // Text is read the same way, so saving a theme captures the ink the user is actually looking
    // at. The stored value can be null on an install that never picked one; the theme needs a real
    // colour, so the app's default ink stands in — which is what is on screen in that case anyway.
    val shownText = draft.textArgb ?: settings.textColorArgb ?: DefaultTextArgb

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PersonalizeHeader(
            title = stringResource(R.string.settings_colours),
            description = stringResource(R.string.settings_colours_description),
            backLabel = stringResource(R.string.settings_back_to_personalize),
            onBack = onBack,
        )

        // ---- Themes ----------------------------------------------------------
        // Above the individual pickers because it is the coarser control: a theme sets all four
        // channels, so it reads as "start here" and the pickers below as fine-tuning.
        ThemePicker(
            builtInThemes = BuiltInThemes.all,
            savedThemes = settings.savedThemes,
            activeBackground = shownBackground,
            activeButton = shownButton,
            activePanel = shownPanel,
            activeHighlight = shownHighlight,
            activeText = shownText,
            onApply = onApplyTheme,
            onSave = onSaveTheme,
            onDelete = onDeleteTheme,
        )

        // ---- Background colour -----------------------------------------------
        PersonalizeSection(
            title = stringResource(R.string.settings_background_colour),
            description = stringResource(R.string.settings_background_colour_description)
        ) {
            // `isCustomColorEnabled`, not the raw `use*` flag. Applying any theme — including the
            // default one — switches every override on, because a theme is one atomic write of a
            // complete palette. Reading the flag directly therefore reported a freshly applied
            // built-in theme as "customised" on every channel. The toggle is on only when the
            // colour actually differs from the app default.
            ToggleRow(
                label = stringResource(R.string.settings_custom_background),
                checked = settings.isCustomColorEnabled(ColorChannel.BACKGROUND),
                onCheckedChange = onToggleBackground
            )
            ColourPickerSlot(
                enabled = settings.useAppBackgroundColor,
                color = draft.backgroundArgb ?: settings.backgroundColorArgb,
                onColorChange = onPreviewBackgroundColor
            )
        }

        // ---- Button colour ---------------------------------------------------
        PersonalizeSection(
            title = stringResource(R.string.settings_button_colour),
            description = stringResource(R.string.settings_button_colour_description)
        ) {
            ToggleRow(
                label = stringResource(R.string.settings_custom_button_colour),
                checked = settings.isCustomColorEnabled(ColorChannel.BUTTON),
                onCheckedChange = onToggleButton
            )
            ColourPickerSlot(
                enabled = settings.useAppButtonColor,
                color = draft.buttonArgb ?: settings.buttonColorArgb,
                onColorChange = onPreviewButtonColor
            )
        }

        // ---- Panel colour ----------------------------------------------------
        PersonalizeSection(
            title = stringResource(R.string.settings_panel_colour),
            description = stringResource(R.string.settings_panel_colour_description)
        ) {
            ToggleRow(
                label = stringResource(R.string.settings_custom_panel_colour),
                checked = settings.isCustomColorEnabled(ColorChannel.PANEL),
                onCheckedChange = onTogglePanel
            )
            ColourPickerSlot(
                enabled = settings.useAppPanelColor,
                color = draft.panelArgb ?: settings.panelColorArgb,
                onColorChange = onPreviewPanelColor
            )
        }

        // ---- Text colour -----------------------------------------------------
        PersonalizeSection(
            title = stringResource(R.string.settings_text_colour),
            description = stringResource(R.string.settings_text_colour_description)
        ) {
            ToggleRow(
                label = stringResource(R.string.settings_custom_text_colour),
                checked = settings.isCustomColorEnabled(ColorChannel.TEXT),
                onCheckedChange = onToggleText
            )
            ColourPickerSlot(
                enabled = settings.useAppTextColor,
                color = draft.textArgb ?: settings.textColorArgb,
                onColorChange = onPreviewTextColor
            )
        }

        // ---- Highlight colour ------------------------------------------------
        PersonalizeSection(
            title = stringResource(R.string.settings_highlight_colour),
            description = stringResource(R.string.settings_highlight_colour_description)
        ) {
            ToggleRow(
                label = stringResource(R.string.settings_custom_highlight_colour),
                checked = settings.isCustomColorEnabled(ColorChannel.HIGHLIGHT),
                onCheckedChange = onToggleHighlight
            )
            ColourPickerSlot(
                enabled = settings.useAppHighlightColor,
                color = draft.highlightArgb ?: settings.highlightColorArgb,
                onColorChange = onPreviewHighlightColor
            )
        }

        // ---- Live preview ----------------------------------------------------
        // Directly under the pickers, and above the pending bar: the point of the preview is to
        // answer "what will these colours look like" before the user taps Apply.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.settings_preview),
                    style = MaterialTheme.typography.labelMedium
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.settings_preview_product),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.settings_preview_quantity),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}) {
                        Text(stringResource(R.string.settings_preview_button))
                    }
                    OutlinedButton(onClick = {}) {
                        Text(stringResource(R.string.settings_preview_outlined_button))
                    }
                }
            }
        }

        if (hasPendingChanges) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_pending_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.settings_pending_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDiscardColors,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settings_discard))
                        }
                        Button(
                            onClick = onApplyColors,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settings_apply_colours))
                        }
                    }
                }
            }
        }

        // `useAppTextColor` belongs in this list. It was missing, which hid the only escape hatch
        // in precisely the state it exists for: a stale text override left every label invisible
        // and the user had no visible control to undo it.
        if (settings.useAppBackgroundColor || settings.useAppButtonColor ||
            settings.useAppTextColor || settings.useAppHighlightColor || hasPendingChanges
        ) {
            TextButton(
                onClick = onResetAppearance,
                // `onSurface`, not the default `primary`. A TextButton inherits its content colour
                // from `primary`, which is the *button fill* colour — so with the default palette
                // these buttons drew green text on a green panel and were effectively invisible.
                colors = textButtonColors(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                Text(stringResource(R.string.settings_reset_colours))
            }
        }

        TextButton(
            onClick = onBack,
            colors = textButtonColors(),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Text("  " + stringResource(R.string.settings_back_to_personalize))
        }
    }
}

/**
 * Colours for a [TextButton] that must read as *text* rather than as a button.
 *
 * Material tints a `TextButton`'s label and icon with `primary` — the same colour used to **fill**
 * real buttons. That is fine when the page is neutral, but the app's panels are the user's panel
 * colour, and the default palette sets the button and panel to the *same* green. A flat button
 * painted in the fill colour on a fill-coloured card is invisible.
 *
 * These buttons carry navigation and reset actions — the two places where being unable to see the
 * control is worst — so they take the text colour at full strength instead.
 */
@Composable
private fun textButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(
    contentColor = MaterialTheme.colorScheme.onSurface,
    // Disabled still has to be legible against a saturated panel, so this is the ink at reduced
    // opacity rather than Material's default (which is derived from `onSurface` anyway, but
    // stating it keeps the pair honest if the palette changes).
    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
)

/**
 * A colour picker that stays collapsed until the user asks for it.
 *
 * Each HSV picker is ~380dp tall. Expanding them all by default made the page enormous and —
 * because each picker captures its own drag gestures — awkward to scroll. They now start hidden
 * behind a single button.
 *
 * The expansion state is keyed on `enabled`, so switching a colour section off collapses it again.
 */
@Composable
private fun ColourPickerSlot(
    enabled: Boolean,
    color: ArgbColor?,
    onColorChange: (ArgbColor) -> Unit,
) {
    if (!enabled) return

    var expanded by remember(enabled) { mutableStateOf(false) }

    TextButton(onClick = { expanded = !expanded }) {
        // The chevron rotates rather than swapping icons, so the toggle reads as one object
        // changing state instead of two different buttons.
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.rotateBy(if (expanded) 180f else 0f)
        )
        Text(
            "  " + stringResource(
                if (expanded) {
                    R.string.settings_hide_colour_picker
                } else {
                    R.string.settings_show_colour_picker
                }
            )
        )
    }

    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(animationSpec = AppMotion.expand()) +
            fadeIn(animationSpec = AppMotion.enterFade()),
        exit = shrinkVertically(animationSpec = AppMotion.expand()) +
            fadeOut(animationSpec = AppMotion.exitFade()),
    ) {
        HsvColorPicker(color = color, onColorChange = onColorChange)
    }
}

/** Rotates in the draw phase, so toggling never costs a recomposition of the caller. */
private fun Modifier.rotateBy(degrees: Float): Modifier =
    graphicsLayer { rotationZ = degrees }
