package com.example.stockapplication

import com.example.stockapplication.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the colour-selection rules behind the configuration tab.
 *
 * The Material scheme construction itself is not exercised here: that needs a real Android
 * Context. These tests pin down the decision logic that decides *whether* a stored colour is
 * applied, which is where the interesting bugs live.
 */
class ThemeColorTest {

    private fun ColorableSettings(
        useBackground: Boolean = false,
        backgroundArgb: Long? = null,
        useButton: Boolean = false,
        buttonArgb: Long? = null,
    ) = AppSettings(
        useAppBackgroundColor = useBackground,
        backgroundColorArgb = backgroundArgb,
        useAppButtonColor = useButton,
        buttonColorArgb = buttonArgb,
    )

    @Test
    fun defaultsHaveNoCustomisation() {
        val settings = AppSettings()
        assertFalse(settings.useAppBackgroundColor)
        assertFalse(settings.useAppButtonColor)
        assertEquals(null, settings.backgroundColorArgb)
        assertEquals(null, settings.buttonColorArgb)
    }

    @Test
    fun aStoredColourWithItsToggleOffIsNotApplied() {
        // Someone picked red, then switched the toggle off: red must stay stored but inactive,
        // so flipping the toggle back on restores their choice.
        val settings = ColorableSettings(useBackground = false, backgroundArgb = 0xFFFF0000L)
        val activeBackground = settings.backgroundColorArgb
            ?.takeIf { settings.useAppBackgroundColor }
        assertEquals(null, activeBackground)
        assertEquals(0xFFFF0000L, settings.backgroundColorArgb)
    }

    @Test
    fun aStoredColourWithItsToggleOnIsApplied() {
        val settings = ColorableSettings(useButton = true, buttonArgb = 0xFF2E7D32L)
        val activeButton = settings.buttonColorArgb
            ?.takeIf { settings.useAppButtonColor }
        assertEquals(0xFF2E7D32L, activeButton)
    }

    @Test
    fun dynamicColourIsDisabledOnceTheUserPicksAnyColour() {
        // Otherwise the Android 12+ wallpaper palette would silently override the user's choice.
        fun dynamicAllowed(s: AppSettings) =
            !s.useAppBackgroundColor && !s.useAppButtonColor && !s.useAppHighlightColor

        assertTrue(dynamicAllowed(AppSettings()))
        assertFalse(dynamicAllowed(AppSettings(useAppButtonColor = true)))
        assertFalse(dynamicAllowed(AppSettings(useAppBackgroundColor = true)))
        assertFalse(dynamicAllowed(AppSettings(useAppHighlightColor = true)))
    }

    @Test
    fun exportFolderLabelFallsBackToDownloads() {
        assertEquals("Downloads", AppSettings().exportFolderLabel)
        assertFalse(AppSettings().hasExportFolder)
    }

    @Test
    fun exportFolderLabelExtractsTheSelectedFolderName() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AStock"
        assertEquals("Stock", AppSettings(exportFolderUri = uri).exportFolderLabel)
        assertTrue(AppSettings(exportFolderUri = uri).hasExportFolder)
    }
}
