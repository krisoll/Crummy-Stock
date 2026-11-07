package com.example.stockapplication

import com.example.stockapplication.data.MaxPromoDiscountCents
import com.example.stockapplication.data.currencySymbol
import com.example.stockapplication.data.formatCents
import com.example.stockapplication.data.showCents
import com.example.stockapplication.ui.screens.formatPriceInput
import com.example.stockapplication.ui.screens.parseMoneyText
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How the promo's fixed amount is seeded into its editable field.
 *
 * Follows the app-wide "Show cents" setting so the field reads like every other amount, but the
 * stored cents are never rounded — that distinction is the whole point of these tests.
 */
class PromoFixedDisplayTest {

    @Test
    fun withCentsOnTheFieldShowsTwoDecimalPlaces() {
        assertEquals("12.00", formatPriceInput(1_200, showDecimals = true))
        assertEquals("12.50", formatPriceInput(1_250, showDecimals = true))
        assertEquals("0.00", formatPriceInput(0, showDecimals = true))
    }

    @Test
    fun withCentsOffAWholeAmountDropsTheDecimalPoint() {
        assertEquals("12", formatPriceInput(1_200, showDecimals = false))
        assertEquals("0", formatPriceInput(0, showDecimals = false))
        assertEquals("1250", formatPriceInput(125_000, showDecimals = false))
    }

    @Test
    fun withCentsOffAnAmountWithACentsPartKeepsItsDecimals() {
        // Dropping these would misreport what the promo is worth: a 12.50 discount is not 12.
        // This is the same rule `formatCents` follows, so the field and the receipt agree.
        assertEquals("12.50", formatPriceInput(1_250, showDecimals = false))
        assertEquals("12.01", formatPriceInput(1_201, showDecimals = false))
    }

    @Test
    fun theDisplayMatchesTheAppsOwnMoneyFormattingForWholeAmounts() {
        // For whole amounts the field and the rest of the app must agree, so the seed cannot look
        // like a different number from the one the receipt will show.
        val previousSymbol = currencySymbol
        val previousShowCents = showCents
        try {
            currencySymbol = ""
            showCents = true
            assertEquals(formatCents(1_200), formatPriceInput(1_200, showDecimals = true))

            showCents = false
            assertEquals(formatCents(1_200), formatPriceInput(1_200, showDecimals = false))
        } finally {
            currencySymbol = previousSymbol
            showCents = previousShowCents
        }
    }

    @Test
    fun theFieldDeliberatelyDivergesFromDisplayFormattingOnAPartialAmount() {
        // `formatCents` ROUNDS with cents off: 12.50 displays as "13". That is right on a receipt,
        // where it is a price to read, and wrong in an editable buffer, where it would silently
        // rewrite the promo's value as soon as the cashier opened and saved it. The field keeps the
        // fraction instead; this test records the divergence so it is a decision and not a bug.
        val previousSymbol = currencySymbol
        val previousShowCents = showCents
        try {
            currencySymbol = ""
            showCents = false
            assertEquals("13", formatCents(1_250))
            assertEquals("12.50", formatPriceInput(1_250, showDecimals = false))
        } finally {
            currencySymbol = previousSymbol
            showCents = previousShowCents
        }
    }

    @Test
    fun theSeedStillRoundTripsThroughTheMoneyParser() {
        // Whatever is displayed must parse back to the same cents, or opening and saving a promo
        // without touching the field would change its value.
        listOf(0L, 1L, 1_200L, 1_250L, 125_000L).forEach { cents ->
            assertEquals(
                cents,
                parseMoneyText(
                    formatPriceInput(cents, showDecimals = false),
                    maxCents = MaxPromoDiscountCents,
                ),
            )
        }
    }
}
