package com.example.stockapplication.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.stockapplication.BuildConfig
import com.example.stockapplication.R
import com.example.stockapplication.data.AppLanguage
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.Currency
import com.example.stockapplication.data.currencySymbol
import androidx.compose.material3.Slider
import com.example.stockapplication.data.MaxCardFeeFixedCents
import com.example.stockapplication.data.MaxProductChangeDelayMillis
import com.example.stockapplication.data.MaxScanDelayMillis
import com.example.stockapplication.data.MinProductChangeDelayMillis
import com.example.stockapplication.data.MinScanDelayMillis
import com.example.stockapplication.data.localizedExportFolderLabel
import com.example.stockapplication.ui.screens.personalize.appSliderColors
import kotlin.math.roundToInt

/**
 * The Config tab: the day-to-day settings — where exports go, the language, and the tax/card-fee
 * rates.
 *
 * Everything that only changes how the app *looks* now lives behind the Personalize button below
 * (see [PersonalizeScreen]). Splitting them keeps this page short and scannable, and keeps the
 * gesture-heavy colour pickers off a page that also holds text fields and a folder picker.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onFolderPicked: (String) -> Unit,
    onClearFolder: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onTaxRateChange: (Int) -> Unit,
    onCardFeeRateChange: (Int) -> Unit,
    onCardFeeFixedChange: (Long) -> Unit,
    /** Whether money labels show their cents. */
    showMoneyDecimals: Boolean,
    onShowMoneyDecimalsChange: (Boolean) -> Unit,
    /** Which currency symbol every amount is shown in. */
    currency: Currency,
    onCurrencyChange: (Currency) -> Unit,
    /** How long the scanner ignores the **same** code, in milliseconds. */
    onScanDelayChange: (Int) -> Unit,
    /** How long the scanner waits before accepting a **different** code, in milliseconds. */
    onProductChangeDelayChange: (Int) -> Unit = {},
    /** Whether the camera shows a banner with the product it just read. */
    showScanPopup: Boolean,
    onShowScanPopupChange: (Boolean) -> Unit,
    /** Whether the optional product-tagging feature is shown; off by default. */
    tagsEnabled: Boolean,
    onTagsEnabledChange: (Boolean) -> Unit,
    onOpenPersonalize: () -> Unit,
    onExportJsonBackup: () -> Unit,
    onShareJsonBackup: () -> Unit,
    /** Receives the URI of a backup the user picked, for reading and confirming. */
    onImportJsonBackup: (android.net.Uri) -> Unit,
    /** Whether the next backup should include promo rules. */
    includePromosInBackup: Boolean,
    onIncludePromosChange: (Boolean) -> Unit,
    /** Whether the next backup should bundle product photos in a ZIP. */
    includeImagesInBackup: Boolean,
    onIncludeImagesChange: (Boolean) -> Unit,
    isExporting: Boolean,
    /** Opens the Wi-Fi transfer screen. */
    onOpenTransfer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) onFolderPicked(uri.toString())
    }

    // OpenDocument with "*/*" is *not* unfiltered. It still uses the document UI, which lists
    // only types it recognises as documents — and it excludes archives, so a `.zip` backup never
    // appeared in the list even with no type filter at all. That made the ZIP export effectively
    // write-only: it could be saved but never read back.
    //
    // GetContent with "*/*" opens the general file picker, which shows every file including ZIPs.
    // The lost persistable grant does not matter: the backup is read once, immediately, and the
    // temporary grant lasts for the life of this activity.
    val backupPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onImportJsonBackup(uri)
    }

    val context = LocalContext.current
    val defaultFolderLabel = stringResource(R.string.settings_default_folder)
    val exportFolderLabel = settings.localizedExportFolderLabel(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            // The app is edge-to-edge, so this page has to keep its own content clear of the
            // status bar. Nothing else in the tree pads for it any more.
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        // 12 dp between sections rather than 16: the cards already have their own edges, so the
        // gap is a separator, not breathing room.
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Export location -------------------------------------------------
        SettingsSection(
            title = stringResource(R.string.settings_export_location),
            description = stringResource(R.string.settings_export_location_description)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        exportFolderLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (settings.hasExportFolder) {
                            stringResource(R.string.settings_export_folder_selected)
                        } else {
                            stringResource(R.string.settings_export_folder_default, defaultFolderLabel)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_choose_folder))
                }
                OutlinedButton(
                    onClick = onClearFolder,
                    enabled = settings.hasExportFolder,
                    colors = outlinedButtonColors(),
                ) {
                    Text(stringResource(R.string.action_reset))
                }
            }

            Text(
                stringResource(R.string.settings_export_fallback, defaultFolderLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Personalize -----------------------------------------------------
        // Deliberately near the top: it is the one entry point to every appearance setting, and
        // burying it under the rates would make it look like an afterthought.
        PersonalizeEntry(onClick = onOpenPersonalize)

        // ---- Rates -----------------------------------------------------------
        SettingsSection(
            title = stringResource(R.string.settings_rates),
            description = stringResource(R.string.settings_rates_description)
        ) {
            RateField(
                label = stringResource(R.string.settings_tax_rate),
                initialBasisPoints = settings.taxRateBasisPoints,
                onRateChange = onTaxRateChange
            )
            RateField(
                label = stringResource(R.string.settings_card_fee_rate),
                initialBasisPoints = settings.cardFeeRateBasisPoints,
                onRateChange = onCardFeeRateChange
            )
            MoneyField(
                label = stringResource(R.string.settings_card_fee_fixed),
                initialCents = settings.cardFeeFixedCents,
                onCentsChange = onCardFeeFixedChange
            )
            Text(
                stringResource(R.string.settings_rates_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Currency --------------------------------------------------------
        // Above Display, because the two answer the same question — "how do amounts read" — and
        // the symbol is the bigger part of that answer. Kept off Personalize: this is not an
        // appearance choice, it changes what the numbers mean on a receipt.
        SettingsSection(
            title = stringResource(R.string.settings_currency),
            description = stringResource(R.string.settings_currency_description)
        ) {
            Currency.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCurrencyChange(option) }
                        // 4 dp: eight rows at 6 dp was most of the gap in this section.
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = currency == option,
                        onClick = { onCurrencyChange(option) }
                    )
                    val name = stringResource(option.labelRes)
                    Text(
                        // The symbol alone: it is what the user is choosing, and it is what will
                        // appear on every receipt. `.trim()` because `CHF ` carries a trailing
                        // space for the amount prefix, which would show as a gap here.
                        option.symbol.trim(),
                        style = MaterialTheme.typography.titleMedium,
                        // The spoken name is kept for screen readers only: "euro sign" is far more
                        // useful read aloud than a bare "€", but the list itself stays a row of
                        // symbols.
                        modifier = Modifier.semantics { contentDescription = name }
                    )
                }
            }
            Text(
                // Spelled out because the consequence is not obvious and cannot be undone by
                // switching back: the symbol relabels the stored numbers, it does not convert.
                stringResource(R.string.settings_currency_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Display ---------------------------------------------------------
        // Display-only: no stored price changes, so it is safe next to the rates rather than being
        // fenced off behind a confirmation.
        SettingsSection(
            title = stringResource(R.string.settings_display),
            description = stringResource(R.string.settings_display_description)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onShowMoneyDecimalsChange(!showMoneyDecimals) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.settings_show_decimals),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        // Spells out both results rather than describing the change: seeing
                        // "12.99" next to "13" is unambiguous, and it also warns the user that
                        // the shorter form rounds rather than truncates.
                        stringResource(R.string.settings_show_decimals_example),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showMoneyDecimals,
                    onCheckedChange = onShowMoneyDecimalsChange
                )
            }
        }

        // ---- Tags ------------------------------------------------------------
        // Last of the day-to-day sections, and off by default: this is an opt-in feature for a
        // shop that needs a second classification axis, not something to explain to everyone.
        // The switch hides the *UI* — see AppSettings.tagsEnabled — so nothing here can lose data.
        SettingsSection(
            title = stringResource(R.string.settings_tags_section),
            description = stringResource(R.string.settings_tags_section_description)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTagsEnabledChange(!tagsEnabled) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.settings_tags_enabled),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = tagsEnabled,
                    onCheckedChange = onTagsEnabledChange
                )
            }
            Text(
                stringResource(R.string.settings_tags_enabled_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Scanner ---------------------------------------------------------
        // How the camera behaves while it is open, kept apart from the Personalize page because
        // none of it is about appearance: the delay stops one label being counted dozens of times
        // a second, and the popup is a working aid rather than a style. The *feedback* (beep,
        // buzz) stays behind Personalize → Sounds, where it is tuned by ear and by feel.
        SettingsSection(
            title = stringResource(R.string.settings_scan_section),
            description = stringResource(R.string.settings_scan_section_description)
        ) {
            // Same product first, then a different one: that is the order the cashier experiences
            // them in, and it reads as "what happens to the label in my hand" before "what happens
            // when I move on".
            ScanDelaySlider(
                value = settings.scanDelayMillis,
                onValueChange = onScanDelayChange,
            )

            HorizontalDivider()

            ProductChangeDelaySlider(
                value = settings.productChangeDelayMillis,
                onValueChange = onProductChangeDelayChange,
            )

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onShowScanPopupChange(!showScanPopup) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.settings_scan_popup),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.settings_scan_popup_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showScanPopup,
                    onCheckedChange = onShowScanPopupChange
                )
            }
        }

        // ---- Language --------------------------------------------------------
        SettingsSection(
            title = stringResource(R.string.settings_language),
            description = stringResource(R.string.settings_language_description)
        ) {
            AppLanguage.entries.forEach { language ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLanguageChange(language) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = AppLanguage.fromTag(settings.languageTag) == language,
                        onClick = { onLanguageChange(language) }
                    )
                    Text(
                        stringResource(language.labelRes),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        HorizontalDivider()

        // ---- Full data backup ------------------------------------------------
        SettingsSection(
            title = stringResource(R.string.settings_backup_title),
            description = stringResource(R.string.settings_backup_description)
        ) {
            // ---- Include promos toggle --------------------------------------
            // Sits directly above the export buttons because it changes what they write. It is
            // deliberately off by default: restoring a backup that carries promos switches those
            // discounts back on, which is not what most people expect from "restore".
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.settings_backup_include_promos),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = includePromosInBackup,
                    onCheckedChange = onIncludePromosChange,
                    enabled = !isExporting
                )
            }
            Text(
                stringResource(R.string.settings_backup_include_promos_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ---- Include photos toggle --------------------------------------
            // Directly under the promos toggle and above the buttons, because like that one it
            // changes what the buttons write. Turning it on switches the file from .json to .zip,
            // which the note below spells out rather than leaving the user to discover it.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onIncludeImagesChange(!includeImagesInBackup) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.settings_backup_include_images),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = includeImagesInBackup,
                    onCheckedChange = onIncludeImagesChange,
                    enabled = !isExporting
                )
            }
            Text(
                stringResource(
                    if (includeImagesInBackup) {
                        R.string.settings_backup_include_images_note_on
                    } else {
                        R.string.settings_backup_include_images_note_off
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onExportJsonBackup,
                    enabled = !isExporting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_backup_export))
                }
                OutlinedButton(
                    onClick = onShareJsonBackup,
                    enabled = !isExporting,
                    colors = outlinedButtonColors(),
                ) {
                    Text(stringResource(R.string.settings_backup_share))
                }
            }
            // Its own row rather than a third button above: importing is the opposite direction
            // from the two exports, and grouping it with them invites the mis-tap that wipes the
            // database. Separated, it reads as "the other way round".
            OutlinedButton(
                onClick = {
                    // Unfiltered on purpose: the importer routes on the file name (.zip vs
                    // .json), so the picker must not hide either kind. See `backupPicker` above.
                    backupPicker.launch("*/*")
                },
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedButtonColors(),
            ) {
                Text(stringResource(R.string.settings_backup_import))
            }
            Text(
                stringResource(R.string.settings_backup_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ---- Wi-Fi transfer ------------------------------------------------
            // Inside the backup card rather than beside it: it is the same job as the three
            // buttons above by another route, and a separate card made it read as an unrelated
            // feature. Set apart by a divider instead, which groups it without ranking it.
            HorizontalDivider()
            Text(
                stringResource(R.string.settings_transfer_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.settings_transfer_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onOpenTransfer,
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedButtonColors(),
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_transfer_open))
            }
        }

        HorizontalDivider()
        Text(
            stringResource(R.string.settings_storage_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Separator above the social block: it is the one part of this page that has nothing to
        // do with running a shop, and the divider is what visually ends the settings.
        HorizontalDivider()

        SocialLinks()

        // Below the social links, so it is the very last thing on the page. It is reference
        // information rather than a setting, which is why it is quiet text and not a card.
        VersionFooter()
    }
}

