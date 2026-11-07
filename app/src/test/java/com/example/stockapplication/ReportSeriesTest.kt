package com.example.stockapplication

import com.example.stockapplication.data.DayOrders
import com.example.stockapplication.data.OrderWithLines
import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.Sale
import com.example.stockapplication.data.SaleOrder
import com.example.stockapplication.data.buildReportSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Pins down the multi-day trend series behind the analytics screen.
 *
 * The important properties are that days are **consecutive** and **gap-filled**: a chart that
 * silently drops quiet days makes sporadic trading look like steady growth.
 */
class ReportSeriesTest {

    private fun dayStart(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun order(
        id: String,
        soldAt: Long,
        subtotal: Long,
        total: Long,
        promo: Long = 0,
        items: Int = 1,
        payment: PaymentMethod = PaymentMethod.CASH,
    ) = SaleOrder(
        orderId = id,
        soldAt = soldAt,
        paymentMethod = payment,
        itemsSubtotalCents = subtotal,
        promoDiscountCents = promo,
        taxRateBasisPoints = 0,
        taxCents = 0,
        cardFeeRateBasisPoints = 0,
        cardFeeCents = 0,
        totalCents = total,
        itemCount = items,
    )

    private fun bucket(dayStart: Long, vararg orders: SaleOrder): DayOrders =
        DayOrders(
            dayStartMillis = dayStart,
            orders = orders.map { o ->
                OrderWithLines(
                    order = o,
                    lines = listOf(
                        Sale(
                            productId = 1,
                            productName = "Thing",
                            barcode = "X",
                            quantity = o.itemCount,
                            unitPriceCents = o.itemsSubtotalCents,
                            soldAt = o.soldAt,
                            orderId = o.orderId,
                        )
                    ),
                )
            },
        )

    private val now = 1_700_000_000_000L

    @Test
    fun theSeriesEndsOnTodayAndIsOldestFirst() {
        val series = buildReportSeries(emptyList(), nowMillis = now, days = 7)

        assertEquals(7, series.points.size)
        assertEquals(dayStart(now), series.points.last().dayStartMillis)
        assertTrue(series.points.zipWithNext().all { (a, b) -> a.dayStartMillis < b.dayStartMillis })
    }

    @Test
    fun quietDaysArePresentWithZeroesRatherThanSkipped() {
        // One trading day, six quiet ones: the chart must still have seven slots.
        val today = dayStart(now)
        val series = buildReportSeries(
            buckets = listOf(bucket(today, order("1", today + 1000, subtotal = 5000, total = 5000))),
            nowMillis = now,
            days = 7,
        )

        assertEquals(7, series.points.size)
        assertEquals(1, series.points.count { !it.isEmpty })
        assertEquals(6, series.points.count { it.isEmpty })
        assertEquals(5000, series.netCents)
        assertEquals(1, series.orderCount)
    }

    @Test
    fun anEmptyInputStillProducesAnAxis() {
        val series = buildReportSeries(emptyList(), nowMillis = now, days = 7)

        assertTrue(series.isEmpty)
        assertEquals(7, series.points.size)
        assertEquals(0, series.netCents)
        assertNull(series.bestDay)
    }

    @Test
    fun theSeriesIsMatchedByDayNotByPosition() {
        // Buckets supplied newest-first must still land on the right days.
        val today = dayStart(now)
        val yesterday = startOfDayBefore(today, 1)
        val series = buildReportSeries(
            buckets = listOf(
                bucket(today, order("1", today + 1000, subtotal = 3000, total = 3000)),
                bucket(yesterday, order("2", yesterday + 1000, subtotal = 1000, total = 1000)),
            ),
            nowMillis = now,
            days = 2,
        )

        assertEquals(1000, series.points[0].netCents)
        assertEquals(3000, series.points[1].netCents)
    }

    @Test
    fun promoDiscountsAreSummedAcrossTheRange() {
        val today = dayStart(now)
        val series = buildReportSeries(
            buckets = listOf(
                bucket(
                    today,
                    order("1", today + 1000, subtotal = 5000, total = 4500, promo = 500),
                    order("2", today + 2000, subtotal = 3000, total = 2700, promo = 300),
                )
            ),
            nowMillis = now,
            days = 1,
        )

        assertEquals(800, series.promoDiscountCents)
    }

    @Test
    fun theAverageIgnoresDaysWithNoSales() {
        // Two trading days out of three, so the average is over two â€” not three.
        val today = dayStart(now)
        val series = buildReportSeries(
            buckets = listOf(
                bucket(today, order("1", today + 1000, subtotal = 4000, total = 4000)),
                bucket(
                    startOfDayBefore(today, 2),
                    order("2", today - 2 * 86_400_000L + 1000, subtotal = 2000, total = 2000),
                ),
            ),
            nowMillis = now,
            days = 3,
        )

        assertEquals(3000, series.averageTradingDayNetCents)
        assertEquals(2, series.points.count { !it.isEmpty })
    }

    @Test
    fun bestDayIsTheHighestNetNotTheMostRecent() {
        val today = dayStart(now)
        val series = buildReportSeries(
            buckets = listOf(
                bucket(today, order("1", today + 1000, subtotal = 1000, total = 1000)),
                bucket(
                    startOfDayBefore(today, 1),
                    order("2", today - 86_400_000L + 1000, subtotal = 9000, total = 9000),
                ),
            ),
            nowMillis = now,
            days = 2,
        )

        assertEquals(9000L, series.bestDay?.netCents)
    }

    @Test
    fun aRangeOfZeroDaysIsEmptyRatherThanCrashing() {
        assertEquals(0, buildReportSeries(emptyList(), now, days = 0).points.size)
        assertEquals(0, buildReportSeries(emptyList(), now, days = -3).points.size)
    }

    /** Local midnight [daysBefore] days before [millis]'s day, mirroring the production helper. */
    private fun startOfDayBefore(millis: Long, daysBefore: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -daysBefore)
        }.timeInMillis
}
