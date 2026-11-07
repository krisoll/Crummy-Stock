package com.example.stockapplication

import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.BuiltInThemes
import com.example.stockapplication.data.ColorChannel
import com.example.stockapplication.data.ColorTheme
import com.example.stockapplication.data.DefaultColors
import com.example.stockapplication.data.DefaultTextArgb
import com.example.stockapplication.data.decodeThemes
import com.example.stockapplication.data.encodeThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the saved-theme storage format and the default-palette invariant.
 *
 * The encoder is hand-rolled (see `encodeThemes`), so the round-trip is the property that matters:
 * anything the user can type into a theme name must survive a save/reload unchanged, and a
 * corrupt entry must never cost them the rest of their settings.
 */
class ColorThemeTest {

    private fun theme(
        name: String = "Mine",
        background: Long = 0xFFF5F5F5,
        button: Long = 0xFF8CE482,
        panel: Long = 0xFF8CE482,
        highlight: Long = 0xFF507DDF,
        text: Long? = null,
    ) = ColorTheme.saved(
        name = name,
        backgroundArgb = background,
        buttonArgb = button,
        panelArgb = panel,
        highlightArgb = highlight,
        textArgb = text,
        now = 1_700_000_000_000L,
    )

    @Test
    fun aThemeSurvivesASaveAndReload() {
        val original = theme(name = "Shop green", text = 0xFF000000)
        val restored = decodeThemes(encodeThemes(listOf(original))).single()

        assertEquals(original.id, restored.id)
        assertEquals("Shop green", restored.name)
        assertEquals(original.backgroundArgb, restored.backgroundArgb)
        assertEquals(original.buttonArgb, restored.buttonArgb)
        assertEquals(original.panelArgb, restored.panelArgb)
        assertEquals(original.highlightArgb, restored.highlightArgb)
        assertEquals(original.textArgb, restored.textArgb)
    }

    @Test
    fun aNameContainingTheFieldSeparatorDoesNotShiftTheColours() {
        // This is the bug the escaping exists to prevent: without it "A|B" splits into an extra
        // field, every colour moves one slot along, and the theme parses *successfully* but with
        // the wrong colours — silently.
        val original = theme(name = "A|B")
        val restored = decodeThemes(encodeThemes(listOf(original))).single()

        assertEquals("A|B", restored.name)
        assertEquals(original.backgroundArgb, restored.backgroundArgb)
        assertEquals(original.highlightArgb, restored.highlightArgb)
    }

    @Test
    fun aNameContainingABackslashRoundTrips() {
        val original = theme(name = "Back\\slash")
        val restored = decodeThemes(encodeThemes(listOf(original))).single()
        assertEquals("Back\\slash", restored.name)
    }

    @Test
    fun multipleThemesAreKeptInOrder() {
        val themes = listOf(theme(name = "One"), theme(name = "Two"), theme(name = "Three"))
        val restored = decodeThemes(encodeThemes(themes))
        assertEquals(listOf("One", "Two", "Three"), restored.map { it.name })
    }

    @Test
    fun aCorruptEntryIsDroppedRatherThanThrowing() {
        // A half-written or hand-edited preference must not take the rest of the settings down
        // with it, so the bad line is skipped and the good one still loads.
        val good = encodeThemes(listOf(theme(name = "Good")))
        val corrupted = "garbage-with-no-separators\n$good\nsaved_x|name|not-a-number|1|2|3|"

        val restored = decodeThemes(corrupted)

        assertEquals(1, restored.size)
        assertEquals("Good", restored.single().name)
    }

    @Test
    fun blankStorageDecodesToNoThemes() {
        assertEquals(emptyList<ColorTheme>(), decodeThemes(null))
        assertEquals(emptyList<ColorTheme>(), decodeThemes(""))
        assertEquals(emptyList<ColorTheme>(), decodeThemes("   "))
    }

