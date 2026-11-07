package com.example.stockapplication.data

import androidx.annotation.StringRes

/** A completed checkout together with the line items it contains. */
data class OrderWithLines(
    val order: SaleOrder,
    val lines: List<Sale>,
) {
    val soldAt: Long get() = order.soldAt
}

/** One calendar day's worth of completed checkouts, newest order first. */
data class DayOrders(
    val dayStartMillis: Long,
    val orders: List<OrderWithLines>,
) {
    /** Items across every order in the day. */
    val unitCount: Int get() = orders.sumOf { it.order.itemCount }

    /** Money the shop actually kept, after tax and card fees. */
    val netCents: Long get() = orders.sumOf { it.order.totalCents }

    val orderCount: Int get() = orders.size

    @StringRes
    fun labelResource(now: Long = System.currentTimeMillis()): Int =
        formatDayLabelResource(dayStartMillis, now)
}

/**
 * Pairs each order with its line items and buckets the result into local calendar days.
 *
 * Orders without any surviving lines are dropped: cancelling an order deletes both the header and
 * its lines, so an orphan would only appear if the database were edited by hand.
 */
fun groupOrdersByDay(orders: List<SaleOrder>, lines: List<Sale>): List<DayOrders> {
    val linesByOrder = lines.groupBy { it.orderId }
    return orders
        .mapNotNull { order ->
            val orderLines = linesByOrder[order.orderId] ?: return@mapNotNull null
            OrderWithLines(order, orderLines.sortedBy { it.id })
        }
        .groupBy { startOfDay(it.soldAt) }
        .toSortedMap(compareByDescending { it })
        .map { (day, dayOrders) ->
            DayOrders(day, dayOrders.sortedByDescending { it.soldAt })
        }
}
