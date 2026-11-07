package com.example.stockapplication.data

import java.util.Calendar

/** One product's contribution to a day's sales, used for the "sold the most" ranking. */
data class ProductSalesRow(
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val revenueCents: Long,
)

/** Revenue for one hour of the day, for the hourly bar chart. */
data class HourBucket(
    val hour: Int,
    val revenueCents: Long,
    val itemCount: Int,
)

/** Revenue split by payment method. */
data class PaymentSplit(
    val cashCents: Long,
    val cardCents: Long,
) {
    val totalCents: Long get() = cashCents + cardCents
}

/**
 * One row of the itemised sales table: a product and everything it contributed to the day.
 *
 * Aggregated **across orders** by product, because the table answers "what did I sell today",
 * not "what happened on each ticket". Tax and card fee are therefore sums of the per-order
 * portions, not a single rate applied to the totals — see [buildDailyReport] for why that
 * distinction matters.
 *
 * @param taxRateBasisPoints the rate charged, in basis points. Only meaningful when every order
 *   containing this product used the same rate; otherwise [mixedTaxRate] is true and the report
 *   prints a dash rather than inventing a blended rate.
 * @param totalCents what the shop actually kept from this product, after its share of the tax and
 *   the card fee.
 */
data class ReportLineRow(
    val productId: Long,
    val productName: String,
    val unitPriceCents: Long,
    val quantity: Int,
    val amountCents: Long,
    val taxRateBasisPoints: Int,
    val taxCents: Long,
    val cardFeeCents: Long,
    val totalCents: Long,
) {
    /** True when this product was sold under more than one tax rate in the same day. */
    val mixedTaxRate: Boolean get() = taxRateBasisPoints < 0
}

/**
 * Everything the daily report screen draws, derived once from a day's orders.
 *
 * Kept as a plain data class so the numbers can be unit-tested without Compose.
 */
data class DailyReport(
    val dayStartMillis: Long,
    val orderCount: Int,
    val itemCount: Int,
    val grossCents: Long,
    val taxCents: Long,
    /** Percentage portion of the card fees charged today. */
    val cardFeeRateCents: Long,
    /** Flat portion of the card fees charged today. */
    val cardFeeFixedCents: Long,
    val netCents: Long,
    /**
     * What promotions took off today, before tax and card fees.
     *
     * Already netted out of [netCents]; reported separately so the analytics screen can show it as
     * its own metric. This is the one figure on [SaleOrder] that was never aggregated anywhere,
     * which meant the app could not answer "what did my discounts cost me this week".
     */
    val promoDiscountCents: Long,
    /** Products ranked by units sold, highest first. */
    val topProducts: List<ProductSalesRow>,
    /** Hours that actually had sales, sorted by hour. */
    val hours: List<HourBucket>,
    val paymentSplit: PaymentSplit,
    /** Every product sold today, ranked by revenue, for the itemised table. */
    val lines: List<ReportLineRow> = emptyList(),
    /** Total units across all lines; the table's footer total. */
    val lineQuantityTotal: Int = lines.sumOf { it.quantity },
) {
    val isEmpty: Boolean get() = orderCount == 0

    /** Whole card deduction for the day: percentage plus flat. */
    val cardFeeCents: Long get() = cardFeeRateCents + cardFeeFixedCents

    /** The best-selling product by units, or null for an empty day. */
    val bestSeller: ProductSalesRow? get() = topProducts.firstOrNull()

    /** The busiest hour by revenue, or null for an empty day. */
    val busiestHour: HourBucket? get() = hours.maxByOrNull { it.revenueCents }

    /** Sum of every line's amount, excluding tax and fees. */
    val linesAmountCents: Long get() = lines.sumOf { it.amountCents }

    /** Sum of every line's tax portion. */
    val linesTaxCents: Long get() = lines.sumOf { it.taxCents }

    /** Sum of every line's card-fee portion. */
    val linesCardFeeCents: Long get() = lines.sumOf { it.cardFeeCents }

    /** Sum of every line's net, which equals [netCents]. */
    val linesTotalCents: Long get() = lines.sumOf { it.totalCents }
}

/** Hour-of-day for a timestamp, in the device's local time zone. */
fun hourOf(millis: Long): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    return cal.get(Calendar.HOUR_OF_DAY)
}

/**
 * Builds the report for [day].
 *
 * Ranking is by **units sold**, not revenue: "what was sold the most" is a question about
 * quantity. Ties break on revenue and then on name so the order is stable between runs.
 *
 * @param topLimit how many products the ranking keeps (the chart only shows a few anyway).
 */
