package com.example.stockapplication.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.stockapplication.data.AppFontFamily
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.DefaultColors
import com.example.stockapplication.data.DefaultTextArgb
import com.example.stockapplication.data.MotionSpeed
import com.example.stockapplication.data.TextSize

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)


/**
 * The app's own palette, used whenever the user has not customised a channel.
 *
 * Built by passing the stock colours through [highlightedScheme] rather than hand-writing every
 * Material slot: that is the same function a customisation goes through, so the default look and a
 * "customised to exactly these colours" look are produced by one piece of code and cannot drift.
 *
 * The values come from [DefaultColors], which reads them from [BuiltInThemes.Default] — the same
 * theme the picker lists as "Default". Selecting that preset and never having opened the colour
 * page therefore land on identical pixels.
 *
 * Note `text = Color.Black`. The app's default panels are a saturated green (`#8CE482`), and the
 * stock Material ink (`#49454F` at 85%) has nowhere near enough contrast on it — outlined buttons,
 * switch tracks and dividers all but disappeared. Black is the app's own default ink regardless of
 * what the user later picks; a user-chosen colour still overrides it through the same channel.
 */
private val DefaultPalette = highlightedScheme(
    base = lightColorScheme(),
    background = Color(DefaultColors.background.toULong() shl 32),
    panel = Color(DefaultColors.panel.toULong() shl 32),
    button = Color(DefaultColors.button.toULong() shl 32),
    text = Color.Black,
    highlight = Color(DefaultColors.highlight.toULong() shl 32),
    darkTheme = false,
)

/**
 * Darumadrop One, bundled from Google Fonts under the SIL Open Font License.
 * Ships as a single 400-weight face. Every bundled typeface is OFL-1.1; the
 * licence texts live in assets/font-licences/ so they ship inside the APK.
 */
val DarumadropOne = FontFamily(Font(com.example.stockapplication.R.font.darumadrop_one));
val CharisilBold = FontFamily(Font(com.example.stockapplication.R.font.charissil_bold));
val CharisilRegular = FontFamily(Font(com.example.stockapplication.R.font.charissil_regular));
val Coiny = FontFamily(Font(com.example.stockapplication.R.font.coiny_regular));
val Enriqueta = FontFamily(Font(com.example.stockapplication.R.font.enriqueta_bold));
val HennyPenny = FontFamily(Font(com.example.stockapplication.R.font.hennypenny_regular));
val Lacquer = FontFamily(Font(com.example.stockapplication.R.font.lacquer_regular));
val Limelight = FontFamily(Font(com.example.stockapplication.R.font.limelight_regular));

/** Resolves the user's font choice to a Compose [FontFamily]. */
fun fontFamilyFor(settings: AppSettings): FontFamily = when (
    AppFontFamily.fromId(settings.fontFamilyId)
) {
    AppFontFamily.DEFAULT -> FontFamily.Default
    AppFontFamily.SANS -> FontFamily.SansSerif
    AppFontFamily.SERIF -> FontFamily.Serif
    AppFontFamily.MONOSPACE -> FontFamily.Monospace
    AppFontFamily.CURSIVE -> FontFamily.Cursive
    AppFontFamily.DARUMADROP -> DarumadropOne
    AppFontFamily.CHARISILBOLD -> CharisilBold
    AppFontFamily.CHARISILREGULAR -> CharisilRegular
    AppFontFamily.COINY -> Coiny
    AppFontFamily.ENRIQUETA -> Enriqueta
    AppFontFamily.HENNYPENNY -> HennyPenny
    AppFontFamily.LACQUER -> Lacquer
    AppFontFamily.LIMELIGHT -> Limelight
}

/**
 * Builds the Material 3 colour scheme, layering the user's customisations on top of either the
 * dynamic (Android 12+) or the bundled default palette.
 *
 * Each override is independent:
 * - **background** → `background` + the surface family the page itself draws on
 * - **panel** → `surfaceVariant` / `surfaceContainer*`, the cards and sections on top
 * - **button** → `primary` and everything button-like (including the "add product" FAB)
 * - **text** → `onBackground` / `onSurface` / `onSurfaceVariant`
 * - **highlight** → `secondary` / `tertiary` / `secondaryContainer`, which is what a selected
 *   filter chip, the selected navigation-bar indicator and emphasised headings all read from
 */
