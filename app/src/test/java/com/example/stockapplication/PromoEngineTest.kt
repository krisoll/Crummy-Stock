package com.example.stockapplication

import com.example.stockapplication.data.CartLine
import com.example.stockapplication.data.DiscountType
import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.Promo
import com.example.stockapplication.data.PromoEngine
import com.example.stockapplication.data.PromoKind
import com.example.stockapplication.data.PromoStatus
import com.example.stockapplication.data.computeTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The discount policy, exercised without a database or a clock.
 *
 * The cases worth guarding are the ones a business owner would complain about: a promo firing one
 * unit early, a discount leaking onto products it does not cover, a fixed amount exceeding the
 * basket, and a schedule boundary landing on the wrong side.
 *
 * With stacking on, there is a second class of complaint to guard: two promos over the same items
 * paying out more than those items cost.
 */
class PromoEngineTest {

    private val drinks = 1L
    private val bakery = 2L

    private val coffee = product(id = 1, name = "Coffee", priceCents = 1_000, categoryId = drinks)
    private val water = product(id = 2, name = "Water", priceCents = 500, categoryId = drinks)
    private val bread = product(id = 3, name = "Bread", priceCents = 300, categoryId = bakery)

    private val catalogue = listOf(coffee, water, bread).associateBy { it.id }

    private fun product(id: Long, name: String, priceCents: Long, categoryId: Long?) =
        Product(
            id = id,
            barcode = "B$id",
            name = name,
            priceCents = priceCents,
            quantity = 99,
            categoryId = categoryId
        )

    private fun line(product: Product, quantity: Int) = CartLine(
        productId = product.id,
        productName = product.name,
        barcode = product.barcode,
        unitPriceCents = product.priceCents,
        quantity = quantity,
        availableStock = 99,
    )

    private fun categoryPromo(
        threshold: Int = 3,
        percent: Int = 1_000,
        id: Long = 1,
        enabled: Boolean = true,
        startsAt: Long? = null,
        endsAt: Long? = null,
        categoryId: Long = drinks,
        name: String = "Drinks deal",
    ) = Promo(
        id = id,
        name = name,
        kind = PromoKind.CATEGORY,
        categoryId = categoryId,
        thresholdQuantity = threshold,
        discountType = DiscountType.PERCENT,
        discountPercentBasisPoints = percent,
        enabled = enabled,
        startsAt = startsAt,
        endsAt = endsAt,
    )

    /** Convenience: the single match, asserting there is exactly one. */
    private fun onlyMatch(
        cart: List<CartLine>,
        promos: List<Promo>,
        now: Long = 0L,
    ) = PromoEngine.applyPromos(cart, promos, catalogue, now).single()

    // ---- Thresholds --------------------------------------------------------

    @Test
    fun `one unit short does not apply`() {
        val cart = listOf(line(coffee, 2))
        assertTrue(PromoEngine.applyPromos(cart, listOf(categoryPromo()), catalogue, 0L).isEmpty())
    }

    @Test
    fun `exactly the threshold applies`() {
        val cart = listOf(line(coffee, 3))
        assertEquals(3, onlyMatch(cart, listOf(categoryPromo())).qualifyingQuantity)
    }

    @Test
    fun `mixed products from the same category count towards one total`() {
        // Two coffees and a water is still "3 from Drinks".
        val cart = listOf(line(coffee, 2), line(water, 1))
        assertEquals(3, onlyMatch(cart, listOf(categoryPromo())).qualifyingQuantity)
    }

    // ---- Repeating ---------------------------------------------------------

    @Test
    fun `a promo fires once per complete group`() {
        // "2 for 10% off" meeting 4 units is two groups, so it saves twice.
        val promo = categoryPromo(threshold = 2, percent = 1_000)
        val match = onlyMatch(listOf(line(coffee, 4)), listOf(promo))

        assertEquals(2, match.groupCount)
        // Both groups earn: 10% of the 4 coffees (4000), not of 2 of them.
        assertEquals(400L, match.discountCents)
        assertEquals(4, match.qualifyingQuantity)
    }

    @Test
    fun `four units against a two-unit threshold discount all four`() {
        // The case the shopkeeper reported: adding four should not still look like one promo.
        val promo = categoryPromo(threshold = 2, percent = 1_000)
        val one = onlyMatch(listOf(line(coffee, 2)), listOf(promo))
        val two = onlyMatch(listOf(line(coffee, 4)), listOf(promo))

        assertEquals(200L, one.discountCents)
        assertEquals(400L, two.discountCents)
        assertEquals(2, two.groupCount)
    }

