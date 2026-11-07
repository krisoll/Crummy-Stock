package com.example.stockapplication.data

/**
 * Settings read out of a backup file, ready to be merged into the local store.
 *
 * **Every field is nullable, and that is the point.** A backup written by an older build has no
 * `textSizeId`; one written by a newer build may have fields this build has never heard of. Null
 * means *the file did not say*, which is different from *the file said the default*: the first
 * leaves the local value alone, the second actively overwrites it. Collapsing the two would mean
 * importing an old backup silently resets half the app to defaults.
 *
 * The importer has already validated and clamped everything here (see [JsonImport.readSettings]),
 * so [SettingsRepository.applyImported] can write these through without re-checking ranges — it
 * still normalises the id-based ones, because a hand-edited file can name an enum that no longer
 * exists.
 *
 * Deliberately **absent**: the export folder URI, and the picked startup image / beep / sale-sound
 * file names. All four name something that only exists on the exporting device — a SAF grant and
 * three copies in app-private storage — so restoring them would leave the app pointing at nothing.
 * They stay in the file under `settings.deviceSpecific` and are skipped on read.
 */
data class ImportedSettings(
    // ---- Shop ---------------------------------------------------------------
    val taxRateBasisPoints: Int? = null,
    val cardFeeRateBasisPoints: Int? = null,
    val cardFeeFixedCents: Long? = null,
    val showMoneyDecimals: Boolean? = null,
    /**
     * Currency id, e.g. "eur". Exported and restored with the other money settings: it describes
     * what the stored amounts mean, so a backup that omitted it would restore prices into the
     * wrong unit.
     */
    val currencyId: String? = null,

    // ---- Language -----------------------------------------------------------
    val languageTag: String? = null,

    // ---- Appearance ---------------------------------------------------------
    val fontFamilyId: String? = null,
    val textSizeId: String? = null,
    val motionSpeedId: String? = null,
    val useAppBackgroundColor: Boolean? = null,
    val backgroundColorArgb: Long? = null,
    val useAppButtonColor: Boolean? = null,
    val buttonColorArgb: Long? = null,
    val useAppPanelColor: Boolean? = null,
    val panelColorArgb: Long? = null,
    val useAppTextColor: Boolean? = null,
    val textColorArgb: Long? = null,
    val useAppHighlightColor: Boolean? = null,
    val highlightColorArgb: Long? = null,
    /** Which built-in or saved theme the exported colours came from, when they match one. */
    val activeThemeId: String? = null,
    /** The exporting device's saved custom themes, or null when the file did not carry them. */
    val savedThemes: List<ColorTheme>? = null,

    // ---- Startup screen -----------------------------------------------------
    val showLoadingScreen: Boolean? = null,
    val loadingIconId: String? = null,
    val loadingSpinId: String? = null,
    val loadingCircleId: String? = null,
    val loadingImageSizeDp: Int? = null,
    val loadingCircleSizeDp: Int? = null,

    // ---- Scanner ------------------------------------------------------------
    val scannerBeepEnabled: Boolean? = null,
    val scannerBeepId: String? = null,
    val saleSoundEnabled: Boolean? = null,
    val saleSoundId: String? = null,
    val saleAnimationId: String? = null,
    val saleAnimationSizeDp: Int? = null,
    val scanDelayMillis: Int? = null,
    val productChangeDelayMillis: Int? = null,
    val showScanPopup: Boolean? = null,
    val scanVibrationEnabled: Boolean? = null,
    val scanVibrationId: String? = null,
    val scanVibrationStrength: Int? = null,
) {
    /**
     * True when the file carried nothing this build can apply.
     *
     * Used by the import dialog to decide whether to offer the restore-settings switch at all: a
     * switch that would change nothing is worse than no switch.
     */
    val isEmpty: Boolean
        get() = this == ImportedSettings()

    /**
     * True when the file carried any appearance value.
     *
     * The Wi-Fi import applies this subset automatically: a desktop theme is an appearance
     * choice, while the shop/scanner settings belong to the phone and must not be overwritten
     * just because a backup was pushed from a computer.
     */
    val hasAppearance: Boolean
        get() = fontFamilyId != null ||
            textSizeId != null ||
            motionSpeedId != null ||
            useAppBackgroundColor != null ||
            backgroundColorArgb != null ||
            useAppButtonColor != null ||
            buttonColorArgb != null ||
            useAppPanelColor != null ||
            panelColorArgb != null ||
            useAppTextColor != null ||
            textColorArgb != null ||
            useAppHighlightColor != null ||
            highlightColorArgb != null ||
            !savedThemes.isNullOrEmpty()
}