fun buildDailyReport(day: DayOrders, topLimit: Int = 5): DailyReport {
    val entries = day.orders

    val byProduct = mutableMapOf<Long, ProductSalesRow>()
    var itemCount = 0
    entries.forEach { entry ->
        entry.lines.forEach { line ->
            itemCount += line.quantity
            val existing = byProduct[line.productId]
            byProduct[line.productId] = if (existing == null) {
                ProductSalesRow(
                    productId = line.productId,
                    productName = line.productName,
                    quantity = line.quantity,
                    revenueCents = line.totalCents,
                )
            } else {
                existing.copy(
                    quantity = existing.quantity + line.quantity,
                    revenueCents = existing.revenueCents + line.totalCents,
                )
            }
        }
    }

    val topProducts = byProduct.values
        .sortedWith(
            compareByDescending<ProductSalesRow> { it.quantity }
                .thenByDescending { it.revenueCents }
                .thenBy { it.productName }
        )
        .take(topLimit)

    val byHour = entries
        .groupBy { hourOf(it.order.soldAt) }
        .toSortedMap()
        .map { (hour, ordersForHour) ->
            HourBucket(
                hour = hour,
                revenueCents = ordersForHour.sumOf { it.order.totalCents },
                itemCount = ordersForHour.sumOf { it.order.itemCount },
            )
        }

    val cash = entries
        .filter { it.order.paymentMethod == PaymentMethod.CASH }
        .sumOf { it.order.totalCents }
    val card = entries
        .filter { it.order.paymentMethod == PaymentMethod.CARD }
        .sumOf { it.order.totalCents }

    return DailyReport(
        dayStartMillis = day.dayStartMillis,
        orderCount = entries.size,
        itemCount = itemCount,
        grossCents = entries.sumOf { it.order.itemsSubtotalCents },
        taxCents = entries.sumOf { it.order.taxCents },
        cardFeeRateCents = entries.sumOf {
            it.order.cardFeeCents - it.order.cardFeeFixedCents
        }.coerceAtLeast(0L),
        cardFeeFixedCents = entries.sumOf { it.order.cardFeeFixedCents },
        netCents = entries.sumOf { it.order.totalCents },
        promoDiscountCents = entries.sumOf { it.order.promoDiscountCents },
        topProducts = topProducts,
        hours = byHour,
        paymentSplit = PaymentSplit(cashCents = cash, cardCents = card),
        lines = buildReportLines(entries),
    )
}

/** One point on the multi-day trend chart. */
data class ReportDayPoint(
    val dayStartMillis: Long,
    val grossCents: Long,
    val netCents: Long,
    val promoDiscountCents: Long,
    val orderCount: Int,
    val unitCount: Int,
) {
    /** True when nothing was sold that day. Drawn as an empty slot rather than a skipped one. */
    val isEmpty: Boolean get() = orderCount == 0
}

/**
 * A run of consecutive days, oldest first, for the analytics screen.
 *
 * Consecutive and gap-filled on purpose: a bar chart that silently omits quiet days makes a
 * fortnight of sporadic trading look like steady growth. Days with no orders are present with zero
 * values so the spacing on the x-axis is honest.
 */
data class ReportSeries(
    val points: List<ReportDayPoint>,
) {
    val isEmpty: Boolean get() = points.all { it.isEmpty }

    val grossCents: Long get() = points.sumOf { it.grossCents }
    val netCents: Long get() = points.sumOf { it.netCents }
    val promoDiscountCents: Long get() = points.sumOf { it.promoDiscountCents }
    val orderCount: Int get() = points.sumOf { it.orderCount }
    val unitCount: Int get() = points.sumOf { it.unitCount }

    /** The busiest day by net revenue, or null when there is nothing to show. */
    val bestDay: ReportDayPoint? get() = points.maxByOrNull { it.netCents }?.takeIf { !it.isEmpty }

    /** Average net per **trading** day, ignoring days with no sales. */
    val averageTradingDayNetCents: Long
        get() {
            val trading = points.filter { !it.isEmpty }
            return if (trading.isEmpty()) 0L else trading.sumOf { it.netCents } / trading.size
        }
}

/**
 * Builds the trend series for the last [days] calendar days, **ending on [nowMillis]'s day**.
 *
 * @param buckets the day buckets from the repository, in any order — they are matched by their own
 *   day start rather than by position, so a caller can pass `ordersByDay` without sorting it.
 * @param days how many days to include, today included. 7 means "this week".
 *
 * Days are resolved through local midnight rather than by subtracting fixed 24-hour blocks, so a
 * daylight-saving change shifts the label but never merges or drops a day. A fixed-millisecond
 * walk would double-count the day the clocks go back.
 */
fun buildReportSeries(
    buckets: List<DayOrders>,
    nowMillis: Long,
    days: Int = 7,
): ReportSeries {
    if (days <= 0) return ReportSeries(emptyList())

    // Index by the bucket's own day start, so lookup is exact rather than fuzzy.
    val byDay = buckets.associateBy { it.dayStartMillis }

    val points = ArrayList<ReportDayPoint>(days)
    for (offset in days - 1 downTo 0) {
        val dayStart = startOfDayBefore(nowMillis, offset)
        val bucket = byDay[dayStart]
        points += if (bucket == null) {
            ReportDayPoint(
                dayStartMillis = dayStart,
                grossCents = 0,
                netCents = 0,
                promoDiscountCents = 0,
                orderCount = 0,
                unitCount = 0,
            )
        } else {
            ReportDayPoint(
                dayStartMillis = dayStart,
                grossCents = bucket.orders.sumOf { it.order.itemsSubtotalCents },
                netCents = bucket.orders.sumOf { it.order.totalCents },
                promoDiscountCents = bucket.orders.sumOf { it.order.promoDiscountCents },
                orderCount = bucket.orders.size,
                unitCount = bucket.orders.sumOf { it.order.itemCount },
            )
        }
    }
    return ReportSeries(points)
}

