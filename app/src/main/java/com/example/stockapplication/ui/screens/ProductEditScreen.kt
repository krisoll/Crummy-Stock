package com.example.stockapplication.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.Category
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.Tag
import com.example.stockapplication.ui.components.CategoryPicker
import com.example.stockapplication.ui.components.TagChipPicker
import com.example.stockapplication.ui.components.ProductImagePicker
import com.example.stockapplication.ui.components.ScannerDialog
import com.example.stockapplication.data.formatCents
import com.example.stockapplication.data.currencySymbol

/**
 * Add / edit form. When [existing] is null the screen creates a new product with an optional
 * prefilled [initialBarcode] (for example a code that was just scanned).
 *
 * Both save paths report a [ProductDraft] rather than a long parameter list. A newly picked photo
 * is handed over as a `Uri` inside the draft and copied into app storage by the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    existing: Product?,
    initialBarcode: String,
    categories: List<Category>,
    onCreateCategory: (String) -> Unit,
    /** Every tag, for the chip picker. Empty whenever tagging is switched off. */
    tags: List<Tag> = emptyList(),
    /** The tags this product already carries. */
    selectedTagIds: Set<Long> = emptySet(),
    /** Whether the optional tagging feature is on; hides the whole tag block when false. */
    tagsEnabled: Boolean = false,
    onExportQrClick: (name: String, barcode: String) -> Unit,
    onSave: (ProductDraft) -> Unit,
    onUpdate: (product: Product, draft: ProductDraft) -> Unit,
    onDelete: (Product) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Drives the scanner's beep and debounce window. */
    settings: AppSettings = AppSettings(),
) {
    val isEdit = existing != null
    var barcode by rememberSaveable(existing?.id, initialBarcode) {
        mutableStateOf(existing?.barcode ?: initialBarcode)
    }
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    // Seed the *editable* buffer with a symbol-free decimal, not a formatted display string.
    // `formatCents` prepends the currency symbol ("$12.99"), and `toCentsOrNull` cannot parse
    // that — so the field started out invalid and every keystroke flagged an error until the user
    // manually deleted the symbol. The symbol is shown by `prefix` instead, which is not part of
    // the value and therefore cannot be edited or parsed.
    var priceText by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.let { formatPriceInput(it.priceCents) }.orEmpty())
    }
    var quantityText by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.quantity?.toString() ?: "1")
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    // The scanner is inline now that the dedicated Scan tab is gone; scanning here only fills
    // the barcode field, it doesn't touch the cart.
    var showScanner by remember { mutableStateOf(false) }
    // null = uncategorised, which is a valid and common choice.
    var categoryId by rememberSaveable(existing?.id) { mutableStateOf(existing?.categoryId) }
    // Keyed on the product so opening a different one starts from its own tags rather than the
    // previous product's selection. `Set<Long>` is saveable as-is, so a rotation mid-edit keeps it.
    var tagIds by rememberSaveable(existing?.id) { mutableStateOf(selectedTagIds) }
    // Photo state: an already-stored file name, plus a freshly picked (not yet copied) image.
    // The picked Uri deliberately isn't saveable — after process death the picker grant is gone
    // anyway, so falling back to "no new pick" is the only correct behaviour.
    var imagePath by rememberSaveable(existing?.id) { mutableStateOf(existing?.imagePath) }
    var pickedImage by remember(existing?.id) { mutableStateOf<Uri?>(null) }
    var imageCleared by rememberSaveable(existing?.id) { mutableStateOf(false) }

    val priceCents = priceText.toCentsOrNull()
    val quantity = quantityText.toIntOrNull()
    val isValid = barcode.isNotBlank() &&
        name.isNotBlank() &&
        (priceCents != null || priceText.isBlank()) &&
        quantity != null && quantity >= 0

    // Snapshot of the whole form for the save callbacks.
    fun currentDraft(): ProductDraft = ProductDraft(
        barcode = barcode,
        name = name,
        priceCents = priceCents ?: 0L,
        quantity = quantity ?: 0,
        categoryId = categoryId,
        imagePath = imagePath,
        pickedImage = pickedImage,
        imageCleared = imageCleared,
        tagIds = tagIds,
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isEdit) R.string.product_edit_title else R.string.product_new_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                label = { Text(stringResource(R.string.product_barcode_label)) },
                singleLine = true,
                enabled = !isEdit,
                modifier = Modifier.fillMaxWidth(),
                supportingText = if (isEdit) {
                    { Text(stringResource(R.string.product_barcode_locked)) }
                } else null,
                trailingIcon = if (!isEdit) {
                    {
                        IconButton(onClick = { showScanner = true }) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = stringResource(R.string.action_scan)
                            )
                        }
                    }
                } else null
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.product_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = name.isBlank()
            )

            ProductImagePicker(
                currentFileName = if (imageCleared) null else imagePath,
                pendingUri = pickedImage,
                onPick = { uri ->
                    pickedImage = uri
                    imageCleared = false
                },
                onRemove = {
                    pickedImage = null
                    imagePath = null
                    imageCleared = true
                }
            )

            CategoryPicker(
                categories = categories,
                selectedId = categoryId,
                onSelect = { categoryId = it },
                onCreate = onCreateCategory
            )

            // Directly under the category, because the two are the same kind of decision: how
            // this product is classified. Hidden entirely when the feature is off.
            if (tagsEnabled) {
                TagChipPicker(
                    tags = tags,
                    selectedIds = tagIds,
                    onToggle = { tagId ->
                        // Toggle rather than replace: this is a multi-select, and tapping a
                        // selected chip is the natural way to remove it.
                        tagIds = if (tagId in tagIds) tagIds - tagId else tagIds + tagId
                    }
                )
            }

            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text(stringResource(R.string.product_price_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                // Trimmed: `CHF` carries a trailing space so it reads correctly in front of an
                // amount, but as a field prefix the layout supplies the gap.
                prefix = { Text(currencySymbol.trim()) },
                supportingText = {
                    Text(
                        if (priceCents != null) {
                            stringResource(R.string.product_price_stored, formatCents(priceCents))
                        } else {
                            stringResource(R.string.product_price_hint)
                        }
                    )
                },
                isError = priceCents == null && priceText.isNotBlank()
            )

            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it.filter { c -> c.isDigit() } },
                label = {
                    Text(
                        stringResource(
                            if (isEdit) R.string.product_quantity_edit_label
                            else R.string.product_quantity_new_label
                        )
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = quantity == null || quantity < 0
            )

            OutlinedButton(
                onClick = { onExportQrClick(name, barcode) },
                enabled = barcode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Text(
                    stringResource(R.string.product_export_qr),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (barcode.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null)
                    Text(
                        stringResource(R.string.product_qr_help),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        val draft = currentDraft()
                        if (existing != null) {
                            onUpdate(
                                existing.copy(
                                    name = draft.name,
                                    priceCents = draft.priceCents,
                                    quantity = draft.quantity,
                                    categoryId = draft.categoryId
                                ),
                                draft
                            )
                        } else {
                            onSave(draft.copy(quantity = draft.quantity.coerceAtLeast(1)))
                        }
                    },
                    enabled = isValid,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(
                            if (isEdit) R.string.action_save else R.string.inventory_add_product
                        )
                    )
                }
            }

            Text(
                stringResource(R.string.product_scanner_help),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showScanner) {
        ScannerDialog(
            onCodeScanned = { code ->
                barcode = code
                showScanner = false
            },
            onDismiss = { showScanner = false },
            // The beep and the debounce window are shared with the cart scanner; the popup is
            // not shown here because the dialog closes on the first code and the field fills in.
            settings = settings.copy(showScanPopup = false),
        )
    }

    if (showDeleteDialog && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.product_delete_dialog_title)) },
            text = {
                Text(stringResource(R.string.product_delete_dialog_body, existing.name))
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(existing)
                    onBack()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * Renders cents as the text shown in the editable price field, e.g. 1299 -> "12.99".
 *
 * Deliberately **not** [formatCents]: that is a display string and includes the currency symbol
 * and the user's show-cents preference, neither of which belongs in a box the user types into. A
 * buffer seeded with "$12.99" cannot be parsed back, and with cents hidden it would seed "13" —
 * silently rounding the stored price the moment the user touched any other field.
 *
 * Always two decimal places, so the amount round-trips exactly however it is displayed elsewhere.
 */
internal fun formatPriceInput(cents: Long): String {
    val safe = cents.coerceAtLeast(0L)
    return "${safe / 100}.${(safe % 100).toString().padStart(2, '0')}"
}

/**
 * Like [formatPriceInput], but follows the app's "Show cents" setting.
 *
 * With cents off, `1250` becomes `"12"` rather than `"12.00"` — matching every other amount the
 * user sees, since [com.example.stockapplication.data.formatCents] drops the fraction in that mode
 * too. Leaving the field at `12.00` while the rest of the app said `12` was the one place the two
 * disagreed.
 *
 * **The stored value is never rounded.** This only shapes the text in an editable buffer; the
 * cents stay in the promo and are what gets applied. Rounding at entry would quietly turn a 12.50
 * discount into 12.00 the moment somebody toggled the setting.
 *
 * The trailing `.00` is only stripped when the fraction is exactly zero. A 12.50 amount keeps its
 * decimals even with cents off, because dropping them would misreport what the promo is worth —
 * the same rule `formatCents` follows.
 */
internal fun formatPriceInput(cents: Long, showDecimals: Boolean): String {
    val exact = formatPriceInput(cents)
    if (showDecimals) return exact
    val safe = cents.coerceAtLeast(0L)
    return if (safe % 100 == 0L) (safe / 100).toString() else exact
}

/** Parses "12.99", "12,99" or "12" into cents. Returns null when not a valid amount. */
internal fun String.toCentsOrNull(): Long? {
    val normalized = trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    if (normalized.count { it == '.' } > 1) return null
    val parts = normalized.split('.')
    val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
    val fraction = if (parts.size == 2) {
        parts[1].take(2).padEnd(2, '0').toLongOrNull() ?: return null
    } else 0L
    if (whole < 0 || fraction < 0) return null
    return whole * 100 + fraction
}