    @Test
    fun `a leftover unit past a complete group earns nothing until it forms another`() {
        // 3 units against "2 for 10% off": one complete group, and the third is full price.
        val promo = categoryPromo(threshold = 2, percent = 1_000)
        val match = onlyMatch(listOf(line(coffee, 3)), listOf(promo))

        assertEquals(1, match.groupCount)
        // 10% of the 2 that earned it (2000), not of all 3 (3000).
        assertEquals(200L, match.discountCents)
        assertEquals(2, match.qualifyingQuantity)
    }

    @Test
    fun `five units against a two-unit threshold earns two groups`() {
        val promo = categoryPromo(threshold = 2, percent = 1_000)
        val match = onlyMatch(listOf(line(coffee, 5)), listOf(promo))

        assertEquals(2, match.groupCount)
        // 10% of 4000 — the fifth coffee is left at full price.
        assertEquals(400L, match.discountCents)
    }

    @Test
    fun `a repeated fixed amount is multiplied per group`() {
        // "1000 off when you buy 2" on 4 units is 2000 off, not 1000. Caught on a real device:
        // the label read (x2) while the money only came off once.
        val promo = categoryPromo(threshold = 2, percent = 0).copy(
            discountType = DiscountType.FIXED,
            discountFixedCents = 1_000,
        )
        val match = onlyMatch(listOf(line(coffee, 4)), listOf(promo))

        assertEquals(2, match.groupCount)
        assertEquals(2_000L, match.discountCents)
    }

    @Test
    fun `a repeated fixed amount never exceeds the earning base`() {
        // "5000 off when you buy 1" on 2 cheap 1000 items would be 10000 off 2000 of goods.
        val cheap = listOf(line(water, 2).copy(unitPriceCents = 1_000))
        val promo = categoryPromo(threshold = 1, percent = 0).copy(
            categoryId = drinks,
            discountType = DiscountType.FIXED,
            discountFixedCents = 5_000,
        )
        val match = onlyMatch(cheap, listOf(promo))

        assertEquals(2, match.groupCount)
        assertEquals(2_000L, match.discountCents)
        assertTrue(match.discountCents <= 2_000L)
    }

    @Test
    fun `a threshold of one fires once per unit`() {
        // "1 for 50c off" on 3 units is three deals.
        val promo = categoryPromo(threshold = 1, percent = 5_000)
        val match = onlyMatch(listOf(line(coffee, 3)), listOf(promo))

        assertEquals(3, match.groupCount)
        assertEquals(1_500L, match.discountCents)
    }

    @Test
    fun `repeating across mixed products counts the combined units`() {
        // 2 coffees + 2 waters = 4 units, so "2 for 10% off" fires twice over the whole base.
        val promo = categoryPromo(threshold = 2, percent = 1_000)
        val cart = listOf(line(coffee, 2), line(water, 2))
        val match = onlyMatch(cart, listOf(promo))

        assertEquals(2, match.groupCount)
        // 10% of (2000 coffee + 1000 water).
        assertEquals(300L, match.discountCents)
    }

    @Test
    fun `repeating never exceeds what the goods are worth`() {
        // A 100%-off "1 for" deal on 3 units gives the 3 units away, not 3x their value.
        val promo = categoryPromo(threshold = 1, percent = 10_000)
        val match = onlyMatch(listOf(line(coffee, 3)), listOf(promo))

        assertEquals(3, match.groupCount)
        assertEquals(3_000L, match.discountCents)
        assertTrue(match.discountCents <= 3_000L)
    }

    @Test
    fun `a repeated promo still leaves later promos an accurate remainder`() {
        // First promo takes 10% off 4 coffees. The second must work from the 3600 left.
        val repeats = categoryPromo(id = 1, threshold = 2, percent = 1_000)
        val second = categoryPromo(id = 2, threshold = 1, percent = 1_000)
        val cart = listOf(line(coffee, 4))

        val matches = PromoEngine.applyPromos(cart, listOf(repeats, second), catalogue, 0L)
            .associateBy { it.promo.id }

        assertEquals(400L, matches.getValue(1L).discountCents)
        // 10% of the 3600 that remains, not of the original 4000.
        assertEquals(360L, matches.getValue(2L).discountCents)
    }

    @Test
    fun `products from another category do not count towards the threshold`() {
        val cart = listOf(line(coffee, 2), line(bread, 5))
        assertTrue(PromoEngine.applyPromos(cart, listOf(categoryPromo()), catalogue, 0L).isEmpty())
    }

    // ---- The discount base -------------------------------------------------