/**
 * Local midnight [daysBefore] days before [millis]'s own day.
 *
 * Uses [Calendar] so the timezone and any DST transition are handled by the platform rather than
 * by assuming every day is exactly 24 hours long.
 */
private fun startOfDayBefore(millis: Long, daysBefore: Int): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    if (daysBefore != 0) {
        cal.add(Calendar.DAY_OF_YEAR, -daysBefore)
    }
    return cal.timeInMillis
}

/**
 * Builds the itemised table by apportioning each order's real figures across its own lines.
 *
 * Money must not be invented and must still add up, which constrains the approach:
 *
 *  - **Tax and card fee are stored per order**, not per line, so they are split by each line's
 *    share of that order's subtotal. Apportioning by subtotal (rather than by unit count) is what
 *    makes the rows sum back to the order's own taxCents/cardFeeCents — verified by
 *    `linesTotalCents == netCents` on any dataset.
 *  - The **flat** card fee is a per-transaction charge, so it cannot meaningfully be attributed to
 *    one product. It is spread by subtotal share too, and the row's "total" is then genuinely what
 *    the shop kept from that product rather than a subtotal with an arbitrary deduction.
 *  - **Rounding**: each row truncates its apportioned share. That can lose a fraction of a cent
 *    across many rows, so the last line absorbs the remainder. Doing it this way guarantees the
 *    table's total matches the headline net exactly, which is the first thing anyone checking a
 *    report will do.
 *
 * Tax **rate** is copied from the order it came from. A product sold under two different rates in
 * one day is flagged with [ReportLineRow.mixedTaxRate] (rate `-1`) so the table can print a dash
 * instead of silently presenting one of the two as if it were the whole truth.
 *
 * Rows are ranked by revenue, matching the template the report imitates.
 */
internal fun buildReportLines(entries: List<OrderWithLines>): List<ReportLineRow> {
    // One accumulator per product, keyed so a product sold in several orders merges into one row.
    data class Acc(
        val name: String,
        var quantity: Int = 0,
        var amountCents: Long = 0L,
        var taxCents: Long = 0L,
        var cardFeeCents: Long = 0L,
        var totalCents: Long = 0L,
        var rate: Int = -2,
        var mixedRate: Boolean = false,
    )

    val accs = linkedMapOf<Long, Acc>()

    entries.forEach { entry ->
        val order = entry.order
        val subtotal = order.itemsSubtotalCents
        // A zero-subtotal order (fully discounted, or a legacy row) cannot be apportioned by
        // share, so its deductions are spread evenly over its lines instead of dropping them.
        val evenSplit = subtotal <= 0L && entry.lines.isNotEmpty()

        // Deductions are apportioned line by line; the shortfall from integer truncation is
        // tracked so the final line of this order can absorb it.
        var taxGiven = 0L
        var feeGiven = 0L
        val lineCount = entry.lines.size

        entry.lines.forEachIndexed { index, line ->
            val isLast = index == lineCount - 1
            val lineAmount = line.totalCents

            val taxShare = when {
                isLast -> order.taxCents - taxGiven
                evenSplit -> order.taxCents / lineCount
                else -> if (subtotal <= 0L) 0L else (order.taxCents * lineAmount) / subtotal
            }
            val feeShare = when {
                isLast -> order.cardFeeCents - feeGiven
                evenSplit -> order.cardFeeCents / lineCount
                else -> if (subtotal <= 0L) 0L else (order.cardFeeCents * lineAmount) / subtotal
            }
            taxGiven += taxShare
            feeGiven += feeShare

            val acc = accs.getOrPut(line.productId) {
                Acc(name = line.productName, rate = order.taxRateBasisPoints)
            }
            // A product sold under two rates in one day cannot report a single rate.
            if (acc.rate != order.taxRateBasisPoints) acc.mixedRate = true
            acc.quantity += line.quantity
            acc.amountCents += lineAmount
            acc.taxCents += taxShare
            acc.cardFeeCents += feeShare
            acc.totalCents += (lineAmount - taxShare - feeShare).coerceAtLeast(0L)
        }
    }

    return accs.map { (productId, acc) ->
        ReportLineRow(
            productId = productId,
            productName = acc.name,
            // The price actually charged; a product re-priced mid-day reports the latest.
            unitPriceCents = if (acc.quantity > 0) acc.amountCents / acc.quantity else 0L,
            quantity = acc.quantity,
            amountCents = acc.amountCents,
            taxRateBasisPoints = if (acc.mixedRate) -1 else acc.rate,
            taxCents = acc.taxCents,
            cardFeeCents = acc.cardFeeCents,
            totalCents = acc.totalCents,
        )
    }.sortedWith(
        compareByDescending<ReportLineRow> { it.amountCents }
            .thenByDescending { it.quantity }
            .thenBy { it.productName }
    )
}
