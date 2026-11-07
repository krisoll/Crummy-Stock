package com.example.stockapplication.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.Category
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.UNCATEGORISED_CATEGORY_ID
import com.example.stockapplication.data.filterProducts
import com.example.stockapplication.data.favouritesFirst
import com.example.stockapplication.data.formatCents
import com.example.stockapplication.ui.components.ProductThumbnail

/**
 * Full-height side sheet listing inventory, shown over the cart.
 *
 * A side sheet rather than a bottom sheet: the cart's totals block already owns the bottom of the
 * screen, and sliding in from the end leaves the cart visible so the shopkeeper can see the
 * running total while picking.
 *
 * Tapping a row adds it to the cart and **keeps the sheet open**, so several items can be added in
 * a row; the cart's existing snackbar confirms each add.
 *
 * @param cartQuantities productId -> quantity already in the cart, used for the "added" marker and
 *                       to disable rows whose stock is exhausted.
 */
@Composable
fun ProductPickerSheet(
    visible: Boolean,
    products: List<Product>,
    categories: List<Category>,
    onPick: (Product) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cartQuantities: Map<Long, Int> = emptyMap(),
) {
    // Intercept back while the sheet is open so it closes before the tab changes.
    BackHandler(enabled = visible, onBack = onDismiss)

    Box(modifier = modifier.fillMaxSize()) {
        // ---- Scrim -----------------------------------------------------------
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        // No ripple or role: it is a plain dismiss surface, not a button.
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // ---- Sheet -----------------------------------------------------------
        // The sheet measures the system bars itself: it is drawn from the root `Box` in the cart
        // screen, outside that screen's `statusBarsPadding()`, so nothing above it has reserved
        // the status bar and its header would otherwise draw under the notification bar. Insetting
        // the outer box (rather than padding the Surface) keeps the rounded corner and the shadow
        // sitting just below the bar instead of being clipped by it.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    WindowInsets.statusBars.asPaddingValues()
                )
        ) {
            val sheetWidth = maxWidth * 0.9f
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it })
            ) {
                Surface(
                    modifier = Modifier
                        .width(sheetWidth)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                ) {
                    PickerSheetContent(
                        products = products,
                        categories = categories,
                        cartQuantities = cartQuantities,
                        onPick = onPick,
                        onDismiss = onDismiss,
                        // Carried here so the last product row clears the gesture/navigation bar
                        // the same way the header clears the status bar.
                        bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerSheetContent(
    products: List<Product>,
    categories: List<Category>,
    cartQuantities: Map<Long, Int>,
    onPick: (Product) -> Unit,
    onDismiss: () -> Unit,
    bottomInset: Dp = 0.dp,
) {
    // null = "All"; UNCATEGORISED_CATEGORY_ID = "Uncategorised"; otherwise a real category id.
    var selectedCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    // Category and search combine with AND so the chips keep narrowing the same result set.
    //
    // Favourites are then pinned to the top by a *sort*, not a filter: the picker must never hide
    // a product, or a favourite could become unreachable with no indication why. Ordering is
    // applied after filtering so the pinning always sits within the current result, not above it.
    val visible = remember(products, selectedCategoryId, query) {
        favouritesFirst(filterProducts(products, selectedCategoryId, query))
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.cart_pick_product),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cart_pick_close)
                )
            }
        }

        if (categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { selectedCategoryId = null },
                        label = { Text(stringResource(R.string.category_all)) }
                    )
                }
                items(categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id },
                        label = { Text(category.name) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategoryId == UNCATEGORISED_CATEGORY_ID,
                        onClick = { selectedCategoryId = UNCATEGORISED_CATEGORY_ID },
                        label = { Text(stringResource(R.string.category_uncategorised)) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.cart_pick_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cart_pick_search_clear)
                        )
                    }
                }
            } else null
        )

        HorizontalDivider()

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(
                        // Distinguish "nothing in this category" from "nothing matched the search".
                        if (query.isNotBlank()) R.string.cart_pick_no_matches
                        else R.string.inventory_empty_title
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + bottomInset
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visible, key = { it.id }) { product ->
                    PickerRow(
                        product = product,
                        inCart = cartQuantities[product.id] ?: 0,
                        onPick = { onPick(product) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    product: Product,
    inCart: Int,
    onPick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val outOfStock = product.isOutOfStock

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        // The row tints while pressed, which is the "added" confirmation together with the
        // snackbar the cart already emits.
        color = when {
            pressed && !outOfStock -> MaterialTheme.colorScheme.secondaryContainer
            outOfStock -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            inCart > 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        onClick = onPick,
        enabled = !outOfStock,
        interactionSource = interaction
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProductThumbnail(fileName = product.imagePath, size = 56.dp)

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (product.favourite) {
                        // Read-only here: the picker is for picking, not for editing, and a
                        // toggle in a sheet that stays open would be a second way to change data
                        // from a screen built for tapping. Marking lives in the inventory. The
                        // star is still drawn so the pinned ordering is explicable rather than
                        // looking like a sorting bug.
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        product.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    formatCents(product.priceCents),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.inventory_quantity, product.quantity),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        outOfStock -> MaterialTheme.colorScheme.error
                        product.isLowStock -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (inCart > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = true, onCheckedChange = null)
                    Text(
                        "$inCart",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else {
                Icon(
                    Icons.Default.AddShoppingCart,
                    contentDescription = stringResource(R.string.cart_pick_added),
                    tint = if (outOfStock) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
