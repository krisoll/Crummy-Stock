package com.example.stockapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** How the customer settled the bill. [CARD] incurs the configured card fee. */
enum class PaymentMethod { CASH, CARD }

/**
 * A completed checkout: one cart, one payment method, one set of totals.
 *
 * The individual [Sale] rows remain the line-item ledger (and what cancellation restores stock
 * from). This entity holds the order-level figures so a receipt can be reproduced exactly, even
 * after the tax or card-fee rates are changed in settings.
 *
 * All money is in cents. [totalCents] is what the shop actually nets:
 * `items − promoDiscount − tax − cardFee`.
 *
 * [promoDiscountCents] and [promoName] record the promo that was applied, if any. They are
 * snapshotted onto the order rather than looked up from the `promos` table, for the same reason the
 * rates are: deleting or renaming a promo later must not rewrite history, and a receipt reprinted
 * next month has to show what was actually given.
 *
 * [cardFeeFixedCents] records the flat per-transaction fee that was in force at checkout, so the
 * receipt still adds up after the config changes.
 *
 * [note] is a free-text memo the cashier can attach before charging (a customer name, a tab
 * number, "paid in two parts“…). It is stored on the order rather than the line so it always
 * describes the whole sale, and it is trimmed on entry — blank means "no note".
 */
@Entity(tableName = "sale_orders")
data class SaleOrder(
    @PrimaryKey val orderId: String,
    val soldAt: Long,
    val paymentMethod: PaymentMethod,
    val itemsSubtotalCents: Long,
    /** Promo saving deducted from the subtotal before tax. Zero when no promo applied. */
    val promoDiscountCents: Long = 0,
    /** Name of the promo that applied, for the receipt. Empty when none did. */
    val promoName: String = "",
    val taxRateBasisPoints: Int,
    val taxCents: Long,
    val cardFeeRateBasisPoints: Int,
    /** Flat card fee applied to this order, in cents. Zero for cash. */
    val cardFeeFixedCents: Long = 0,
    val cardFeeCents: Long,
    val totalCents: Long,
    val itemCount: Int,
    /** Free-text memo for the whole order. Empty when the cashier left it blank. */
    val note: String = "",
)

/**
 * Rates are stored in basis points (1/100th of a percent) so 2.75% is `275` — integers survive
 * DataStore round-tripping and avoid the drift of storing floats.
 */
fun percentToBasisPoints(percent: Double): Int =
    Math.round(percent * 100.0).toInt().coerceIn(0, 10_000)

fun basisPointsToPercent(basisPoints: Int): Double = basisPoints / 100.0

/** Applies a basis-point rate to [cents]. Always truncates, so rounding never inflates a charge. */
fun applyRateBasisPoints(cents: Long, basisPoints: Int): Long {
    if (cents <= 0 || basisPoints <= 0) return 0L
    return (cents * basisPoints) / 10_000L
}

/** The money breakdown for a cart, computed from the subtotal and the configured rates. */
data class SaleTotals(
    val itemsSubtotalCents: Long,
    /** Promo saving, deducted from the subtotal before tax. Zero when no promo applied. */
    val promoDiscountCents: Long,
    val taxCents: Long,
    /** Percentage portion of the card fee, in cents. */
    val cardFeeRateCents: Long,
    /** Flat portion of the card fee, in cents (zero for cash, or when unconfigured). */
    val cardFeeFixedCents: Long,
    val totalCents: Long,
) {
    /** The whole card deduction: percentage plus flat. */
    val cardFeeCents: Long get() = cardFeeRateCents + cardFeeFixedCents

    /** What the customer pays for goods, after the promo but before tax and card fee. */
    val discountedSubtotalCents: Long
        get() = (itemsSubtotalCents - promoDiscountCents).coerceAtLeast(0L)

    /**
     * What to charge: the goods minus any promo, and **nothing else**.
     *
     * Deliberately identical to [discountedSubtotalCents]. Tax and the card fee are the shop's
     * costs of doing business, not something the till adds on — the cashier asks for what the goods
     * come to and the rest comes out of it. Adding them back here (as this property briefly did)
     * produced a figure nobody owed.
     *
     * Kept as its own name rather than inlined so the cart panel reads as "what to charge" and not
     * "the discounted subtotal" — the two agree today, and a reader can see that is intentional.
     */
    val amountDueCents: Long
        get() = discountedSubtotalCents
}

/**
 * Computes the cart totals.
 *
 * Per the app's accounting model both the tax and the card fee come *off* the money the shop
 * keeps: `total = items − promo − tax − cardFee`. The card fee only applies to
 * [PaymentMethod.CARD], and is the sum of a percentage of the discounted subtotal and a flat
 * per-transaction charge.
 *
 * **The promo is deducted before tax**, so the customer is taxed on what they actually pay. That
 * is the point of a discount from the customer's side: charging tax on the pre-discount amount
 * would make a "10% off" promo save less than 10% at the till. The card fee is likewise computed
 * on the discounted amount, since the processor charges on what is actually billed.
 *
 * @param itemsSubtotalCents sum of price × quantity for every line in the cart
 * @param taxRateBasisPoints general tax rate, always applied
 * @param cardFeeRateBasisPoints percentage card surcharge, applied only when paying by card
 * @param paymentMethod the method the user selected
 * @param cardFeeFixedCents flat card surcharge in cents, applied only when paying by card
 * @param promoDiscountCents promo saving in cents, deducted from the subtotal before tax
 */
fun computeTotals(
    itemsSubtotalCents: Long,
    taxRateBasisPoints: Int,
    cardFeeRateBasisPoints: Int,
    paymentMethod: PaymentMethod,
    cardFeeFixedCents: Long = 0L,
    promoDiscountCents: Long = 0L,
): SaleTotals {
    val subtotal = itemsSubtotalCents.coerceAtLeast(0L)
    // A discount can never exceed the goods it applies to.
    val discount = promoDiscountCents.coerceIn(0L, subtotal)
    val taxable = subtotal - discount

    val tax = applyRateBasisPoints(taxable, taxRateBasisPoints)
    val isCard = paymentMethod == PaymentMethod.CARD
    val feeRate = if (isCard) applyRateBasisPoints(taxable, cardFeeRateBasisPoints) else 0L
    val feeFixed = if (isCard) cardFeeFixedCents.coerceIn(0L, MaxCardFeeFixedCents) else 0L
    val fee = feeRate + feeFixed
    // Never let deductions push the net below zero.
    val total = (taxable - tax - fee).coerceAtLeast(0L)
    return SaleTotals(
        itemsSubtotalCents = subtotal,
        promoDiscountCents = discount,
        taxCents = tax,
        cardFeeRateCents = feeRate,
        cardFeeFixedCents = feeFixed,
        totalCents = total,
    )
}