fun resolveColorScheme(
    settings: AppSettings,
    darkTheme: Boolean,
    dynamicColor: Boolean,
    context: android.content.Context,
): androidx.compose.material3.ColorScheme {
    // ---- Does the user own the palette right now? ----
    //
    // A theme picked from the colour page (a preset or a saved one) is an *explicit* statement
    // about the page, the panels, the buttons and the ink, and it is the same statement whether
    // the phone is in light or dark mode. The system theme must not get a vote once it is made.
    val ownsPalette = settings.useAppBackgroundColor ||
        settings.useAppPanelColor ||
        settings.useAppButtonColor

    // The app's own palette is mode-independent for the same reason: `BuiltInThemes.Default` names
    // a *light* page (#F5F5F5) and a black ink to go with it. Substituting Material's dark scheme
    // when the system flipped to dark mode threw all of it away — the screen went near-black while
    // the ink stayed black (it always fell back to [DefaultTextArgb]), so every label vanished.
    // That reads as "the colours got messed up", because they did: the app has one look.
    //
    // So [DefaultPalette] is now the base *whenever the user has not installed a palette*, in both
    // modes. `darkTheme` only picks the starting point for someone who has customised a single
    // channel and left the rest alone — it never replaces the app's own look, and never touches the
    // finished look of someone who has customised all of them.
    val base = when {
        // The app's own palette, not Material's stock purple: this is what a fresh install shows
        // and what "Reset to the default colours" returns to.
        !ownsPalette -> DefaultPalette

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> DefaultPalette
    }

    val background = settings.backgroundColorArgb
        ?.takeIf { settings.useAppBackgroundColor }
        ?.let { Color(it.toULong() shl 32) }
    val panel = settings.panelColorArgb
        ?.takeIf { settings.useAppPanelColor }
        ?.let { Color(it.toULong() shl 32) }
    val button = settings.buttonColorArgb
        ?.takeIf { settings.useAppButtonColor }
        ?.let { Color(it.toULong() shl 32) }
    // ---- Ink ----
    //
    // Unlike the four accent channels, text is **never** left null here. A null `text` means "keep
    // whatever the base scheme's `onSurface` is", and the base scheme is not the palette the user
    // is looking at once a theme has installed its own panel — that is how a cleared text override
    // used to hand the app back an ink nobody chose.
    //
    // With no override the ink is now derived from the page the user is actually looking at rather
    // than pinned to [DefaultTextArgb]. That constant is *black*: correct for the app's default
    // light page, unreadable on any dark one — a Midnight preset, or a page the user painted dark
    // themselves. Deriving it means one predicate covers every case: an override switched off after
    // being set to white, a theme applied on a phone in dark mode, and a hand-picked near-black
    // background all get a legible ink instead of a stale one.
    val text = settings.textColorArgb
        ?.takeIf { settings.useAppTextColor }
        ?.let { Color(it.toULong() shl 32) }
        ?: (background ?: panel ?: Color(DefaultColors.background.toULong() shl 32)).legibleInk()
    val highlight = settings.highlightColorArgb
        ?.takeIf { settings.useAppHighlightColor }
        ?.let { Color(it.toULong() shl 32) }

    // "The page is dark", not "the phone is in dark mode": this only drives how far the derived
    // card tones are lifted off the panel. Passing the phone's mode lit up cards on a light page
    // whenever the system went dark.
    val darkSurfaces = (background ?: panel)?.isDark() ?: false

    return highlightedScheme(
        base = base,
        background = background,
        panel = panel,
        button = button,
        text = text,
        highlight = highlight,
        darkTheme = darkSurfaces,
    )
}

/**
 * Layers the resolved overrides onto [base]. Pure, so it can be unit-tested without a Context —
 * [resolveColorScheme] only picks the base palette and converts the stored ARGB values.
 */
