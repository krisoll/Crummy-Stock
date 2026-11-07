package com.example.stockapplication

import com.example.stockapplication.data.AppFontFamily
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.DefaultFontFamilyId
import com.example.stockapplication.ui.viewmodel.ColorDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceSettingsTest {

    @Test
    fun everyAppearanceToggleDefaultsToOff() {
        val settings = AppSettings()
        assertFalse(settings.useAppBackgroundColor)
        assertFalse(settings.useAppButtonColor)
        assertFalse(settings.useAppPanelColor)
        assertFalse(settings.useAppTextColor)
        assertFalse(settings.useAppHighlightColor)
        assertEquals(null, settings.highlightColorArgb)
    }

    @Test
    fun fontFamilyDefaultsToDefault() {
        assertEquals(DefaultFontFamilyId, AppSettings().fontFamilyId)
        assertEquals(AppFontFamily.DEFAULT, AppFontFamily.fromId(null))
        assertEquals(AppFontFamily.DEFAULT, AppFontFamily.fromId("nonsense"))
    }

    @Test
    fun everyFontFamilyIdRoundTrips() {
        AppFontFamily.entries.forEach { family ->
            assertEquals(family, AppFontFamily.fromId(family.id))
        }
    }

    @Test
    fun draftReportsPendingChangesForAnyChannel() {
        assertFalse(ColorDraft().hasPending)
        assertTrue(ColorDraft(backgroundArgb = 0xFF112233L).hasPending)
        assertTrue(ColorDraft(buttonArgb = 0xFF112233L).hasPending)
        assertTrue(ColorDraft(panelArgb = 0xFF112233L).hasPending)
        assertTrue(ColorDraft(textArgb = 0xFF112233L).hasPending)
        assertTrue(ColorDraft(highlightArgb = 0xFF112233L).hasPending)
    }

    @Test
    fun highlightColourIsIndependentOfTextColour() {
        // Highlight drives selected chips / the nav indicator / emphasised headings; text drives
        // body copy. Sharing one channel would make "selected" and "normal" indistinguishable.
        val settings = AppSettings(
            useAppHighlightColor = true,
            highlightColorArgb = 0xFF00BCD4L,
            useAppTextColor = true,
            textColorArgb = 0xFF212121L,
        )
        assertEquals(0xFF00BCD4L, settings.highlightColorArgb)
        assertEquals(0xFF212121L, settings.textColorArgb)
    }

    @Test
    fun highlightColourStaysStoredWhileItsToggleIsOff() {
        val settings = AppSettings(
            useAppHighlightColor = false,
            highlightColorArgb = 0xFF00BCD4L,
        )
        val active = settings.highlightColorArgb?.takeIf { settings.useAppHighlightColor }
        assertEquals(null, active)
        assertEquals(0xFF00BCD4L, settings.highlightColorArgb)
    }

    @Test
    fun panelAndTextColoursAreIndependent() {
        // Each channel must stay separately addressable, otherwise the pickers would fight.
        val settings = AppSettings(
            useAppPanelColor = true,
            panelColorArgb = 0xFF00FF00L,
            useAppTextColor = false,
            textColorArgb = 0xFFFF0000L,
        )
        val activePanel = settings.panelColorArgb?.takeIf { settings.useAppPanelColor }
        val activeText = settings.textColorArgb?.takeIf { settings.useAppTextColor }

        assertEquals(0xFF00FF00L, activePanel)
        assertEquals(null, activeText)
        // The stored value survives even while its toggle is off.
        assertEquals(0xFFFF0000L, settings.textColorArgb)
    }
}
