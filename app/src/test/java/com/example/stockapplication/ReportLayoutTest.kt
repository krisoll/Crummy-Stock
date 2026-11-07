package com.example.stockapplication

import android.graphics.Canvas
import android.graphics.Paint
import com.example.stockapplication.data.DailyReport
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
import java.util.Calendar

/**
 * Checks the vertical extent of the rendered page.
 *
 * A full-page report has no scrolling and no pagination, so anything drawn below the page height
 * is silently clipped — the reader simply never sees it. That is exactly how the notes block (and
 * the card-fee figures in it) went missing on days with many products.
 *
 * These tests measure the *drawing calls* rather than counting pixels: the PDF and bitmap canvases
 * do not lay ink down identically under Robolectric, but the coordinates handed to the canvas are
 * always the truth about where content was asked to go.
 */
@RunWith(RobolectricTestRunner::class)
class ReportLayoutTest {

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

    /** A4 at 72dpi, which is what the renderer lays out for. */
    private val pageHeight = 842f

    private fun stamp(hour: Int = 10): Long =
        Calendar.getInstance().apply {
            set(2025, Calendar.MARCH, 14, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** A day with [productCount] distinct products, each its own card order with tax and a fee. */
    private fun dayWith(productCount: Int): DayOrders =
        DayOrders(
            stamp(),
            (0 until productCount).map { index ->
                val id = "ORD-$index"
                val price = 1000L + index * 100L
                val tax = price * 12 / 100
                val fee = 500L
                OrderWithLines(
                    SaleOrder(
                        orderId = id,
                        soldAt = stamp(8 + index % 12),
                        paymentMethod = PaymentMethod.CARD,
                        itemsSubtotalCents = price,
                        taxRateBasisPoints = 1200,
                        taxCents = tax,
                        cardFeeRateBasisPoints = 1200,
                        cardFeeCents = fee,
                        totalCents = price - tax - fee,
                        itemCount = 1,
                    ),
                    listOf(
                        Sale(
                            productId = index.toLong() + 1,
                            productName = "Product $index",
                            barcode = "BC$index",
                            quantity = 1,
                            unitPriceCents = price,
                            soldAt = stamp(8 + index % 12),
                            orderId = id,
                        )
                    ),
                )
            },
        )

    /** Records the lowest y coordinate any drawing call asked for. */
    private class BottomTrackingCanvas : Canvas() {
        var lowestY = 0f
            private set

        private fun track(y: Float) {
            if (y > lowestY) lowestY = y
        }

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            track(bottom)
            super.drawRect(left, top, right, bottom, paint)
        }

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            track(y)
            super.drawText(text, x, y, paint)
        }

        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            track(stopY)
            super.drawLine(startX, startY, stopX, stopY, paint)
        }
    }

    private fun contentBottom(report: DailyReport): Float =
        BottomTrackingCanvas().also {
            ReportRenderer.drawReport(it, 595f, report, labels)
        }.lowestY

    @Test
    fun aNormalDayFitsComfortablyOnThePage() {
        listOf(1, 5, 12).forEach { count ->
            val bottom = contentBottom(buildDailyReport(dayWith(count)))
            assertTrue(
                "a $count-product day reached $bottom, past the $pageHeight page",
                bottom <= pageHeight,
            )
        }
    }

    @Test
    fun aVeryBusyDayStillFitsOnThePage() {
        // The regression this guards: the table used to grow without limit, pushing the notes —
        // and the card-fee figures quoted in them — off the bottom of the sheet.
        listOf(20, 40, 100).forEach { count ->
            val bottom = contentBottom(buildDailyReport(dayWith(count)))
            assertTrue(
                "a $count-product day reached $bottom, past the $pageHeight page",
                bottom <= pageHeight,
            )
        }
    }

    @Test
    fun theSameDayAlwaysProducesTheSameHeight() {
        // Guards against the table silently reintroducing row growth: the cap should make a day
        // with 20, 40 or 100 products lay out to exactly the same height.
        val heights = listOf(20, 40, 100)
            .map { contentBottom(buildDailyReport(dayWith(it))) }
            .distinct()
        assertTrue("table height still varies with product count: $heights", heights.size == 1)
    }

    @Test
    fun anEmptyDayStillDrawsAPage() {
        val bottom = contentBottom(buildDailyReport(DayOrders(stamp(), emptyList())))
        assertTrue("empty day produced no content", bottom > 0f)
        assertTrue("empty day overflowed", bottom <= pageHeight)
    }

    @Test
    fun theFooterStaysWithinTheBottomMargin() {
        // The footer is the last thing drawn. It must sit inside the page while still leaving the
        // standard margin beneath it — a footer pinned to the very edge usually means the content
        // above it overran and pushed it down.
        val bottom = contentBottom(buildDailyReport(dayWith(40)))
        assertTrue("footer at $bottom is outside the page", bottom <= pageHeight)
        assertTrue(
            "footer at $bottom is flush with the page edge, so the content above overflowed",
            bottom <= pageHeight - 20f,
        )
    }
}
