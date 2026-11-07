package com.example.stockapplication.data

import androidx.annotation.StringRes
import com.example.stockapplication.R

/**
 * A named set of the four colour channels the app exposes — background, button, panel and
 * highlight — plus the optional text colour.
 *
 * ## Why a theme is not just a map of channel → colour
 *
 * The settings deliberately store colours as **nullable and independent** (see [AppSettings]) so
 * that "never customised" stays distinguishable from "customised to the default value". A theme,
 * by contrast, is a *complete* palette: picking one is a statement about all of its channels at
 * once, so applying a theme writes a concrete colour for every channel it carries and switches
 * each of them on.
 *
 * ## Text has a default
 *
 * [textArgb] defaults to black rather than null, and every built-in theme states its ink
 * explicitly. It used to be nullable so a preset could "leave the text colour alone", but that
 * made applying a theme depend on history: switching Midnight (white text) to Ocean left the white
 * behind, and every label on a pale page disappeared. A theme is a *complete* palette, so it is now
 * declared as one.
 */
data class ColorTheme(
    /** Stable id. Built-in ids are `builtin_*`; saved themes use `saved_<millis>`. */
    val id: String,
    /**
     * Display name. Null for built-in themes, whose name comes from [nameRes] so it follows the
     * app language; a saved theme is named by the user and keeps that exact text.
     */
    val name: String? = null,
    @param:StringRes val nameRes: Int? = null,
    val backgroundArgb: ArgbColor,
    val buttonArgb: ArgbColor,
    val panelArgb: ArgbColor,
    val highlightArgb: ArgbColor,
    /**
     * Ink for every word in the app.
     *
     * Every **built-in** theme states this explicitly. Null is still permitted for a theme loaded
     * from storage that predates the field being written, in which case applying it clears the
     * override and the palette's own ink comes back.
     */
    val textArgb: ArgbColor? = null,
) {
    /** True when this came from [BuiltInThemes] rather than the user's saved list. */
    val isBuiltIn: Boolean get() = id.startsWith(BuiltInIdPrefix)

    /** The four channels as a list, for previews and comparisons. */
    val swatches: List<ArgbColor>
        get() = listOf(backgroundArgb, buttonArgb, panelArgb, highlightArgb)

    companion object {
        const val BuiltInIdPrefix = "builtin_"
        const val SavedIdPrefix = "saved_"

        /** Builds a saved theme, naming it and giving it a unique id from [now]. */
        fun saved(
            name: String,
            backgroundArgb: ArgbColor,
            buttonArgb: ArgbColor,
            panelArgb: ArgbColor,
            highlightArgb: ArgbColor,
            textArgb: ArgbColor? = null,
            now: Long = System.currentTimeMillis(),
        ): ColorTheme = ColorTheme(
            id = SavedIdPrefix + now.toString(36),
            name = name.trim(),
            backgroundArgb = backgroundArgb,
            buttonArgb = buttonArgb,
            panelArgb = panelArgb,
            highlightArgb = highlightArgb,
            textArgb = textArgb,
        )
    }
}

/**
 * The palettes that ship with the app.
 *
 * [Default] is the app's own look and the one [DefaultColors] mirrors: the "reset to defaults"
 * path and the "Default" preset therefore land on exactly the same colours, which is what makes
 * selecting the default theme indistinguishable from never having customised anything.
 *
 * The rest are starting points rather than finished designs — each is tuned so its four channels
 * are legible together (a button that reads against its panel, a highlight that reads against
 * both).
 */
object BuiltInThemes {

    val Default = ColorTheme(
        id = ColorTheme.BuiltInIdPrefix + "default",
        nameRes = R.string.theme_default,
        // 0xFFF5F5F5
        backgroundArgb = 0xFFF5F5F5,
        // 0xFF3E9141
        buttonArgb = 0xFF3E9141,
        // The panel is a lighter green than the button, so a card reads as part of the same family
        // while still leaving room for the button and the slider track to show against it. Setting
        // both to the same value made every filled button and every slider invisible.
        panelArgb = 0xFF8CE482,
        // 0xFF507DDF
        highlightArgb = 0xFF507DDF,
        // Black, not "unset". The panel is a saturated green, so the stock Material ink has
        // nowhere near enough contrast on it and controls drawn from that ink vanish into the
        // card. This is the app's own default ink; a colour the user picks still overrides it.
        textArgb = 0xFF000000,
    )