/**
 * The app version, at the very foot of the Config page.
 *
 * Read from `BuildConfig` rather than hardcoded, so it cannot drift from the `versionName` in
 * `build.gradle.kts` — the number a user reads out in a bug report has to be the number that was
 * actually shipped.
 *
 * The version *code* is included alongside the name because Play requires it to increase on every
 * upload, and "1.0.0 (1)" is what identifies a build unambiguously.
 */
@Composable
private fun VersionFooter(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            R.string.settings_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * The developer's social accounts, at the very foot of the Config page.
 *
 * Last on purpose: it is the one block on this page that has nothing to do with running a shop, so
 * it belongs after every setting rather than competing with them for attention.
 *
 * The Instagram and X icons are vector drawables rather than Material icons because
 * `material-icons-extended` ships neither brand mark — its closest matches are `Facebook` and
 * `Whatsapp`, which would be simply wrong here. The website glyph is a plain globe: a personal
 * site has no brand mark to draw, and a globe is the universally understood stand-in.
 */
@Composable
private fun SocialLinks(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            stringResource(R.string.settings_social_title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SocialLink(
                iconRes = R.drawable.ic_globe,
                labelRes = R.string.settings_social_website,
                url = WebsiteUrl,
                // No @handle to read out: the shared label ends in "(@krisoll)", which would be
                // nonsense applied to a website.
                description = stringResource(R.string.settings_social_open_website),
                onOpen = { openUrl(context, it) }
            )
            SocialLink(
                iconRes = R.drawable.ic_instagram,
                labelRes = R.string.settings_social_instagram,
                url = InstagramUrl,
                onOpen = { openUrl(context, it) }
            )
            SocialLink(
                iconRes = R.drawable.ic_x,
                labelRes = R.string.settings_social_x,
                url = XUrl,
                onOpen = { openUrl(context, it) }
            )
        }
    }
}

