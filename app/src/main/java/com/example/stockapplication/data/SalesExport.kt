package com.example.stockapplication.data

import android.content.Context
import androidx.annotation.StringRes
import com.example.stockapplication.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A single calendar day's worth of sales, used by the day-grouped history screen. */
data class DaySales(
    val dayStartMillis: Long,
    val sales: List<Sale>,
) {
    val unitCount: Int get() = sales.sumOf { it.quantity }
    val totalCents: Long get() = sales.sumOf { it.totalCents }
    val transactionCount: Int get() = sales.size

    /**
     * The string resource holding this day's heading. Resolve it with [dayStartMillis] as the
     * single format argument; [R.string.day_today] and [R.string.day_yesterday] ignore it.
     */
    @StringRes
    fun labelResource(now: Long = System.currentTimeMillis()): Int =
        formatDayLabelResource(dayStartMillis, now)
}

/** Returns the local-midnight timestamp that [millis] falls on. */
fun startOfDay(millis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/**
 * The string resource holding a day heading: today, yesterday, or a short date that takes the
 * day's start timestamp as its format argument.
 */
@StringRes
fun formatDayLabelResource(dayStartMillis: Long, now: Long = System.currentTimeMillis()): Int {
    val today = startOfDay(now)
    val day = startOfDay(dayStartMillis)
    val dayLength = 24L * 60 * 60 * 1000
    return when (day) {
        today -> R.string.day_today
        today - dayLength -> R.string.day_yesterday
        else -> R.string.date_short
    }
}

/** "Today", "Yesterday" or a short date such as "14 Mar 2025". */
fun formatDayLabel(
    context: Context,
    dayStartMillis: Long,
    now: Long = System.currentTimeMillis(),
): String = context.getString(formatDayLabelResource(dayStartMillis, now), dayStartMillis)

/** Groups sales (already sorted newest-first) into day buckets, newest day first. */
fun groupSalesByDay(sales: List<Sale>): List<DaySales> =
    sales.groupBy { startOfDay(it.soldAt) }
        .toSortedMap(compareByDescending { it })
        .map { (day, daySales) -> DaySales(day, daySales.sortedByDescending { it.soldAt }) }

/**
 * Builds a CSV document for [sales]. The header row is localised from string resources, so it
 * follows the language chosen in Settings; the data columns keep ISO dates and plain `.` decimals
 * so a spreadsheet in any locale parses them.
 */
fun salesToCsv(sales: List<Sale>, context: Context): String {
    val iso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val builder = StringBuilder()
    builder.append(
        listOf(
            context.getString(R.string.csv_col_sale_id),
            context.getString(R.string.csv_col_date),
            context.getString(R.string.csv_col_time),
            context.getString(R.string.csv_col_product),
            context.getString(R.string.csv_col_barcode),
            context.getString(R.string.csv_col_quantity),
            context.getString(R.string.csv_col_unit_price),
            context.getString(R.string.csv_col_total),
        ).joinToString(",") { csvEscape(it) }
    ).append('\n')
    sales.sortedBy { it.soldAt }.forEach { sale ->
        val timestamp = Date(sale.soldAt)
        val formatted = iso.format(timestamp)
        builder.append(sale.id).append(',')
        builder.append(formatted.substringBefore(' ')).append(',')
        builder.append(formatted.substringAfter(' ')).append(',')
        builder.append(csvEscape(sale.productName)).append(',')
        builder.append(csvEscape(sale.barcode)).append(',')
        builder.append(sale.quantity).append(',')
        // Escaped because the cell now carries the currency symbol, see `ordersToCsv`: every
        // money cell reads exactly as it does on screen, symbol included.
        builder.append(csvEscape(formatCents(sale.unitPriceCents))).append(',')
        builder.append(csvEscape(formatCents(sale.totalCents))).append('\n')
    }
    // Totals row so the exported file reconciles without a manual sum.
    builder.append(csvEscape(context.getString(R.string.csv_totals)))
        .append(",,,,").append(sales.sumOf { it.quantity })
        .append(',').append(csvEscape(formatCents(sales.sumOf { it.totalCents }))).append('\n')
    return builder.toString()
}

/** Default file name for a day's export, e.g. "sales-2025-03-14.csv". */
fun csvFileName(dayStartMillis: Long): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dayStartMillis))
    return "sales-$stamp.csv"
}

/** Quotes a field per RFC 4180 when it contains a comma, quote or newline. */
internal fun csvEscape(value: String): String {
    val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    val escaped = value.replace("\"", "\"\"")
    return if (needsQuotes) "\"$escaped\"" else escaped
}
