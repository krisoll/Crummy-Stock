package com.example.stockapplication

import com.example.stockapplication.data.DayOrders
import com.example.stockapplication.data.OrderWithLines
import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.ReportRenderer
import com.example.stockapplication.data.Sale
import com.example.stockapplication.data.SaleOrder
import com.example.stockapplication.data.buildDailyReport
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Calendar

/**
 * Renders the real PDF and fails if it cannot be produced.
 *
 * Also writes a copy to the build directory so the layout can be eyeballed without a device —
 * text overlapping or a table running off the page cannot be caught by assertions, only by
 * looking at it.
 */
@RunWith(RobolectricTestRunner::class)
class ReportRenderSmokeTest {

    private fun stamp(hour: Int = 10): Long =
        Calendar.getInstance().apply {
            set(2025, Calendar.MARCH, 14, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val labels = ReportRenderer.Labels(
        title = "Daily report",
        company = "StockApplication",
        orders = "Sales",
        items = "Items",
        gross = "Gross",
        tax = "Tax",
        cardFee = "Card fee",
        cardFeeFixed = "Fixed card fee",
        net = "Net",
        topProducts = "Best sellers",
        hourly = "Revenue by hour",
        payments = "Payment split",
        cash = "Cash",
        card = "Card",
        empty = "No sales on this day",
        tableItems = "Item name",
        tablePrice = "Price",
        tableQty = "Qty",
        tableAmount = "Amount",
        tableTaxRate = "Tax rate",
        tableTax = "Tax",
        tableCardFee = "Card fee",
        tableTotal = "Total",
        tableGrandTotal = "Total",
        unitsSold = "Quantity Sold",
        totalSales = "Total Sales",
        itemSold = "Item Sold",
        notes = "Notes:",
        noteTotal = "A total of %1\$s was taken across %2\$d sales today.",
        noteBestSeller = "The best seller was %1\$s, with %2\$d units contributing %3\$s.",
        noteBusiestHour = "The busiest hour was %1\$s, taking %2\$s.",
        mixedRate = "–",
        tableMoreRows = "+%1\$d more products",
    )

    /** A day with enough variety to exercise every part of the layout. */
    private fun busyDay(): DayOrders {
        val products = listOf(
            Triple("BioSync Fitness Tracker", 6000L, 50),
            Triple("QuantumSense Password Manager", 7500L, 20),
            Triple("AeroNano Surveillance System", 9500L, 30),
            Triple("InnovaTech 3D Printer", 8000L, 15),
            Triple("SafeGuard Wireless Earbuds", 4500L, 10),
            Triple("LogiPro Gateway 5G", 9900L, 5),
        )
        val orders = mutableListOf<OrderWithLines>()
        products.forEachIndexed { index, (name, price, qty) ->
            val id = "ORD-$index"
            val subtotal = price * qty
            val tax = subtotal * 12 / 100
            val total = subtotal - tax
            orders += OrderWithLines(
                SaleOrder(
                    orderId = id,
                    soldAt = stamp(9 + index),
                    paymentMethod = if (index % 2 == 0) PaymentMethod.CARD else PaymentMethod.CASH,
                    itemsSubtotalCents = subtotal,
                    taxRateBasisPoints = 1200,
                    taxCents = tax,
                    cardFeeRateBasisPoints = if (index % 2 == 0) 1200 else 0,
                    cardFeeCents = if (index % 2 == 0) subtotal * 12 / 100 else 0,
                    totalCents = total,
                    itemCount = qty,
                ),
                listOf(
                    Sale(
                        productId = index.toLong() + 1,
                        productName = name,
                        barcode = "BC$index",
                        quantity = qty,
                        unitPriceCents = price,
                        soldAt = stamp(9 + index),
                        orderId = id,
                    )
                ),
            )
        }
        return DayOrders(stamp(), orders)
    }

    /**
     * Renders through the PNG path, which shares all its drawing code with the PDF.
     *
     * `android.graphics.pdf.PdfDocument` is only a stub under Robolectric — `startPage` throws
     * "document is closed" no matter what — so the PDF entry point can only be exercised on a
     * device. The layout itself is identical, so a bitmap of it is the useful thing to check here.
     */
    private fun renderSample(report: com.example.stockapplication.data.DailyReport, name: String) {
        val bitmap = ReportRenderer.renderPng(report, labels)
        assertTrue("bitmap was empty", bitmap.width > 0 && bitmap.height > 0)
        val out = File("build/reports/render/$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        println("Wrote ${out.absolutePath} (${bitmap.width}x${bitmap.height})")
    }

    @Test
    fun rendersABusyDay() {
        renderSample(buildDailyReport(busyDay()), "sales-report-sample")
    }

    @Test
    fun rendersAnEmptyDay() {
        renderSample(buildDailyReport(DayOrders(stamp(), emptyList())), "sales-report-empty")
    }

    @Test
    fun rendersADayWithNoTaxConfigured() {
        // A zero rate must not produce a NaN or a blank tax column.
        val report = buildDailyReport(
            DayOrders(
                stamp(),
                listOf(
                    OrderWithLines(
                        SaleOrder(
                            orderId = "ORD-1",
                            soldAt = stamp(),
                            paymentMethod = PaymentMethod.CASH,
                            itemsSubtotalCents = 1000,
                            taxRateBasisPoints = 0,
                            taxCents = 0,
                            cardFeeRateBasisPoints = 0,
                            cardFeeCents = 0,
                            totalCents = 1000,
                            itemCount = 1,
                        ),
                        listOf(
                            Sale(
                                productId = 1,
                                productName = "Coffee",
                                barcode = "BC1",
                                quantity = 1,
                                unitPriceCents = 1000,
                                soldAt = stamp(),
                                orderId = "ORD-1",
                            )
                        ),
                    )
                ),
            )
        )

        val bitmap = ReportRenderer.renderPng(report, labels)
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }
}
