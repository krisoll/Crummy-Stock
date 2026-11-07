package com.example.stockapplication

import com.example.stockapplication.data.Cart
import com.example.stockapplication.data.MaxCardFeeFixedCents
import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.applyRateBasisPoints
import com.example.stockapplication.data.basisPointsToPercent
import com.example.stockapplication.data.computeTotals
import com.example.stockapplication.data.percentToBasisPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the checkout maths and cart behaviour.
 *
 * The accounting model is: `net = items − tax − cardFee`, with the card fee only applied to card
 * payments. These tests pin that down, including the rounding direction.
 */
class CartTotalsTest {

    private fun product(id: Long, price: Long, stock: Int = 10) = Product(
        id = id,
        barcode = "BC$id",
        name = "Product $id",
        priceCents = price,
        quantity = stock
    )

    // ---- Rate conversion -----------------------------------------------------

    @Test
    fun rateRoundTripsThroughBasisPoints() {
        assertEquals(275, percentToBasisPoints(2.75))
        assertEquals(0, percentToBasisPoints(0.0))
        assertEquals(2100, percentToBasisPoints(21.0))
        assertEquals(2.75, basisPointsToPercent(275), 0.0001)
    }

    @Test
    fun rateIsClampedToSaneBounds() {
        assertEquals(10_000, percentToBasisPoints(500.0))
        assertEquals(0, percentToBasisPoints(-5.0))
    }

    @Test
    fun applyingARateTruncatesRatherThanRoundsUp() {
        // 1000 * 2.75% = 27.5 cents -> 27, never 28.
        assertEquals(27L, applyRateBasisPoints(1000, 275))
        assertEquals(0L, applyRateBasisPoints(0, 275))
        assertEquals(0L, applyRateBasisPoints(1000, 0))
    }

    // ---- Totals --------------------------------------------------------------

    @Test
    fun cashHasNoCardFee() {
        val totals = computeTotals(
            itemsSubtotalCents = 10_000,
            taxRateBasisPoints = 1000, // 10%
            cardFeeRateBasisPoints = 275,
            paymentMethod = PaymentMethod.CASH,
        )
        assertEquals(10_000, totals.itemsSubtotalCents)
        assertEquals(1000, totals.taxCents)
        assertEquals(0, totals.cardFeeCents)
        assertEquals(9000, totals.totalCents)
    }

    @Test
    fun cardDeductsBothTaxAndFee() {
        val totals = computeTotals(
            itemsSubtotalCents = 10_000,
            taxRateBasisPoints = 1000,
            cardFeeRateBasisPoints = 275,
            paymentMethod = PaymentMethod.CARD,
        )
        assertEquals(1000, totals.taxCents)
        assertEquals(275, totals.cardFeeCents)
        assertEquals(8725, totals.totalCents)
    }

    @Test
    fun zeroRatesLeaveTheSubtotalUntouched() {
        val totals = computeTotals(5000, 0, 0, PaymentMethod.CARD)
        assertEquals(5000, totals.totalCents)
        assertEquals(0, totals.taxCents)
        assertEquals(0, totals.cardFeeCents)
    }

    @Test
    fun flatCardFeeIsAddedOnTopOfThePercentage() {
        val totals = computeTotals(
            itemsSubtotalCents = 10_000,
            taxRateBasisPoints = 0,
            cardFeeRateBasisPoints = 275,
            paymentMethod = PaymentMethod.CARD,
            cardFeeFixedCents = 50,
        )
        assertEquals(275, totals.cardFeeRateCents)
        assertEquals(50, totals.cardFeeFixedCents)
        assertEquals(325, totals.cardFeeCents)
        assertEquals(9675, totals.totalCents)
    }

    @Test
    fun flatCardFeeAloneWorksWithNoPercentage() {
        val totals = computeTotals(
            itemsSubtotalCents = 5000,
            taxRateBasisPoints = 0,
            cardFeeRateBasisPoints = 0,
            paymentMethod = PaymentMethod.CARD,
            cardFeeFixedCents = 50,
        )
        assertEquals(0, totals.cardFeeRateCents)
        assertEquals(50, totals.cardFeeFixedCents)
        assertEquals(4950, totals.totalCents)
    }

    @Test
    fun cashNeverPaysTheFlatCardFee() {
        val totals = computeTotals(
            itemsSubtotalCents = 10_000,
            taxRateBasisPoints = 0,
            cardFeeRateBasisPoints = 275,
            paymentMethod = PaymentMethod.CASH,
            cardFeeFixedCents = 50,
        )
        assertEquals(0, totals.cardFeeFixedCents)
        assertEquals(0, totals.cardFeeCents)
        assertEquals(10_000, totals.totalCents)
    }

    @Test
    fun flatCardFeeIsClampedToItsMaximum() {
        val totals = computeTotals(
            itemsSubtotalCents = 10_000_000,
            taxRateBasisPoints = 0,
            cardFeeRateBasisPoints = 0,
            paymentMethod = PaymentMethod.CARD,
            cardFeeFixedCents = Long.MAX_VALUE,
        )
        assertEquals(MaxCardFeeFixedCents, totals.cardFeeFixedCents)
    }

    @Test
    fun totalNeverGoesNegative() {
        val totals = computeTotals(
            itemsSubtotalCents = 100,
            taxRateBasisPoints = 10_000,
            cardFeeRateBasisPoints = 10_000,
            paymentMethod = PaymentMethod.CARD,
        )
        assertEquals(0, totals.totalCents)
    }

    // ---- Cart ----------------------------------------------------------------

    @Test
    fun addingTheSameProductMergesLines() {
        val cart = Cart().add(product(1, 500)).add(product(1, 500))
        assertEquals(1, cart.lines.size)
        assertEquals(2, cart.lines.single().quantity)
        assertEquals(1000, cart.subtotalCents)
    }

    @Test
    fun quantityIsClampedToAvailableStock() {
        val cart = Cart().add(product(1, 500, stock = 2), quantity = 5)
        assertEquals(2, cart.lines.single().quantity)
    }

    @Test
    fun outOfStockProductIsNotAdded() {
        val cart = Cart().add(product(1, 500, stock = 0))
        assertTrue(cart.isEmpty)
    }

    @Test
    fun decrementingToZeroRemovesTheLine() {
        val cart = Cart().add(product(1, 500)).updateQuantity(1, 0)
        assertTrue(cart.isEmpty)
    }

    @Test
    fun reconcileDropsDeletedAndSoldOutProducts() {
        val cart = Cart()
            .add(product(1, 500))
            .add(product(2, 300))
            .add(product(3, 100))

        // Product 2 still exists but is now sold out; product 3 was deleted entirely.
        val reconciled = cart.reconcile(
            listOf(
                product(1, 500, stock = 5),
                product(2, 300, stock = 0),
            )
        )

        assertEquals(listOf(1L), reconciled.lines.map { it.productId })
    }

    @Test
    fun reconcileClampsQuantityWhenStockShrinks() {
        val cart = Cart().add(product(1, 500, stock = 10), quantity = 5)
        val reconciled = cart.reconcile(listOf(product(1, 500, stock = 2)))
        assertEquals(2, reconciled.lines.single().quantity)
    }

    @Test
    fun itemCountAndIsEmptyTrackTheCart() {
        val cart = Cart().add(product(1, 500)).add(product(1, 500)).add(product(2, 100))
        assertEquals(3, cart.itemCount)
        assertFalse(cart.isEmpty)
        assertTrue(cart.clear().isEmpty)
    }
}
