package com.example.stockapplication

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.stockapplication.data.DayOrders
import com.example.stockapplication.data.OrderWithLines
import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.SaleOrder
import com.example.stockapplication.data.ordersCsvFileName
import com.example.stockapplication.data.ordersToCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

// Robolectric is needed because the CSV headers come from string resources.
@RunWith(RobolectricTestRunner::class)
class OrderCsvExportTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun dayOf(entry: OrderWithLines) = DayOrders(0L, listOf(entry))

    private fun entry(
        id: String,
        method: PaymentMethod = PaymentMethod.CASH,
        items: Int = 1,
        subtotal: Long = 1000,
        taxRate: Int = 1900,
        tax: Long = 190,
        feeRate: Int = 0,
        feeFixed: Long = 0,
        fee: Long = 0,
        total: Long = 810,
        note: String = "",
        soldAt: Long,
    ) = OrderWithLines(
        order = SaleOrder(
            orderId = id,
            soldAt = soldAt,
            paymentMethod = method,
            itemsSubtotalCents = subtotal,
            taxRateBasisPoints = taxRate,
            taxCents = tax,
            cardFeeRateBasisPoints = feeRate,
            cardFeeFixedCents = feeFixed,
            cardFeeCents = fee,
            totalCents = total,
            itemCount = items,
            note = note,
        ),
        lines = emptyList(),
    )

    private fun stamp(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun headerMatchesTheDocumentedColumns() {
        val csv = ordersToCsv(emptyList(), context)
        assertEquals(
            "Order ID,Date,Time,Payment,Items,Items Subtotal,Tax Rate (%),Tax," +
                "Card Fee Rate (%),Card Fee Fixed,Card Fee,Net Total,Note",
            csv.lineSequence().first()
        )
    }

    @Test
    @Config(qualifiers = "es-rES")
    fun headerFollowsTheSelectedLanguage() {
        val csv = ordersToCsv(emptyList(), context)
        assertEquals(
            "ID de pedido,Fecha,Hora,Pago,Artículos,Subtotal artículos,Impuesto (%)" +
                ",Impuesto,Comisión tarjeta (%),Comisión fija,Comisión tarjeta,Total neto,Nota",
            csv.lineSequence().first()
        )
    }

    @Test
    fun oneRowPerOrderWithRates() {
        val at = stamp(2025, Calendar.MARCH, 14, 10)
        val csv = ordersToCsv(
            listOf(
                dayOf(
                    entry(
                        id = "ORD-1",
                        method = PaymentMethod.CARD,
                        items = 2,
                        subtotal = 10000,
                        taxRate = 1000,
                        tax = 1000,
                        feeRate = 275,
                        feeFixed = 50,
                        fee = 325,
                        total = 8675,
                        soldAt = at,
                    )
                )
            ),
            context
        )

        val rows = csv.trim().lines()
        assertEquals(3, rows.size) // header + 1 order + totals
        // Money cells carry the currency symbol, exactly as the screens show them.
        assertEquals(
            "ORD-1,2025-03-14,10:00:00,Card,2,$100.00,10,$10.00,2.75,$0.50,$3.25,$86.75,",
            rows[1]
        )
    }

    @Test
    fun noteIsExportedAndEscaped() {
        val at = stamp(2025, Calendar.MARCH, 14, 10)
        val csv = ordersToCsv(
            listOf(
                dayOf(entry("A", note = "Table 4, Mr. Okafor", soldAt = at)),
                dayOf(entry("B", note = "", soldAt = at + 1000)),
            ),
            context
        )

        val rows = csv.trim().lines()
        // A comma inside the note must not shift the columns.
        assertTrue(rows[1].endsWith(",\"Table 4, Mr. Okafor\""))
        // An order without a note still gets the trailing (empty) column.
        assertTrue(rows[2].endsWith(",$8.10,"))
    }

    @Test
    @Config(qualifiers = "es-rES")
    fun paymentAndTotalsLabelAreTranslated() {
        val at = stamp(2025, Calendar.MARCH, 14, 10)
        val csv = ordersToCsv(
            listOf(
                dayOf(
                    entry(
                        id = "ORD-1",
                        method = PaymentMethod.CASH,
                        subtotal = 1000,
                        tax = 190,
                        total = 810,
                        soldAt = at,
                    )
                )
            ),
            context
        )

        val rows = csv.trim().lines()
        assertTrue(rows[1].startsWith("ORD-1,2025-03-14,10:00:00,Efectivo,"))
        // Dates stay ISO and money keeps the '.' decimal separator; only the symbol is local to
        // the setting. The trailing comma is the empty note column.
        assertTrue(rows[1].endsWith(",$8.10,"))
        assertTrue(rows.last().startsWith("Totales,"))
    }

    @Test
    fun totalsRowSumsEveryOrder() {
        val at = stamp(2025, Calendar.MARCH, 14, 10)
        val csv = ordersToCsv(
            listOf(
                dayOf(entry("A", subtotal = 1000, tax = 190, total = 810, soldAt = at)),
                dayOf(entry("B", subtotal = 2000, tax = 380, total = 1620, soldAt = at + 1000)),
            ),
            context
        )

        val totalsRow = csv.trim().lines().last()
        assertTrue(totalsRow.startsWith("Totals,"))
        // Notes aren't summed, so the totals row leaves that trailing column empty.
        assertTrue(totalsRow.endsWith(",$30.00,,$5.70,,$0.00,$0.00,$24.30,"))
    }

    @Test
    fun ordersAreSortedByTimeAcrossDays() {
        val early = stamp(2025, Calendar.MARCH, 13, 9)
        val late = stamp(2025, Calendar.MARCH, 14, 9)
        val csv = ordersToCsv(
            listOf(
                DayOrders(0L, listOf(entry("LATE", soldAt = late))),
                DayOrders(0L, listOf(entry("EARLY", soldAt = early))),
            ),
            context
        )

        val rows = csv.trim().lines()
        assertTrue(rows[1].startsWith("EARLY"))
        assertTrue(rows[2].startsWith("LATE"))
    }

    @Test
    fun fileNameIsDated() {
        val at = stamp(2025, Calendar.MARCH, 14, 10)
        assertEquals("sales-orders-2025-03-14.csv", ordersCsvFileName(at))
    }
}
