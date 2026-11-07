package com.example.stockapplication.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.BackupContents
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Confirms a restore before anything is written.
 *
 * The dialog exists because importing is destructive and irreversible: it overwrites the stock
 * levels, products, orders and sales on this device. So it spells out **what is in the file**
 * (counts and export date, so the user can tell a week-old backup from yesterday's) and **what
 * will be lost**, rather than asking a bare "are you sure".
 *
 * Rows the reader had to drop are surfaced here too, before the write, not only afterwards in a
 * snackbar: that is the last moment the user can decide the file is not worth importing.
 *
 * Backups written by this build also carry the app's **settings**, and restoring those is a
 * separate decision from restoring stock: products are the shop, settings are this phone. So the
 * dialog offers a second switch, off by default, that only appears when the file actually has
 * settings to apply.
 *
 * @param fileName shown so the user can confirm they picked the file they thought they did.
 * @param restoreSettings whether the switch is on. Ignored when [canRestoreSettings] is false.
 */
@Composable
fun ImportBackupDialog(
    contents: BackupContents,
    fileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    canRestoreSettings: Boolean = contents.settings != null,
    restoreSettings: Boolean = false,
    onRestoreSettingsChange: (Boolean) -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_confirm_title)) },
        text = {
            Column(
                // Capped and scrollable: a file with dozens of skipped rows would otherwise
                // produce a dialog taller than the screen with the buttons pushed off it.
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.import_confirm_file),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(fileName, style = MaterialTheme.typography.bodyMedium)
                }

                Text(
                    stringResource(
                        R.string.import_confirm_summary,
                        contents.categories.size,
                        contents.products.size,
                        contents.orders.size,
                        contents.lines.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (contents.hasSupplies) {
                    Text(
                        stringResource(
                            R.string.import_confirm_summary_supplies,
                            contents.providers.size,
                            contents.materials.size,
                            contents.materialEntries.size,
                            contents.materialEntryLines.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (contents.promos.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.import_confirm_summary_promos,
                            contents.promos.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    exportedLabel(contents.exportedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    stringResource(R.string.import_confirm_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )

                if (contents.notes.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.import_confirm_warning_partial,
                            contents.notes.take(MaxListedSkips).joinToString("; ")
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // ---- Settings --------------------------------------------------
                // Only when the file has any: a switch that would change nothing is worse than
                // no switch at all, and it would make an old backup look like it lost a feature.
                if (canRestoreSettings) {
                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRestoreSettingsChange(!restoreSettings) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.import_confirm_restore_settings),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = restoreSettings,
                            onCheckedChange = onRestoreSettingsChange
                        )
                    }
                    Text(
                        stringResource(R.string.import_confirm_restore_settings_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Says plainly what is *not* coming back, so nobody is surprised that the
                    // export folder or a custom beep did not follow the backup.
                    Text(
                        stringResource(R.string.import_confirm_restore_settings_device),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.import_confirm_restore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.import_confirm_cancel))
            }
        }
    )
}

/** How many skipped-row reasons the dialog lists before it stops enumerating them. */
private const val MaxListedSkips = 5

/**
 * Formats the backup's timestamp in the user's locale and timezone.
 *
 * Presentation only — the import itself uses the raw epoch millis. A missing or implausible
 * timestamp (0, or a file hand-edited with no `exportedAt`) shows as "unknown" rather than as
 * 1 January 1970, which would look like a bug rather than a missing field.
 */
@Composable
private fun exportedLabel(exportedAt: Long?): String {
    if (exportedAt == null || exportedAt <= 0L) {
        return stringResource(R.string.import_confirm_exported_unknown)
    }
    val locale = LocalConfiguration.current.locales.let { if (it.isEmpty) Locale.ROOT else it[0] }
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    return stringResource(R.string.import_confirm_exported, formatter.format(Date(exportedAt)))
}
