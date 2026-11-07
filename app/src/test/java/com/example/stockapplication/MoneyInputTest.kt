package com.example.stockapplication

import com.example.stockapplication.data.MaxCardFeeFixedCents
import com.example.stockapplication.data.MaxPromoDiscountCents
import com.example.stockapplication.ui.screens.formatMoneyText
import com.example.stockapplication.ui.screens.formatPriceInput
import com.example.stockapplication.ui.screens.parseMoneyText
import com.example.stockapplication.ui.screens.sanitizeMoneyInput
import com.example.stockapplication.ui.screens.toCentsOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins down the two money-parsing defects that shipped together.
 *
 * Both were the same underlying confusion: a *display* string being fed into *editable* state.
 * `formatCents` exists to put "$12.99" on a receipt, and using it to seed a text field meant the
 * buffer contained characters the parser could not read back.
 */
class MoneyInputTest {

    @Test
    fun priceInputIsSymbolFreeAndRoundTrips() {
        // The seed for an editable price box: no currency symbol, always two decimals.
        assertEquals("12.99", formatPriceInput(1299))
        assertEquals("0.00", formatPriceInput(0))
        assertEquals("10.00", formatPriceInput(1000))
        assertEquals("1234.50", formatPriceInput(123450))

        // Whatever is seeded must parse straight back to the same cents.
        listOf(0L, 1L, 99L, 100L, 1299L, 123450L).forEach { cents ->
            assertEquals(cents, formatPriceInput(cents).toCentsOrNull())
        }
    }

    @Test
    fun aSeededDisplayStringWouldNotHaveParsed() {
        // The old behaviour, kept as a regression guard: this is exactly what broke the form.
        assertNull("$12.99".toCentsOrNull())
        assertEquals(1299L, "12.99".toCentsOrNull())
    }

    @Test
    fun promoFixedAmountIsNotClampedToTheCardFeeCap() {
        // A 5,000.00 discount is legitimate for a promo and must survive parsing.
        assertEquals(500_000L, parseMoneyText("5000.00", maxCents = MaxPromoDiscountCents))

        // The old path clamped it to the card-fee ceiling, which is how every promo ended up at
        // 1000.00 — the cap, not the amount the user typed.
        assertEquals(MaxCardFeeFixedCents, parseMoneyText("5000.00"))
        assertEquals(1_000_000L, MaxPromoDiscountCents)
    }

    @Test
    fun theCardFeeCapStillAppliesToTheCardFee() {
        // Widening the promo cap must not widen the card fee's own field.
        assertEquals(MaxCardFeeFixedCents, parseMoneyText("5000.00", maxCents = MaxCardFeeFixedCents))
    }

    @Test
    fun sanitisingKeepsTheDecimalPointSoTheAmountSurvives() {
        // Sanitising drops the symbol but *keeps* the decimal point, so the amount still reads
        // correctly. It is the seed that was wrong, not this step — recording the real behaviour
        // here so a future "fix" does not start folding digits together.
        assertEquals("10.00", sanitizeMoneyInput("$10.00"))
        assertEquals(1000L, parseMoneyText(sanitizeMoneyInput("$10.00")))
        // A seed that never contained the symbol needs no sanitising at all.
        assertEquals("10.00", sanitizeMoneyInput("10.00"))
    }

    @Test
    fun moneyTextRoundTrips() {
        listOf(0L, 5L, 50L, 100L, 1299L, 100_000L).forEach { cents ->
            assertEquals(cents, parseMoneyText(formatMoneyText(cents)))
        }
    }

    @Test
    fun incompleteInputIsTreatedAsZeroRatherThanAnError() {
        // Typing ".50" passes through a bare "." state; it must not flag the field as invalid.
        assertEquals(0L, parseMoneyText(""))
        assertEquals(0L, parseMoneyText("."))
        assertEquals(50L, parseMoneyText(".50"))
    }
}
