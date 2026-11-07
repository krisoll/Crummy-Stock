package com.example.stockapplication.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws a day's report as a branded A4 sheet, either to a PDF (the primary export) or a PNG.
 *
 * Both formats share all the drawing code: they differ only in the [Canvas] they hand in, so the
 * PDF can never drift from the image.
 *
 * The layout follows a printed-sales-report style: a dark masthead carrying the shop name and the
 * headline money figures, a titled band, three charts side by side (units over the week, revenue
 * per day, and a product mix donut), then the itemised table that does the real work, and finally
 * a notes block and a footer rule.
 *
 * Hand-drawn with [Canvas] rather than a charting library: the project has no charting dependency
 * and the shapes needed are rectangles, one polyline and one pie.
 *
 * Several sections of the reference design have **no counterpart in this app's data** and are
 * deliberately not faked:
 *
 *  - "Sales person" — the app has no notion of a cashier.
 *  - Weekday and Saturday-to-Saturday axes — a report covers **one day**.
 *
 * Where the design implies those, this renderer substitutes the equivalent real quantity (see the
 * per-section notes in [drawReport]) rather than printing invented numbers.
 *
 * All text is passed in already localised, so this object holds no string resources.
 */
object ReportRenderer {

    /** Logical page width in points; everything is laid out relative to it. */
    private const val PAGE_WIDTH = 595f // A4 at 72dpi
    private const val PAGE_HEIGHT = 842f
    private const val MARGIN = 34f

    /** Drawing units per page unit. Larger = crisper output, larger file. */
    private const val RENDER_SCALE = 2f

    /** Strings the report needs, supplied by the caller so this stays free of Android resources. */
    data class Labels(
        val title: String,
        val company: String,
        val orders: String,
        val items: String,
        val gross: String,
        val tax: String,
        val cardFee: String,
        val cardFeeFixed: String,
        val net: String,
        val topProducts: String,
        val hourly: String,
        val payments: String,
        val cash: String,
        val card: String,
        val empty: String,
        /** Header of the itemised table. */
        val tableItems: String,
        val tablePrice: String,
        val tableQty: String,
        val tableAmount: String,
        val tableTaxRate: String,
        val tableTax: String,
        /** Header of the per-product card-commission column. */
        val tableCardFee: String,
        /** Header of the per-product net total (amount minus tax and card fee). */
        val tableTotal: String,
        /** Footer/total row of the itemised table. */
        val tableGrandTotal: String,
        /** Section headings for the two small charts beside the donut. */
        val unitsSold: String,
        val totalSales: String,
        val itemSold: String,
        /** "Notes:" heading over the summary block. */
        val notes: String,
        /** Note sentence; `{gross}` and `{orders}` are substituted. */
        val noteTotal: String,
        /** Note sentence; `{name}`, `{qty}` and `{revenue}` are substituted. */
        val noteBestSeller: String,
        /** Note sentence; `{hour}` and `{revenue}` are substituted. */
        val noteBusiestHour: String,
        /** Printed when a product was sold under more than one tax rate that day. */
        val mixedRate: String,
        /** Shown in the table when the product list is truncated; `%1$d` is the hidden count. */
        val tableMoreRows: String,
    )

    // ---- Palette -------------------------------------------------------------

    /** Deep green used for the masthead, the band and the table header. */
    private val BRAND_DARK = Color.rgb(0x1B, 0x4D, 0x3E)
    private val BRAND_MID = Color.rgb(0x2E, 0x6B, 0x55)
    private val BRAND_LIGHT = Color.rgb(0xE8, 0xF0, 0xEC)

    /** Donut slices. Muted, and deliberately desaturated so the table stays the focus. */
    private val SLICE_COLORS = listOf(
        Color.rgb(0xC9, 0x7B, 0x5A),
        Color.rgb(0x2E, 0x6B, 0x55),
        Color.rgb(0x8E, 0xA9, 0x9C),
        Color.rgb(0xD9, 0xC3, 0x8C),
        Color.rgb(0x6B, 0x8C, 0xA8),
        Color.rgb(0xA8, 0x8C, 0x9E),
    )