/**
 * One tappable social account: the brand glyph with its network name underneath.
 *
 * The name is drawn rather than hidden in the content description alone: two icons side by side
 * are recognisable but not *unambiguous*, and a mis-tap here leaves the app for a browser.
 */
@Composable
private fun SocialLink(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    url: String,
    onOpen: (String) -> Unit,
    /** TalkBack label. Defaults to the "Open %1$s (@krisoll)" form; the website overrides it. */
    description: String? = null,
) {
    val label = stringResource(labelRes)
    val spoken = description ?: stringResource(R.string.settings_social_open, label)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpen(url) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            // The glyph alone is a 24 dp tap target; the padding above brings the whole cell to a
            // comfortable size without having to wrap it in a bigger invisible box.
            .semantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Opens [url] outside the app.
 *
 * Wrapped in `runCatching` for the same reason as the export helpers: a device can have no browser
 * and no app that claims the scheme, and an unhandled `ActivityNotFoundException` would take the
 * whole Config page down over a decorative link.
 */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
        )
    }
}

/** The developer's website. Hard-coded rather than a setting: it is not user data. */
private const val WebsiteUrl = "https://krisoll.carrd.co/"

/** The developer's Instagram profile. Hard-coded rather than a setting: it is not user data. */
private const val InstagramUrl = "https://www.instagram.com/krisoll/"

