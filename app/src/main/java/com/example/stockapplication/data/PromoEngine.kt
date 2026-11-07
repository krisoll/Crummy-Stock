package com.example.stockapplication.data

/**
 * A promo that applies to a particular cart, and what it saves.
 *
 * @param qualifyingQuantity units in the cart that earned this promo — a whole multiple of the
 *   promo's threshold, because a "2 for 10% off" deal on 5 units is only *paid* on the 4 that
 *   formed complete groups. Carried for display and so the cart can explain why the discount
 *   appeared.
 * @param discountCents money taken off the net total. Never more than the qualifying base.
 * @param groupCount how many times the promo fired, i.e. how many complete threshold groups the
 *   cart contained. 1 for the ordinary case; 2 when "buy 2 get 10% off" meets 4 units.
 */
data class PromoMatch(
    val promo: Promo,
    val qualifyingQuantity: Int,
    val discountCents: Long,
    val groupCount: Int = 1,
)

/**
 * Decides which promo — if any — applies to a cart.
 *
 * Deliberately **pure**: no Room, no Android, no clock of its own (the caller passes `now`). That
 * makes the whole discount policy testable without a database, and means the cart can recompute it
 * on every scan without touching disk.
 *
 * ## The rules
 *
 * 1. Only promos active at `now` are considered — enabled, and inside their date window.
 * 2. A promo counts the units in the cart that belong to it: every line whose product is in the
 *    promo's category, or in its chosen product set. **Mixed products count towards the same
 *    total**, so "any 3 drinks" is satisfied by two coffees and a water.
 * 3. If those units are below the threshold the promo does not apply.
 * 4. The discount is computed on the **qualifying lines only**, never the whole cart — a coffee
 *    promo must not discount the milk that happens to be in the same basket.
 * 5. **Promos stack.** Every qualifying promo is applied, each to its own qualifying lines, so a
 *    coffee promo and a pastries promo both apply to the same basket. A promo may never discount
 *    more than the value of the items it covers, and two promos covering the *same* items cannot
 *    both take money off them — see [applyPromos] for exactly how that is bounded.
 */
object PromoEngine {

    /**
     * Every promo that applies to [lines], in the order they were applied, or an empty list.
     *
     * @param productsById the catalogue, needed to resolve each line's category. Lines whose
     *   product is missing are ignored — the cart is reconciled separately, and a promo must not
     *   throw because inventory changed underneath it.
     * @param now epoch millis, passed in rather than read so tests are deterministic.
     */
    fun applyPromos(
        lines: List<CartLine>,
        promos: List<Promo>,
        productsById: Map<Long, Product>,
        now: Long,
    ): List<PromoMatch> {
        if (lines.isEmpty()) return emptyList()

        // What each line is still worth to a promo. A line starts at its own price; once one promo
        // has taken money off it, a second promo covering the same line can only claim the rest.
        // This is what stops two "$1 off everything" promos turning a $1.50 item into free money,
        // while still letting a coffee promo and a separate pastry promo both apply.
        val remainingByLine = lines.associate { it.productId to it.lineTotalCents }.toMutableMap()

        val applied = mutableListOf<PromoMatch>()
        // Sorted so the outcome cannot depend on the order Room happened to return the rows in.
        // Applying in a fixed order also makes the "who gets there first" of overlapping promos
        // deterministic, which matters once two of them can both claim the same item.
        for (promo in promos.sortedBy { it.id }) {
            if (!promo.isActiveAt(now)) continue

            val qualifying = qualifyingLines(promo, lines, productsById)
            if (qualifying.isEmpty()) continue
            val quantity = qualifying.sumOf { it.quantity }
            val threshold = promo.thresholdQuantity.coerceAtLeast(1)

            // How many times the promo fires. "2 for 10% off" on 4 units is two complete groups
            // and saves twice; on 5 units it is two groups plus a leftover unit that is only
            // worth anything once it can form a third group.
            val groups = quantity / threshold
            if (groups < 1) continue

            // The base is the value of the units in the *earning* groups only. A basket of 3
            // against a "2 for 10% off" deal discounts the 2 that earned it and leaves the third
            // at full price, rather than discounting all 3 and giving away a group nobody bought.
            val earningUnits = groups * threshold
            val base = valueOfEarningUnits(
                qualifying = qualifying,
                earningUnits = earningUnits,
                remainingByLine = remainingByLine,
            )
            if (base <= 0L) continue

            // A fixed amount is per *group*: "1000 off when you buy 2" on 4 units is 2000 off. A
            // percentage needs no multiplier — computing it on the whole earning base already
            // scales with the number of groups. The result is still capped at the base, so a
            // repeated fixed amount can never discount more than the goods are worth.
            val perGroup = discountFor(promo, base)
            val discount = if (promo.discountType == DiscountType.FIXED) {
                (perGroup * groups).coerceAtMost(base)
            } else {
                perGroup
            }
            if (discount <= 0L) continue

            // Take the money off the lines this promo covers, proportionally to what each has
            // left, so an overlapping promo later sees an accurate remainder.
            consume(qualifying, remainingByLine, base, discount, earningUnits)

            applied += PromoMatch(
                promo = promo,
                qualifyingQuantity = earningUnits,
                discountCents = discount,
                groupCount = groups,
            )
        }
        return applied
    }