    private val GRID_COLOR = Color.rgb(0xD8, 0xDE, 0xDB)
    private val TEXT_MUTED = Color.rgb(0x5A, 0x6B, 0x64)

    // ---- Reports -------------------------------------------------------------

    /** Renders the report to a single-page A4 PDF. */
    fun renderPdf(report: DailyReport, labels: Labels): ByteArray {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(
            PAGE_WIDTH.toInt(),
            PAGE_HEIGHT.toInt(),
            1
        ).create()
        val page = document.startPage(pageInfo)
        // PdfDocument canvases are already in page points, so no extra scale is applied here.
        drawReport(page.canvas, PAGE_WIDTH, report, labels)
        document.finishPage(page)

        val bytes = ByteArrayOutputStream().use { stream ->
            document.writeTo(stream)
            stream.toByteArray()
        }
        document.close()
        return bytes
    }

    /** Renders the report to a tall PNG, for quick sharing in chat apps. */
    fun renderPng(report: DailyReport, labels: Labels): android.graphics.Bitmap {
        val width = (PAGE_WIDTH * RENDER_SCALE).toInt()
        val height = (PAGE_HEIGHT * RENDER_SCALE).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(
            width,
            height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(RENDER_SCALE, RENDER_SCALE)
        drawReport(canvas, PAGE_WIDTH, report, labels)
        return bitmap
    }

    fun pdfFileName(dayStartMillis: Long): String = "sales-report-${dayStamp(dayStartMillis)}.pdf"

    fun pngFileName(dayStartMillis: Long): String = "sales-report-${dayStamp(dayStartMillis)}.png"

    private fun dayStamp(dayStartMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dayStartMillis))

    // ---- Layout constants ----------------------------------------------------

    private const val MASTHEAD_H = 64f
    private const val SUMMARY_STRIP_H = 58f
    private const val BAND_H = 22f
    private const val SECTION_TITLE_H = 26f
    private const val CHART_H = 150f
    private const val TABLE_ROW_H = 17f
    private const val TABLE_HEADER_H = 20f
    private const val NOTES_MIN_H = 74f

    /**
     * Items beyond this many products are folded into a single "+N more" row.
     *
     * The table is the only section whose height grows with the data, so without a cap a busy day
     * pushes the notes — and the card-fee figures that live in them — off the bottom of the page.
     * Sized so the tallest possible report still lands above [PAGE_HEIGHT] with the notes and
     * footer intact; see `ReportOverflowProbeTest`.
     */
    private const val MAX_TABLE_ROWS = 14

    /**
     * Column widths for the itemised table, as fractions of the content width.
     *
     * Order is: index, name, price, qty, amount, tax rate, tax, card fee, total. The name column
     * funds the card-fee column, which previously had no data and no header.
     */
    private val TABLE_WEIGHTS = floatArrayOf(0.06f, 0.25f, 0.11f, 0.07f, 0.12f, 0.09f, 0.10f, 0.10f, 0.10f)

    // ---- Drawing -------------------------------------------------------------