/** The developer's X profile. */
private const val XUrl = "https://x.com/krisoll"

/**
 * Step either scanner-delay slider moves in, in milliseconds.
 *
 * Each slider derives its `steps` from this, so the span between its bounds must be a whole
 * multiple of it or the last stop will not land on the maximum. A test asserts that rather than
 * leaving it to be noticed in the UI.
 */
private const val DelayStepMillis = 100

/**
 * How long the scanner waits before accepting the **same** code again.
 *
 * The long of the two scanner delays: it exists so a label still in frame is not accepted over and
 * over. Lives on the Config tab rather than under Personalize because it is a timing rule, not an
 * appearance choice, and the cashier sets it once for their camera rather than tuning it by feel.
 */
@Composable
private fun ScanDelaySlider(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    DelaySlider(
        title = stringResource(R.string.settings_scan_delay),
        description = stringResource(R.string.settings_scan_delay_description),
        value = value,
        range = MinScanDelayMillis..MaxScanDelayMillis,
        onValueChange = onValueChange,
    )
}

/**
 * How long the scanner waits before accepting a **different** code.
 *
 * The short one. Moving to the next product is the cashier working, not a misfire, so this slider
 * has its own, much lower bounds rather than sharing the other one's.
 */
@Composable
private fun ProductChangeDelaySlider(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    DelaySlider(
        title = stringResource(R.string.settings_product_change_delay),
        description = stringResource(R.string.settings_product_change_delay_description),
        value = value,
        range = MinProductChangeDelayMillis..MaxProductChangeDelayMillis,
        onValueChange = onValueChange,
    )
}

