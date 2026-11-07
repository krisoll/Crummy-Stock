package com.example.stockapplication.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.CartLine
import com.example.stockapplication.data.Category
import com.example.stockapplication.data.DiscountType
import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.PromoKind
import com.example.stockapplication.data.PromoMatch
import com.example.stockapplication.data.SaleAnimation
import com.example.stockapplication.data.SaleOrder
import com.example.stockapplication.data.formatCents
import com.example.stockapplication.ui.components.SaleLottieAnimation
import com.example.stockapplication.ui.components.SaleThumbsUp
import com.example.stockapplication.ui.components.ScanResult
import com.example.stockapplication.ui.components.ScanResultBanner
import com.example.stockapplication.ui.components.ScannerDialog
import com.example.stockapplication.ui.theme.AppMotion
import com.example.stockapplication.ui.viewmodel.CartUiState

/** Formats a basis-point rate as a short percentage, e.g. 275 -> "2.75". */
private fun formatRate(basisPoints: Int): String {
    val whole = basisPoints / 100
    val fraction = basisPoints % 100
    return if (fraction == 0) whole.toString() else "$whole.${fraction.toString().padStart(2, '0')}"
}

/**
 * Labels the card-fee row from whichever parts of the fee are actually in play, so the customer
 * sees "Card fee (2.75%)", "Card fee (flat)" or "Card fee (2.75% + 0.50)" rather than a
 * percentage that hides the flat charge.
 */
@Composable
private fun cardFeeLabel(rateBasisPoints: Int, fixedCents: Long): String = when {
    fixedCents <= 0L ->
        stringResource(R.string.cart_card_fee_label, formatRate(rateBasisPoints))
    rateBasisPoints <= 0 ->
        stringResource(R.string.cart_card_fee_fixed_only_label)
    else ->
        stringResource(
            R.string.cart_card_fee_mixed_label,
            formatRate(rateBasisPoints),
            formatCents(fixedCents)
        )
}

/**
 * The checkout tab: a scanner that feeds a cart, the cart itself, the payment method and the
 * final totals.
 *
 * The scanner sits at the top because scanning is the primary action here; burying it behind a
 * button would add a tap to every item.
 */
