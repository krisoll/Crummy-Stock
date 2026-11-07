package com.example.stockapplication.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.DayOrders
import com.example.stockapplication.data.OrderWithLines
import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.Sale
import com.example.stockapplication.data.SaleOrder
import com.example.stockapplication.data.formatCents
import com.example.stockapplication.ui.components.ProductThumbnail
import com.example.stockapplication.ui.components.rememberDayLabel
import com.example.stockapplication.ui.components.rememberRevealModifier
import com.example.stockapplication.ui.theme.AppMotion
import java.text.DateFormat
import java.util.Date

/** Formats a basis-point rate as a short percentage, e.g. 275 -> "2.75". */
private fun formatRate(basisPoints: Int): String {
    val whole = basisPoints / 100
    val fraction = basisPoints % 100
    return if (fraction == 0) whole.toString() else "$whole.${fraction.toString().padStart(2, '0')}"
}

/** Same contract as the cart's label: show whichever parts of the card fee applied. */
@Composable
private fun cardFeeLabel(order: SaleOrder): String = when {
    order.cardFeeFixedCents <= 0L ->
        stringResource(R.string.cart_card_fee_label, formatRate(order.cardFeeRateBasisPoints))
    order.cardFeeRateBasisPoints <= 0 ->
        stringResource(R.string.cart_card_fee_fixed_only_label)
    else ->
        stringResource(
            R.string.cart_card_fee_mixed_label,
            formatRate(order.cardFeeRateBasisPoints),
            formatCents(order.cardFeeFixedCents)
        )
}

/**
 * Sales history, listed **by completed sale** rather than by line item.
 *
 * Each row is one checkout; tapping it expands the receipt: every item, the payment method, the
 * tax and card fee that were deducted, and the net total the shop kept.
 *
 * @param products current inventory, used to look up each line item's photo by `productId`. Lines
 *                 whose product was deleted simply fall back to the placeholder icon.
 */
@Composable
fun SalesScreen(
    ordersByDay: List<DayOrders>,
    products: List<Product> = emptyList(),
    onCancelOrder: (SaleOrder) -> Unit,
    onExportCsv: () -> Unit,
    onExportDayCsv: (DayOrders) -> Unit,
    onOpenReport: (DayOrders) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Snapshot of productId -> photo name, rebuilt only when the catalogue changes.
    val imageByProductId = remember(products) {
        products.associateBy({ it.id }, { it.imagePath })
    }
    val allOrders = ordersByDay.flatMap { it.orders }
    val netTotal = allOrders.sumOf { it.order.totalCents }
    val unitCount = allOrders.sumOf { it.order.itemCount }

    // Newest day expands by default; older days start collapsed to keep long histories readable.
    var collapsedDays by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var orderPendingCancel by remember { mutableStateOf<OrderWithLines?>(null) }

    // Edge-to-edge: the summary card is the topmost element, so it carries the status-bar
    // inset rather than a scaffold above it.
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        SummaryHeader(
            netTotal = netTotal,
            unitCount = unitCount,
            orderCount = allOrders.size,
            dayCount = ordersByDay.size,
            onExportCsv = onExportCsv,
            exportEnabled = allOrders.isNotEmpty(),
        )

        HorizontalDivider()

        // The first order of the day swaps the empty state for the list; a cross-fade keeps that
        // from looking like a redraw.
        val historyEnter = AppMotion.enterFade()
        val historyExit = AppMotion.exitFade()
        val staggerMillis = AppMotion.stagger()
        AnimatedContent(
            targetState = allOrders.isEmpty(),
            transitionSpec = {
                fadeIn(animationSpec = historyEnter) togetherWith fadeOut(animationSpec = historyExit)
            },
            label = "history"
        ) { empty ->
        if (empty) {
            EmptySalesState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(
                    items = ordersByDay,
                    key = { _, day -> day.dayStartMillis }
                ) { index, day ->
                    val expanded = day.dayStartMillis !in collapsedDays
                    DaySection(
                        day = day,
                        expanded = expanded,
                        imageByProductId = imageByProductId,
                        onExportDayCsv = { onExportDayCsv(day) },
                        onOpenReport = { onOpenReport(day) },
                        onToggleExpand = {
                            collapsedDays = if (expanded) {
                                collapsedDays + day.dayStartMillis
                            } else {
                                collapsedDays - day.dayStartMillis
                            }
                        },
                        onRequestCancel = { orderPendingCancel = it },
                        // Days cascade in so a long history does not arrive as one solid block.
                        modifier = rememberRevealModifier(index, staggerMillis)
                    )
                }
            }
        }
        }
    }

    orderPendingCancel?.let { pending ->
        CancelOrderDialog(
            entry = pending,
            onConfirm = {
                orderPendingCancel = null
                onCancelOrder(pending.order)
            },
            onDismiss = { orderPendingCancel = null }
        )
    }
}

