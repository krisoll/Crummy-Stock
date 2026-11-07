package com.example.stockapplication

import androidx.compose.ui.graphics.Color
import com.example.stockapplication.ui.theme.highlightedScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins down the contract between a user colour and the Material roles that actually render it.
 *
 * This is where the "add product" FAB bug lived: the FAB reads `primaryContainer`, not `primary`,
 * so wiring only `primary` left it on the stock palette. `highlightedScheme` is the pure function
 * `resolveColorScheme` delegates to, so it can be exercised without an Android Context.
 */
class HighlightColourSchemeTest {

    private val button = 0xFF2E7D32L
    private val highlight = 0xFF8B61F9L

    /** The ARGB value [this] was built from, so assertions read like the stored setting. */
    private fun Color.argb(): Long = value.shr(32).toLong() and 0xFFFFFFFFL

    private fun base() = androidx.compose.material3.lightColorScheme()

    @Test
    fun highlightColourDrivesTheSelectedChipAndNavIndicatorRoles() {
        val scheme = highlightedScheme(
            base = base(),
            background = null,
            panel = null,
            button = null,
            text = null,
            highlight = Color(highlight),
            darkTheme = false,
        )

        // Selected FilterChip: container + label colour.
        assertEquals(highlight, scheme.secondaryContainer.argb())
        // Nav-bar indicator and emphasised headings.
        assertEquals(highlight, scheme.secondary.argb())
        assertEquals(highlight, scheme.tertiary.argb())
        // The label on top must stay readable rather than keep the default purple-derived ink.
        assertEquals(Color.White, scheme.onSecondaryContainer)
    }

    @Test
    fun buttonColourAlsoDrivesTheContainerPairTheFabUses() {
        val scheme = highlightedScheme(
            base = base(),
            background = null,
            panel = null,
            button = Color(button),
            text = null,
            highlight = null,
            darkTheme = false,
        )

        assertEquals(button, scheme.primary.argb())
        assertEquals(button, scheme.primaryContainer.argb())
        assertEquals(Color.White, scheme.onPrimaryContainer)
    }

    @Test
    fun buttonAndHighlightAreIndependent() {
        val scheme = highlightedScheme(
            base = base(),
            background = null,
            panel = null,
            button = Color(button),
            text = null,
            highlight = Color(highlight),
            darkTheme = false,
        )

        assertEquals(button, scheme.primaryContainer.argb())
        assertEquals(highlight, scheme.secondaryContainer.argb())
        assertNotEquals(scheme.primaryContainer.argb(), scheme.secondaryContainer.argb())
    }

    @Test
    fun noOverridesLeaveTheSchemeUntouched() {
        val scheme = highlightedScheme(
            base = base(),
            background = null,
            panel = null,
            button = null,
            text = null,
            highlight = null,
            darkTheme = false,
        )

        assertEquals(base().primary, scheme.primary)
        assertEquals(base().secondaryContainer, scheme.secondaryContainer)
        assertEquals(base().onPrimaryContainer, scheme.onPrimaryContainer)
    }

    @Test
    fun aLightColourGetsDarkInk() {
        // contrastingText picks Ink from luminance, so a pale highlight must not get white text.
        val scheme = highlightedScheme(
            base = base(),
            background = null,
            panel = null,
            button = null,
            text = null,
            highlight = Color(0xFFFFF59DL),
            darkTheme = false,
        )
        assertEquals(Color.Black, scheme.onSecondaryContainer)
    }
}