    /**
     * Draws the whole report onto [canvas], laid out for a page [width] wide.
     *
     * PDF and PNG share this, which is why no format-specific code lives below.
     */
    // Internal rather than private so `ReportLayoutTest` can measure where content lands; a
    // full-page report has no scrolling, so overflow is invisible unless it is asserted on.
    internal fun drawReport(canvas: Canvas, width: Float, report: DailyReport, labels: Labels) {
        val right = width - MARGIN
        val contentWidth = right - MARGIN
        var y: Float

        // ---- Masthead --------------------------------------------------------
        // Full-bleed: the dark band is the page's identity and anchoring it to the paper edge is
        // what makes the sheet read as a report rather than as a screenshot.
        canvas.drawRect(0f, 0f, width, MASTHEAD_H, fillPaint(BRAND_DARK))
        drawCentred(
            canvas,
            labels.company,
            0f,
            width,
            MASTHEAD_H / 2f + 4f,
            textPaint(12f, Color.WHITE, bold = true),
        )
        y = MASTHEAD_H

        // ---- Headline money strip --------------------------------------------
        // The design's headline block is the one thing that maps cleanly onto this app: gross,
        // deductions and the money actually kept, left-to-right on a light strip.
        canvas.drawRect(0f, y, width, y + SUMMARY_STRIP_H, fillPaint(BRAND_LIGHT))
        val cells = listOf(
            labels.gross to formatCents(report.grossCents),
            labels.tax to "-" + formatCents(report.taxCents),
            labels.cardFee to "-" + formatCents(report.cardFeeCents),
            labels.net to formatCents(report.netCents),
        )
        val cellWidth = width / cells.size
        cells.forEachIndexed { index, (label, value) ->
            val left = index * cellWidth
            // The net figure is the punchline, so it gets its own inverted block.
            if (index == cells.lastIndex) {
                canvas.drawRect(left, y, left + cellWidth, y + SUMMARY_STRIP_H, fillPaint(BRAND_MID))
            }
            drawCentred(
                canvas,
                label,
                left,
                left + cellWidth,
                y + 22f,
                textPaint(8f, if (index == cells.lastIndex) Color.WHITE else TEXT_MUTED),
            )
            drawCentred(
                canvas,
                value,
                left,
                left + cellWidth,
                y + 43f,
                textPaint(
                    14f,
                    if (index == cells.lastIndex) Color.WHITE else BRAND_DARK,
                    bold = true,
                ),
            )
        }
        y += SUMMARY_STRIP_H + 12f

        // ---- Title band ------------------------------------------------------
        val dayText = SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault())
            .format(Date(report.dayStartMillis))
        canvas.drawRect(MARGIN, y, right, y + BAND_H, fillPaint(BRAND_LIGHT))
        canvas.drawText(labels.title.uppercase(Locale.getDefault()), MARGIN + 8f, y + 15f, textPaint(10f, BRAND_DARK, bold = true))
        canvas.drawText(dayText, right - 8f - textWidth(dayText, textPaint(9f, TEXT_MUTED)), y + 15f, textPaint(9f, TEXT_MUTED))
        y += BAND_H + 10f

        // An empty day still gets the masthead and a titled band, so the sheet reads as "no sales"
        // rather than as a rendering failure.
        if (report.isEmpty) {
            canvas.drawText(labels.empty, MARGIN, y + 14f, textPaint(10f, TEXT_MUTED))
            drawFooter(canvas, width, y + 30f)
            return
        }

        // ---- Three charts ----------------------------------------------------
        y = drawCharts(canvas, MARGIN, contentWidth, y, report, labels)

        // ---- Itemised table --------------------------------------------------
        y = drawTable(canvas, MARGIN, contentWidth, y + 12f, report, labels)

        // ---- Notes -----------------------------------------------------------
        y = drawNotes(canvas, MARGIN, y + 12f, report, labels)