fun highlightedScheme(
    base: androidx.compose.material3.ColorScheme,
    background: Color?,
    panel: Color?,
    button: Color?,
    text: Color?,
    highlight: Color?,
    darkTheme: Boolean,
): androidx.compose.material3.ColorScheme {
    var scheme = base

    if (background != null) {
        scheme = scheme.copy(
            background = background,
            surface = background,
        )
    }

    if (panel != null) {
        // `surfaceVariant` is the panel colour proper — it is what the app's section cards use.
        //
        // `surfaceContainer*` is deliberately **not** the panel colour. Those tones back cards
        // *inside* a panel, and the default palette sets panel and button to the same green. Making
        // the two identical meant a section card and the filled button sitting on it were the same
        // object visually, and every outlined control on the card had nothing to contrast against.
        // They are derived as a lighter tint of the panel instead, so a card still reads as raised
        // without importing a second colour the user never picked.
        val cardTone = lerp(panel, Color.White, if (darkTheme) 0.12f else 0.08f)
        scheme = scheme.copy(
            surfaceVariant = panel,
            surfaceContainer = cardTone,
            surfaceContainerLow = lerp(panel, Color.White, if (darkTheme) 0.06f else 0.04f),
            surfaceContainerHigh = lerp(panel, Color.White, if (darkTheme) 0.18f else 0.12f),
            surfaceContainerHighest = lerp(panel, Color.White, if (darkTheme) 0.24f else 0.16f),
            // The page surface stays on the background unless the user also set one.
            surface = background ?: panel,
        )
    } else if (background != null) {
        // No explicit panel colour: derive one so cards remain distinguishable from the page.
        val tinted = lerp(background, Color.White, if (darkTheme) 0.08f else 0.03f)
        scheme = scheme.copy(
            surfaceVariant = tinted,
            surfaceContainer = tinted,
            surfaceContainerLow = tinted,
            surfaceContainerHigh = tinted,
        )
    }

    if (button != null) {
        scheme = scheme.copy(
            primary = button,
            inversePrimary = button,
            onPrimary = button.contrastingText(),
            // `primary` alone is not enough: an ExtendedFloatingActionButton defaults to the
            // `primaryContainer` pair, so without these the "add product" FAB kept the stock
            // purple while every other button followed the user's colour.
            primaryContainer = button,
            onPrimaryContainer = button.contrastingText(),
        )
    }

    if (highlight != null) {
        // A selected FilterChip fills with `secondaryContainer` and tints its label with
        // `onSecondaryContainer`, so both are derived from the one picked colour to keep the pair
        // readable. `secondary`/`tertiary` drive the nav-bar indicator and emphasised headings.
        //
        // NOTE: the `on*` slots set here are **fills' labels**, not the app's text colour. They
        // are overwritten below whenever the user has chosen a font colour, which is deliberate —
        // see the `text` block. This block only has to supply a readable pair when no font colour
        // was picked.
        scheme = scheme.copy(
            secondary = highlight,
            tertiary = highlight,
            secondaryContainer = highlight,
            onSecondary = highlight.contrastingText(),
            onTertiary = highlight.contrastingText(),
            onSecondaryContainer = highlight.contrastingText(),
        )
    }

    // **Last**, so it wins over every accent-derived `on*` value above.
    //
    // Every `on*` slot is a *text* colour, and text is the one channel the user controls
    // separately. Letting the accent-derived values stand (as this used to) meant a green button
    // re-tinted the bold category headings, the inventory tab labels and every chip label — text
    // following the background/button colour instead of the font colour. The accents now own only
    // the *fills*; the user's colour owns every word drawn on them.
    if (text != null) {
        scheme = scheme.copy(
            onBackground = text,
            onSurface = text,
            // Full opacity, not a softened copy: the previous 0.85 alpha made the text channel
            // stop at "almost" the chosen colour, so a pure black choice rendered as grey.
            onSurfaceVariant = text,
            // Fills the app puts text on top of: buttons, the FAB, selected chips.
            onPrimary = text,
            onPrimaryContainer = text,
            onSecondary = text,
            onSecondaryContainer = text,
            onTertiary = text,
            onTertiaryContainer = text,
            // The tab bar, top app bars and the navigation suite.
            inverseOnSurface = text,
            onError = text,
            onErrorContainer = text,
            //
            // ---- Outline and plain-text controls ----
            //
            // `OutlinedButton`, `TextButton`, `Checkbox`, `RadioButton`, `Switch` and the
            // text-field outline all read `outline` / `outlineVariant` for their **borders and
            // unfilled glyphs**. Those used to keep the stock grey — invisible against a saturated
            // panel — while the filled variant next to them was accent-coloured, which is why the
            // Settings page had controls that vanished into its own background.
            //
            // They follow the *text* colour rather than the button accent for the same reason the
            // labels do: an outlined or text control draws no fill, so its whole visibility is its
            // ink. If button and panel are ever set to the same colour — which the default palette
            // does, both `#8CE482` — accent-coloured ink on an accent-coloured panel is invisible.
            //
            // `outline` is reserved for thing that carry *meaning as an edge* (text-field borders,
            // dividers), so it is the text colour at full strength; `outlineVariant` is the softer
            // one and gets most of it, which keeps dividers from shouting.
            outline = text,
            outlineVariant = text.copy(alpha = 0.38f),
            // The navigation bar's own content and the scrim behind dialogs both have to stay
            // legible over the panel colour too.
            scrim = Color.Black,
        )
    } else {
        // No font colour chosen: keep the outline family readable against whatever the user did
        // pick for the panel. Falls back to the palette's own on-surface ink, which is the colour
        // Material would have used for text on that surface anyway.
        scheme = scheme.copy(
            outline = scheme.onSurfaceVariant,
            outlineVariant = scheme.onSurfaceVariant.copy(alpha = 0.38f),
        )
    }

    return scheme
}

