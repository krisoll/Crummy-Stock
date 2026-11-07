package com.example.stockapplication

import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.BuiltInThemes
import com.example.stockapplication.data.ColorTheme
import com.example.stockapplication.data.DatabaseSnapshot
import com.example.stockapplication.data.DefaultLoadingImageSizeDp
import com.example.stockapplication.data.ImportedSettings
import com.example.stockapplication.data.JsonImport
import com.example.stockapplication.data.SaleAnimation
import com.example.stockapplication.data.SaleAnimationLottieId
import com.example.stockapplication.data.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings half of a backup: what goes into the file, and what comes back out.
 *
 * These tests exist because the two halves are written in different files and drift silently. An
 * export that writes `textSizeId` under a key the importer reads as `textSize` produces no error
 * anywhere — the field is simply always null, and the only symptom is a setting that mysteriously
 * does not survive a restore. Asserting the round trip is what catches that.
 *
 * The parse side is deliberately lenient (see [JsonImport]), so the tests also pin down the
 * leniency: a value out of range is clamped rather than rejected, and a missing block is null
 * rather than an exception. Otherwise "restore my products" would start failing because somebody
 * hand-edited a font size.
 */
class BackupSettingsTest {

    /** A snapshot with every setting pushed off its default, so a dropped field is visible. */
    private fun snapshot(settings: AppSettings) = DatabaseSnapshot(
        schemaVersion = 4,
        appVersionName = "1.0",
        categories = emptyList(),
        products = emptyList(),
        orders = emptyList(),
        saleLines = emptyList(),
        legacySales = emptyList(),
        settings = settings,
        exportedAt = 1_700_000_000_000,
    )

    private val customised = AppSettings(
        taxRateBasisPoints = 1900,
        cardFeeRateBasisPoints = 275,
        cardFeeFixedCents = 15,
        showMoneyDecimals = false,
        currencyId = "eur",
        languageTag = "es",
        fontFamilyId = "serif",
        textSizeId = "large",
        motionSpeedId = "slow",
        useAppBackgroundColor = true,
        backgroundColorArgb = 0xFF102030,
        useAppButtonColor = true,
        buttonColorArgb = 0xFF405060,
        useAppPanelColor = true,
        panelColorArgb = 0xFF708090,
        useAppTextColor = true,
        textColorArgb = 0xFFA0B0C0,
        useAppHighlightColor = true,
        highlightColorArgb = 0xFFD0E0F0,
        showLoadingScreen = false,
        loadingIconId = "star",
        loadingSpinId = "pendulum",
        loadingCircleId = "solid",
        loadingImageSizeDp = 96,
        loadingCircleSizeDp = 140,
        scannerBeepEnabled = false,
        scanDelayMillis = 3_500,
        showScanPopup = false,
        scanVibrationEnabled = false,
        scanVibrationStrength = 120,
    )

    private fun roundTrip(settings: AppSettings): ImportedSettings? {
        val text = snapshot(settings).toJson()
        return JsonImport.readBackup(text).settings
    }

    @Test
    fun `every shop and appearance setting survives the round trip`() {
        val imported = roundTrip(customised)

        assertNotNull("the settings block should have been read back", imported)
        requireNotNull(imported)
        assertEquals(1900, imported.taxRateBasisPoints)
        assertEquals(275, imported.cardFeeRateBasisPoints)
        assertEquals(15L, imported.cardFeeFixedCents)
        assertEquals(false, imported.showMoneyDecimals)
        // The symbol travels with the amounts: restoring prices without it would put them in the
        // wrong unit.
        assertEquals("eur", imported.currencyId)
        assertEquals("es", imported.languageTag)

        assertEquals("serif", imported.fontFamilyId)
        assertEquals("large", imported.textSizeId)
        assertEquals("slow", imported.motionSpeedId)
        assertEquals(true, imported.useAppBackgroundColor)
        assertEquals(0xFF102030, imported.backgroundColorArgb)
        assertEquals(true, imported.useAppButtonColor)
        assertEquals(0xFF405060, imported.buttonColorArgb)
        assertEquals(true, imported.useAppPanelColor)
        assertEquals(0xFF708090, imported.panelColorArgb)
        assertEquals(true, imported.useAppTextColor)
        assertEquals(0xFFA0B0C0, imported.textColorArgb)
        assertEquals(true, imported.useAppHighlightColor)
        assertEquals(0xFFD0E0F0, imported.highlightColorArgb)
    }