@Composable
private fun SummaryHeader(
    netTotal: Long,
    unitCount: Int,
    orderCount: Int,
    dayCount: Int,
    onExportCsv: () -> Unit,
    exportEnabled: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    stringResource(R.string.sales_total),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    formatCents(netTotal),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    pluralStringResource(R.plurals.sales_units, unitCount, unitCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    pluralStringResource(R.plurals.sales_transactions, orderCount, orderCount),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    pluralStringResource(R.plurals.sales_active_days, dayCount, dayCount),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    // Sits below the summary so it is reachable without scrolling a long history.
    TextButton(
        onClick = onExportCsv,
        enabled = exportEnabled,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Icon(Icons.Default.FileDownload, contentDescription = null)
        Text("  " + stringResource(R.string.sales_export_all))
    }
}

@Composable
private fun EmptySalesState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ReceiptLong,
                null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                stringResource(R.string.sales_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.sales_empty_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun DaySection(
    day: DayOrders,
    expanded: Boolean,
    imageByProductId: Map<Long, String?>,
    onExportDayCsv: () -> Unit,
    onOpenReport: () -> Unit,
    onToggleExpand: () -> Unit,
    onRequestCancel: (OrderWithLines) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        rememberDayLabel(day.dayStartMillis),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.sales_transactions,
                            day.orderCount,
                            day.orderCount
                        ) + " · " +
                            pluralStringResource(R.plurals.sales_units, day.unitCount, day.unitCount),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    formatCents(day.netCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onExportDayCsv) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = stringResource(R.string.sales_export_day_csv)
                    )
                }
                IconButton(onClick = onOpenReport) {
                    Icon(
                        Icons.Default.Assessment,
                        contentDescription = stringResource(R.string.sales_report)
                    )
                }
                IconButton(onClick = onToggleExpand) {
                    // One icon rotating, rather than two icons swapping: the control reads as a
                    // single affordance whose state changed.
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.sales_collapse else R.string.sales_expand
                        ),
                        modifier = Modifier.graphicsLayer {
                            rotationZ = if (expanded) 180f else 0f
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = AppMotion.expand()) +
                    fadeIn(animationSpec = AppMotion.enterFade()),
                exit = shrinkVertically(animationSpec = AppMotion.expand()) +
                    fadeOut(animationSpec = AppMotion.exitFade()),
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    day.orders.forEach { entry ->
                        OrderRow(
                            entry = entry,
                            imageByProductId = imageByProductId,
                            onRequestCancel = { onRequestCancel(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderRow(
    entry: OrderWithLines,
    imageByProductId: Map<Long, String?>,
    onRequestCancel: () -> Unit,
) {
    var expanded by rememberSaveable(entry.order.orderId) { mutableStateOf(false) }
    val order = entry.order

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = { expanded = !expanded }
    ) {
        Column(Modifier.padding(12.dp)) {
            // ---- Collapsed summary -------------------------------------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (order.paymentMethod == PaymentMethod.CARD) {
                        Icons.Default.Payments
                    } else {
                        Icons.Default.Storefront
                    },
                    contentDescription = stringResource(
                        if (order.paymentMethod == PaymentMethod.CARD) {
                            R.string.payment_card
                        } else {
                            R.string.payment_cash
                        }
                    ),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(order.soldAt)),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.sales_units,
                            order.itemCount,
                            order.itemCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // The note itself is long-form, so the collapsed row only hints at it; the
                    // full text appears once the receipt is expanded.
                    if (order.note.isNotEmpty()) {
                        // A note is text, not an accent: it follows the font colour like every other
                            // label. The highlight channel belongs to chips and indicators.
                            Text(
                            order.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    formatCents(order.totalCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            // ---- Expanded receipt --------------------------------------------
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    entry.lines.forEach { line ->
                        ReceiptLineRow(
                            line = line,
                            imageFileName = imageByProductId[line.productId]
                        )
                    }
                    if (order.note.isNotEmpty()) {
                        // Above the money rows so it reads as context for the whole sale.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                stringResource(R.string.sales_note_label) + ":",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                order.note,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    ReceiptRow(
                        label = stringResource(R.string.cart_items_subtotal),
                        value = formatCents(order.itemsSubtotalCents)
                    )
                    ReceiptRow(
                        label = stringResource(
                            R.string.cart_tax_label,
                            formatRate(order.taxRateBasisPoints)
                        ),
                        value = "-" + formatCents(order.taxCents)
                    )
                    if (order.cardFeeCents > 0) {
                        ReceiptRow(
                            label = cardFeeLabel(order),
                            value = "-" + formatCents(order.cardFeeCents)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.cart_net_total),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            formatCents(order.totalCents),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = onRequestCancel) {
                        Text(stringResource(R.string.sales_cancel_confirm))
                    }
                }
            }
        }
    }
}

/**
 * One purchased item in the expanded receipt: photo, "qty × name" and its line total.
 *
 * The photo comes from the current catalogue rather than the sale row, so a product renamed or
 * re-photographed since the sale shows its current picture. A deleted product has no entry and
 * falls back to the placeholder icon.
 */
@Composable
private fun ReceiptLineRow(line: Sale, imageFileName: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProductThumbnail(
            fileName = imageFileName,
            size = 40.dp,
            cornerRadius = 8.dp
        )
        Text(
            "${line.quantity} × ${line.productName}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(formatCents(line.totalCents), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CancelOrderDialog(
    entry: OrderWithLines,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sales_cancel_dialog_title)) },
        text = {
            Text(
                stringResource(
                    R.string.sales_cancel_order_body,
                    entry.order.itemCount,
                    formatCents(entry.order.totalCents)
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.sales_cancel_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sales_cancel_keep))
            }
        }
    )
}
