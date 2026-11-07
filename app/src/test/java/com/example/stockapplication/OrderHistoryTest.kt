package com.example.stockapplication

import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.Sale
import com.example.stockapplication.data.SaleOrder
import com.example.stockapplication.data.groupOrdersByDay
import com.example.stockapplication.data.startOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * The history screen lists whole checkouts, so these tests pin down that orders are paired with
 * their lines correctly and bucketed by day.
 */
class OrderHistoryTest {

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun order(
        id: String,
        soldAt: Long,
        itemCount: Int = 1,
        total: Long = 100,
    ) = SaleOrder(
        orderId = id,
        soldAt = soldAt,
        paymentMethod = PaymentMethod.CASH,
        itemsSubtotalCents = total,
        taxRateBasisPoints = 0,
        taxCents = 0,
        cardFeeRateBasisPoints = 0,
        cardFeeCents = 0,
        totalCents = total,
        itemCount = itemCount,
    )

    private fun line(orderId: String, name: String, quantity: Int = 1, price: Long = 100) = Sale(
        productId = 1,
        productName = name,
        barcode = "BC",
        quantity = quantity,
        unitPriceCents = price,
        soldAt = 0,
        orderId = orderId,
    )

    @Test
    fun pairsEachOrderWithItsOwnLines() {
        val orders = listOf(order("A", 1000), order("B", 2000))
        val lines = listOf(
            line("A", "Coffee"),
            line("B", "Tea"),
            line("A", "Sugar"),
        )

        val grouped = groupOrdersByDay(orders, lines)
        val byId = grouped.flatMap { it.orders }.associateBy { it.order.orderId }

        assertEquals(
            listOf("Coffee", "Sugar"),
            byId.getValue("A").lines.map { it.productName }
        )
        assertEquals(listOf("Tea"), byId.getValue("B").lines.map { it.productName })
    }

    @Test
    fun ordersAreBucketedByCalendarDayNewestFirst() {
        val day1 = millisAt(2025, Calendar.MARCH, 14, 9)
        val day2 = millisAt(2025, Calendar.MARCH, 15, 9)

        val grouped = groupOrdersByDay(
            orders = listOf(order("A", day1), order("B", day2)),
            lines = listOf(line("A", "Coffee"), line("B", "Tea")),
        )

        assertEquals(2, grouped.size)
        assertEquals(startOfDay(day2), grouped[0].dayStartMillis)
        assertEquals("B", grouped[0].orders.single().order.orderId)
    }

    @Test
    fun ordersWithoutLinesAreDropped() {
        // A cancelled order loses both header and lines; this guards against orphans showing up.
        val grouped = groupOrdersByDay(
            orders = listOf(order("A", 1000), order("GHOST", 2000)),
            lines = listOf(line("A", "Coffee")),
        )

        val ids = grouped.flatMap { it.orders }.map { it.order.orderId }
        assertEquals(listOf("A"), ids)
    }

    @Test
    fun dayTotalsUseTheNetOrderValue() {
        val day = millisAt(2025, Calendar.MARCH, 14, 9)
        val grouped = groupOrdersByDay(
            orders = listOf(
                order("A", day, itemCount = 2, total = 8725),
                order("B", day + 1000, itemCount = 1, total = 500),
            ),
            lines = listOf(line("A", "Coffee", quantity = 2), line("B", "Tea")),
        )

        val section = grouped.single()
        assertEquals(2, section.orderCount)
        assertEquals(3, section.unitCount)
        assertEquals(9225, section.netCents)
    }

    @Test
    fun emptyInputProducesNoDays() {
        assertTrue(groupOrdersByDay(emptyList(), emptyList()).isEmpty())
    }
}
