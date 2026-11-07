package com.example.stockapplication.ui.screens.personalize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.ArgbColor
import com.example.stockapplication.data.BuiltInThemes
import com.example.stockapplication.data.ColorTheme
import com.example.stockapplication.data.DefaultTextArgb

/**
 * The theme picker shown at the top of the colour page: built-in palettes, then the user's saved
 * ones, then a way to save the current four colours as a new theme.
 *
 * ## Applying is immediate
 *
 * Unlike the pickers below it, choosing a theme does **not** wait for Apply. A theme is a finished
 * palette rather than a colour being dialled in, so the "preview, then confirm" flow that exists to
 * keep the HSV pickers smooth has nothing to protect here — it would just be a second tap.
 *
 * @param activeBackground etc. the colours currently in effect, so the picker can mark which theme
 *   is "in use" and can save exactly what the user is looking at.
 */
@Composable
internal fun ThemePicker(
    builtInThemes: List<ColorTheme>,
    savedThemes: List<ColorTheme>,
    activeBackground: ArgbColor?,
    activeButton: ArgbColor?,
    activePanel: ArgbColor?,
    activeHighlight: ArgbColor?,
    /** The ink currently in effect, so a saved theme carries a complete palette. */
    activeText: ArgbColor?,
    onApply: (ColorTheme) -> Unit,
    onSave: (
        name: String,
        background: ArgbColor,
        button: ArgbColor,
        panel: ArgbColor,
        highlight: ArgbColor,
        text: ArgbColor,
    ) -> Unit,
    onDelete: (String) -> Unit,
) {
    // A theme is "active" when all four of its channels match what is in effect. Comparing the
    // colours rather than remembering a chosen id means the marker stays honest if the user edits
    // one channel by hand afterwards — the theme is no longer exactly in use, and stops claiming
    // to be.
    fun isActive(theme: ColorTheme): Boolean =
        theme.backgroundArgb == activeBackground &&
            theme.buttonArgb == activeButton &&
            theme.panelArgb == activePanel &&
            theme.highlightArgb == activeHighlight

    var naming by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ColorTheme?>(null) }

    PersonalizeSection(
        title = stringResource(R.string.settings_themes),
        description = stringResource(R.string.settings_themes_description),
    ) {
        FieldLabel(stringResource(R.string.settings_themes_builtin))
        ThemeGrid(
            themes = builtInThemes,
            isActive = ::isActive,
            onApply = onApply,
            onDelete = null,
        )

        FieldLabel(stringResource(R.string.settings_themes_saved))
        if (savedThemes.isEmpty()) {
            HintText(stringResource(R.string.settings_themes_empty))
        } else {
            ThemeGrid(
                themes = savedThemes,
                isActive = ::isActive,
                onApply = onApply,
                onDelete = { pendingDelete = it },
            )
        }

        TextButton(
            onClick = { naming = true },
            // Saving needs four real colours to save. Before any customisation they are technically
            // null, so the defaults are what would be captured — which is a legitimate thing to
            // save (it makes the default look into a named theme), so the button stays enabled.
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_themes_save))
        }
        HintText(stringResource(R.string.settings_themes_save_hint))
    }

    if (naming) {
        NameThemeDialog(
            onDismiss = { naming = false },
            onConfirm = { name ->
                naming = false
                // The ink is captured too. Omitting it used to produce a saved theme whose
                // `textArgb` was null, and applying such a theme is what left the previous
                // theme's colour in place — the Midnight-then-disappearing-text bug. A theme the
                // user saves is a complete palette for the same reason a built-in one is.
                onSave(
                    name,
                    activeBackground ?: BuiltInThemes.Default.backgroundArgb,
                    activeButton ?: BuiltInThemes.Default.buttonArgb,
                    activePanel ?: BuiltInThemes.Default.panelArgb,
                    activeHighlight ?: BuiltInThemes.Default.highlightArgb,
                    activeText ?: DefaultTextArgb,
                )
            },
        )
    }

    pendingDelete?.let { theme ->
        DeleteThemeDialog(
            themeName = theme.name.orEmpty(),
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                onDelete(theme.id)
            },
        )
    }
}

/** The themes laid out in rows of two, one card each. */
@Composable
private fun ThemeGrid(
    themes: List<ColorTheme>,
    isActive: (ColorTheme) -> Boolean,
    onApply: (ColorTheme) -> Unit,
    onDelete: ((ColorTheme) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // `chunked(2)` rather than a LazyVerticalGrid: these pages are already inside a
        // `verticalScroll` Column, and nesting a lazy list in a scrollable parent is what Compose
        // forbids. The list is at most a handful of themes, so there is nothing to virtualise.
        themes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { theme ->
                    ThemeCard(
                        theme = theme,
                        active = isActive(theme),
                        onApply = { onApply(theme) },
                        onDelete = onDelete?.let { { it(theme) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a lone theme on the last row the same width as the ones above it.
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/**
 * One theme: a row of its four swatches over its name, with a tick when it is the active palette.
 *
 * The swatches are the whole point — a name alone ("Sage") says nothing about whether the user
 * wants it, and a full-size preview would be far too tall for a list.
 */
@Composable
private fun ThemeCard(
    theme: ColorTheme,
    active: Boolean,
    onApply: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val name = theme.name ?: theme.nameRes?.let { stringResource(it) } ?: theme.id
    val activeLabel = stringResource(R.string.settings_themes_active)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onApply)
            .semantics {
                // Spoken as "Sage, in use" so the tick is not a purely visual cue.
                contentDescription = if (active) "$name, $activeLabel" else name
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (active) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                theme.swatches.forEach { argb ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(argb.toULong() shl 32))
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (active) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.settings_themes_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Asks for a name before saving the current colours. */
@Composable
private fun NameThemeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_themes_save)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_themes_name_label)) },
                placeholder = { Text(stringResource(R.string.settings_themes_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                // A nameless theme would show up in the list as a blank card, which is
                // indistinguishable from a broken one.
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Confirms deleting a saved theme, naming it so the wrong card cannot be removed by accident. */
@Composable
private fun DeleteThemeDialog(
    themeName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_themes_delete_confirm_title)) },
        text = {
            Text(stringResource(R.string.settings_themes_delete_confirm_body, themeName))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_themes_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
