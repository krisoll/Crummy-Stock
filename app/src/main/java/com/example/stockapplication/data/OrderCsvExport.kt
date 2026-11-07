package com.example.stockapplication.data

import android.content.Context
import com.example.stockapplication.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a CSV of completed sales **one row per order**, matching what the History screen shows.
 *
 * The older per-line [salesToCsv] no longer reflects the screen, so this is the one wired to the
 * export button.
 *
 * The header row is **localised** — the column names are looked up from string resources (and
 * therefore follow the language picked in Settings), just like the on-screen labels. The data
 * itself stays machine-readable on purpose:
 *
 * - dates use ISO `yyyy-MM-dd` and `HH:mm:ss`, so a spreadsheet parses them regardless of language;
 * - numbers are written plain with `.` as the decimal separator and no thousands grouping, so the
 *   file opens correctly in every locale.
 *
 * The card fee is split into its flat and total parts so the row always reconciles:
 * `Card Fee Rate (%) × Items Subtotal + Card Fee Fixed = Card Fee`.
 *
 * ## Money and currency
 *
 * Money columns carry the **bare number with the currency symbol** (`$12.99`), the same text the
 * screen shows — a file should read like the app it came from. The symbol rides on the cell rather
 * than being given its own column, because a column of bare numbers whose unit is described
 * elsewhere is exactly the ambiguity this export exists to avoid. A spreadsheet treats such a cell
 * as text rather than a number; that is the honest trade for a file meant to be read.
 */
fun ordersToCsv(days: List<DayOrders>, context: Context): String {
    val iso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val builder = StringBuilder()
    builder.append(
        listOf(
            context.getString(R.string.csv_col_order_id),
            context.getString(R.string.csv_col_date),
            context.getString(R.string.csv_col_time),
            context.getString(R.string.csv_col_payment),
            context.getString(R.string.csv_col_items),
            context.getString(R.string.csv_col_items_subtotal),
            context.getString(R.string.csv_col_tax_rate),
            context.getString(R.string.csv_col_tax),
            context.getString(R.string.csv_col_card_fee_rate),
            context.getString(R.string.csv_col_card_fee_fixed),
            context.getString(R.string.csv_col_card_fee),
            context.getString(R.string.csv_col_net_total),
            context.getString(R.string.csv_col_note),
        ).joinToString(",") { csvEscape(it) }
    ).append('\n')

    val allOrders = days.flatMap { it.orders }.sortedBy { it.order.soldAt }
    allOrders.forEach { entry ->
        val order = entry.order
        val stamp = iso.format(Date(order.soldAt))
        builder.append(csvEscape(order.orderId)).append(',')
        builder.append(stamp.substringBefore(' ')).append(',')
        builder.append(stamp.substringAfter(' ')).append(',')
        // Payment labels are localised too; cash/card only, so no cell can contain a comma.
        builder.append(csvEscape(order.paymentMethod.toLabel(context))).append(',')
        builder.append(order.itemCount).append(',')
        // Escaped: a symbol like `CHF ` or a currency added later could contain a comma.
        builder.append(csvEscape(formatCents(order.itemsSubtotalCents))).append(',')
        builder.append(rateText(order.taxRateBasisPoints)).append(',')
        builder.append(csvEscape(formatCents(order.taxCents))).append(',')
        builder.append(rateText(order.cardFeeRateBasisPoints)).append(',')
        builder.append(csvEscape(formatCents(order.cardFeeFixedCents))).append(',')
        builder.append(csvEscape(formatCents(order.cardFeeCents))).append(',')
        builder.append(csvEscape(formatCents(order.totalCents))).append(',')
        // Free text: may contain commas, quotes or line breaks, hence csvEscape.
        builder.append(csvEscape(order.note)).append('\n')
    }

    // Totals row so the file reconciles without a manual sum.
    val totalSubtotal = allOrders.sumOf { it.order.itemsSubtotalCents }
    val totalTax = allOrders.sumOf { it.order.taxCents }
    val totalFeeFixed = allOrders.sumOf { it.order.cardFeeFixedCents }
    val totalFee = allOrders.sumOf { it.order.cardFeeCents }
    val totalNet = allOrders.sumOf { it.order.totalCents }
    // The label sits in the ID column; everything up to "Items Subtotal" stays empty (six cells
    // including the label), then the money columns line up under their headers.
    builder.append(csvEscape(context.getString(R.string.csv_totals)))
        .append(",,,,,").append(csvEscape(formatCents(totalSubtotal))).append(",,")
        .append(csvEscape(formatCents(totalTax))).append(",,")
        .append(csvEscape(formatCents(totalFeeFixed)))
        .append(',').append(csvEscape(formatCents(totalFee)))
        .append(',').append(csvEscape(formatCents(totalNet)))
        // Notes intentionally aren't summed, so the totals row leaves that column empty.
        .append(',').append('\n')

    return builder.toString()
}

/** The localised name of this payment method, e.g. "Card" / "Tarjeta". */
private fun PaymentMethod.toLabel(context: Context): String = context.getString(
    when (this) {
        PaymentMethod.CASH -> R.string.payment_cash
        PaymentMethod.CARD -> R.string.payment_card
    }
)

/** A compact percentage for a basis-point rate, e.g. 275 -> "2.75", 1900 -> "19". */
private fun rateText(basisPoints: Int): String {
    val whole = basisPoints / 100
    val fraction = basisPoints % 100
    return if (fraction == 0) whole.toString() else "$whole.${fraction.toString().padStart(2, '0')}"
}

/** File name for the whole-history export. */
fun ordersCsvFileName(now: Long = System.currentTimeMillis()): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
    return "sales-orders-$stamp.csv"
}

/**
 * CSV for a **single day**, named after that day rather than today — so exporting yesterday's
 * sales doesn't produce a file that looks like it belongs to today.
 */
fun dayOrdersToCsv(day: DayOrders, context: Context): String = ordersToCsv(listOf(day), context)

fun dayOrdersCsvFileName(dayStartMillis: Long): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dayStartMillis))
    return "sales-$stamp.csv"
}
