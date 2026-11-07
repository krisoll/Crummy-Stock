package com.example.stockapplication.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable Canvas charts, shared by the analytics screen and the daily report.
 *
 * ## Why hand-drawn
 *
 * These were extracted from `DailyReportSheet`, which — like the PDF renderer — already drew its
 * charts on a raw `Canvas`. A charting library would add a megabyte or two to a small app to solve
 * a problem that was already solved here, so the drawing code moved into one place instead.
 *
 * ## Why colours come from the theme
 *
 * The original charts hardcoded a "paper" palette (white background, fixed blue slices) because
 * they were imitating a printed page. On screen that clashes with every custom theme in the app —
 * a user on the Midnight palette would get a white card floating in a dark page. These derive their
 * colours from [MaterialTheme.colorScheme] so charts belong to whichever palette is active.
 */

/** The palette a chart draws with, resolved once from the theme. */
data class ChartColors(
    val background: Color,
    val bar: Color,
    val barMuted: Color,
    val grid: Color,
    val label: Color,
    val muted: Color,
    val slices: List<Color>,
) {
    companion object {
        @Composable
        fun fromTheme(): ChartColors {
            val scheme = MaterialTheme.colorScheme
            return ChartColors(
                background = scheme.surfaceContainer,
                bar = scheme.primary,
                barMuted = scheme.primary.copy(alpha = 0.35f),
                grid = scheme.outlineVariant,
                label = scheme.onSurface,
                muted = scheme.onSurfaceVariant,
                // A short rotation of the theme's own accents, so a donut still separates into
                // legible slices without importing colours the user never chose.
                slices = listOf(
                    scheme.primary,
                    scheme.secondary,
                    scheme.tertiary,
                    scheme.primary.copy(alpha = 0.72f),
                    scheme.secondary.copy(alpha = 0.72f),
                    scheme.tertiary.copy(alpha = 0.72f),
                ),
            )
        }
    }
}

/**
 * A boxed column chart with a label under each bar and its value above.
 *
 * Generic over the row type so both the report (product lines) and the analytics trend (days) can
 * use it without converting into a shared model first.
 *
 * @param valueOf the magnitude deciding bar height. Negative values are treated as zero: a chart
 *   has no meaningful way to draw a bar below its baseline without also implying an axis the
 *   labels are not describing.
 */
@Composable
fun <T> BarChart(
    title: String,
    rows: List<T>,
    valueOf: (T) -> Float,
    valueLabel: (T) -> String,
    rowLabel: (T) -> String,
    modifier: Modifier = Modifier,
    colors: ChartColors = ChartColors.fromTheme(),
) {
    val values = rows.map { valueOf(it).coerceAtLeast(0f) }
    val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.background)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
        )

        // With no data there is nothing to plot, but the frame still draws so the layout does not
        // jump when the first order arrives.
        if (rows.isEmpty()) {
            Spacer(Modifier.weight(1f))
            Text(
                "—",
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            )
            return@Column
        }

        val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp)
        val valueStyle = labelStyle.copy(color = colors.label, textAlign = TextAlign.Center)
        val textMeasurer = rememberTextMeasurer()

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            val baseline = size.height - 2.dp.toPx()
            val plotHeight = (baseline - 12.dp.toPx()).coerceAtLeast(1f)
            val slot = size.width / rows.size
            val barWidth = (slot * 0.5f).coerceAtLeast(3.dp.toPx())

            drawLine(
                color = colors.grid,
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = 1f,
            )

            rows.forEachIndexed { index, row ->
                val centre = slot * index + slot / 2f
                val barLeft = centre - barWidth / 2f
                val ratio = (values[index] / max).coerceIn(0f, 1f)
                val barHeight = plotHeight * ratio

                if (barHeight > 0f) {
                    drawRect(
                        color = colors.bar,
                        topLeft = Offset(barLeft, baseline - barHeight),
                        size = Size(barWidth, barHeight),
                    )
                }

                // The value sits above its own bar and is centred on the bar, so a wide label
                // cannot push the bars around.
                val measured = textMeasurer.measure(valueLabel(row), valueStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        centre - measured.size.width / 2f,
                        (baseline - barHeight - measured.size.height - 1.dp.toPx())
                            .coerceAtLeast(0f),
                    ),
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            rows.forEach { row ->
                Text(
                    rowLabel(row),
                    style = labelStyle,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * A donut whose slices are each row's share of the total, with a legend beside it.
 *
 * Slices are sized by [valueOf] relative to the sum, so callers pass raw magnitudes (cents, units)
 * rather than pre-computed percentages.
 */
@Composable
fun <T> DonutChart(
    title: String,
    rows: List<T>,
    valueOf: (T) -> Float,
    rowLabel: (T) -> String,
    modifier: Modifier = Modifier,
    colors: ChartColors = ChartColors.fromTheme(),
) {
    val values = rows.map { valueOf(it).coerceAtLeast(0f) }
    val total = values.sum().takeIf { it > 0f } ?: 1f

    Column(modifier = modifier.fillMaxHeight()) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
        )
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DonutRing(values = values, total = total, colors = colors)
            Spacer(Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                rows.take(5).forEachIndexed { index, row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.width(6.dp).height(6.dp)) {
                            drawRect(colors.slices[index % colors.slices.size])
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(
                            rowLabel(row),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The ring itself.
 *
 * Split out so the payment-method split on the analytics screen can draw the same shape from two
 * raw values without inventing a row type for it.
 */
@Composable
fun DonutRing(
    values: List<Float>,
    total: Float,
    modifier: Modifier = Modifier.width(84.dp).height(84.dp),
    colors: ChartColors = ChartColors.fromTheme(),
) {
    Canvas(modifier = modifier) {
        drawRing(values, total, colors)
    }
}

/** Draws the ring into the current draw scope; shared by [DonutRing] and any custom caller. */
fun DrawScope.drawRing(values: List<Float>, total: Float, colors: ChartColors) {
    val radius = size.minDimension / 2f
    val thickness = radius * 0.46f
    val inset = thickness / 2f
    var startAngle = -90f

    if (values.none { it > 0f }) {
        // An all-zero ring would be invisible; draw the track so the chart still reads as "no
        // data" rather than as a rendering bug.
        drawArc(
            color = colors.grid,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - thickness, size.height - thickness),
            style = Stroke(width = thickness),
        )
        return
    }

    values.forEachIndexed { index, value ->
        val sweep = 360f * (value / total)
        drawArc(
            color = colors.slices[index % colors.slices.size],
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - thickness, size.height - thickness),
            style = Stroke(width = thickness),
        )
        startAngle += sweep
    }
}

/**
 * Shortens a label for an axis slot, which is only a few characters wide.
 *
 * The first word, or the first [limit] characters of it. Deliberately crude: an axis slot is not
 * the place for clever ellipsis rules, and a truncated word still lets the user recognise the
 * series while the full name is available elsewhere.
 */
fun String.shortLabel(limit: Int = 7): String {
    val first = trim().substringBefore(' ')
    return if (first.length <= limit) first else first.take(limit)
}
