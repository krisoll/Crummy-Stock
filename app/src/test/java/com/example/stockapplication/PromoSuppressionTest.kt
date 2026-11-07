package com.example.stockapplication

import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.computeTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two figures on the cart panel.
 *
 * ## What each one means
 *
 *  - **To charge** — the goods minus any promo. Tax and the card fee are *not* added on; they are
 *    the shop's own costs of doing business.
 *  - **Net total** — what the shop keeps once those costs come out: `goods - promo - tax - fee`.
 *
 * They therefore differ by exactly `tax + cardFee`, and the charged figure is always the larger of
 * the two.
 *
 * ## What this replaced
 *
 * "To charge" briefly added tax and fees *back* to the discounted goods, which produced a figure
 * nobody owed: the customer was never asked for it and the shop never received it.
 */
class PromoSuppressionTest {

    @Test
    fun toChargeIsTheGoodsMinusThePromoAndNothingElse() {
        val totals = computeTotals(
            itemsSubtotalCents = 10_000,
            taxRateBasisPoints = 2_000, // 20%
            cardFeeRateBasisPoints = 0,
            paymentMethod = PaymentMethod.CASH,
        )

        // Tax is deducted from what the shop keeps, never added to what is charged.
        assertEquals(2_000L, totals.taxCents)
        assertEquals(10_000L, totals.amountDueCents)
        assertEquals(8_000L, totals.totalCents)
    }

    @Test
    fun aPromoComesOffBothFigures() {
        val withoutPromo = computeTotals(
            itemsSubtotalCents = 10_000,
            taxRateBasisPoints = 1_000,
            cardFeeRateBasisPoints = 0,
            paymentMethod = PaymentMethod.CASH,
        )
        val withPromo = computeTotals(
            itemsSubtotalCents = 10_000,
            taxRateBasisPoints = 1_000,
            cardFeeRateBasisPoints = 0,
            paymentMethod = PaymentMethod.CASH,
            promoDiscountCents = 2_000,
        )

        assertEquals(10_000L, withoutPromo.amountDueCents)
        assertEquals(8_000L, withPromo.amountDueCents)
        assertTrue(withPromo.totalCents < withoutPromo.totalCents)
    }

    @Test
    fun theTwoFiguresDifferByExactlyTheDeductions() {
        // The invariant tying the panel together: what is charged, minus what comes out, is what
        // is kept. Holds for any combination of rates and payment method.
        listOf(PaymentMethod.CASH, PaymentMethod.CARD).forEach { method ->
            val totals = computeTotals(
                itemsSubtotalCents = 10_000,
                taxRateBasisPoints = 1_500,
                cardFeeRateBasisPoints = 300,
                paymentMethod = method,
                cardFeeFixedCents = 50,
                promoDiscountCents = 1_000,
            )

            assertEquals(
                "charge - deductions should be the net, for $method",
                totals.totalCents,
                totals.amountDueCents - totals.taxCents - totals.cardFeeCents,
            )
        }
    }

    @Test
    fun withNoTaxOrFeesTheTwoFiguresCoincide() {
        val totals = computeTotals(
            itemsSubtotalCents = 4_250,
            taxRateBasisPoints = 0,
            cardFeeRateBasisPoints = 0,
            paymentMethod = PaymentMethod.CASH,
        )

        assertEquals(4_250L, totals.amountDueCents)
        assertEquals(4_250L, totals.totalCents)
    }

    @Test
    fun aCardFeeNeverChangesWhatIsCharged() {
        // It is a cost the shop absorbs, so the customer's figure is the same either way.
        val cash = computeTotals(
            itemsSubtotalCents = 10_000,
            taxRateBasisPoints = 0,
            cardFeeRateBasisPoints = 500,
            paymentMethod = PaymentMethod.CASH,
        )
        val card = computeTotals(
            itemsSubtotalCents = 10_000,
            taxRateBasisPoints = 0,
            cardFeeRateBasisPoints = 500,
            paymentMethod = PaymentMethod.CARD,
            cardFeeFixedCents = 100,
        )

        assertEquals(10_000L, cash.amountDueCents)
        assertEquals(10_000L, card.amountDueCents)
        // …but it does reduce what the shop keeps.
        assertEquals(10_000L, cash.totalCents)
        assertEquals(9_400L, card.totalCents)
    }
}