    @Test
    fun `every startup screen and scanner setting survives the round trip`() {
        val imported = roundTrip(customised)

        requireNotNull(imported)
        assertEquals(false, imported.showLoadingScreen)
        assertEquals("star", imported.loadingIconId)
        assertEquals("pendulum", imported.loadingSpinId)
        assertEquals("solid", imported.loadingCircleId)
        assertEquals(96, imported.loadingImageSizeDp)
        assertEquals(140, imported.loadingCircleSizeDp)

        assertEquals(false, imported.scannerBeepEnabled)
        assertEquals(3_500, imported.scanDelayMillis)
        assertEquals(false, imported.showScanPopup)
        assertEquals(false, imported.scanVibrationEnabled)
        assertEquals(120, imported.scanVibrationStrength)
    }

    @Test
    fun `a default snapshot still round-trips its values rather than dropping them`() {
        // The defaults are the interesting case: a field written as "the default" and then read as
        // null would look identical values-wise today but would stop overriding a local value.
        val imported = roundTrip(AppSettings())

        requireNotNull(imported)
        assertEquals(0, imported.taxRateBasisPoints)
        assertEquals(true, imported.showMoneyDecimals)
        assertEquals(0L, imported.cardFeeFixedCents)
    }

    @Test
    fun `device specific values are exported but never restored`() {
        // These name files and a folder that exist on the exporting phone only. They are written
        // so the file is a faithful record, and skipped on read so a restore cannot leave the app
        // pointing at nothing.
        val settings = AppSettings(
            exportFolderUri = "content://tree/primary%3AStock",
            loadingImageFileName = "loading-99.png",
            scannerBeepFileName = "beep-1.ogg",
            saleSoundFileName = "sale-2.ogg",
            saleLottieFileName = "sale-abc.json",
        )
        val text = snapshot(settings).toJson()

        // Present in the file, so the backup is a faithful record of the exporting device...
        assertTrue("export folder should be recorded", text.contains("content://tree/primary%3AStock"))
        assertTrue("picked image should be recorded", text.contains("loading-99.png"))
        assertTrue("picked beep should be recorded", text.contains("beep-1.ogg"))
        assertTrue("imported animation should be recorded", text.contains("sale-abc.json"))
        assertTrue("the block must be marked as such", text.contains("\"deviceSpecific\""))

        // ...but impossible to reach through the parsed result, because the reader never looks at
        // the deviceSpecific block. There is no field on `ImportedSettings` to put them in, so
        // the type system backs the guarantee; this asserts the reader did not cheat.
        // `loadingImageSizeDp` is an ordinary appearance value and does come back; the *file name*
        // of the picked image does not, and there is no field here it could occupy.
        val imported = roundTrip(settings)
        requireNotNull(imported)
        assertEquals(DefaultLoadingImageSizeDp, imported.loadingImageSizeDp)
    }

    @Test
    fun `the choice of a custom animation round-trips even though the file does not`() {
        // The id travels in the scanner block, so a restore knows the shop *wanted* its own
        // animation; the file name does not, because it names a copy on the exporting device.
        // `usesLottieSaleAnimation` then requires both, so the target device falls back to the
        // default glyph rather than rendering an empty box.
        val settings = AppSettings(
            saleAnimationId = SaleAnimationLottieId,
            saleLottieFileName = "sale-abc.json",
        )
        val imported = roundTrip(settings)

        requireNotNull(imported)
        assertEquals(SaleAnimationLottieId, imported.saleAnimationId)
    }

    @Test
    fun `a custom animation selection with no file falls back to the built-in glyph`() {
        // The restore case above: the id says "lottie", but no file came with it.
        val withFile = AppSettings(
            saleAnimationId = SaleAnimationLottieId,
            saleLottieFileName = "sale-abc.json",
        )
        assertTrue(withFile.usesLottieSaleAnimation)

        val withoutFile = AppSettings(saleAnimationId = SaleAnimationLottieId)
        assertFalse(
            "an id with no file must not claim to have an animation",
            withoutFile.usesLottieSaleAnimation,
        )
        // And the glyph it falls back to is always drawable.
        assertEquals(SaleAnimation.Default, withoutFile.saleAnimation)
    }

    @Test
    fun `out of range values are clamped rather than rejected`() {
        // A hand-edited file should still restore the products, with the bad setting pulled back
        // into range instead of aborting the whole import.
        val text = backupWithSettings(
            """
            "taxRateBasisPoints": 50000,
            "cardFeeRateBasisPoints": -20,
            "scanner": {
              "scanDelayMillis": 999999,
              "scanVibrationStrength": 5000
            }
            """.trimIndent()
        )
        val imported = JsonImport.readBackup(text).settings

        requireNotNull(imported)
        assertEquals(10_000, imported.taxRateBasisPoints)
        assertEquals(0, imported.cardFeeRateBasisPoints)
        assertEquals(5_000, imported.scanDelayMillis)
        assertEquals(255, imported.scanVibrationStrength)
    }