/** Perceived brightness of this colour, weighted for the eye rather than per channel. */
private val Color.luminance: Float get() = 0.299f * red + 0.587f * green + 0.114f * blue

/** Black or white text, whichever is readable on [this] background. */
fun Color.contrastingText(): Color {
    return if (luminance > 0.6f) Color.Black else Color.White
}

/**
 * True when this colour is dark enough that it needs light content drawn on it.
 *
 * Shares its threshold with [contrastingText] so the two cannot disagree — the ink a page counts as
 * needing is exactly the ink [legibleInk] gives it.
 */
fun Color.isDark(): Boolean = luminance <= 0.6f

/**
 * The app's ink for a page of [this] colour: black on light pages, white on dark ones.
 *
 * The default page (#F5F5F5) is far above the midpoint, so a fresh install still gets exactly
 * [DefaultTextArgb] — black — while a Midnight page gets the light ink it actually needs instead
 * of the black that used to be pinned on top of it.
 */
fun Color.legibleInk(): Color = if (isDark()) Color.White else Color.Black

/**
 * Scales every typography role by [factor], keeping the design's relative sizes intact.
 *
 * Both `fontSize` and `lineHeight` are scaled: scaling only the glyphs would leave the lines at
 * their original spacing and make large text overlap and small text swim in whitespace.
 *
 * A 1f factor returns the receiver untouched rather than a rebuilt copy, so the default text size
 * costs nothing and cannot drift from the stock Material typography.
 */
private fun Typography.scaled(factor: Float): Typography {
    if (factor == 1f) return this

    fun TextStyle.resized() = copy(
        fontSize = fontSize * factor,
        lineHeight = lineHeight * factor,
    )

    return Typography(
        displayLarge = displayLarge.resized(),
        displayMedium = displayMedium.resized(),
        displaySmall = displaySmall.resized(),
        headlineLarge = headlineLarge.resized(),
        headlineMedium = headlineMedium.resized(),
        headlineSmall = headlineSmall.resized(),
        titleLarge = titleLarge.resized(),
        titleMedium = titleMedium.resized(),
        titleSmall = titleSmall.resized(),
        bodyLarge = bodyLarge.resized(),
        bodyMedium = bodyMedium.resized(),
        bodySmall = bodySmall.resized(),
        labelLarge = labelLarge.resized(),
        labelMedium = labelMedium.resized(),
        labelSmall = labelSmall.resized(),
    )
}

/** Applies [family] to every typography role so the whole app adopts it. */
private fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.restyled() = copy(fontFamily = family)
    return Typography(
        displayLarge = displayLarge.restyled(),
        displayMedium = displayMedium.restyled(),
        displaySmall = displaySmall.restyled(),
        headlineLarge = headlineLarge.restyled(),
        headlineMedium = headlineMedium.restyled(),
        headlineSmall = headlineSmall.restyled(),
        titleLarge = titleLarge.restyled(),
        titleMedium = titleMedium.restyled(),
        titleSmall = titleSmall.restyled(),
        bodyLarge = bodyLarge.restyled(),
        bodyMedium = bodyMedium.restyled(),
        bodySmall = bodySmall.restyled(),
        labelLarge = labelLarge.restyled(),
        labelMedium = labelMedium.restyled(),
        labelSmall = labelSmall.restyled(),
    )
}

@Composable
fun StockApplicationTheme(
    settings: AppSettings = AppSettings(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic colour is **off by default**, unlike the Compose template it started from.
    //
    // Android 12+ would otherwise repaint the app in the wallpaper's palette, which quietly
    // overrides the app's own default look (and any theme picked from the colour page) on most
    // modern devices — the custom palette would appear to work on an emulator and not on the
    // user's phone. The app has a deliberate palette, so the wallpaper does not get a vote.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = resolveColorScheme(
        settings = settings,
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        context = context,
    )
    // Size first, then family: `withFamily` rebuilds every role and would drop a scale applied
    // after it. Composed in this order the two settings stay independent, so changing the size
    // keeps the chosen typeface and vice versa.
    val typography = Typography
        .scaled(TextSize.fromId(settings.textSizeId).scale)
        .let { base ->
            if (AppFontFamily.fromId(settings.fontFamilyId) == AppFontFamily.DEFAULT) {
                base
            } else {
                base.withFamily(fontFamilyFor(settings))
            }
        }

    // Publishing the speed here means every AppMotion.* helper deep in the tree sees the user's
    // choice without the settings object being threaded through every screen and component.
    val motionSpeed = remember(settings.motionSpeedId) {
        MotionSpeed.fromId(settings.motionSpeedId)
    }

    CompositionLocalProvider(LocalMotionSpeed provides motionSpeed) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