    @Test
    fun aThemeWithoutATextColourLeavesItUnset() {
        // Text is deliberately optional: a preset must not force a value over the user's choice.
        val restored = decodeThemes(encodeThemes(listOf(theme(text = null)))).single()
        assertNull(restored.textArgb)
    }

    @Test
    fun theDefaultThemeIsTheAppsDefaultPalette() {
        // Selecting the "Default" preset and never having customised anything must produce the
        // same pixels, so the two definitions cannot be allowed to drift apart.
        assertEquals(DefaultColors.background, BuiltInThemes.Default.backgroundArgb)
        assertEquals(DefaultColors.button, BuiltInThemes.Default.buttonArgb)
        assertEquals(DefaultColors.panel, BuiltInThemes.Default.panelArgb)
        assertEquals(DefaultColors.highlight, BuiltInThemes.Default.highlightArgb)
    }

    @Test
    fun theDefaultPaletteIsTheRequestedColours() {
        assertEquals(0xFFF5F5F5L, BuiltInThemes.Default.backgroundArgb)
        assertEquals(0xFF3E9141L, BuiltInThemes.Default.buttonArgb)
        assertEquals(0xFF507DDFL, BuiltInThemes.Default.highlightArgb)
    }

    @Test
    fun theDefaultButtonAndPanelAreDistinct() {
        // When these were the same colour, every filled button and every slider track was drawn
        // in the value it sat on and became invisible. The button has to be distinguishable from
        // the panel it is placed over.
        assertNotEquals(
            "the button colour must not equal the panel colour",
            BuiltInThemes.Default.panelArgb,
            BuiltInThemes.Default.buttonArgb,
        )
    }

    @Test
    fun theDefaultButtonIsDarkerThanThePanel() {
        // The button is the accent drawn *on* the panel, so it has to read as the more saturated
        // of the two. Compared by green channel only, which is enough to catch an accidental swap.
        val button = BuiltInThemes.Default.buttonArgb
        val panel = BuiltInThemes.Default.panelArgb
        val buttonGreen = (button shr 8) and 0xFF
        val panelGreen = (panel shr 8) and 0xFF
        assertTrue(
            "button green ($buttonGreen) should be darker than panel green ($panelGreen)",
            buttonGreen < panelGreen,
        )
    }

    @Test
    fun everyBuiltInThemeHasANameAndAUniqueId() {
        val ids = BuiltInThemes.all.map { it.id }
        assertEquals("built-in theme ids must be unique", ids.size, ids.toSet().size)
        BuiltInThemes.all.forEach { theme ->
            assertTrue("built-in must carry a nameRes", theme.nameRes != null)
            assertTrue("built-in must be recognised as built-in", theme.isBuiltIn)
        }
    }

    @Test
    fun aSavedThemeIsNotMistakenForABuiltIn() {
        assertTrue(!theme().isBuiltIn)
    }

    @Test
    fun builtInThemesResolveById() {
        assertEquals(BuiltInThemes.Sage, BuiltInThemes.byId(BuiltInThemes.Sage.id))
        assertNull(BuiltInThemes.byId("saved_abc"))
        assertNull(BuiltInThemes.byId(null))
    }

    @Test
    fun everyBuiltInThemeOffersFourSwatches() {
        BuiltInThemes.all.forEach { assertEquals(4, it.swatches.size) }
    }

    @Test
    fun everyBuiltInThemeDeclaresItsOwnTextColour() {
        // A null text colour is what let Midnight's white ink leak into the light themes: applying
        // a theme that omits the channel left the previous one's value in place. Every preset has
        // to be a complete palette.
        BuiltInThemes.all.forEach { theme ->
            assertTrue(
                "${theme.id} must declare textArgb so switching to it is self-contained",
                theme.textArgb != null,
            )
        }
    }