    @Test
    fun `a file with no settings block yields null instead of failing`() {
        val text = """
            {
              "format": "stockapplication.backup",
              "version": 1,
              "schemaVersion": 4,
              "categories": [],
              "products": [],
              "orders": []
            }
        """.trimIndent()

        // The data still imports; there is simply nothing to offer on the settings switch.
        assertNull(JsonImport.readBackup(text).settings)
    }

    @Test
    fun `a settings block with only the rates is still usable`() {
        // What a file exported before the appearance grouping looked like.
        val text = backupWithSettings(
            """
            "taxRateBasisPoints": 2100,
            "cardFeeRateBasisPoints": 0,
            "cardFeeFixedCents": 20
            """.trimIndent()
        )
        val imported = JsonImport.readBackup(text).settings

        requireNotNull(imported)
        assertEquals(2100, imported.taxRateBasisPoints)
        // Absent means "the file did not say", not "reset it".
        assertNull(imported.fontFamilyId)
        assertNull(imported.textSizeId)
        assertNull(imported.languageTag)
    }

    @Test
    fun `an unknown enum id is passed through for the writer to normalise`() {
        // A backup from a build that shipped a font this one has never heard of. The id is handed
        // on as-is; `SettingsRepository.applyImported` maps it to the default via `fromId`.
        val text = backupWithSettings(
            """
            "appearance": {
              "fontFamilyId": "not-a-real-font"
            }
            """.trimIndent()
        )
        val imported = JsonImport.readBackup(text).settings

        requireNotNull(imported)
        assertEquals("not-a-real-font", imported.fontFamilyId)
    }

    @Test
    fun `an untouched install names the default theme in its backup`() {
        // The app draws the default palette before the user ever opens Personalize. Writing null
        // here made "never chose a theme" indistinguishable from "a palette no theme describes",
        // and an importer would have no way to show which preset was in effect.
        val imported = roundTrip(AppSettings())

        requireNotNull(imported)
        assertEquals(BuiltInThemes.Default.id, imported.activeThemeId)
    }

    @Test
    fun `saved themes and the active selection survive the round trip`() {
        // A saved theme is more than five colours: the name is what makes it selectable again on
        // another device. Before these fields existed a backup kept the active colours but lost
        // the theme itself, so the picker could only show a nameless custom palette.
        val theme = ColorTheme(
            id = "saved_test",
            name = "Shop colours",
            backgroundArgb = 0xFF102030,
            buttonArgb = 0xFF405060,
            panelArgb = 0xFF708090,
            highlightArgb = 0xFFD0E0F0,
            textArgb = 0xFFA0B0C0,
        )
        val settings = AppSettings(
            useAppBackgroundColor = true,
            backgroundColorArgb = theme.backgroundArgb,
            useAppButtonColor = true,
            buttonColorArgb = theme.buttonArgb,
            useAppPanelColor = true,
            panelColorArgb = theme.panelArgb,
            useAppTextColor = true,
            textColorArgb = theme.textArgb,
            useAppHighlightColor = true,
            highlightColorArgb = theme.highlightArgb,
            savedThemes = listOf(theme),
        )

        val imported = roundTrip(settings)

        requireNotNull(imported)
        assertEquals("saved_test", imported.activeThemeId)
        assertEquals(1, imported.savedThemes?.size)
        val restored = imported.savedThemes?.single()
        requireNotNull(restored)
        assertEquals("Shop colours", restored.name)
        assertEquals(0xFF405060, restored.buttonArgb)
        assertEquals(0xFFA0B0C0, restored.textArgb)
    }

    @Test
    fun `the settings object declares its own version`() {
        // Lets a future reader distinguish "grouped settings" from the older flat layout without
        // having to infer it from which keys happen to be present.
        val text = snapshot(AppSettings()).toJson()
        assert(text.contains("\"settingsVersion\""))
    }

    /** A minimal valid backup whose `settings` object holds the supplied fields. */
    private fun backupWithSettings(settingsBody: String): String = """
        {
          "format": "stockapplication.backup",
          "version": 1,
          "schemaVersion": 4,
          "categories": [],
          "products": [],
          "orders": [],
          "settings": {
            $settingsBody
          }
        }
    """.trimIndent()
}
