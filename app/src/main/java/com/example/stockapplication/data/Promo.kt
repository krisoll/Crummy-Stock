package com.example.stockapplication.data

import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.stockapplication.R

/** Which products a promo counts units from. */
enum class PromoKind(@param:StringRes val labelRes: Int) {
    /** Any X units from a category, mixed products allowed. */
    CATEGORY(R.string.promo_kind_category),

    /** Any X units from a hand-picked set of products. */
    PRODUCTS(R.string.promo_kind_products),
}

/** How the discount is expressed. */
enum class DiscountType(@param:StringRes val labelRes: Int) {
    PERCENT(R.string.promo_discount_percent),
    FIXED(R.string.promo_discount_fixed),
}

/**
 * A discount that applies itself when the cart holds enough of something.
 *
 * **Why the product set is a CSV column rather than a join table.** A promo's product list is
 * small, always read as a whole, and never queried *by product* — nothing ever asks "which promos
 * mention product 7?". A join table would add an entity, a DAO, a migration and a second
 * transaction on every save to answer a question nobody asks. The list is parsed in exactly one
 * place, [productIdList].
 *
 * **Why [startsAt] / [endsAt] are nullable rather than 0/max.** "No start bound" and "starts at
 * epoch 0" are different statements, and the list UI needs to tell them apart to show
 * *Scheduled* versus *Active*.
 *
 * A promo is stored as *rules*, never as a computed amount: the discount depends on the cart it is
 * applied to, so it is evaluated on every cart change by [PromoEngine].
 */
@Entity(tableName = "promos")
data class Promo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val kind: PromoKind = PromoKind.CATEGORY,
    /** Set when [kind] is [PromoKind.CATEGORY]; null for a product promo. */
    val categoryId: Long? = null,
    /** Comma-separated product ids, used when [kind] is [PromoKind.PRODUCTS]. */
    val productIdsCsv: String = "",
    /** How many qualifying units the cart needs. 1 is meaningless but harmless. */
    val thresholdQuantity: Int = 1,
    val discountType: DiscountType = DiscountType.PERCENT,
    /** Percentage off, in basis points: 1000 == 10%. Only read for [DiscountType.PERCENT]. */
    val discountPercentBasisPoints: Int = 0,
    /** Amount off, in cents. Only read for [DiscountType.FIXED]. */
    val discountFixedCents: Long = 0L,
    val enabled: Boolean = true,
    /** Epoch millis the promo becomes active, or null for "right away". */
    val startsAt: Long? = null,
    /** Epoch millis the promo stops being active, or null for "until switched off". */
    val endsAt: Long? = null,
) {
    /** The picked products, parsed. Empty for a category promo. */
    val productIdList: List<Long>
        get() = productIdsCsv
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }

    /** The product ids as a set, for the engine's membership test. */
    val productIdSet: Set<Long> get() = productIdList.toSet()

    /**
     * Whether this promo is active at [now].
     *
     * Both bounds are inclusive and independently optional, so a promo can start yesterday and
     * never end, start now and end Friday, or simply be left undated and controlled by [enabled].
     */
    fun isActiveAt(now: Long): Boolean =
        enabled &&
            (startsAt == null || now >= startsAt) &&
            (endsAt == null || now <= endsAt)

    /** The percentage as a whole-percent readout, for the editor field. */
    val discountPercentWhole: Int get() = discountPercentBasisPoints / 100

    /** Writes the product ids back out. Kept here so the format lives in one place. */
    fun withProductIds(ids: Collection<Long>): Promo =
        copy(productIdsCsv = ids.joinToString(","))
}

/**
 * Where a promo sits relative to the clock.
 *
 * Derived rather than stored: "expired" is a function of [Promo.endsAt] and the current time, and
 * a stored copy would go stale the moment the clock passed the boundary.
 */
enum class PromoStatus(@param:StringRes val labelRes: Int) {
    ACTIVE(R.string.promo_status_active),
    SCHEDULED(R.string.promo_status_scheduled),
    EXPIRED(R.string.promo_status_expired),
    OFF(R.string.promo_status_off),
    ;

    companion object {
        fun of(promo: Promo, now: Long): PromoStatus = when {
            !promo.enabled -> OFF
            promo.startsAt != null && now < promo.startsAt -> SCHEDULED
            promo.endsAt != null && now > promo.endsAt -> EXPIRED
            else -> ACTIVE
        }
    }
}