@Composable
fun SalesCartScreen(
    state: CartUiState,
    products: List<Product>,
    categories: List<Category>,
    onCodeScanned: (String) -> Unit,
    onAddProduct: (Product) -> Unit,
    onIncrement: (Long) -> Unit,
    onDecrement: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onClearCart: () -> Unit,
    onPaymentMethodChange: (PaymentMethod) -> Unit,
    onNoteChange: (String) -> Unit,
    onCheckout: () -> Unit,
    onDismissReceipt: () -> Unit,
    modifier: Modifier = Modifier,
    /** Drives the scanner's beep, popup and debounce window. */
    settings: AppSettings = AppSettings(),
    /** Clears the scanner banner once the dialog closes. */
    onDismissScanResult: () -> Unit = {},
    /** Takes an applying promo off this sale, or puts it back. */
    onTogglePromoSuppressed: (Long) -> Unit = {},
) {
    var showScanner by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showProductPicker by remember { mutableStateOf(false) }

    // Kept after the banner's own state goes null, so its slide-out animation has something to
    // draw instead of popping an empty card.
    var rememberedScan by remember { mutableStateOf<ScanResult?>(null) }
    LaunchedEffect(state.lastScan) {
        state.lastScan?.let { rememberedScan = it }
    }

    // The picker covers the whole screen (as an overlay), so the cart and its totals stay mounted
    // underneath and keep updating while items are added.
    Box(modifier = modifier.fillMaxSize()) {
    // Edge-to-edge: the Scan / Add buttons are the topmost row, so they carry the status-bar
    // inset themselves.
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // ---- Add items -------------------------------------------------------
        // The camera used to sit permanently at the top of this screen, holding its own preview
        // surface and a wake-lock while the tab was visible. It now opens on demand so the cart
        // gets the whole screen and the camera is only active while actually scanning.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showScanner = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "  " + stringResource(R.string.cart_scan_button),
                )
            }
            OutlinedButton(
                onClick = { showProductPicker = true },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.cart_add_from_inventory)) }
        }

        // ---- Cart ------------------------------------------------------------
        // The empty state and the list cross-fade so scanning the first item of a sale reads as
        // "the cart filled up" rather than "the screen was replaced".
        val cartEmpty = state.cart.isEmpty
        val cartEnter = AppMotion.enterFade()
        val cartExit = AppMotion.exitFade()
        AnimatedContent(
            targetState = cartEmpty,
            transitionSpec = { fadeIn(animationSpec = cartEnter) togetherWith fadeOut(animationSpec = cartExit) },
            label = "cart-empty",
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { isEmpty ->
            if (isEmpty) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.cart_empty_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.cart_empty_body),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                // `animateItem` slides the remaining rows up when a line is removed, which is the
                // one place a cart most needs to feel responsive.
                //
                // The list is also faded out towards its bottom edge while there is more to see,
                // so a cart with more lines than fit reads as "keep scrolling" even with the
                // totals panel taking up the lower half of the screen. The fade scales with how
                // much is actually hidden: an indented last row is barely clipped and fades
                // almost not at all, one cut in half fades properly.
                val listState = rememberLazyListState()
                val overflow = rememberOverflowFraction(listState)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .overflowFade(overflow),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.cart.lines, key = { it.productId }) { line ->
                        CartLineRow(
                            line = line,
                            onIncrement = { onIncrement(line.productId) },
                            onDecrement = { onDecrement(line.productId) },
                            onRemove = { onRemove(line.productId) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        // ---- Totals + payment + checkout -------------------------------------
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Free-text memo for this sale, shown on the history row and in the CSV export.
                // Sits above the payment chips because it describes the sale, not the payment.
                OutlinedTextField(
                    value = state.note,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cart_note_label)) },
                    placeholder = { Text(stringResource(R.string.cart_note_placeholder)) },
                    singleLine = false,
                    maxLines = 2,
                    trailingIcon = {
                        if (state.note.isNotEmpty()) {
                            IconButton(onClick = { onNoteChange("") }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.cart_note_clear)
                                )
                            }
                        }
                    }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.paymentMethod == PaymentMethod.CASH,
                        onClick = { onPaymentMethodChange(PaymentMethod.CASH) },
                        label = { Text(stringResource(R.string.payment_cash)) }
                    )
                    FilterChip(
                        selected = state.paymentMethod == PaymentMethod.CARD,
                        onClick = { onPaymentMethodChange(PaymentMethod.CARD) },
                        label = { Text(stringResource(R.string.payment_card)) }
                    )
                }

                TotalRow(
                    stringResource(R.string.cart_items_subtotal),
                    formatCents(state.totals.itemsSubtotalCents)
                )

                // ---- Promo -------------------------------------------------
                // Above the tax line because that is the order the maths happens in now: the
                // discount reduces the taxable amount, so showing it lower down would make the
                // tax row appear to be calculated from the wrong number.
                // One row per applying promo. Promos stack, so listing each with its own amount
                // shows the cashier *why* the total came down as far as it did, rather than a
                // single unexplained figure.
                state.promoMatches.forEach { match ->
                    // A promo that fired more than once says so: "2 for 1 (\u00d72)" explains
                    // both why the saving is larger than one deal's worth and that the extra
                    // items were not ignored.
                    val name = if (match.groupCount > 1) {
                        stringResource(
                            R.string.cart_promo_repeated,
                            match.promo.name,
                            match.groupCount
                        )
                    } else {
                        match.promo.name
                    }
                    PromoRow(
                        label = stringResource(R.string.cart_promo_label) + ": " + name,
                        amount = "-" + formatCents(match.discountCents),
                        // A promo is awarded by the engine, not chosen, so "remove" can only mean
                        // "not this one on this sale". The button is small and to the side so it
                        // is reachable without competing with the amount for attention.
                        onRemove = { onTogglePromoSuppressed(match.promo.id) },
                    )
                }

                // Putting one back. Only shown when something has been taken off, so the common
                // case (no removals) costs no height in a panel that already shares the screen
                // with the cart list.
                state.suppressedPromos.forEach { promo ->
                    PromoRow(
                        label = stringResource(R.string.cart_promo_removed, promo.name),
                        amount = null,
                        muted = true,
                        onRestore = { onTogglePromoSuppressed(promo.id) },
                    )
                }
                // Shown only when nothing applies, so it never competes with an earned discount.
                state.nextPromo?.let { hint ->
                    // Text, so it follows the font colour; the promo itself is still marked out by
                // the discount row above, which uses the highlight channel.
                Text(
                    promoHintText(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TotalRow(
                    stringResource(R.string.cart_tax_label, formatRate(state.taxRateBasisPoints)),
                    "-" + formatCents(state.totals.taxCents)
                )
                if (state.paymentMethod == PaymentMethod.CARD) {
                    TotalRow(
                        cardFeeLabel(
                            rateBasisPoints = state.cardFeeRateBasisPoints,
                            fixedCents = state.cardFeeFixedCents
                        ),
                        "-" + formatCents(state.totals.cardFeeCents)
                    )
                }
                // ---- Amount to charge -------------------------------------
                // The goods minus any promo, and nothing else. Tax and the card fee are the shop's
                // own costs, taken out of what is charged rather than added to it — so this stays
                // the largest figure, and the net below shows what is left once they come out.
                // The two differ by exactly `tax + cardFee`.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.cart_amount_due),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatCents(state.totals.amountDueCents),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.cart_net_total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatCents(state.totals.totalCents),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Clearing the cart is an escape hatch, not a routine action, so it shares a row
                // with the checkout button instead of owning one: every row spent up here comes
                // straight off the height of the cart list. An icon keeps it narrow when idle and
                // expands to the full label while disabled (empty cart), where its "why is this
                // greyed out" reason is worth the pixels.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        enabled = !state.cart.isEmpty,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(ButtonDefaults.MinHeight)
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        if (state.cart.isEmpty) {
                            Text(
                                "  " + stringResource(R.string.cart_clear),
                                maxLines = 1
                            )
                        }
                    }
                    Button(
                        onClick = onCheckout,
                        enabled = state.canCheckout,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (state.isCheckingOut) {
                                stringResource(R.string.cart_checking_out)
                            } else {
                                stringResource(R.string.cart_checkout)
                            },
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    if (showScanner) {
        // The dialog keeps itself open so several items can be scanned in a row; each accepted
        // code is debounced, beeped and reported from inside it.
        ScannerDialog(
            onCodeScanned = onCodeScanned,
            onDismiss = {
                showScanner = false
                onDismissScanResult()
            },
            settings = settings,
            // `code` is only used as the trigger: the product behind it is resolved
            // asynchronously and arrives through `state.lastScan`.
            banner = {
                ScanResultBanner(result = state.lastScan, remembered = rememberedScan)
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.cart_clear_confirm_title)) },
            text = { Text(stringResource(R.string.cart_clear_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearCart()
                }) { Text(stringResource(R.string.cart_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showProductPicker) {
        ProductPickerSheet(
            visible = true,
            products = products,
            categories = categories,
            cartQuantities = state.cart.lines.associateBy({ it.productId }, { it.quantity }),
            onPick = onAddProduct,
            onDismiss = { showProductPicker = false }
        )
    }

    state.lastOrder?.let { order ->
        ReceiptDialog(
            order = order,
            // Which flourish plays and how big, both chosen in Personalize. Read from the same
            // settings the picker uses, so the receipt never disagrees with the preview.
            animation = settings.saleAnimation,
            animationSize = settings.saleAnimationSizeDp,
            // Null unless the user imported a file *and* selected it; `usesLottieSaleAnimation`
            // also covers the stale-id case, so a deleted file falls back to the glyph.
            lottieFileName = settings.saleLottieFileName
                .takeIf { settings.usesLottieSaleAnimation },
            onDismiss = onDismissReceipt
        )
    }
    }
}

/**
 * How much of the list's bottom row is hidden, as a 0..1 fraction of that row's height.
 *
 * Measured from the last row's size and offset rather than from `canScrollForward`, because the
 * fade wants a magnitude, not a boolean. Derived so the cart only recomposes on scroll when the
 * answer actually changes, not on every pixel of it.
 */
@Composable
private fun rememberOverflowFraction(state: LazyListState): Float = remember(state) {
    derivedStateOf {
        val info = state.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()
        // Nothing laid out yet (or a single row that fits) means nothing is hidden below.
        if (last == null || last.size <= 0 || info.totalItemsCount == 0) {
            0f
        } else {
            val hidden = (last.offset + last.size) - info.viewportEndOffset
            (hidden.toFloat() / last.size).coerceIn(0f, 1f)
        }
    }
}.value

/**
 * Fades the bottom [fraction] of the content out towards the background, so a list that continues
 * past the fold looks like it continues rather than like it was sliced off.
 *
 * A graphics layer rather than an overlay scrim: it washes out only the content, never the colours
 * behind it, so it keeps working whatever surface the list happens to sit on.
 */
private fun Modifier.overflowFade(fraction: Float): Modifier {
    // Fade over a fixed band instead of a fraction of the content, so a short clipped row and a
    // tall one are softened over the same physical distance.
    val fade = 48.dp
    val alpha = (fraction * 2f).coerceIn(0f, 1f)
    return this
        // DstOut composites against this layer alone, so only the list is cut away and whatever
        // the list sits on (the screen background) shows through untouched.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (alpha <= 0f) return@drawWithContent
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                    startY = size.height - fade.toPx(),
                    endY = size.height
                ),
                alpha = alpha,
                blendMode = BlendMode.DstOut
            )
        }
}

/**
 * A promo line in the totals panel.
 *
 * One composable covers both directions — an applying promo with a remove button, and a removed
 * one with a restore — because they are the same row in two states. Splitting them would let the
 * two drift apart in height, and this panel shares the screen with the cart list.
 *
 * @param onRemove called when the promo is currently applied and the cashier takes it off.
 * @param onRestore called when the promo has been removed and the cashier puts it back.
 */
@Composable
private fun PromoRow(
    label: String,
    amount: String?,
    onRemove: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    muted: Boolean = false,
) {
    // An applied promo is an accent; a removed one is struck-through grey, so the panel says
    // "this qualified and is off" rather than silently not mentioning it.
    val colour = if (muted) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = colour,
            fontWeight = if (muted) null else FontWeight.SemiBold,
            textDecoration = if (muted) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (amount != null) {
            Text(
                amount,
                style = MaterialTheme.typography.bodyMedium,
                color = colour,
                fontWeight = FontWeight.SemiBold
            )
        }
        // A small icon button to the side, not a text button: it must be reachable without
        // stealing width from the promo's name, which is the part that explains the discount.
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cart_promo_remove, label),
                    modifier = Modifier.size(16.dp),
                    tint = colour
                )
            }
        }
        if (onRestore != null) {
            IconButton(onClick = onRestore, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.cart_promo_restore, label),
                    modifier = Modifier.size(16.dp),
                    tint = colour
                )
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String, highlight: Boolean = false) {
    val colour = if (highlight) MaterialTheme.colorScheme.primary else Color.Unspecified
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = colour,
            fontWeight = if (highlight) FontWeight.SemiBold else null,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Crossfading the value means adding an item animates the total rather than replacing the
        // number mid-glance.
        Crossfade(
            targetState = value,
            animationSpec = tween(AppMotion.screenEnterMillis()),
            label = "total"
        ) { current ->
            Text(
                current,
                style = MaterialTheme.typography.bodyMedium,
                color = colour,
                fontWeight = if (highlight) FontWeight.SemiBold else null
            )
        }
    }
}

