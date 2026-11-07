package com.example.stockapplication.ui.screens.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.DailyReport
import com.example.stockapplication.data.PaymentSplit
import com.example.stockapplication.data.ReportSeries
import com.example.stockapplication.data.formatCents
import com.example.stockapplication.ui.components.BarChart
import com.example.stockapplication.ui.components.ChartColors
import com.example.stockapplication.ui.components.DonutChart
import com.example.stockapplication.ui.components.shortLabel
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * The analytics screen: what the day looked like, and how the week has gone.
 *
 * ## Why this replaced a dialog
 *
 * The old daily report was a print-styled sheet in a modal dialog — deliberately mimicking paper,
 * because its job was to be exported as a PDF. In the app it was cramped, it could not scroll past
 * a fixed height, and it showed one day with no context. This screen keeps the export buttons (the
 * PDF path is unchanged) and adds the trend, the payment split and the discount total, laid out for
 * reading on a phone rather than on A4.
 *
 * ## What is a range and what is a day
 *
 * The metric tiles and the trend are **range** figures (the last seven days). The hourly chart and
 * the product ranking are **day** figures for the day the user tapped in History. Mixing the two
 * without saying so is the classic way a dashboard misleads, so the two groups are visually
 * separated and each carries its own heading.
 */
@Composable
fun AnalyticsScreen(
    report: DailyReport,
    series: ReportSeries,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    onDismiss: () -> Unit,
    isExporting: Boolean,
    exportLabel: String?,
) {
    val colors = ChartColors.fromTheme()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                AnalyticsHeader(
                    dayStartMillis = report.dayStartMillis,
                    onDismiss = onDismiss,
                    onExportCsv = onExportCsv,
                    onExportPdf = onExportPdf,
                    isExporting = isExporting,
                )

                // ---- Range metrics -------------------------------------------------------
                SectionHeading(stringResource(R.string.analytics_range_week))
                MetricGrid(
                    cells = listOf(
                        stringResource(R.string.sales_report_orders) to
                            series.orderCount.toString(),
                        stringResource(R.string.sales_report_items) to
                            series.unitCount.toString(),
                        stringResource(R.string.sales_report_gross) to
                            formatCents(series.grossCents),
                        stringResource(R.string.sales_report_net) to
                            formatCents(series.netCents),
                        stringResource(R.string.analytics_discounts) to
                            formatCents(series.promoDiscountCents),
                        stringResource(R.string.analytics_average_day) to
                            formatCents(series.averageTradingDayNetCents),
                    ),
                )

                // ---- Trend ---------------------------------------------------------------
                ChartBox(height = 190) {
                    BarChart(
                        title = stringResource(R.string.analytics_trend_chart),
                        rows = series.points,
                        valueOf = { it.netCents.toFloat() },
                        valueLabel = { if (it.isEmpty) "" else formatCents(it.netCents) },
                        rowLabel = { dayAxisLabel(it.dayStartMillis) },
                        colors = colors,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // ---- Payment split -------------------------------------------------------
                ChartBox(height = 150) {
                    DonutChart(
                        title = stringResource(R.string.analytics_payment_chart),
                        rows = paymentRows(report.paymentSplit),
                        valueOf = { it.second },
                        rowLabel = { it.first },
                        colors = colors,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                // ---- The selected day ----------------------------------------------------
                SectionHeading(stringResource(R.string.analytics_day_section))

                ChartBox(height = 170) {
                    BarChart(
                        title = stringResource(R.string.sales_report_by_hour),
                        rows = report.hours,
                        valueOf = { it.revenueCents.toFloat() },
                        valueLabel = {
                            if (it.revenueCents == 0L) "" else formatCents(it.revenueCents)
                        },
                        rowLabel = { it.hour.toString().padStart(2, '0') },
                        colors = colors,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                ChartBox(height = 170) {
                    BarChart(
                        title = stringResource(R.string.analytics_top_products),
                        rows = report.topProducts,
                        valueOf = { it.quantity.toFloat() },
                        valueLabel = { it.quantity.toString() },
                        rowLabel = { it.productName.shortLabel() },
                        colors = colors,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(Modifier.height(24.dp))
            }

            // The busy overlay covers the screen while an export runs, so a second tap cannot
            // start a competing job and the user is not left wondering whether it worked.
            if (isExporting) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            if (exportLabel != null) {
                                Spacer(Modifier.height(12.dp))
                                Text(exportLabel, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsHeader(
    dayStartMillis: Long,
    onDismiss: () -> Unit,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    isExporting: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.analytics_close),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.analytics_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                DateFormat.getDateInstance(DateFormat.LONG).format(Date(dayStartMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onExportCsv,
            enabled = !isExporting,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.sales_report_export_csv), maxLines = 1)
        }
        OutlinedButton(
            onClick = onExportPdf,
            enabled = !isExporting,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.sales_report_export_pdf), maxLines = 1)
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp),
    )
}

/** A two-column grid of label/value tiles. */
@Composable
private fun MetricGrid(cells: List<Pair<String, String>>) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cells.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (label, value) ->
                    MetricTile(label, value, Modifier.weight(1f))
                }
                // Keeps an odd final tile the same width as the pairs above it.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** A fixed-height frame around a chart, so a chart with no data does not collapse the layout. */
@Composable
private fun ChartBox(height: Int, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(height.dp)
    ) {
        content()
    }
}

/** Turns the split into the label/value pairs the donut expects. */
@Composable
private fun paymentRows(split: PaymentSplit): List<Pair<String, Float>> = listOf(
    stringResource(R.string.analytics_payment_cash) to split.cashCents.toFloat(),
    stringResource(R.string.analytics_payment_card) to split.cardCents.toFloat(),
)

/** A one- or two-letter weekday label, which is all that fits under a bar. */
private fun dayAxisLabel(dayStartMillis: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = dayStartMillis
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "Mo"
        Calendar.TUESDAY -> "Tu"
        Calendar.WEDNESDAY -> "We"
        Calendar.THURSDAY -> "Th"
        Calendar.FRIDAY -> "Fr"
        Calendar.SATURDAY -> "Sa"
        else -> "Su"
    }
}