    @Test
    fun `the discount is computed on qualifying lines only`() {
        // 3 coffees (3000) qualify; the bread is in the cart but must not be discounted.
        val cart = listOf(line(coffee, 3), line(bread, 10))
        // 10% of 3000, not of 6000.
        assertEquals(300L, onlyMatch(cart, listOf(categoryPromo())).discountCents)
    }

    @Test
    fun `a fixed amount is capped at the qualifying base`() {
        // "$5 off" on a single 5.00 water: cannot save more than the goods are worth.
        val promo = categoryPromo(threshold = 1).copy(
            discountType = DiscountType.FIXED,
            discountFixedCents = 500,
        )
        assertEquals(500L, onlyMatch(listOf(line(water, 1)), listOf(promo)).discountCents)

        val tiny = listOf(line(water, 1).copy(unitPriceCents = 200))
        assertEquals(200L, onlyMatch(tiny, listOf(promo)).discountCents)
    }

    @Test
    fun `a percentage never exceeds the base`() {
        val promo = categoryPromo(threshold = 1, percent = 10_000)
        assertEquals(1_000L, onlyMatch(listOf(line(coffee, 1)), listOf(promo)).discountCents)
    }

    // ---- Product promos ----------------------------------------------------

    @Test
    fun `a product promo only counts its chosen products`() {
        val promo = Promo(
            id = 2,
            name = "Coffee only",
            kind = PromoKind.PRODUCTS,
            productIdsCsv = "${coffee.id}",
            thresholdQuantity = 3,
            discountType = DiscountType.PERCENT,
            discountPercentBasisPoints = 2_000,
        )
        // Coffee + water = 3 units, but only the coffees qualify.
        assertTrue(
            PromoEngine
                .applyPromos(listOf(line(coffee, 2), line(water, 1)), listOf(promo), catalogue, 0L)
                .isEmpty()
        )
        assertEquals(600L, onlyMatch(listOf(line(coffee, 3)), listOf(promo)).discountCents)
    }

    @Test
    fun `a product promo with no products never applies`() {
        val promo = Promo(
            id = 3,
            name = "Empty",
            kind = PromoKind.PRODUCTS,
            productIdsCsv = "",
            thresholdQuantity = 1,
            discountType = DiscountType.PERCENT,
            discountPercentBasisPoints = 1_000,
        )
        assertTrue(
            PromoEngine.applyPromos(listOf(line(coffee, 5)), listOf(promo), catalogue, 0L).isEmpty()
        )
    }

    // ---- Stacking ----------------------------------------------------------

    @Test
    fun `two promos on different categories both apply`() {
        val drinkDeal = categoryPromo(id = 1, name = "Drinks", categoryId = drinks)
        val bakeryDeal = categoryPromo(id = 2, name = "Bakery", categoryId = bakery)
        val cart = listOf(line(coffee, 3), line(bread, 3))

        val matches = PromoEngine.applyPromos(
            cart,
            listOf(drinkDeal, bakeryDeal),
            catalogue,
            0L,
        )

        assertEquals(2, matches.size)
        // 10% of 3000 coffee + 10% of 900 bread.
        assertEquals(390L, matches.sumOf { it.discountCents })
    }

    @Test
    fun `each stacked promo discounts only its own lines`() {
        val drinkDeal = categoryPromo(id = 1, categoryId = drinks, percent = 1_000)
        val bakeryDeal = categoryPromo(id = 2, categoryId = bakery, percent = 5_000)
        val cart = listOf(line(coffee, 3), line(bread, 3))

        val matches =
            PromoEngine.applyPromos(cart, listOf(drinkDeal, bakeryDeal), catalogue, 0L)
                .associateBy { it.promo.id }

        // Coffee 3000 at 10% = 300; bread 900 at 50% = 450. No cross-contamination.
        assertEquals(300L, matches.getValue(1L).discountCents)
        assertEquals(450L, matches.getValue(2L).discountCents)
    }

    @Test
    fun `two promos over the same items cannot discount more than those items cost`() {
        // The core safety property. 3 coffees = 3000. A 60% promo takes 1800, leaving 1200.
        // The second 60% promo may only take 60% of the 1200 that is left, not of the original.
        val first = categoryPromo(id = 1, percent = 6_000)
        val second = categoryPromo(id = 2, percent = 6_000)
        val cart = listOf(line(coffee, 3))

        val matches = PromoEngine.applyPromos(cart, listOf(first, second), catalogue, 0L)

        assertEquals(2, matches.size)
        assertEquals(1_800L, matches[0].discountCents)
        assertEquals(720L, matches[1].discountCents)
        // Total given away never exceeds what the goods are worth.
        assertTrue(matches.sumOf { it.discountCents } <= 3_000L)
    }

