package com.example.stockapplication

import androidx.test.core.app.ApplicationProvider
import com.example.stockapplication.data.Sale
import com.example.stockapplication.data.csvEscape
import com.example.stockapplication.data.csvFileName
import com.example.stockapplication.data.groupSalesByDay
import com.example.stockapplication.data.salesToCsv
import com.example.stockapplication.data.startOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

// Robolectric is needed because the CSV headers come from string resources.
@RunWith(RobolectricTestRunner::class)
class SalesExportTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun sale(
        id: Long,
        name: String,
        barcode: String = "123",
        quantity: Int = 1,
        priceCents: Long = 500,
        soldAt: Long,
    ) = Sale(
        id = id,
        productId = 1,
        productName = name,
        barcode = barcode,
        quantity = quantity,
        unitPriceCents = priceCents,
        soldAt = soldAt
    )

    @Test
    fun groupsSalesIntoDaysNewestFirst() {
        val day1 = millisAt(2025, Calendar.MARCH, 14, 9, 0)
        val day2 = millisAt(2025, Calendar.MARCH, 15, 9, 0)
        val sales = listOf(
            sale(1, "Coffee", soldAt = day1),
            sale(2, "Tea", soldAt = day2),
            sale(3, "Sugar", soldAt = day1 + 3_600_000)
        )

        val grouped = groupSalesByDay(sales)

        assertEquals(2, grouped.size)
        assertEquals(startOfDay(day2), grouped[0].dayStartMillis)
        assertEquals(1, grouped[0].sales.size)
        assertEquals(2, grouped[1].sales.size)
        // Within a day the newest sale comes first.
        assertEquals("Sugar", grouped[1].sales.first().productName)
    }

    @Test
    fun dayTotalsAreAggregated() {
        val at = millisAt(2025, Calendar.MARCH, 14, 12, 0)
        val grouped = groupSalesByDay(
            listOf(
                sale(1, "Coffee", quantity = 2, priceCents = 500, soldAt = at),
                sale(2, "Tea", quantity = 1, priceCents = 300, soldAt = at + 1000)
            )
        )

        assertEquals(3, grouped.single().unitCount)
        assertEquals(2 * 500L + 300L, grouped.single().totalCents)
        assertEquals(2, grouped.single().transactionCount)
    }

    @Test
    fun csvHasHeaderDataAndTotals() {
        val at = millisAt(2025, Calendar.MARCH, 14, 12, 0)
        val csv = salesToCsv(
            listOf(
                sale(1, "Coffee", barcode = "111", quantity = 2, priceCents = 500, soldAt = at),
                sale(2, "Tea", barcode = "222", quantity = 1, priceCents = 300, soldAt = at + 1000)
            ),
            context
        )
        val lines = csv.trim().lines()

        assertEquals("Sale ID,Date,Time,Product,Barcode,Quantity,Unit Price,Total", lines[0])
        assertEquals(4, lines.size)
        // Money cells carry the currency symbol, exactly as the screens show them.
        assertEquals("1,2025-03-14,12:00:00,Coffee,111,2,$5.00,$10.00", lines[1])
        assertTrue(lines[3].startsWith("Totals,"))
        assertTrue(lines[3].endsWith(",3,$13.00"))
    }

    @Test
    fun csvEscapesSpecialCharacters() {
        assertEquals("\"Beans, ground\"", csvEscape("Beans, ground"))
        assertEquals("\"Say \"\"hi\"\"\"", csvEscape("Say \"hi\""))
        assertEquals("Plain", csvEscape("Plain"))
    }

    @Test
    fun csvFileNameIncludesDay() {
        val at = millisAt(2025, Calendar.MARCH, 14, 12, 0)
        assertEquals("sales-2025-03-14.csv", csvFileName(startOfDay(at)))
    }
}
