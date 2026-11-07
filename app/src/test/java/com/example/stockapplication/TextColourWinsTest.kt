package com.example.stockapplication

import androidx.compose.ui.graphics.Color
import com.example.stockapplication.ui.theme.highlightedScheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins down that a chosen **font colour** drives every role the app draws words with.
 *
 * The bug this guards against: `onPrimary`, `onSecondaryContainer` and friends were derived from
 * the *accent* colour (black-or-white contrast), so a green button turned the bold category
 * headings and the inventory tab labels green regardless of what the user picked for text. The
 * accents own fills; text owns words. These tests keep that split enforced.
 */
class TextColourWinsTest {

    private val text = 0xFF1A237EL // a dark blue, obviously not black or white
    private val button = 0xFF2E7D32L
    private val highlight = 0xFF8B61F9L

    private fun Color.argb(): Long = value.shr(32).toLong() and 0xFFFFFFFFL

    private fun schemeWithText() = highlightedScheme(
        base = androidx.compose.material3.lightColorScheme(),
        background = Color(0xFFF5F5F5),
        panel = Color(0xFF8CE482),
        button = Color(button),
        text = Color(text),
        highlight = Color(highlight),
        darkTheme = false,
    )

    @Test
    fun textColourDrivesThePlainTextRoles() {
        val scheme = schemeWithText()
        assertEquals(text, scheme.onBackground.argb())
        assertEquals(text, scheme.onSurface.argb())
        assertEquals(text, scheme.onSurfaceVariant.argb())
    }

    @Test
    fun textColourDrivesLabelsDrawnOnButtons() {
        // These were the accent's contrasting black/white, which is what made bold text on a
        // coloured button ignore the user's font colour entirely.
        val scheme = schemeWithText()
        assertEquals(text, scheme.onPrimary.argb())
        assertEquals(text, scheme.onPrimaryContainer.argb())
    }

    @Test
    fun textColourDrivesLabelsDrawnOnHighlights() {
        val scheme = schemeWithText()
        assertEquals(text, scheme.onSecondary.argb())
        assertEquals(text, scheme.onSecondaryContainer.argb())
        assertEquals(text, scheme.onTertiary.argb())
    }

    @Test
    fun theAccentColoursStillOwnTheirFills() {
        // The split that matters: text owns words, the accents still own the surfaces behind them.
        val scheme = schemeWithText()
        assertEquals(button, scheme.primary.argb())
        assertEquals(button, scheme.primaryContainer.argb())
        assertEquals(highlight, scheme.secondary.argb())
        assertEquals(highlight, scheme.secondaryContainer.argb())
    }

    @Test
    fun withoutATextColourTheStockOnColoursAreKept() {
        // No font colour chosen means the palette's own contrast rules still apply — the fix must
        // not force black-on-white everywhere by default.
        val scheme = highlightedScheme(
            base = androidx.compose.material3.lightColorScheme(),
            background = null,
            panel = null,
            button = Color(button),
            text = null,
            highlight = Color(highlight),
            darkTheme = false,
        )
        val stock = androidx.compose.material3.lightColorScheme()
        assertEquals(stock.onPrimary.argb(), scheme.onPrimary.argb())
    }
}