/**
 * A labelled millisecond slider.
 *
 * Shared by both scanner delays so they behave identically — the only differences are the bounds
 * and the copy, both passed in. Duplicating the composable per setting would let one drift in step
 * size or formatting without the other noticing.
 */
@Composable
private fun DelaySlider(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                // Seconds, not milliseconds: the user is choosing how long to wait, and
                // "2.5 s" is a duration while "2500" is a measurement.
                stringResource(R.string.settings_scan_delay_value, formatScanDelay(value)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            // Whole steps, coarse enough that the label does not flicker through values the user
            // did not choose during a drag. The span doubles as the step, so each slider offers a
            // comparable number of stops whatever its range.
            steps = ((range.last - range.first) / DelayStepMillis) - 1,
            modifier = Modifier.fillMaxWidth(),
            // Material's default slider is `primary` on `primary` here — the button colour on top
            // of itself — so the track and thumb vanished into the card. See `appSliderColors`.
            colors = appSliderColors(),
        )
    }
}

/**
 * Formats a delay as seconds with at most one decimal, e.g. 1000 -> "1", 850 -> "0.9".
 *
 * Trailing zeros are dropped on purpose: "1 s" reads as a setting, "1.0 s" reads as a measurement.
 */
internal fun formatScanDelay(millis: Int): String {
    val tenths = (millis / 100.0).roundToInt()
    return if (tenths % 10 == 0) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}

/**
 * The row that opens [PersonalizeScreen].
 *
 * Styled like a settings section rather than a list item so it reads as part of the same page, with
 * a chevron to say "there is more behind this".
 */