    /** Muted naturals: oatmeal page, sage panels, deep teal accents. */
    val Sage = ColorTheme(
        id = ColorTheme.BuiltInIdPrefix + "sage",
        nameRes = R.string.theme_sage,
        backgroundArgb = 0xFFF2F0EA,
        buttonArgb = 0xFF7C9A86,
        panelArgb = 0xFFE3E8E0,
        highlightArgb = 0xFF2E7D74,
        // Every theme states its own ink. Leaving it null meant the channel depended on whatever
        // theme was applied before this one — see `SettingsRepository.applyTheme`.
        textArgb = 0xFF000000,
    )

    /** Warm and high-contrast: cream page, apricot buttons, red-orange highlight. */
    val Sunset = ColorTheme(
        id = ColorTheme.BuiltInIdPrefix + "sunset",
        nameRes = R.string.theme_sunset,
        backgroundArgb = 0xFFFFF8F0,
        buttonArgb = 0xFFF6A96B,
        panelArgb = 0xFFFDE8D7,
        highlightArgb = 0xFFD9534F,
        textArgb = 0xFF000000,
    )

    /** The dark option: near-black page, slate panels, a warm amber button. */
    val Midnight = ColorTheme(
        id = ColorTheme.BuiltInIdPrefix + "midnight",
        nameRes = R.string.theme_midnight,
        backgroundArgb = 0xFF1C1C1E,
        buttonArgb = 0xFFE0A458,
        panelArgb = 0xFF2C2C2E,
        highlightArgb = 0xFF4A9EDA,
        textArgb = 0xFFF2F2F7,
    )

    /** Cool and clean: white page, ice-blue panels, deep indigo button. */
    val Ocean = ColorTheme(
        id = ColorTheme.BuiltInIdPrefix + "ocean",
        nameRes = R.string.theme_ocean,
        backgroundArgb = 0xFFF7FAFC,
        buttonArgb = 0xFF3F5B8B,
        panelArgb = 0xFFE6EEF5,
        highlightArgb = 0xFF2B8CA6,
        textArgb = 0xFF000000,
    )

    /** Every preset, in the order the picker shows them. */
    val all: List<ColorTheme> = listOf(Default, Sage, Sunset, Ocean, Midnight)

    /** Resolves an id to a built-in theme, or null when it names a saved one. */
    fun byId(id: String?): ColorTheme? = all.firstOrNull { it.id == id }
}

/**
 * The app's stock palette, as the four ARGB values [AppSettings] falls back to.
 *
 * Kept beside [BuiltInThemes.Default] so the two cannot drift; a test asserts they match.
 */
object DefaultColors {
    val background: ArgbColor = BuiltInThemes.Default.backgroundArgb
    val button: ArgbColor = BuiltInThemes.Default.buttonArgb
    val panel: ArgbColor = BuiltInThemes.Default.panelArgb
    val highlight: ArgbColor = BuiltInThemes.Default.highlightArgb
}

/**
 * The id of the theme whose five channels match these settings, or null for a hand-mixed palette.
 *
 * The phone has never stored a "selected theme id" — the picker marks a theme active by comparing
 * the colours in effect (see `ThemePicker.isActive`). A backup needs something more portable than
 * that comparison alone: the id names the selection, and the colours beside it make the file
 * readable even in an app that has never seen this theme.
 *
 * Matching requires every accent channel to be switched on. A user who enabled only the background
 * is not "using" a theme, even if the other four values happen to equal one.
 */
fun AppSettings.activeThemeId(): String? {
    // A fresh install has no colour overrides at all, and the app still draws a definite palette:
    // BuiltInThemes.Default. Naming it makes the default export as explicit as any other choice.
    val anyOverride = useAppBackgroundColor ||
        useAppButtonColor ||
        useAppPanelColor ||
        useAppTextColor ||
        useAppHighlightColor
    if (!anyOverride) return BuiltInThemes.Default.id
    // A half-customised palette is not a theme, even if the unset channels happen to match one.
    if (!useAppBackgroundColor || !useAppButtonColor || !useAppPanelColor || !useAppHighlightColor) {
        return null
    }
    val background = backgroundColorArgb ?: return null
    val button = buttonColorArgb ?: return null
    val panel = panelColorArgb ?: return null
    val highlight = highlightColorArgb ?: return null
    val text = (if (useAppTextColor) textColorArgb else null) ?: DefaultTextArgb
    return (BuiltInThemes.all + savedThemes).firstOrNull { theme ->
        theme.backgroundArgb == background &&
            theme.buttonArgb == button &&
            theme.panelArgb == panel &&
            theme.highlightArgb == highlight &&
            (theme.textArgb ?: DefaultTextArgb) == text
    }?.id
}
