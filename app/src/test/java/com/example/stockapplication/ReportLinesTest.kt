package com.example.stockapplication

import com.example.stockapplication.data.DayOrders
import com.example.stockapplication.data.OrderWithLines
import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.Sale
import com.example.stockapplication.data.SaleOrder
import com.example.stockapplication.data.buildDailyReport
import com.example.stockapplication.data.buildReportLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Pins the apportioning that produces the itemised table.
 *
 * Tax and card fees are stored per **order** but displayed per **product**, so the derivation is
 * the one place in the report where money could quietly go missing or be double-counted. Every
 * test here is ultimately checking the same promise: the rows add up to the headline figures.
 */
class ReportLinesTest {

    private fun stamp(hour: Int = 10): Long =
        Calendar.getInstance().apply {
            set(2025, Calendar.MARCH, 14, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun order(
        id: String,
        subtotal: Long,
        tax: Long,
        fee: Long,
        total: Long,
        rate: Int = 0,
        method: PaymentMethod = PaymentMethod.CASH,
    ) = SaleOrder(
        orderId = id,
        soldAt = stamp(),
        paymentMethod = method,
        itemsSubtotalCents = subtotal,
        taxRateBasisPoints = rate,
        taxCents = tax,
        cardFeeRateBasisPoints = if (method == PaymentMethod.CARD) rate else 0,
        cardFeeCents = fee,
        totalCents = total,
        itemCount = 1,
    )

    private fun line(
        orderId: String,
        productId: Long,
        name: String,
        quantity: Int,
        unitPrice: Long,
    ) = Sale(
        productId = productId,
        productName = name,
        barcode = "BC$productId",
        quantity = quantity,
        unitPriceCents = unitPrice,
        soldAt = stamp(),
        orderId = orderId,
    )

    @Test
    fun aSingleLineTakesTheWholeOrderDeductions() {
        val entry = OrderWithLines(
            order("A", subtotal = 3000, tax = 360, fee = 0, total = 2640, rate = 1200),
            listOf(line("A", 1, "Tracker", 50, 60)),
        )

        val row = buildReportLines(listOf(entry)).single()

        assertEquals(3000, row.amountCents)
        assertEquals(360, row.taxCents)
        assertEquals(0, row.cardFeeCents)
        assertEquals(2640, row.totalCents)
        assertEquals(1200, row.taxRateBasisPoints)
        assertFalse(row.mixedTaxRate)
    }

    @Test
    fun deductionsAreApportionedBySubtotalShare() {
        // Two lines, 1/4 and 3/4 of the order. The split must follow the money, not the units.
        val entry = OrderWithLines(
            order("A", subtotal = 4000, tax = 400, fee = 0, total = 3600, rate = 1000),
            listOf(
                line("A", 1, "Cheap", 10, 100), // 1000
                line("A", 2, "Pricey", 10, 300), // 3000
            ),
        )

        val rows = buildReportLines(listOf(entry)).associateBy { it.productName }

        assertEquals(100, rows.getValue("Cheap").taxCents)
        assertEquals(300, rows.getValue("Pricey").taxCents)
    }

    @Test
    fun rowsAlwaysSumBackToTheOrderTotals() {
        // A deliberately awkward case: thirds, so per-row truncation cannot cleanly divide.
        val entry = OrderWithLines(
            order("A", subtotal = 1000, tax = 100, fee = 30, total = 870, rate = 1000),
            listOf(
                line("A", 1, "One", 1, 333),
                line("A", 2, "Two", 1, 333),
                line("A", 3, "Three", 1, 334),
            ),
        )

        val rows = buildReportLines(listOf(entry))

        assertEquals(1000, rows.sumOf { it.amountCents.toInt() }.toLong())
        assertEquals(100, rows.sumOf { it.taxCents.toInt() }.toLong())
        assertEquals(30, rows.sumOf { it.cardFeeCents.toInt() }.toLong())
        // The remainder is absorbed by the last line, so nothing is lost to truncation.
        assertEquals(870, rows.sumOf { it.totalCents.toInt() }.toLong())
    }

    @Test
    fun theTableTotalEqualsTheHeadlineNet() {
        // The property that matters most: someone checking the report will add up the last column
        // and compare it to the headline figure.
        val day = DayOrders(
            stamp(),
            listOf(
                OrderWithLines(
                    order("A", subtotal = 5000, tax = 600, fee = 0, total = 4400, rate = 1200),
                    listOf(line("A", 1, "Alpha", 5, 200), line("A", 2, "Beta", 10, 400)),
                ),
                OrderWithLines(
                    order(
                        id = "B",
                        subtotal = 3350,
                        tax = 268,
                        fee = 52,
                        total = 3030,
                        rate = 800,
                        method = PaymentMethod.CARD,
                    ),
                    listOf(line("B", 3, "Gamma", 1, 3350)),
                ),
            ),
        )

        val report = buildDailyReport(day)

        assertEquals(report.netCents, report.linesTotalCents)
        assertEquals(report.grossCents, report.linesAmountCents)
        assertEquals(report.taxCents, report.linesTaxCents)
        assertEquals(report.cardFeeCents, report.linesCardFeeCents)
    }

    @Test
    fun aProductSoldInTwoOrdersMergesIntoOneRow() {
        val day = DayOrders(
            stamp(),
            listOf(
                OrderWithLines(
                    order("A", subtotal = 1000, tax = 100, fee = 0, total = 900, rate = 1000),
                    listOf(line("A", 1, "Coffee", 2, 500)),
                ),
                OrderWithLines(
                    // 3 units at 500 = 1500, so this order's subtotal is 1500, not 2000.
                    order("B", subtotal = 1500, tax = 150, fee = 0, total = 1350, rate = 1000),
                    listOf(line("B", 1, "Coffee", 3, 500)),
                ),
            ),
        )

        val row = buildDailyReport(day).lines.single()

        assertEquals(5, row.quantity)
        assertEquals(2500, row.amountCents)
        assertEquals(250, row.taxCents)
        assertEquals(2250, row.totalCents)
        assertEquals(500, row.unitPriceCents)
    }

    @Test
    fun aProductSoldUnderTwoRatesIsFlaggedAsMixed() {
        // Reporting either rate as "the" rate would be a lie; the row must say so instead.
        val day = DayOrders(
            stamp(),
            listOf(
                OrderWithLines(
                    order("A", subtotal = 1000, tax = 100, fee = 0, total = 900, rate = 1000),
                    listOf(line("A", 1, "Coffee", 1, 1000)),
                ),
                OrderWithLines(
                    order("B", subtotal = 1000, tax = 210, fee = 0, total = 790, rate = 2100),
                    listOf(line("B", 1, "Coffee", 1, 1000)),
                ),
            ),
        )

        val row = buildDailyReport(day).lines.single()

        assertTrue(row.mixedTaxRate)
        // The real deductions are still reported; only the *rate* is withheld.
        assertEquals(310, row.taxCents)
    }

    @Test
    fun cardFeeIsSplitAcrossLinesOfACardOrder() {
        val entry = OrderWithLines(
            order(
                id = "A",
                subtotal = 2000,
                tax = 0,
                fee = 200,
                total = 1800,
                rate = 1000,
                method = PaymentMethod.CARD,
            ),
            listOf(
                line("A", 1, "Half", 1, 1000),
                line("A", 2, "Other half", 1, 1000),
            ),
        )

        val rows = buildReportLines(listOf(entry))

        assertEquals(100, rows.single { it.productName == "Half" }.cardFeeCents)
        assertEquals(100, rows.single { it.productName == "Other half" }.cardFeeCents)
    }

    @Test
    fun aZeroSubtotalOrderDoesNotDivideByZero() {
        // Defensive: a fully-discounted or legacy order has a zero subtotal, which would be a
        // division by zero if the share were computed naively.
        val entry = OrderWithLines(
            order("A", subtotal = 0, tax = 0, fee = 0, total = 0),
            listOf(line("A", 1, "Freebie", 2, 0)),
        )

        val row = buildReportLines(listOf(entry)).single()

        assertEquals(0, row.amountCents)
        assertEquals(0, row.taxCents)
        assertEquals(0, row.totalCents)
    }

    @Test
    fun rowsAreRankedByRevenue() {
        val entry = OrderWithLines(
            order("A", subtotal = 3000, tax = 0, fee = 0, total = 3000),
            listOf(
                line("A", 1, "Small", 1, 500),
                line("A", 2, "Large", 1, 2500),
            ),
        )

        val names = buildReportLines(listOf(entry)).map { it.productName }

        assertEquals(listOf("Large", "Small"), names)
    }

    @Test
    fun anEmptyDayHasNoRows() {
        val report = buildDailyReport(DayOrders(stamp(), emptyList()))

        assertTrue(report.lines.isEmpty())
        assertEquals(0, report.linesTotalCents)
    }
}
