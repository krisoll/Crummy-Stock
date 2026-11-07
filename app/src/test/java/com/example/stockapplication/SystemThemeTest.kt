package com.example.stockapplication

import androidx.compose.ui.graphics.Color
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.BuiltInThemes
import com.example.stockapplication.data.DefaultColors
import com.example.stockapplication.data.DefaultTextArgb
import com.example.stockapplication.ui.theme.legibleInk
import com.example.stockapplication.ui.theme.resolveColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins down what the *system* light/dark setting is allowed to change.
 *
 * The app has a deliberate palette, and a colour page that lets the user install another one. Both
 * are statements about the page, the panels, the buttons and the ink, and neither says anything
 * about the phone's day/night setting — so flipping that setting must not repaint them.
 *
 * The bug this guards shipped as "the colours get all messed up and you can barely see the text":
 * with the default settings no override is active, `resolveColorScheme` substituted Material's
 * `darkColorScheme` (near-black surfaces) while the ink stayed pinned to the black
 * [DefaultTextArgb], and every label disappeared into the page it was drawn on.
 */
@RunWith(RobolectricTestRunner::class)
class SystemThemeTest {

    /** Dynamic colour is off throughout, so this Context is only ever passed through. */
    private val context: android.content.Context =
        androidx.test.core.app.ApplicationProvider.getApplicationContext()

    private fun Color.argb(): Long = value.shr(32).toLong() and 0xFFFFFFFFL

    /** Perceived brightness, matching the app's own weighting. */
    private fun Color.luminance(): Double = 0.299 * red + 0.587 * green + 0.114 * blue

    private fun schemeFor(settings: AppSettings, darkTheme: Boolean) =
        resolveColorScheme(
            settings = settings,
            darkTheme = darkTheme,
            dynamicColor = false,
            context = context,
        )

    @Test
    fun theDefaultLookIsTheSameInLightAndDarkMode() {
        // Never customised anything: both modes must show the app's own palette.
        val light = schemeFor(AppSettings(), darkTheme = false)
        val dark = schemeFor(AppSettings(), darkTheme = true)

        assertEquals(light.background.argb(), dark.background.argb())
        assertEquals(light.surfaceVariant.argb(), dark.surfaceVariant.argb())
        assertEquals(light.primary.argb(), dark.primary.argb())
        assertEquals(light.onBackground.argb(), dark.onBackground.argb())
    }

    @Test
    fun theDefaultPageKeepsItsOwnInkInDarkMode() {
        // The ink has to stay on the far side of the page's brightness in *both* modes, which is
        // exactly what "you can barely see the text" was a violation of.
        val dark = schemeFor(AppSettings(), darkTheme = true)
        val page = dark.background.luminance()
        val ink = dark.onBackground.luminance()

        assertTrue(
            "ink should contrast with the page in dark mode: page=$page ink=$ink",
            if (page > 0.6) ink < 0.6 else ink > 0.6,
        )
    }

    @Test
    fun anAppliedLightThemeSurvivesDarkMode() {
        // Applying a preset writes all five channels, so the phone's mode has nothing left to say.
        val applied = AppSettings(
            useAppBackgroundColor = true,
            backgroundColorArgb = BuiltInThemes.Ocean.backgroundArgb,
            useAppButtonColor = true,
            buttonColorArgb = BuiltInThemes.Ocean.buttonArgb,
            useAppPanelColor = true,
            panelColorArgb = BuiltInThemes.Ocean.panelArgb,
            useAppHighlightColor = true,
            highlightColorArgb = BuiltInThemes.Ocean.highlightArgb,
            useAppTextColor = true,
            textColorArgb = BuiltInThemes.Ocean.textArgb,
        )

        val light = schemeFor(applied, darkTheme = false)
        val dark = schemeFor(applied, darkTheme = true)

        assertEquals(BuiltInThemes.Ocean.backgroundArgb, dark.background.argb())
        assertEquals(BuiltInThemes.Ocean.panelArgb, dark.surfaceVariant.argb())
        assertEquals(BuiltInThemes.Ocean.buttonArgb, dark.primary.argb())
        assertEquals(BuiltInThemes.Ocean.textArgb, dark.onBackground.argb())
        assertEquals(light.background.argb(), dark.background.argb())
    }

    @Test
    fun aDarkPageGetsLightInkWhenNoInkWasChosen() {
        // A hand-picked near-black page with the text override never switched on used to inherit
        // the black default and become invisible. The ink follows the page instead.
        val settings = AppSettings(
            useAppBackgroundColor = true,
            backgroundColorArgb = 0xFF101014L,
            useAppPanelColor = true,
            panelColorArgb = 0xFF1A1A20L,
        )

        val scheme = schemeFor(settings, darkTheme = true)

        assertEquals(Color.White.argb(), scheme.onBackground.argb())
        assertEquals(Color.White.argb(), scheme.onSurface.argb())
    }

    @Test
    fun anExplicitInkStillWinsOnADarkPage() {
        // The derivation is only a fallback: a colour the user actually chose is never second-
        // guessed, even when it sits on the "wrong" side of the page's brightness.
        val settings = AppSettings(
            useAppBackgroundColor = true,
            backgroundColorArgb = 0xFF101014L,
            useAppTextColor = true,
            textColorArgb = 0xFFFF00FFL,
        )

        val scheme = schemeFor(settings, darkTheme = true)

        assertEquals(0xFFFF00FFL, scheme.onBackground.argb())
    }

    @Test
    fun theDefaultInkIsExactlyBlackOnTheDefaultPage() {
        // Deriving the ink must not change what a fresh install looks like: the default page is
        // #F5F5F5, far above the threshold, so it still gets [DefaultTextArgb].
        assertEquals(
            Color(DefaultTextArgb).argb(),
            Color(DefaultColors.background).legibleInk().argb(),
        )
        assertEquals(Color.Black.argb(), Color(DefaultColors.background).legibleInk().argb())
    }
}
