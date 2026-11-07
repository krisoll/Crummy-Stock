package com.example.stockapplication.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.ProductGroup
import com.example.stockapplication.ui.components.ProductThumbnail
import com.example.stockapplication.ui.components.rememberRevealModifier
import com.example.stockapplication.ui.theme.AppMotion

/**
 * The id standing in for "uncategorised" when a folder is opened.
 *
 * The data layer represents uncategorised as `null`, which cannot be distinguished from "no folder
 * is open" in a single nullable field. Sending a real id through means the open/closed check stays
 * one comparison instead of a second flag that could disagree with it.
 */
internal const val UNCATEGORISED_FOLDER_ID = -42L

/**
 * The inventory drawn as category folders.
 *
 * ## Why a second view rather than a replacement
 *
 * The flat list is better when a shop has a handful of products — headers do the grouping without
 * an extra tap. Folders win once the list is longer than a screen, because scrolling past six
 * categories to reach the seventh is the thing that actually costs time. Which one is right
 * depends on the shop, so both exist and the choice is remembered.
 *
 * ## What it shows
 *
 * The same [com.example.stockapplication.ui.viewmodel.InventoryUiState] the list uses, so every
 * filter, the search box and the tag chips all apply here too — the grid is fed the *visible*
 * groups, so switching views never changes which products are shown, only how they are laid out.
 *
 * @param openCategoryId the folder currently opened, or null when the grid is showing. Hoisted so
 *   the caller can wire the back gesture.
 */
@Composable
internal fun FolderInventory(
    groups: List<ProductGroup>,
    openCategoryId: Long?,
    onOpenCategory: (Long?) -> Unit,
    onProductClick: (Product) -> Unit,
    onQuickRestock: (Product) -> Unit,
    onQuickRemoveStock: (Product) -> Unit,
    onToggleFavourite: (Product) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val openGroup = groups.firstOrNull {
        (it.categoryId ?: UNCATEGORISED_FOLDER_ID) == openCategoryId
    }

    if (openCategoryId == null || openGroup == null) {
        FolderGrid(
            groups = groups,
            onOpenCategory = onOpenCategory,
            modifier = modifier,
        )
    } else {
        FolderContents(
            group = openGroup,
            onBack = { onOpenCategory(null) },
            onProductClick = onProductClick,
            onQuickRestock = onQuickRestock,
            onQuickRemoveStock = onQuickRemoveStock,
            onToggleFavourite = onToggleFavourite,
            modifier = modifier,
        )
    }
}

@Composable
private fun FolderGrid(
    groups: List<ProductGroup>,
    onOpenCategory: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val staggerMillis = AppMotion.stagger()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = groups,
            key = { it.categoryId ?: UNCATEGORISED_FOLDER_ID },
        ) { group ->
            FolderCard(
                group = group,
                onClick = { onOpenCategory(group.categoryId ?: UNCATEGORISED_FOLDER_ID) },
                modifier = rememberRevealModifier(0, staggerMillis),
            )
        }
    }
}

/**
 * One category tile.
 *
 * The preview picture is the first product in the folder that actually has one, rather than a
 * purpose-made icon: it is the quickest way for a user to recognise their own category, and it
 * costs no extra assets.
 */
@Composable
private fun FolderCard(
    group: ProductGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = group.products.firstOrNull { !it.imagePath.isNullOrBlank() }?.imagePath

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null) {
                    ProductThumbnail(
                        fileName = preview,
                        size = 64.dp,
                        cornerRadius = 14.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Text(
                text = group.categoryName ?: stringResource(R.string.category_none),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // A count is more useful than "empty", but "No products" reads better than
                // "0 products" for a folder the user has not filled yet.
                text = if (group.products.isEmpty()) {
                    stringResource(R.string.inventory_folder_empty)
                } else {
                    stringResource(R.string.inventory_folder_count, group.products.size)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** The products inside one folder, with a header carrying a back affordance. */
@Composable
private fun FolderContents(
    group: ProductGroup,
    onBack: () -> Unit,
    onProductClick: (Product) -> Unit,
    onQuickRestock: (Product) -> Unit,
    onQuickRemoveStock: (Product) -> Unit,
    onToggleFavourite: (Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    val staggerMillis = AppMotion.stagger()
    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.inventory_folder_back),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        group.categoryName ?: stringResource(R.string.category_none),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.inventory_folder_count, group.products.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (group.products.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.inventory_folder_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items = group.products, key = { _, p -> p.id }) { index, product ->
                ProductRow(
                    product = product,
                    onClick = { onProductClick(product) },
                    onQuickRestock = { onQuickRestock(product) },
                    onQuickRemoveStock = { onQuickRemoveStock(product) },
                    modifier = rememberRevealModifier(index, staggerMillis),
                    onToggleFavourite = onToggleFavourite,
                )
            }
        }
    }
}