    @Test
    fun lightThemesUseDarkInkAndTheDarkThemeUsesLightInk() {
        // The visible symptom was white-on-cream after leaving Midnight, so the ink has to be on
        // the correct side of the background's luminance in every preset.
        fun luminanceOnWhite(argb: Long): Double {
            val r = ((argb shr 16) and 0xFF) / 255.0
            val g = ((argb shr 8) and 0xFF) / 255.0
            val b = (argb and 0xFF) / 255.0
            return 0.299 * r + 0.587 * g + 0.114 * b
        }

        BuiltInThemes.all.forEach { theme ->
            val bg = luminanceOnWhite(theme.backgroundArgb)
            val ink = luminanceOnWhite(theme.textArgb!!)
            val dark = bg < 0.5
            if (dark) {
                assertTrue(
                    "${theme.id}: ink must be light on a dark background",
                    ink > bg,
                )
            } else {
                assertTrue(
                    "${theme.id}: ink must be dark on a light background",
                    ink < bg,
                )
            }
        }
    }

    @Test
    fun everyThemeTransitionProducesReadableInk() {
        // The reported bug in one assertion: after Midnight, every other theme must still put dark
        // ink on its own light background. Because each theme is now self-contained, simulating
        // the transition is just checking the destination, but the loop documents the case.
        val midnight = BuiltInThemes.Midnight
        assertTrue("Midnight is expected to use light ink", midnight.textArgb!! > 0xFFAAAAAA)

        BuiltInThemes.all.filter { it.id != midnight.id }.forEach { theme ->
            assertTrue(
                "${theme.id} must not inherit Midgnight's light ink",
                theme.textArgb != midnight.textArgb,
            )
        }
    }

    @Test
    fun aThemeWithoutInkFallsBackToTheAppsDefaultInk() {
        // Not every reachable theme states an ink: one saved by an older build, or merged in from
        // a backup, can carry `textArgb == null`. Applying one must land on the app's own ink,
        // never on whatever the previous theme left in the preference.
        val inkless = ColorTheme(
            id = ColorTheme.SavedIdPrefix + "legacy",
            name = "Legacy",
            backgroundArgb = 0xFFF2F0EA,
            buttonArgb = 0xFF7C9A86,
            panelArgb = 0xFFE3E8E0,
            highlightArgb = 0xFF2E7D74,
            textArgb = null,
        )

        assertNull(inkless.textArgb)
        // The value `applyTheme` writes for such a theme.
        assertEquals(DefaultTextArgb, inkless.textArgb ?: DefaultTextArgb)
    }

    @Test
    fun theDefaultInkIsBlack() {
        // Pinned because it is now written into storage as a concrete colour rather than implied
        // by an absent override: a light default would reintroduce the invisible-text bug for
        // every pale palette in one line.
        assertEquals(0xFF000000L, DefaultTextArgb)
        assertEquals(DefaultTextArgb, BuiltInThemes.Default.textArgb)
    }

    @Test
    fun everySavedThemeRoundTripsItsInk() {
        // `ColorTheme.saved` must not drop the channel. A saved theme with no ink is exactly the
        // theme that used to leave Midnight's white behind when it was re-applied.
        val saved = ColorTheme.saved(
            name = "Shop",
            backgroundArgb = 0xFFF5F5F5,
            buttonArgb = 0xFF3E9141,
            panelArgb = 0xFF8CE482,
            highlightArgb = 0xFF507DDF,
            textArgb = DefaultTextArgb,
        )

        assertEquals(DefaultTextArgb, saved.textArgb)
        assertTrue("a saved theme is not built-in", !saved.isBuiltIn)
    }

    // ---- "Custom colour" toggles -------------------------------------------

    /** AppSettings holding exactly what applying [theme] writes. */
    private fun settingsFor(theme: ColorTheme) = AppSettings(
        useAppBackgroundColor = true,
        backgroundColorArgb = theme.backgroundArgb,
        useAppButtonColor = true,
        buttonColorArgb = theme.buttonArgb,
        useAppPanelColor = true,
        panelColorArgb = theme.panelArgb,
        useAppHighlightColor = true,
        highlightColorArgb = theme.highlightArgb,
        useAppTextColor = true,
        textColorArgb = theme.textArgb ?: DefaultTextArgb,
    )