    @Test
    fun `a second fixed promo on exhausted items yields nothing`() {
        // 100% off already takes everything; a second "$5 off" has no base left to work on.
        val full = categoryPromo(id = 1, threshold = 1, percent = 10_000)
        val fixed = categoryPromo(id = 2, threshold = 1).copy(
            discountType = DiscountType.FIXED,
            discountFixedCents = 500,
        )
        val cart = listOf(line(coffee, 1))

        val matches = PromoEngine.applyPromos(cart, listOf(full, fixed), catalogue, 0L)

        assertEquals(1, matches.size)
        assertEquals(full.id, matches.single().promo.id)
    }

    @Test
    fun `stacking results are independent of input order`() {
        val a = categoryPromo(id = 1, percent = 6_000)
        val b = categoryPromo(id = 2, percent = 6_000)
        val cart = listOf(line(coffee, 3))

        val forward = PromoEngine.applyPromos(cart, listOf(a, b), catalogue, 0L)
        val reverse = PromoEngine.applyPromos(cart, listOf(b, a), catalogue, 0L)

        assertEquals(
            forward.associate { it.promo.id to it.discountCents },
            reverse.associate { it.promo.id to it.discountCents },
        )
    }

    @Test
    fun `a partly overlapping promo only claims what is left`() {
        // Promo 1 covers coffee only; promo 2 covers the whole drinks category. The coffee is
        // already 50% gone, so the wider promo must work from the remainder, not the full price.
        val coffeeOnly = Promo(
            id = 1,
            name = "Coffee",
            kind = PromoKind.PRODUCTS,
            productIdsCsv = "${coffee.id}",
            thresholdQuantity = 1,
            discountType = DiscountType.PERCENT,
            discountPercentBasisPoints = 5_000,
        )
        val allDrinks = categoryPromo(id = 2, threshold = 1, percent = 1_000)
        val cart = listOf(line(coffee, 1), line(water, 1))

        val matches = PromoEngine.applyPromos(cart, listOf(coffeeOnly, allDrinks), catalogue, 0L)
            .associateBy { it.promo.id }

        assertEquals(500L, matches.getValue(1L).discountCents)
        // Coffee has 500 left, water still has 500: base 1000, 10% = 100.
        assertEquals(100L, matches.getValue(2L).discountCents)
    }

    // ---- Scheduling --------------------------------------------------------

    @Test
    fun `a disabled promo never applies`() {
        val cart = listOf(line(coffee, 3))
        assertTrue(
            PromoEngine
                .applyPromos(cart, listOf(categoryPromo(enabled = false)), catalogue, 0L)
                .isEmpty()
        )
    }

    @Test
    fun `a promo before its start date does not apply`() {
        val promo = categoryPromo(startsAt = 10_000)
        val cart = listOf(line(coffee, 3))
        assertTrue(PromoEngine.applyPromos(cart, listOf(promo), catalogue, 9_999).isEmpty())
        assertEquals(1, PromoEngine.applyPromos(cart, listOf(promo), catalogue, 10_000).size)
    }

    @Test
    fun `a promo after its end date does not apply`() {
        val promo = categoryPromo(endsAt = 20_000)
        val cart = listOf(line(coffee, 3))
        assertEquals(1, PromoEngine.applyPromos(cart, listOf(promo), catalogue, 20_000).size)
        assertTrue(PromoEngine.applyPromos(cart, listOf(promo), catalogue, 20_001).isEmpty())
    }

    @Test
    fun `status reflects the clock`() {
        val now = 15_000L
        assertEquals(PromoStatus.OFF, PromoStatus.of(categoryPromo(enabled = false), now))
        assertEquals(PromoStatus.SCHEDULED, PromoStatus.of(categoryPromo(startsAt = 20_000), now))
        assertEquals(PromoStatus.EXPIRED, PromoStatus.of(categoryPromo(endsAt = 10_000), now))
        assertEquals(PromoStatus.ACTIVE, PromoStatus.of(categoryPromo(), now))
    }

    // ---- Edge cases --------------------------------------------------------

    @Test
    fun `an empty cart matches nothing`() {
        assertTrue(PromoEngine.applyPromos(emptyList(), listOf(categoryPromo()), catalogue, 0L).isEmpty())
    }

    @Test
    fun `a line whose product is missing is ignored rather than throwing`() {
        val orphan = CartLine(
            productId = 999,
            productName = "Gone",
            barcode = "X",
            unitPriceCents = 100,
            quantity = 10,
            availableStock = 10,
        )
        assertTrue(PromoEngine.applyPromos(listOf(orphan), listOf(categoryPromo()), catalogue, 0L).isEmpty())
    }

