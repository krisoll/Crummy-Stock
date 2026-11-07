package com.example.stockapplication.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.Category
import com.example.stockapplication.data.DiscountType
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.Promo
import com.example.stockapplication.data.MaxPromoDiscountCents
import com.example.stockapplication.data.PromoKind
import com.example.stockapplication.data.currencySymbol
import com.example.stockapplication.data.formatCents
import com.example.stockapplication.ui.components.SimpleDatePickerDialog
import com.example.stockapplication.ui.components.formatDateLabel

/**
 * The promo editor: the whole of a [Promo] in one scrollable form.
 *
 * Every control commits to local state and nothing is saved until **Save**, unlike the Personalize
 * page where some settings write straight through. That is deliberate: a half-edited promo that is
 * already live would start discounting real sales the moment the user typed a threshold.
 *
 * The save button stays disabled until the promo is *complete enough to run* — a name, a target and
 * a non-zero discount — so an untargeted promo cannot be created by accident.
 */
@Composable
fun PromoEditScreen(
    existing: Promo?,
    categories: List<Category>,
    products: List<Product>,
    onSave: (Promo) -> Unit,
    onDelete: (Promo) -> Unit,
    onBack: () -> Unit,
    /**
     * The app-wide "Show cents" setting, so the fixed amount is seeded the same way every other
     * money figure is displayed. Display-only: the stored cents are never rounded.
     */
    showMoneyDecimals: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isEdit = existing != null

    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var kind by rememberSaveable(existing?.id) { mutableStateOf(existing?.kind ?: PromoKind.CATEGORY) }
    var categoryId by rememberSaveable(existing?.id) { mutableStateOf(existing?.categoryId) }
    var selectedIds by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.productIdSet ?: emptySet())
    }
    var thresholdText by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.thresholdQuantity?.toString() ?: "1")
    }
    var discountType by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.discountType ?: DiscountType.PERCENT)
    }
    var percentText by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.let { formatPercent(it.discountPercentBasisPoints) }.orEmpty())
    }
    var fixedText by rememberSaveable(existing?.id) {
        // Symbol-free, so the buffer round-trips through `parseMoneyText`. Seeding it with
        // `formatCents` put "$10.00" in a box that also draws a `$` prefix, and then sanitising
        // left "1000" — which parsed as 1000.00 and clamped to the card-fee cap, making every
        // promo's fixed discount 1000.
        //
        // The `showMoneyDecimals` pass-through drops a `.00` when the user has cents switched off,
        // so this field reads like every other amount in the app. It is display only: what gets
        // stored is still whatever `parseMoneyText` reads back, and a 12.50 discount keeps its
        // half regardless of the setting.
        mutableStateOf(
            existing?.let { formatPriceInput(it.discountFixedCents, showMoneyDecimals) }.orEmpty()
        )
    }
    var enabled by rememberSaveable(existing?.id) { mutableStateOf(existing?.enabled ?: true) }
    var startsAt by rememberSaveable(existing?.id) { mutableStateOf(existing?.startsAt) }
    var endsAt by rememberSaveable(existing?.id) { mutableStateOf(existing?.endsAt) }

    var showProductPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val threshold = thresholdText.toIntOrNull()
    val percentBasisPoints = parsePercentText(percentText)
    val fixedCents = parseMoneyText(fixedText, maxCents = MaxPromoDiscountCents)

    // What the form is missing, in the order the user reads the form, so the message points at
    // the first thing to fix rather than at whatever check happened to be last.
    val problem: Int? = when {
        name.isBlank() -> R.string.promo_name_label
        kind == PromoKind.CATEGORY && categoryId == null -> R.string.promo_category_none
        kind == PromoKind.PRODUCTS && selectedIds.isEmpty() -> R.string.promo_products_none
        threshold == null || threshold < 1 -> R.string.promo_threshold_label
        discountType == DiscountType.PERCENT && (percentBasisPoints == null || percentBasisPoints <= 0) ->
            R.string.promo_discount_percent_label
        discountType == DiscountType.FIXED && (fixedCents == null || fixedCents <= 0L) ->
            R.string.promo_discount_fixed_label
        // An end that precedes its start is a promo that can never run; catching it here beats
        // letting the user wonder why nothing discounts.
        startsAt != null && endsAt != null && endsAt!! < startsAt!! -> R.string.promo_ends_label
        else -> null
    }

    // Opaque background, not just transparent-over-whatever-is-behind. This screen is drawn as an
    // overlay on top of the Inventory tabs, and without a background of its own those tabs show
    // straight through it — the bar lands right across this screen's title. The product editor
    // gets the same effect for free from its Scaffold, which paints a surface; this one is a bare
    // Column and has to paint its own.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize()) {
            // The app is edge-to-edge, so this overlay owns both insets: the title keeps clear of
            // the status bar, and the save bar below keeps clear of the navigation bar. Only the
            // *contents* are padded — the Surface behind them still paints edge to edge.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
                Text(
                    stringResource(
                        if (isEdit) R.string.promo_edit_title else R.string.promo_new
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ---- Name --------------------------------------------------
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.promo_name_label)) },
                    placeholder = { Text(stringResource(R.string.promo_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ---- What it applies to -----------------------------------
                EditorSection(stringResource(R.string.promo_kind)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PromoKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(stringResource(option.labelRes)) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }

                if (kind == PromoKind.CATEGORY) {
                    if (categories.isEmpty()) {
                        Text(
                            stringResource(R.string.category_new),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoryId = category.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = categoryId == category.id,
                                onClick = { categoryId = category.id }
                            )
                            Text(category.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showProductPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (selectedIds.isEmpty()) {
                                stringResource(R.string.promo_products_choose)
                            } else {
                                stringResource(R.string.promo_products_selected, selectedIds.size)
                            }
                        )
                    }
                    // Names the picked products so the user can confirm the set without reopening
                    // the picker.
                    val chosen = products.filter { it.id in selectedIds }
                    if (chosen.isNotEmpty()) {
                        Text(
                            chosen.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

                // ---- Threshold ---------------------------------------------
                EditorSection(
                title = stringResource(R.string.promo_threshold_label),
                description = stringResource(R.string.promo_threshold_description)
            ) {
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { typed ->
                        thresholdText = typed.filter { it.isDigit() }.take(4)
                    },
                    label = { Text(stringResource(R.string.promo_threshold_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

                // ---- Discount ----------------------------------------------
                EditorSection(stringResource(R.string.promo_discount)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiscountType.entries.forEach { option ->
                        FilterChip(
                            selected = discountType == option,
                            onClick = { discountType = option },
                            label = { Text(stringResource(option.labelRes)) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }

                when (discountType) {
                    DiscountType.PERCENT -> OutlinedTextField(
                        value = percentText,
                        onValueChange = { percentText = sanitizePercentInput(it) },
                        label = { Text(stringResource(R.string.promo_discount_percent_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    DiscountType.FIXED -> OutlinedTextField(
                        value = fixedText,
                        onValueChange = { fixedText = sanitizeMoneyInput(it) },
                        label = { Text(stringResource(R.string.promo_discount_fixed_label)) },
                        // The chosen currency, not a literal: this field is the one place a
                        // promo's amount is entered, so it has to match what the receipt will say.
                        prefix = { Text(currencySymbol.trim()) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

                // ---- Schedule ----------------------------------------------
                EditorSection(
                title = stringResource(R.string.promo_schedule),
                description = stringResource(R.string.promo_schedule_description)
            ) {
                DateRow(
                    label = stringResource(R.string.promo_starts_label),
                    value = startsAt?.let { formatDateLabel(it) }
                        ?: stringResource(R.string.promo_no_start),
                    onPick = { showStartPicker = true },
                    onClear = { startsAt = null },
                    clearable = startsAt != null
                )
                DateRow(
                    label = stringResource(R.string.promo_ends_label),
                    value = endsAt?.let { formatDateLabel(it) }
                        ?: stringResource(R.string.promo_no_end),
                    onPick = { showEndPicker = true },
                    onClear = { endsAt = null },
                    clearable = endsAt != null
                )
            }

                // ---- Enabled -----------------------------------------------
                Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.promo_enabled),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Text(
                stringResource(R.string.promo_badge_auto),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isEdit) {
                HorizontalDivider()
                TextButton(onClick = { showDeleteDialog = true }) {
                    Text(stringResource(R.string.promo_delete))
                }
            }
        }

        // ---- Save bar ------------------------------------------------------
        Surface(tonalElevation = 3.dp) {
            Column(
                // Padded *inside* the Surface so its tonal fill extends under the navigation
                // bar, and the button itself sits clear of it. Without this the bar covered the
                // bottom ~80% of the button.
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (problem != null) {
                    // Says which field is missing rather than just greying the button out, which
                    // leaves the user hunting for what is wrong.
                    Text(
                        stringResource(R.string.promo_incomplete, stringResource(problem)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        val base = existing ?: Promo(name = name)
                        onSave(
                            base.copy(
                                name = name,
                                kind = kind,
                                categoryId = if (kind == PromoKind.CATEGORY) categoryId else null,
                                productIdsCsv = if (kind == PromoKind.PRODUCTS) {
                                    selectedIds.joinToString(",")
                                } else "",
                                thresholdQuantity = threshold ?: 1,
                                discountType = discountType,
                                discountPercentBasisPoints =
                                    percentBasisPoints?.coerceIn(0, 10_000) ?: 0,
                                discountFixedCents = fixedCents ?: 0L,
                                enabled = enabled,
                                startsAt = startsAt,
                                endsAt = endsAt,
                            )
                        )
                    },
                    enabled = problem == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.promo_save))
                }
            }
        }
        }
    }

    // The dialogs sit outside the Surface: they are separate windows, and keeping them out here
    // also makes it obvious they are not part of the scrolling form.
    if (showProductPicker) {
        PromoProductPicker(
            products = products,
            selected = selectedIds,
            onSelectionChange = { selectedIds = it },
            onDismiss = { showProductPicker = false }
        )
    }

    if (showStartPicker) {
        SimpleDatePickerDialog(
            initial = startsAt,
            onPick = { startsAt = it; showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }

    if (showEndPicker) {
        SimpleDatePickerDialog(
            initial = endsAt,
            onPick = { endsAt = it; showEndPicker = false },
            onDismiss = { showEndPicker = false }
        )
    }

    if (showDeleteDialog && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.promo_delete_title)) },
            text = { Text(stringResource(R.string.promo_delete_body, existing.name)) },
            confirmButton = {
                // Deliberately *not* labelled with `colorScheme.error`.
                //
                // `error` is the one colour channel the theme never derives: `highlightedScheme`
                // overrides the four user channels and the `on*` ink slots, but leaves `error` at
                // Material's stock `#B3261E` on every palette. On a dark panel that red sits
                // almost exactly on the dialog's own `surfaceContainerHigh` fill, so the confirm
                // button read as "missing" rather than as unreadable.
                //
                // Filling it with the highlight colour instead solves both halves of that at
                // once: it is guaranteed to stand off a panel the user already chose (the theme
                // tunes the two to be legible together), and `onSecondaryContainer` gives it the
                // ink the whole highlight family uses. A plain TextButton inherited the panel's
                // own ink, which is why it vanished here while the Cancel next to it did not:
                // that one sits on the dialog surface and this one is the odd one out.
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(existing)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun DateRow(
    label: String,
    value: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
    clearable: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onPick) { Text(value) }
        if (clearable) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.promo_clear_date)) }
        }
    }
}

/** A titled block inside the form, matching the Personalize page's visual language. */
@Composable
private fun EditorSection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

/**
 * A searchable multi-select over the catalogue.
 *
 * A dialog rather than a full screen: picking products is a side quest from the promo being edited,
 * and the editor's own state has to stay mounted behind it.
 */
@Composable
private fun PromoProductPicker(
    products: List<Product>,
    selected: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(products, query) {
        if (query.isBlank()) {
            products
        } else {
            products.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.barcode.contains(query, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.promo_products_choose)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(stringResource(R.string.inventory_search_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(visible, key = { it.id }) { product ->
                        val checked = product.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelectionChange(
                                        if (checked) selected - product.id
                                        else selected + product.id
                                    )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    product.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    formatCents(product.priceCents),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (visible.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.cart_pick_no_matches),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.promo_products_done))
            }
        }
    )
}

/**
 * Keeps a percentage field to digits and at most one decimal separator with two decimals.
 *
 * Mirrors the rate field's sanitiser in the Config tab so the two numeric inputs in the app behave
 * identically, and so the field can only ever hold text [parsePercentText] understands.
 */
internal fun sanitizePercentInput(typed: String): String {
    val digitsAndDot = typed.replace(',', '.').filter { it.isDigit() || it == '.' }
    val firstDot = digitsAndDot.indexOf('.')
    return if (firstDot < 0) {
        digitsAndDot
    } else {
        digitsAndDot.substring(0, firstDot + 1) +
            digitsAndDot.substring(firstDot + 1).filter { it.isDigit() }.take(2)
    }
}

/**
 * Parses a percentage into basis points, clamped to 0..100%.
 *
 * Returns null for input that is not yet a number (blank, or a lone dot) so the form can show its
 * "what is missing" hint instead of silently treating an empty field as 0%.
 */
internal fun parsePercentText(text: String): Int? {
    if (text.isBlank() || text == ".") return null
    val percent = text.toDoubleOrNull() ?: return null
    if (percent < 0) return null
    return Math.round(percent * 100.0).toInt().coerceIn(0, 10_000)
}