/**
 * The "add one more for the discount" line.
 *
 * Names the promo where it has a category to point at, and falls back to a neutral phrase for a
 * product promo — the cart has no single category to name there.
 */
@Composable
private fun promoHintText(hint: PromoMatch): String {
    val needed = (hint.promo.thresholdQuantity - hint.qualifyingQuantity).coerceAtLeast(1)
    val saving = when (hint.promo.discountType) {
        DiscountType.PERCENT -> stringResource(
            R.string.promo_summary_percent,
            formatPercent(hint.promo.discountPercentBasisPoints)
        )

        DiscountType.FIXED -> stringResource(
            R.string.promo_summary_fixed,
            formatCents(hint.promo.discountFixedCents)
        )
    }
    return when (hint.promo.kind) {
        PromoKind.CATEGORY -> stringResource(
            R.string.cart_promo_hint,
            needed,
            hint.promo.name,
            saving
        )

        PromoKind.PRODUCTS -> stringResource(R.string.cart_promo_hint_products, needed, saving)
    }
}

@Composable
private fun CartLineRow(
    line: CartLine,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    line.productName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(
                        R.string.cart_line_price,
                        formatCents(line.unitPriceCents),
                        line.quantity
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onDecrement) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = stringResource(R.string.cart_decrease)
                )
            }
            Text(line.quantity.toString(), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onIncrement, enabled = line.quantity < line.availableStock) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.cart_increase)
                )
            }
            Text(
                formatCents(line.lineTotalCents),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cart_remove_line)
                )
            }
        }
    }
}