@Composable
private fun PersonalizeEntry(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.settings_personalize_open),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    stringResource(R.string.settings_personalize_open_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * A percentage input that commits on every valid keystroke.
 *
 * Rates have no drag gesture, so there is nothing to debounce — unlike the colour pickers, writing
 * straight through to DataStore is cheap here.
 */
@Composable
private fun RateField(
    label: String,
    initialBasisPoints: Int,
    onRateChange: (Int) -> Unit,
) {
    // Keyed on the label, not on `initialBasisPoints` — see the note in [MoneyField]. Committing on
    // every keystroke changes `initialBasisPoints`, and keying on it reset the buffer mid-edit.
    var text by remember(label) { mutableStateOf(formatRateText(initialBasisPoints)) }

    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            // Allow digits plus a single decimal separator, at most 2 decimals.
            val cleaned = typed.replace(',', '.')
                .filter { it.isDigit() || it == '.' }
                .let { candidate ->
                    val firstDot = candidate.indexOf('.')
                    if (firstDot < 0) candidate
                    else candidate.substring(0, firstDot + 1) +
                        candidate.substring(firstDot + 1).filter { it.isDigit() }.take(2)
                }
            text = cleaned
            val basisPoints = parseRateText(cleaned)
            if (basisPoints != null) onRateChange(basisPoints)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * A money input for a flat fee, committed on every valid keystroke.
 *
 * Mirrors [RateField] rather than reusing it: money has no percent sign and is capped at
 * [MaxCardFeeFixedCents] instead of 100%, so the two would diverge anyway. The prefix is the
 * generic currency symbol — the app is locale-switchable and has no per-currency setting.
 *
 * [maxCents] exists because this field was written for the card fee and its cap leaked into every
 * other money input that reused it. A promo's fixed discount is not a card fee, and clamping it to
 * [MaxCardFeeFixedCents] silently turned large discounts into 1000.00.
 */
@Composable
private fun MoneyField(
    label: String,
    initialCents: Long,
    onCentsChange: (Long) -> Unit,
    maxCents: Long = MaxCardFeeFixedCents,
) {
    // Keyed on the *label*, not on `initialCents`. The value commits on every keystroke, which
    // recomposes with a new `initialCents` — keying on it would reset the buffer mid-edit and make
    // the field fight the user (typing "30" produced "0.03" then "0.030"). The label identifies
    // the field for as long as it exists, which is exactly the lifetime this state should have.
    var text by remember(label) { mutableStateOf(formatMoneyText(initialCents)) }

    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            val cleaned = sanitizeMoneyInput(typed)
            text = cleaned
            val cents = parseMoneyText(cleaned, maxCents)
            if (cents != null) onCentsChange(cents)
        },
        label = { Text(label) },
        singleLine = true,
        // Trimmed: `CHF` carries a trailing space so it reads correctly in front of an amount,
        // but as a field prefix the padding is applied by the layout instead.
        prefix = { Text(currencySymbol.trim()) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Keeps a money field to digits plus at most one decimal separator with 2 decimals — the same
 * shape [formatMoneyText] emits, so the field round-trips.
 */
internal fun sanitizeMoneyInput(typed: String): String {
    val digitsAndDot = typed.replace(',', '.').filter { it.isDigit() || it == '.' }
    val firstDot = digitsAndDot.indexOf('.')
    return if (firstDot < 0) {
        digitsAndDot
    } else {
        digitsAndDot.substring(0, firstDot + 1) +
            digitsAndDot.substring(firstDot + 1).filter { it.isDigit() }.take(2)
    }
}

/** Renders cents as the text shown in the money field, e.g. 50 -> "0.50". */
internal fun formatMoneyText(cents: Long): String {
    val safe = cents.coerceAtLeast(0L)
    return "${safe / 100}.${(safe % 100).toString().padStart(2, '0')}"
}

/** Parses "0.50" into 50 cents, clamped to [maxCents]. Null for incomplete input. */
internal fun parseMoneyText(text: String, maxCents: Long = MaxCardFeeFixedCents): Long? {
    if (text.isBlank() || text == ".") return 0L
    val amount = text.toDoubleOrNull() ?: return null
    if (amount < 0) return null
    return Math.round(amount * 100.0).coerceIn(0L, maxCents)
}

/** Renders basis points as the text shown in the rate field, e.g. 275 -> "2.75". */
internal fun formatRateText(basisPoints: Int): String {
    val whole = basisPoints / 100
    val fraction = basisPoints % 100
    return when {
        fraction == 0 -> whole.toString()
        fraction % 10 == 0 -> "$whole.${fraction / 10}"
        else -> "$whole.${fraction.toString().padStart(2, '0')}"
    }
}

/** Parses "2.75" into 275 basis points. Returns null for input that isn't a number yet. */
internal fun parseRateText(text: String): Int? {
    if (text.isBlank() || text == ".") return 0
    val percent = text.toDoubleOrNull() ?: return null
    if (percent < 0) return null
    return Math.round(percent * 100.0).toInt().coerceIn(0, 10_000)
}

/**
 * A titled card holding a group of settings.
 *
 * The metrics here are tuned for **density**: this page is a long list of sections and every dp of
 * padding is multiplied by how many of them the user scrolls past. Specifically:
 *
 * - 14 dp inner padding with 10 dp between rows, down from 16/12. The card edge still reads as a
 *   container without a wide empty margin inside it.
 * - the description drops to `bodySmall` in a dimmer colour and is capped at two lines. These are
 *   explanations, not content: at full length several of them wrapped to three lines on a phone
 *   and pushed the actual controls off screen.
 *
 * The card, the radius and the title style are unchanged, so the page still looks like itself — 
 * this is a tightening, not a redesign.
 */
@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        // `surfaceContainer` rather than `surfaceVariant`: the latter is the panel colour, and the
        // default palette sets panel and button to the same green — so a section card and the
        // filled button inside it were the same object visually. `surfaceContainer` is the
        // palette's card-on-page tone, which keeps the two apart.
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            content()
        }
    }
}

/**
 * Colours for an [OutlinedButton] that has to stay visible on a coloured panel.
 *
 * Material draws an outlined button's border from `outline` and its label from `primary`. Both were
 * wrong for this app: `outline` kept the stock grey (washed out to `#989898` over a saturated
 * panel) and `primary` is the *button fill* colour, which the default palette sets to the same
 * green as the panel behind the card.
 *
 * Both now come from the theme's text colour at usable strength, so the control reads as ink on a
 * card rather than as a differently-shaded rectangle.
 */
@Composable
private fun outlinedButtonColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    contentColor = MaterialTheme.colorScheme.onSurface,
    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
)