    /**
     * What the first [earningUnits] units of [qualifying] are worth, using what each line has
     * **left** after promos already applied.
     *
     * Counted unit by unit rather than per line because a line mixes earning and non-earning units:
     * 5 coffees against a "2 for 10% off" deal has 4 earning and 1 that is not. Taking unit prices
     * in the order the lines appear keeps the arithmetic simple and the result deterministic.
     */
    private fun valueOfEarningUnits(
        qualifying: List<CartLine>,
        earningUnits: Int,
        remainingByLine: Map<Long, Long>,
    ): Long {
        var unitsLeft = earningUnits
        var total = 0L
        for (line in qualifying) {
            if (unitsLeft <= 0) break
            val remainingTotal = remainingByLine[line.productId] ?: line.lineTotalCents
            if (line.quantity <= 0) continue

            // What one unit of this line is still worth, after anything already taken off it.
            val unitLeft = remainingTotal / line.quantity
            val take = minOf(unitsLeft, line.quantity)
            total += unitLeft * take
            unitsLeft -= take
        }
        return total
    }

    /**
     * Deducts [discount] from the covered [lines]' remaining value, in proportion to what each has
     * left, so the total deducted is exactly [discount].
     *
     * Proportional rather than "first line absorbs it all": with a partial discount, draining one
     * line completely would wrongly tell a later promo that line is worthless while its neighbour
     * is untouched.
     */
    private fun consume(
        lines: List<CartLine>,
        remainingByLine: MutableMap<Long, Long>,
        base: Long,
        discount: Long,
        earningUnits: Int,
    ) {
        if (base <= 0L || discount <= 0L) return
        var given = 0L
        var unitsLeft = earningUnits
        lines.forEachIndexed { index, line ->
            val remaining = remainingByLine[line.productId] ?: return@forEachIndexed
            if (unitsLeft <= 0) return@forEachIndexed

            // Only the units in complete groups may absorb the discount. A line marked in
            // `unitsLeft` is partially earning, so the share is scaled to the earning fraction:
            // in 5 coffees against a "2 for 10% off" deal, only 4 of the 5 units are discounted.
            val earningFraction = minOf(unitsLeft, line.quantity)
            val earningRemaining = if (line.quantity <= 0) {
                0L
            } else {
                val unitLeft = remaining / line.quantity
                (unitLeft * earningFraction).coerceAtMost(remaining)
            }
            unitsLeft -= earningFraction
            if (earningRemaining <= 0L) return@forEachIndexed

            // The last earning line takes whatever is left, so division cannot lose a cent.
            val isLastEarning = unitsLeft <= 0 || index == lines.lastIndex
            val share = if (isLastEarning) {
                discount - given
            } else {
                (discount * earningRemaining / base).coerceAtMost(earningRemaining)
            }
            val take = share.coerceIn(0L, earningRemaining)
            remainingByLine[line.productId] = remaining - take
            given += take
        }
    }

    /**
     * Evaluates one promo against the cart.
     *
     * Evaluates this promo against the cart's **original** line values, ignoring whether other
     * promos have already discounted them. Used by [nextPromo] to size a hint. The authoritative
     * stacking arithmetic lives in [applyPromos], which does track what is left.
     *
     * @return null when the promo does not qualify, otherwise the match (whose
     *   [PromoMatch.discountCents] may still be 0 for a misconfigured promo such as a 0% discount,
     *   which [applyPromos] then discards).
     */
    fun matchFor(
        promo: Promo,
        lines: List<CartLine>,
        productsById: Map<Long, Product>,
    ): PromoMatch? {
        val qualifying = qualifyingLines(promo, lines, productsById)
        if (qualifying.isEmpty()) return null

        val quantity = qualifying.sumOf { it.quantity }
        val threshold = promo.thresholdQuantity.coerceAtLeast(1)
        // Same group arithmetic as [applyPromos], so a hint and the eventual charge agree.
        val groups = quantity / threshold
        if (groups < 1) return null

        val earningUnits = groups * threshold
        var unitsLeft = earningUnits
        var base = 0L
        for (line in qualifying) {
            if (unitsLeft <= 0) break
            if (line.quantity <= 0) continue
            val take = minOf(unitsLeft, line.quantity)
            base += (line.lineTotalCents / line.quantity) * take
            unitsLeft -= take
        }
        return PromoMatch(
            promo = promo,
            qualifyingQuantity = earningUnits,
            discountCents = discountFor(promo, base),
            groupCount = groups,
        )
    }

