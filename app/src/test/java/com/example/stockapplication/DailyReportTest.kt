package com.example.stockapplication

import androidx.test.core.app.ApplicationProvider
import com.example.stockapplication.data.DayOrders
import com.example.stockapplication.data.OrderWithLines
import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.Sale
import com.example.stockapplication.data.SaleOrder
import com.example.stockapplication.data.buildDailyReport
import com.example.stockapplication.data.dayOrdersCsvFileName
import com.example.stockapplication.data.dayOrdersToCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

/**
 * The daily report ranks products and buckets revenue; the CSV export is per-day. Both are pure
 * data operations, so they're pinned down here without Compose or a device. Robolectric is only
 * needed for the localised CSV headers, which come from string resources.
 */
@RunWith(RobolectricTestRunner::class)
class DailyReportTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun stamp(hour: Int, minute: Int = 0, dayOfMonth: Int = 14): Long =
        Calendar.getInstance().apply {
            set(2025, Calendar.MARCH, dayOfMonth, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun order(
        id: String,
        soldAt: Long,
        method: PaymentMethod = PaymentMethod.CASH,
        items: Int = 1,
        subtotal: Long = 1000,
        tax: Long = 0,
        fee: Long = 0,
        total: Long = 1000,
    ) = SaleOrder(
        orderId = id,
        soldAt = soldAt,
        paymentMethod = method,
        itemsSubtotalCents = subtotal,
        taxRateBasisPoints = 0,
        taxCents = tax,
        cardFeeRateBasisPoints = 0,
        cardFeeCents = fee,
        totalCents = total,
        itemCount = items,
    )

    private fun line(
        orderId: String,
        productId: Long,
        name: String,
        quantity: Int,
        price: Long,
    ) = Sale(
        productId = productId,
        productName = name,
        barcode = "BC$productId",
        quantity = quantity,
        unitPriceCents = price,
        soldAt = 0,
        orderId = orderId,
    )

    private fun day(startMillis: Long, vararg entries: OrderWithLines) =
        DayOrders(startMillis, entries.toList())

    @Test
    fun ranksProductsByUnitsSoldNotRevenue() {
        // Tea sells more units but earns less; it should still rank first.
        val d = day(
            stamp(9),
            OrderWithLines(
                order("A", stamp(9), subtotal = 10_000, total = 10_000),
                listOf(
                    line("A", 1, "Coffee", quantity = 2, price = 5000),
                    line("A", 2, "Tea", quantity = 5, price = 100),
                )
            )
        )

        val report = buildDailyReport(d)

        assertEquals("Tea", report.topProducts.first().productName)
        assertEquals(5, report.topProducts.first().quantity)
        assertEquals("Coffee", report.topProducts[1].productName)
        assertEquals("Tea", report.bestSeller?.productName)
    }

    @Test
    fun aggregatesTheSameProductAcrossOrders() {
        val d = day(
            stamp(9),
            OrderWithLines(
                order("A", stamp(9), subtotal = 500, total = 500),
                listOf(line("A", 1, "Coffee", quantity = 1, price = 500))
            ),
            OrderWithLines(
                order("B", stamp(10), subtotal = 1500, total = 1500),
                listOf(line("B", 1, "Coffee", quantity = 3, price = 500))
            ),
        )

        val report = buildDailyReport(d)

        val coffee = report.topProducts.single()
        assertEquals(4, coffee.quantity)
        assertEquals(2000, coffee.revenueCents)
    }

    @Test
    fun bucketsRevenueByHour() {
        val d = day(
            stamp(9),
            OrderWithLines(
                order("A", stamp(9, 10), total = 1000),
                listOf(line("A", 1, "Coffee", 1, 1000))
            ),
            OrderWithLines(
                order("B", stamp(9, 50), total = 500),
                listOf(line("B", 1, "Coffee", 1, 500))
            ),
            OrderWithLines(
                order("C", stamp(15, 0), total = 700),
                listOf(line("C", 1, "Coffee", 1, 700))
            ),
        )

        val report = buildDailyReport(d)

        assertEquals(2, report.hours.size)
        assertEquals(9, report.hours[0].hour)
        assertEquals(1500, report.hours[0].revenueCents)
        assertEquals(15, report.hours[1].hour)
        // Busiest = highest revenue, and hour 9 took more than hour 15 despite fewer orders.
        assertEquals(9, report.busiestHour?.hour)
    }

    @Test
    fun splitsTotalsAndPaymentMethods() {
        val d = day(
            stamp(9),
            OrderWithLines(
                order(
                    "A", stamp(9), method = PaymentMethod.CASH,
                    subtotal = 1000, tax = 100, total = 900
                ),
                listOf(line("A", 1, "Coffee", 1, 1000))
            ),
            OrderWithLines(
                order(
                    "B", stamp(10), method = PaymentMethod.CARD,
                    subtotal = 2000, tax = 200, fee = 50, total = 1750
                ),
                listOf(line("B", 2, "Tea", 2, 1000))
            ),
        )

        val report = buildDailyReport(d)

        assertEquals(2, report.orderCount)
        assertEquals(3, report.itemCount)
        assertEquals(3000, report.grossCents)
        assertEquals(300, report.taxCents)
        assertEquals(50, report.cardFeeCents)
        assertEquals(2650, report.netCents)
        assertEquals(900, report.paymentSplit.cashCents)
        assertEquals(1750, report.paymentSplit.cardCents)
    }

    @Test
    fun respectsTheTopProductLimit() {
        val lines = (1..8).map { line("A", it.toLong(), "P$it", quantity = it, price = 100) }
        val d = day(
            stamp(9),
            OrderWithLines(order("A", stamp(9)), lines)
        )

        val report = buildDailyReport(d, topLimit = 3)

        assertEquals(3, report.topProducts.size)
        // Highest quantity first: P8, P7, P6.
        assertEquals(listOf("P8", "P7", "P6"), report.topProducts.map { it.productName })
    }

    @Test
    fun anEmptyDayReportsEmpty() {
        val report = buildDailyReport(DayOrders(stamp(9), emptyList()))

        assertTrue(report.isEmpty)
        assertNull(report.bestSeller)
        assertNull(report.busiestHour)
        assertTrue(report.topProducts.isEmpty())
    }

    // ---- Day-scoped CSV ------------------------------------------------------

    // The headers are localised, so the exporter needs a Context to look them up.

    @Test
    fun dayCsvContainsOnlyThatDaysOrders() {
        val d = DayOrders(
            stamp(9),
            listOf(
                OrderWithLines(
                    order("ORD-A", stamp(9, 30)),
                    listOf(line("ORD-A", 1, "Coffee", 1, 1000))
                )
            )
        )

        val csv = dayOrdersToCsv(d, context).trim().lines()

        assertEquals(3, csv.size) // header + 1 order + totals
        assertTrue(csv[1].startsWith("ORD-A"))
    }

    @Test
    fun dayCsvFileNameUsesThatDayNotToday() {
        // The day being exported, not the day the export happens on.
        assertEquals("sales-2025-03-14.csv", dayOrdersCsvFileName(stamp(9)))
    }

    @Test
    fun reportIsNotNullForARealDay() {
        val d = day(
            stamp(9),
            OrderWithLines(order("A", stamp(9)), listOf(line("A", 1, "Coffee", 1, 100)))
        )
        assertNotNull(buildDailyReport(d).bestSeller)
    }
}