@Composable
private fun ReceiptDialog(
    order: SaleOrder,
    animation: SaleAnimation,
    animationSize: Int,
    /** The imported Lottie file to play, or null for the built-in flourish. */
    lottieFileName: String?,
    onDismiss: () -> Unit,
) {
    // Resolved here rather than inside `semantics {}`: that lambda is not @Composable, so it has
    // to be handed a plain string.
    val receiptTitle = stringResource(R.string.cart_receipt_title)

    // Full screen rather than a centred [AlertDialog]. An alert draws a surface *card*, and the
    // request here was specifically no card: the app's own UI should disappear behind a dark wash
    // so the flourish and the amount are the only things on screen.
    //
    // `Dialog` (not `DialogProperties(usePlatformDefaultWidth = false)` on a small window) is what
    // lets the scrim cover the status and navigation bars too, which is the point of "fades the
    // background to black".
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            // The scrim is ours, so the platform's own dim behind the window would double up.
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // The wash itself. Tapping anywhere dismisses, matching the old dialog's outside-tap.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(receiptScrim)
                .clickable(
                    // No ripple or indication: this is a dismissal surface, not a button, and a
                    // ripple spreading across the whole screen would read as a bug.
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    // Stops a tap on the receipt from reaching the scrim behind it and dismissing
                    // the thing the user is trying to read.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (lottieFileName != null) {
                    SaleLottieAnimation(
                        fileName = lottieFileName,
                        sizeDp = animationSize,
                        // Falls back to the built-in glyph if the file has gone, so a bad import
                        // can never leave the receipt blank.
                        fallbackAnimation = animation,
                        modifier = Modifier.semantics { contentDescription = receiptTitle }
                    )
                } else {
                    SaleThumbsUp(
                        animation = animation,
                        thumbSize = animationSize.dp,
                        modifier = Modifier.semantics { contentDescription = receiptTitle }
                    )
                }

                // White rather than a theme colour: the scrim is black and fixed, so the text has
                // to be too. `onSurface` would follow the user's palette and could end up dark on
                // dark.
                Text(
                    text = stringResource(
                        R.string.cart_receipt_total,
                        formatCents(order.totalCents)
                    ),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(
                        if (order.paymentMethod == PaymentMethod.CARD) {
                            R.string.payment_card
                        } else {
                            R.string.payment_cash
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
                if (order.note.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.sales_note_label) + ": " + order.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.cart_receipt_done),
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * The wash behind the receipt: black at 65% opacity.
 *
 * High enough that the screen underneath is clearly *behind* the receipt rather than competing
 * with it, low enough that the cashier keeps their sense of place — a completely opaque screen
 * looks like a different page, and loses the context of the cart they are about to return to.
 */
private val receiptScrim = Color.Black.copy(alpha = 0.65f)