    /** The cart lines this promo counts units from. */
    fun qualifyingLines(
        promo: Promo,
        lines: List<CartLine>,
        productsById: Map<Long, Product>,
    ): List<CartLine> = when (promo.kind) {
        PromoKind.CATEGORY -> {
            val categoryId = promo.categoryId ?: return emptyList()
            lines.filter { productsById[it.productId]?.categoryId == categoryId }
        }

        PromoKind.PRODUCTS -> {
            val ids = promo.productIdSet
            if (ids.isEmpty()) emptyList() else lines.filter { it.productId in ids }
        }
    }

    /**
     * The money taken off for a promo on a qualifying base.
     *
     * A fixed amount is **capped at the base**: "$5 off" on a $3 basket saves $3, never $5. Without
     * the cap the net total could go negative and the shop would be paying the customer.
     *
     * Percentages reuse [applyRateBasisPoints] so promo arithmetic truncates exactly like tax and
     * card-fee arithmetic — one rounding rule across the whole app.
     */
    fun discountFor(promo: Promo, qualifyingBaseCents: Long): Long {
        if (qualifyingBaseCents <= 0L) return 0L
        val raw = when (promo.discountType) {
            DiscountType.PERCENT ->
                applyRateBasisPoints(qualifyingBaseCents, promo.discountPercentBasisPoints)

            DiscountType.FIXED -> promo.discountFixedCents.coerceAtLeast(0L)
        }
        return raw.coerceIn(0L, qualifyingBaseCents)
    }

    /**
     * The next promo the cart would unlock, and how many more units it needs.
     *
     * This exists because a promo that only appears *after* it is earned is a promo the cashier
     * discovers too late. The cart uses it for an "add 1 more for 10% off" hint.
     *
     * Promos that are already applied are skipped: with stacking on, advertising "add 1 more for
     * 10% off" for a promo that is already discounting the cart would be misleading.
     *
     * Returns null when nothing is close (only gaps of [lookahead] units or fewer qualify) or when
     * the cart has no qualifying lines for it at all.
     *
     * @param applied ids of promos already discounting this cart, from [applyPromos].
     */
    fun nextPromo(
        lines: List<CartLine>,
        promos: List<Promo>,
        productsById: Map<Long, Product>,
        now: Long,
        lookahead: Int = 1,
        applied: Set<Long> = emptySet(),
    ): PromoMatch? {
        if (lines.isEmpty()) return null

        var closest: PromoMatch? = null
        var closestGap = Int.MAX_VALUE

        for (promo in promos.sortedBy { it.id }) {
            if (!promo.isActiveAt(now)) continue
            if (promo.id in applied) continue
            val qualifying = qualifyingLines(promo, lines, productsById)
            if (qualifying.isEmpty()) continue

            val quantity = qualifying.sumOf { it.quantity }
            val threshold = promo.thresholdQuantity.coerceAtLeast(1)
            // Units still needed for the *next* complete group. With 3 units against a "2 for"
            // deal the cart already earns one group, so one more unit earns a second — reporting
            // the raw threshold gap would claim 3 more were needed.
            val gap = threshold - (quantity % threshold).let { if (it == 0) threshold else it }
            if (gap <= 0 || gap > lookahead) continue

            val base = qualifying.sumOf { it.lineTotalCents }
            // What it *would* save, measured on the base as it stands now: an honest estimate that
            // does not promise a figure the shop could not honour.
            val potential = discountFor(promo, base)
            if (gap < closestGap) {
                closestGap = gap
                // The cart's *current* count, not the projected one. The caller derives the
                // shortfall as `threshold - qualifyingQuantity`, so reporting the post-addition
                // figure would tell the cashier the wrong number of items is still needed.
                closest = PromoMatch(
                    promo = promo,
                    qualifyingQuantity = quantity,
                    discountCents = potential,
                    groupCount = quantity / threshold,
                )
            }
        }
        return closest
    }
}