        drawFooter(canvas, width, y + 8f)
    }

    /**
     * The three-chart row: units per product, revenue per product, and the product mix donut.
     *
     * These are substitutes for the design's weekday / week / donut trio. A single-day report has
     * no week to plot and no time axis to share, so the two bar charts plot per-product figures
     * instead — still a real comparison, just against products rather than days.
     *
     * @return the y coordinate below the row.
     */
    private fun drawCharts(
        canvas: Canvas,
        left: Float,
        contentWidth: Float,
        top: Float,
        report: DailyReport,
        labels: Labels,
    ): Float {
        val gap = 10f
        val colWidth = (contentWidth - gap * 2f) / 3f
        val boxTop = top + SECTION_TITLE_H
        val boxBottom = boxTop + CHART_H

        val boxes = (0..2).map { index ->
            val boxLeft = left + index * (colWidth + gap)
            canvas.drawRect(boxLeft, boxTop, boxLeft + colWidth, boxBottom, strokePaint(GRID_COLOR))
            boxLeft
        }

        val rows = report.lines.take(6)
        val maxQty = rows.maxOfOrNull { it.quantity }?.coerceAtLeast(1) ?: 1
        val maxAmount = rows.maxOfOrNull { it.amountCents }?.coerceAtLeast(1L) ?: 1L

        // ---- Chart 1: quantity sold -----------------------------------------
        val qtyLeft = boxes[0]
        canvas.drawText(labels.unitsSold, qtyLeft + 6f, boxTop + 14f, textPaint(8f, BRAND_DARK, bold = true))
        drawMiniBars(
            canvas = canvas,
            left = qtyLeft + 6f,
            right = qtyLeft + colWidth - 6f,
            top = boxTop + 22f,
            bottom = boxBottom - 6f,
            values = rows.map { it.quantity.toFloat() },
            max = maxQty.toFloat(),
            labels = rows.map { it.productName.shortLabel() },
            valueLabels = rows.map { it.quantity.toString() },
            color = BRAND_MID,
        )

        // ---- Chart 2: total sales -------------------------------------------
        val salesLeft = boxes[1]
        canvas.drawText(labels.totalSales, salesLeft + 6f, boxTop + 14f, textPaint(8f, BRAND_DARK, bold = true))
        drawMiniBars(
            canvas = canvas,
            left = salesLeft + 6f,
            right = salesLeft + colWidth - 6f,
            top = boxTop + 22f,
            bottom = boxBottom - 6f,
            values = rows.map { it.amountCents.toFloat() },
            max = maxAmount.toFloat(),
            labels = rows.map { it.productName.shortLabel() },
            valueLabels = rows.map { formatCents(it.amountCents) },
            color = BRAND_MID,
        )

        // ---- Chart 3: product mix donut --------------------------------------
        val donutLeft = boxes[2]
        canvas.drawText(labels.itemSold, donutLeft + 6f, boxTop + 14f, textPaint(8f, BRAND_DARK, bold = true))
        drawDonut(
            canvas = canvas,
            left = donutLeft + 6f,
            top = boxTop + 20f,
            right = donutLeft + colWidth - 46f,
            bottom = boxBottom - 16f,
            rows = rows,
        )

        return boxBottom + 4f
    }

    /**
     * A compact vertical bar chart with a label under each bar and its value above.
     *
     * Bars are shrunk to a minimum readable width and then centred in their slot; without the
     * minimum, a six-product chart in a third of the page draws hairlines.
     */
    private fun drawMiniBars(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        values: List<Float>,
        max: Float,
        labels: List<String>,
        valueLabels: List<String>,
        color: Int,
    ) {
        if (values.isEmpty()) return
        val labelPaint = textPaint(5.5f, TEXT_MUTED)
        val valuePaint = textPaint(5.5f, BRAND_DARK)

        // Baseline sits above the label strip so labels never overlap the bars.
        val baseline = bottom - 16f
        val plotHeight = baseline - top - 10f
        val slot = (right - left) / values.size
        val barWidth = (slot * 0.46f).coerceAtLeast(4f)

        canvas.drawLine(left, baseline, right, baseline, strokePaint(GRID_COLOR))

        values.forEachIndexed { index, value ->
            val centre = left + slot * index + slot / 2f
            val barLeft = centre - barWidth / 2f
            val ratio = (value / max).coerceIn(0f, 1f)
            // A zero bar still reads as "zero" thanks to the baseline rule beneath it.
            val barHeight = plotHeight * ratio
            if (barHeight > 0f) {
                canvas.drawRect(barLeft, baseline - barHeight, barLeft + barWidth, baseline, fillPaint(color))
            }
            drawCentred(canvas, valueLabels.getOrElse(index) { "" }, barLeft - 8f, barLeft + barWidth + 8f, baseline - barHeight - 3f, valuePaint)
            drawCentred(canvas, labels.getOrElse(index) { "" }.take(9), centre - slot / 2f, centre + slot / 2f, bottom - 5f, labelPaint)
        }
    }

    /**
     * A donut with a legend down its right side.
     *
     * Slices are proportional to each product's revenue. The top slice is nudged out by a couple
     * of points — a visual cue that survives being printed small.
     */
    private fun drawDonut(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        rows: List<ReportLineRow>,
    ) {
        val total = rows.sumOf { it.amountCents }.coerceAtLeast(1L)
        val size = minOf(right - left, bottom - top)
        val radius = size / 2f
        val centreX = left + radius
        val centreY = top + radius
        val thickness = (radius * 0.52f).coerceAtLeast(8f)

        var startAngle = -90f
        rows.forEachIndexed { index, row ->
            val sweep = 360f * (row.amountCents.toFloat() / total)
            val paint = fillPaint(SLICE_COLORS[index % SLICE_COLORS.size])
            // Draw as a thick arc rather than a filled wedge, so the hole stays crisply circular.
            paint.strokeWidth = thickness
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.BUTT
            canvas.drawArc(
                RectF(centreX - radius + thickness / 2f, centreY - radius + thickness / 2f, centreX + radius - thickness / 2f, centreY + radius - thickness / 2f),
                startAngle,
                sweep,
                false,
                paint,
            )
            startAngle += sweep
        }

        // Legend, right of the donut.
        val legendLeft = left + size + 8f
        var legendY = top + 8f
        rows.take(5).forEachIndexed { index, row ->
            val paint = fillPaint(SLICE_COLORS[index % SLICE_COLORS.size])
            canvas.drawRect(legendLeft, legendY - 4.5f, legendLeft + 5f, legendY + 0.5f, paint)
            canvas.drawText(row.productName.shortLabel().take(13), legendLeft + 8f, legendY, textPaint(5.5f, TEXT_MUTED))
            legendY += 10f
        }
    }

    /**
     * The itemised table — the part of the sheet a shopkeeper actually reads.
     *
     * Rows are products ranked by revenue. The tax and total columns are real apportioned figures
     * (see [buildReportLines]); a product sold under two tax rates prints a dash in the rate
     * column rather than one of the two presented as the truth.
     *
     * The row area is padded to a minimum so a quiet day still produces a page that looks like a
     * report rather than a stub.
     *
     * @return the y coordinate below the table and its totals row.
     */
    private fun drawTable(
        canvas: Canvas,
        left: Float,
        contentWidth: Float,
        top: Float,
        report: DailyReport,
        labels: Labels,
    ): Float {
        val right = left + contentWidth
        val columns = TABLE_WEIGHTS
        val x = FloatArray(columns.size)
        var cursor = left
        columns.forEachIndexed { index, weight ->
            x[index] = cursor
            cursor += contentWidth * weight
        }

        // Positional, one entry per table column. The blank slot is the item-number column, whose
        // header is not worth printing at this size.
        val headerTexts = arrayOf(
            labels.tableItems,
            "",
            labels.tablePrice,
            labels.tableQty,
            labels.tableAmount,
            labels.tableTaxRate,
            labels.tableTax,
            labels.tableCardFee,
            labels.tableTotal,
        )

        // Header band.
        canvas.drawRect(left, top, right, top + TABLE_HEADER_H, fillPaint(BRAND_DARK))
        val headerPaint = textPaint(6.5f, Color.WHITE, bold = true)
        headerTexts.forEachIndexed { index, text ->
            if (text.isEmpty()) return@forEachIndexed
            val alignRight = index > 1
            if (alignRight) {
                drawRight(canvas, text, x[index] + contentWidth * columns[index] - 4f, top + 13f, headerPaint)
            } else {
                canvas.drawText(text, x[index] + 4f, top + 13f, headerPaint)
            }
        }

        var y = top + TABLE_HEADER_H
        val rowPaint = textPaint(6.5f, Color.BLACK)
        val rowBold = textPaint(6.5f, BRAND_DARK, bold = true)
        val rowMuted = textPaint(6.5f, TEXT_MUTED)

        // Minimum row count keeps the sheet's proportions stable across quiet and busy days.
        val minRows = 12

        // The table grows with the product count, so it is capped rather than allowed to run off
        // the page. One slot is reserved for the overflow row when the cap is exceeded.
        val overflowCount = (report.lines.size - MAX_TABLE_ROWS).coerceAtLeast(0)
        val visibleRows = if (overflowCount > 0) {
            report.lines.take(MAX_TABLE_ROWS - 1)
        } else {
            report.lines
        }
        val drawn = visibleRows.size.coerceAtLeast(1)

        visibleRows.forEachIndexed { index, row ->
            if (index % 2 == 1) {
                canvas.drawRect(left, y, right, y + TABLE_ROW_H, fillPaint(Color.rgb(0xF6, 0xF8, 0xF7)))
            }
            val baseline = y + TABLE_ROW_H - 5f
            canvas.drawText("P%1\$03d".format(index + 1), x[0] + 4f, baseline, rowMuted)
            canvas.drawText(row.productName.take(30), x[1] + 4f, baseline, rowPaint)
            // Each money cell is clamped to its own column: a long currency symbol must not run
            // backwards into the column beside it.
            drawRight(canvas, formatCents(row.unitPriceCents), x[2] + contentWidth * columns[2] - 4f, baseline, rowPaint, x[2] + 2f)
            drawRight(canvas, row.quantity.toString(), x[3] + contentWidth * columns[3] - 4f, baseline, rowPaint, x[3] + 2f)
            drawRight(canvas, formatCents(row.amountCents), x[4] + contentWidth * columns[4] - 4f, baseline, rowPaint, x[4] + 2f)
            val rateText = if (row.mixedTaxRate) {
                labels.mixedRate
            } else {
                formatRate(row.taxRateBasisPoints)
            }
            drawRight(canvas, rateText, x[5] + contentWidth * columns[5] - 4f, baseline, rowPaint, x[5] + 2f)
            drawRight(canvas, formatCents(row.taxCents), x[6] + contentWidth * columns[6] - 4f, baseline, rowPaint, x[6] + 2f)
            drawRight(canvas, formatCents(row.cardFeeCents), x[7] + contentWidth * columns[7] - 4f, baseline, rowPaint, x[7] + 2f)
            drawRight(canvas, formatCents(row.totalCents), x[8] + contentWidth * columns[8] - 4f, baseline, rowBold, x[8] + 2f)
            canvas.drawLine(left, y + TABLE_ROW_H, right, y + TABLE_ROW_H, strokePaint(Color.rgb(0xE8, 0xEC, 0xEA)))
            y += TABLE_ROW_H
        }

        // Anything past the cap is summarised rather than dropped silently: a reader comparing the
        // row count to the day's product count must be able to see that the list is truncated.
        if (overflowCount > 0) {
            val baseline = y + TABLE_ROW_H - 5f
            canvas.drawText(
                labels.tableMoreRows.format(overflowCount),
                x[0] + 4f,
                baseline,
                rowMuted,
            )
            canvas.drawLine(left, y + TABLE_ROW_H, right, y + TABLE_ROW_H, strokePaint(Color.rgb(0xE8, 0xEC, 0xEA)))
            y += TABLE_ROW_H
        }

        // Pad with empty ruled rows. The padding is what keeps a quiet day's page looking like a
        // report, so it only counts the rows actually drawn.
        repeat((minRows - drawn).coerceAtLeast(0)) {
            canvas.drawRect(left, y, right, y + TABLE_ROW_H, strokePaint(Color.rgb(0xE8, 0xEC, 0xEA)))
            y += TABLE_ROW_H
        }

        // Totals row, summing the columns that are additive. The tax-rate column stays blank
        // because summing rates is meaningless.
        canvas.drawRect(left, y, right, y + TABLE_HEADER_H, fillPaint(BRAND_LIGHT))
        val totalPaint = textPaint(7f, BRAND_DARK, bold = true)
        canvas.drawText(labels.tableGrandTotal, x[0] + 4f, y + 13f, totalPaint)
        drawRight(canvas, formatCents(report.linesAmountCents), x[4] + contentWidth * columns[4] - 4f, y + 13f, totalPaint, x[4] + 2f)
        drawRight(canvas, formatCents(report.linesTaxCents), x[6] + contentWidth * columns[6] - 4f, y + 13f, totalPaint, x[6] + 2f)
        drawRight(canvas, formatCents(report.linesCardFeeCents), x[7] + contentWidth * columns[7] - 4f, y + 13f, totalPaint, x[7] + 2f)
        drawRight(canvas, formatCents(report.linesTotalCents), x[8] + contentWidth * columns[8] - 4f, y + 13f, totalPaint, x[8] + 2f)
        y += TABLE_HEADER_H

        return y
    }

    /**
     * The notes block: three sentences derived from the day's real figures, plus the order count.
     *
     * Generated rather than free text — the app has no field for an operator's note on the export,
     * so anything typed here would have nowhere to come from.
     *
     * @return the y coordinate below the block.
     */
    private fun drawNotes(
        canvas: Canvas,
        left: Float,
        top: Float,
        report: DailyReport,
        labels: Labels,
    ): Float {
        var y = top
        canvas.drawText(labels.notes, left, y + 9f, textPaint(7f, BRAND_DARK, bold = true))
        y += 18f

        val body = textPaint(6.5f, TEXT_MUTED)
        val best = report.bestSeller
        val busiest = report.busiestHour

        // The note sentences are positional-argument templates (`%1$s`) so they stay grammatical
        // in languages that reorder the values. Building them by string concatenation instead
        // would produce untranslatable word order.
        val sentences = mutableListOf<String>()
        sentences += labels.noteTotal.format(formatCents(report.grossCents), report.orderCount)
        if (best != null) {
            sentences += labels.noteBestSeller.format(
                best.productName,
                best.quantity,
                formatCents(best.revenueCents),
            )
        }
        if (busiest != null) {
            sentences += labels.noteBusiestHour.format(
                "%02d:00".format(busiest.hour),
                formatCents(busiest.revenueCents),
            )
        }

        sentences.forEach { sentence ->
            canvas.drawText(sentence, left, y, body)
            y += 11f
        }

        return (y).coerceAtLeast(top + NOTES_MIN_H)
    }

    /** The closing rule at the foot of the sheet. */
    private fun drawFooter(canvas: Canvas, width: Float, top: Float) {
        val y = minOf(top, PAGE_HEIGHT - 22f)
        canvas.drawRect(MARGIN, y, width - MARGIN, y + 5f, fillPaint(BRAND_DARK))
    }

    // ---- text helpers --------------------------------------------------------

    /** Shortens a product name to the first word, for the cramped chart labels. */
    private fun String.shortLabel(): String = trim().split(" ").firstOrNull().orEmpty()

    /** Renders basis points as a percentage, e.g. 1200 -> "12%", 575 -> "5.75%". */
    internal fun formatRate(basisPoints: Int): String {
        if (basisPoints <= 0) return "0%"
        val whole = basisPoints / 100
        val fraction = basisPoints % 100
        return when {
            fraction == 0 -> "$whole%"
            fraction % 10 == 0 -> "$whole.${fraction / 10}%"
            else -> "$whole.${fraction.toString().padStart(2, '0')}%"
        }
    }

    private fun textWidth(text: String, paint: Paint): Float = paint.measureText(text)

    private fun drawCentred(
        canvas: Canvas,
        text: String,
        left: Float,
        right: Float,
        baseline: Float,
        paint: Paint,
    ) {
        canvas.drawText(text, (left + right) / 2f - paint.measureText(text) / 2f, baseline, paint)
    }

    /**
     * Draws [text] ending at [rightX], shrinking it if it would otherwise overflow left.
     *
     * The table columns are fixed fractions of the page width, and money cells now carry a
     * currency symbol — "CHF 1,234.00" is materially wider than the "1,234.00" these columns were
     * sized for. A right-aligned value that does not fit runs backwards into the column beside it,
     * which is worse than being small, so the paint is scaled down to fit rather than allowed to
     * collide.
     */
    private fun drawRight(
        canvas: Canvas,
        text: String,
        rightX: Float,
        baseline: Float,
        paint: Paint,
        /** Left edge of the column; text is squeezed only if it would cross it. */
        leftLimit: Float = Float.NEGATIVE_INFINITY,
    ) {
        val available = rightX - leftLimit
        val width = paint.measureText(text)
        if (width > available && available > 0f) {
            val original = paint.textSize
            paint.textSize = original * (available / width)
            canvas.drawText(text, rightX - paint.measureText(text), baseline, paint)
            paint.textSize = original
        } else {
            canvas.drawText(text, rightX - width, baseline, paint)
        }
    }

    // ---- Paints --------------------------------------------------------------

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create(
            Typeface.DEFAULT,
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun strokePaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
    }
}