    @Test
    fun `a zero percent promo yields no match`() {
        // It technically qualifies, but saving nothing is not worth showing as "applied".
        val cart = listOf(line(coffee, 3))
        assertTrue(
            PromoEngine.applyPromos(cart, listOf(categoryPromo(percent = 0)), catalogue, 0L).isEmpty()
        )
    }

    // ---- The near-miss hint ------------------------------------------------

    @Test
    fun `the next promo reports how many units are missing`() {
        val cart = listOf(line(coffee, 2))
        val next = PromoEngine.nextPromo(cart, listOf(categoryPromo()), catalogue, 0L)
        assertNotNull(next)
        assertEquals(2, next!!.qualifyingQuantity)
    }

    @Test
    fun `there is no hint once the promo applies`() {
        val cart = listOf(line(coffee, 3))
        assertNull(PromoEngine.nextPromo(cart, listOf(categoryPromo()), catalogue, 0L))
    }

    @Test
    fun `there is no hint when nothing from the promo is in the cart`() {
        val cart = listOf(line(bread, 1))
        assertNull(PromoEngine.nextPromo(cart, listOf(categoryPromo()), catalogue, 0L))
    }

    @Test
    fun `an already-applied promo is not offered as the next one`() {
        // With stacking, a second promo can still be one unit away — but the one already
        // discounting the cart must not advertise itself as a new opportunity.
        val bakeryDeal = categoryPromo(id = 2, categoryId = bakery)
        val cart = listOf(line(bread, 1))
        val next = PromoEngine.nextPromo(
            lines = cart,
            promos = listOf(bakeryDeal),
            productsById = catalogue,
            now = 0L,
            applied = setOf(bakeryDeal.id),
        )
        assertNull(next)
    }

    // ---- Totals integration ------------------------------------------------

    @Test
    fun `the discount is deducted before tax`() {
        // The customer is taxed on what they actually pay, not on the pre-discount amount.
        val totals = computeTotals(
            itemsSubtotalCents = 10_000,
            taxRateBasisPoints = 1_000, // 10%
            cardFeeRateBasisPoints = 0,
            paymentMethod = PaymentMethod.CASH,
            promoDiscountCents = 2_000,
        )
        assertEquals(2_000L, totals.promoDiscountCents)
        // 10% of 8000, not of 10000.
        assertEquals(800L, totals.taxCents)
        assertEquals(7_200L, totals.totalCents)
    }

    @Test
    fun `without a promo the totals are unchanged`() {
        val withPromo = computeTotals(10_000, 1_000, 0, PaymentMethod.CASH, promoDiscountCents = 0)
        val legacy = computeTotals(10_000, 1_000, 0, PaymentMethod.CASH)
        assertEquals(legacy, withPromo)
    }

    @Test
    fun `a discount larger than the subtotal cannot make the total negative`() {
        val totals = computeTotals(
            itemsSubtotalCents = 1_000,
            taxRateBasisPoints = 1_000,
            cardFeeRateBasisPoints = 500,
            paymentMethod = PaymentMethod.CARD,
            promoDiscountCents = 99_999,
        )
        assertEquals(1_000L, totals.promoDiscountCents)
        assertEquals(0L, totals.totalCents)
    }

    @Test
    fun `the card fee is charged on the discounted amount`() {
        val totals = computeTotals(
            itemsSubtotalCents = 10_000,
            taxRateBasisPoints = 0,
            cardFeeRateBasisPoints = 1_000,
            paymentMethod = PaymentMethod.CARD,
            promoDiscountCents = 5_000,
        )
        // 10% of the 5000 actually billed, not of 10000.
        assertEquals(500L, totals.cardFeeCents)
        assertEquals(4_500L, totals.totalCents)
    }

    @Test
    fun `the discounted subtotal is exposed for display`() {
        val totals = computeTotals(5_000, 0, 0, PaymentMethod.CASH, promoDiscountCents = 1_000)
        assertEquals(4_000L, totals.discountedSubtotalCents)
    }

    @Test
    fun `a promo discount larger than the whole cart is rejected on checkout totals`() {
        // A misconfigured promo must never turn a sale into a payout.
        val totals = computeTotals(2_000, 0, 0, PaymentMethod.CASH, promoDiscountCents = 5_000)
        assertTrue(totals.totalCents >= 0)
        assertEquals(2_000L, totals.promoDiscountCents)
    }
}