    @Test
    fun applyingTheDefaultThemeDoesNotLookLikeACustomColour() {
        // Applying a theme switches every override on — it is one atomic write of a complete
        // palette. Reading the raw flag therefore reported "custom" for a palette the user had
        // not customised at all. Selecting "Default" has to leave every toggle off.
        val settings = settingsFor(BuiltInThemes.Default)

        ColorChannel.entries.forEach { channel ->
            assertTrue(
                "$channel: an override may be stored after applying Default",
                when (channel) {
                    ColorChannel.BACKGROUND -> settings.useAppBackgroundColor
                    ColorChannel.BUTTON -> settings.useAppButtonColor
                    ColorChannel.PANEL -> settings.useAppPanelColor
                    ColorChannel.HIGHLIGHT -> settings.useAppHighlightColor
                    ColorChannel.TEXT -> settings.useAppTextColor
                },
            )
            assertTrue(
                "$channel: the toggle must read as not customised",
                !settings.isCustomColorEnabled(channel),
            )
            assertTrue(
                "$channel: the colour is the app default",
                settings.isDefaultColor(channel),
            )
        }
    }

    @Test
    fun aNonDefaultThemeReadsAsCustom() {
        // The mirror case: a palette whose colours differ from the app default is a
        // customisation and the toggle says so.
        val settings = settingsFor(BuiltInThemes.Sage)

        // Sage's four accent channels all differ from the app default.
        listOf(
            ColorChannel.BACKGROUND,
            ColorChannel.BUTTON,
            ColorChannel.PANEL,
            ColorChannel.HIGHLIGHT,
        ).forEach { channel ->
            assertFalse(
                "$channel: Sage differs from the default palette",
                settings.isDefaultColor(channel),
            )
            assertTrue(
                "$channel: the toggle must read as customised",
                settings.isCustomColorEnabled(channel),
            )
        }

        // Sage's ink is the same black as the app default, so it is *not* a customisation —
        // nothing would change if the user turned the toggle off. The other presets state a
        // non-black ink and therefore do read as customised; Midnight is checked as the
        // extreme case in `theDarkPresetsInkIsACustomisation`.
        assertTrue(
            "Sage's ink equals the app default",
            settings.isDefaultColor(ColorChannel.TEXT),
        )
        assertFalse(
            "Sage's ink toggle must read as off",
            settings.isCustomColorEnabled(ColorChannel.TEXT),
        )
    }

    @Test
    fun anInkThatDiffersFromTheDefaultReadsAsCustom() {
        // Midnight's near-white ink is the one users notice, and the channel that caused the
        // original disappearing-text bug. It must read as a customisation.
        val settings = settingsFor(BuiltInThemes.Midnight)

        assertNotEquals(DefaultTextArgb, settings.textColorArgb)
        assertFalse(settings.isDefaultColor(ColorChannel.TEXT))
        assertTrue(settings.isCustomColorEnabled(ColorChannel.TEXT))
    }

    @Test
    fun aClearedChannelIsNeverCustomised() {
        // An override that is switched off is not a customisation whatever colour it holds —
        // this is what "Restore the predetermined colours" produces.
        val settings = AppSettings()

        ColorChannel.entries.forEach { channel ->
            assertFalse(
                "$channel: a cleared channel is not customised",
                settings.isCustomColorEnabled(channel),
            )
            assertTrue(
                "$channel: a cleared channel holds the default",
                settings.isDefaultColor(channel),
            )
        }
    }

    @Test
    fun theDefaultInkIsWhatAClearedTextChannelResolvesTo() {
        // `resetAppearance` clears the text channel outright, so what the app draws then is the
        // default ink — resolving a null stored value has to land on exactly the colour the old
        // "write black instead of clearing" workaround wrote.
        val cleared = AppSettings()

        assertNull(cleared.textColorArgb)
        assertFalse(cleared.useAppTextColor)
        assertEquals(DefaultTextArgb, cleared.textColorArgb ?: DefaultTextArgb)
    }
}
