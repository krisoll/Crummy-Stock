package com.example.stockapplication.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stockapplication.R
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.ProductGroup
import com.example.stockapplication.data.Promo
import com.example.stockapplication.data.Tag
import com.example.stockapplication.data.formatCents
import com.example.stockapplication.ui.components.ProductThumbnail
import com.example.stockapplication.data.InventoryViewMode
import com.example.stockapplication.ui.components.rememberRevealModifier
import com.example.stockapplication.ui.theme.AppMotion
import com.example.stockapplication.ui.viewmodel.InventoryFilter
import com.example.stockapplication.ui.viewmodel.InventoryUiState

/** Which of the three mutually exclusive bodies the inventory list is showing. */
private enum class InventoryPhase { LOADING, EMPTY, LIST }

/** The two halves of the Inventory destination. */
enum class InventoryTab { INVENTORY, PROMOS }

/**
 * The Inventory destination, split into two tabs.
 *
 * The tab bar sits above the summary tiles rather than replacing the bottom navigation: it is a
 * *sub*-section of one destination, not a peer of Inventory/Sales/History. The header above it was
 * tightened (smaller tiles, less padding) to pay for its height, so the list does not lose any
 * rows to it.
 *
 * @param selectedTab which half is showing; hoisted so the FAB and the editor routing can follow.
 */
@Composable
fun InventoryScreen(
    state: InventoryUiState,
    selectedTab: InventoryTab,
    promos: List<Promo>,
    onTabChange: (InventoryTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (InventoryFilter) -> Unit,
    onProductClick: (Product) -> Unit,
    onQuickRestock: (Product) -> Unit,
    /** Adjusts stock down by one unit without recording a sale. */
    onQuickRemoveStock: (Product) -> Unit,
    onAddClick: () -> Unit,
    onPromoClick: (Promo) -> Unit,
    onPromoToggleEnabled: (Promo, Boolean) -> Unit,
    onAddPromoClick: () -> Unit,
    /** Opens the category manager from the overflow menu. */
    onEditCategoriesClick: () -> Unit,
    /** Opens the tag manager from the overflow menu. Only offered while tags are switched on. */
    onEditTagsClick: () -> Unit = {},
    /** Whether the optional tagging feature is on; hides every tag control when false. */
    tagsEnabled: Boolean = false,
    /** Every tag, for the filter chips. Empty when the feature is off. */
    tags: List<Tag> = emptyList(),
    /** Tag ids currently used to filter the list. */
    selectedTagIds: Set<Long> = emptySet(),
    /** Toggles one tag in the filter. */
    onTagToggle: (Long) -> Unit = {},
    /** Clock used for promo status chips; injectable so the tab can be previewed at any date. */
    now: Long = System.currentTimeMillis(),
    /** Which layout the product list uses; persisted, so the choice survives a restart. */
    viewMode: InventoryViewMode = InventoryViewMode.LIST,
    onViewModeChange: (InventoryViewMode) -> Unit = {},
    /**
     * The folder opened in [InventoryViewMode.FOLDERS], or null for the grid.
     *
     * Hoisted rather than local so [StockApp] can register a back handler: opening a folder is a
     * navigation step as far as the user is concerned, and back should leave the folder before it
     * leaves the tab.
     */
    openFolderId: Long? = null,
    onOpenFolder: (Long?) -> Unit = {},
    /** Stars or unstars a product. Silent on success; only a failure reports. */
    onToggleFavourite: (Product) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Owned here rather than inside the menu: a `DropdownMenu` that owns its own `expanded` flag
    // cannot be closed by its own items without a coroutine, and the menu outlives the row that
    // anchors it while it animates out.
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // The root Scaffold in [StockApp] already consumed the system-bar insets and handed them
        // down as padding. A nested Scaffold would measure the status bar a second time and pad
        // for it again, leaving a dead strip above the tab row (~45 dp on a Pixel 8 Pro). Nothing
        // new can reach this Scaffold, so it owns no insets of its own.
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            // One FAB whose meaning follows the tab, rather than two stacked FABs: only one kind
            // of thing can be created on a given tab.
            ExtendedFloatingActionButton(
                onClick = if (selectedTab == InventoryTab.INVENTORY) {
                    onAddClick
                } else {
                    onAddPromoClick
                },
                icon = {
                    Icon(
                        if (selectedTab == InventoryTab.INVENTORY) {
                            Icons.Default.Add
                        } else {
                            Icons.Default.LocalOffer
                        },
                        null
                    )
                },
                text = {
                    Text(
                        stringResource(
                            if (selectedTab == InventoryTab.INVENTORY) {
                                R.string.inventory_add_product
                            } else {
                                R.string.promo_new
                            }
                        )
                    )
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // ---- Tabs --------------------------------------------------------
            // The tab row is the topmost element on this destination, so under edge-to-edge it
            // is what has to keep clear of the status bar. Padded here, on the row itself,
            // rather than on the Column: that way the row's colour still reaches the top edge
            // and only its contents move down.
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.statusBarsPadding(),
                // The selected indicator is an accent (a fill), but the labels are text and must
                // follow the font colour — hence the explicit `contentColor`, which stops Tab from
                // deriving its label from `primary` and going green with the buttons.
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                InventoryTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { onTabChange(tab) },
                        // Unselected labels sit back at reduced emphasis rather than a different
                        // hue, so the whole bar still belongs to the text channel.
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                stringResource(
                                    if (tab == InventoryTab.INVENTORY) {
                                        R.string.promo_tab_inventory
                                    } else {
                                        R.string.promo_tab_promos
                                    }
                                )
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                InventoryTab.INVENTORY -> InventoryTabBody(
                    state = state,
                    menuExpanded = menuExpanded,
                    onMenuExpandedChange = { menuExpanded = it },
                    onEditCategoriesClick = onEditCategoriesClick,
                    onEditTagsClick = onEditTagsClick,
                    tagsEnabled = tagsEnabled,
                    tags = tags,
                    selectedTagIds = selectedTagIds,
                    onTagToggle = onTagToggle,
                    onQueryChange = onQueryChange,
                    onFilterChange = onFilterChange,
                    onProductClick = onProductClick,
                    onQuickRestock = onQuickRestock,
                    onQuickRemoveStock = onQuickRemoveStock,
                    viewMode = viewMode,
                    onViewModeChange = onViewModeChange,
                    openFolderId = openFolderId,
                    onOpenFolder = onOpenFolder,
                    onToggleFavourite = onToggleFavourite,
                )

                InventoryTab.PROMOS -> PromosTab(
                    promos = promos,
                    categories = state.categories,
                    products = state.products,
                    now = now,
                    onPromoClick = onPromoClick,
                    onToggleEnabled = onPromoToggleEnabled,
                )
            }
        }
    }
}

/** The stock list: summary, search, filters and the sectioned product list. */
@Composable
private fun InventoryTabBody(
    state: InventoryUiState,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onEditCategoriesClick: () -> Unit,
    onEditTagsClick: () -> Unit,
    tagsEnabled: Boolean,
    tags: List<Tag>,
    selectedTagIds: Set<Long>,
    onTagToggle: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (InventoryFilter) -> Unit,
    onProductClick: (Product) -> Unit,
    onQuickRestock: (Product) -> Unit,
    onQuickRemoveStock: (Product) -> Unit,
    viewMode: InventoryViewMode,
    onViewModeChange: (InventoryViewMode) -> Unit,
    openFolderId: Long?,
    onOpenFolder: (Long?) -> Unit,
    onToggleFavourite: (Product) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SummaryHeader(state)

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.inventory_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )

        // The category chips and the overflow menu share a row. The menu used to sit beside the
        // Inventory/Promos tabs, which cost the full width of a three-dot button out of the tab
        // labels — "Promociones" is the long one and it lost its breathing room first. Down here
        // it costs one chip-width of trailing space instead, above a list that is already
        // inset by 16 dp on both sides.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // `FilterChipDefaults.filterChipColors()` derives its selected colours from the
                // scheme's `secondaryContainer` pair, which the theme now drives from the user's
                // highlight colour.
                FilterChip(
                    selected = state.filter == InventoryFilter.ALL,
                    onClick = { onFilterChange(InventoryFilter.ALL) },
                    label = {
                        Text(stringResource(R.string.inventory_filter_all, state.products.size))
                    },
                    colors = FilterChipDefaults.filterChipColors()
                )
                FilterChip(
                    selected = state.filter == InventoryFilter.LOW,
                    onClick = { onFilterChange(InventoryFilter.LOW) },
                    label = { Text(stringResource(R.string.inventory_filter_low)) },
                    colors = FilterChipDefaults.filterChipColors()
                )
                FilterChip(
                    selected = state.filter == InventoryFilter.OUT,
                    onClick = { onFilterChange(InventoryFilter.OUT) },
                    label = { Text(stringResource(R.string.inventory_filter_out)) },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }

            // View switch, immediately before the overflow menu: it belongs to the search and
            // filter row because it changes how the results below are presented, not what they
            // are. An icon toggle rather than a segmented control so it does not steal width from
            // the filter chips on a narrow screen.
            IconButton(
                onClick = {
                    onViewModeChange(
                        if (viewMode == InventoryViewMode.LIST) {
                            InventoryViewMode.FOLDERS
                        } else {
                            InventoryViewMode.LIST
                        }
                    )
                }
            ) {
                Icon(
                    imageVector = if (viewMode == InventoryViewMode.LIST) {
                        Icons.Default.Folder
                    } else {
                        Icons.AutoMirrored.Filled.ViewList
                    },
                    // The description names the *destination*, matching how a toggle that shows
                    // the current state would read backwards to a screen reader.
                    contentDescription = stringResource(
                        if (viewMode == InventoryViewMode.LIST) {
                            R.string.inventory_view_folders
                        } else {
                            R.string.inventory_view_list
                        }
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Overflow: settings that belong to the whole inventory rather than to the filters you
            // are looking at. Deliberately small and unlabelled — everything behind it is
            // configuration, not a daily action.
            Box {
                IconButton(onClick = { onMenuExpandedChange(true) }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.category_menu)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.category_menu_edit)) },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        },
                        onClick = {
                            onMenuExpandedChange(false)
                            onEditCategoriesClick()
                        }
                    )
                    // Only offered when the feature is on: a menu entry leading to a page the
                    // user never asked for is worse than no entry at all.
                    if (tagsEnabled) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tags_menu)) },
                            leadingIcon = {
                                Icon(Icons.Default.LocalOffer, contentDescription = null)
                            },
                            onClick = {
                                onMenuExpandedChange(false)
                                onEditTagsClick()
                            }
                        )
                    }
                }
            }
        }

        // ---- Tag filter ------------------------------------------------------
        // A second, independently scrollable chip row under the stock filter, and only when the
        // feature is on. Multi-select, because a tag filter that allowed one value would need a
        // "both" option to answer the obvious follow-up question.
        if (tagsEnabled && tags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.inventory_tag_filter),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Wrapped in a centred box rather than using `Modifier.align`: inside a
                        // `LazyRow` item the ambient scope is horizontal, so the vertical
                        // alignment overload does not resolve.
                        modifier = Modifier.wrapContentSize(Alignment.Center),
                    )
                }
                items(tags, key = { it.id }) { tag ->
                    val selected = tag.id in selectedTagIds
                    FilterChip(
                        selected = selected,
                        onClick = { onTagToggle(tag.id) },
                        label = { Text(tag.name) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
            }
        }

        HorizontalDivider()

            // Cross-fading between the three states means filtering, clearing the search or the
            // catalogue arriving all read as one screen changing rather than a hard cut.
            val staggerMillis = AppMotion.stagger()
            // Resolved in composable scope: `transitionSpec` is not a composable lambda.
            val phaseEnter = AppMotion.enterFade()
            val phaseExit = AppMotion.exitFade()
            AnimatedContent(
                targetState = when {
                    state.isLoading -> InventoryPhase.LOADING
                    state.visibleProducts.isEmpty() -> InventoryPhase.EMPTY
                    else -> InventoryPhase.LIST
                },
                transitionSpec = {
                    fadeIn(animationSpec = phaseEnter) togetherWith fadeOut(animationSpec = phaseExit)
                },
                label = "inventory-phase"
            ) { phase ->
                when (phase) {
                    InventoryPhase.LOADING -> LoadingState()
                    InventoryPhase.EMPTY -> EmptyState(state.products.isEmpty())
                    InventoryPhase.LIST -> when (viewMode) {
                        InventoryViewMode.LIST -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                // 2 dp between rows and at the top: the cards already have their
                                // own edges, so a larger gap reads as a gap rather than as
                                // separation. List density matters more here than air.
                                start = 16.dp, end = 16.dp, top = 2.dp, bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Headers are not staggered: they anchor each section, and delaying
                            // them makes the list look like it is still loading.
                            state.groupedProducts.forEach { group ->
                                item(key = "header-${group.categoryId ?: UNCATEGORISED_KEY}") {
                                    CategoryHeader(group)
                                }
                                itemsIndexed(
                                    items = group.products,
                                    key = { _, product -> product.id }
                                ) { index, product ->
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

                        // Folders read the same `groupedProducts`, which is already the
                        // *visible* set, so search and the filters narrow it identically in
                        // both layouts.
                        InventoryViewMode.FOLDERS -> FolderInventory(
                            groups = state.groupedProducts,
                            openCategoryId = openFolderId,
                            onOpenCategory = onOpenFolder,
                            onProductClick = onProductClick,
                            onQuickRestock = onQuickRestock,
                            onQuickRemoveStock = onQuickRemoveStock,
                            onToggleFavourite = onToggleFavourite,
                        )
                    }
                }
            }
    }
}

@Composable
private fun SummaryHeader(state: InventoryUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryTile(
            label = stringResource(R.string.inventory_summary_items),
            value = state.products.size.toString(),
            modifier = Modifier.weight(1f)
        )
        SummaryTile(
            label = stringResource(R.string.inventory_summary_units),
            value = state.totalUnits.toString(),
            modifier = Modifier.weight(1f)
        )
        SummaryTile(
            label = stringResource(R.string.inventory_summary_value),
            value = formatCents(state.totalValueCents),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Sticky-ish section heading for one category in the inventory list. */
@Composable
private fun CategoryHeader(group: ProductGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = group.categoryName ?: stringResource(R.string.category_uncategorised),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            // `onSurface`, not `primary`: this is a *heading*, so it belongs to the text channel.
            // Using the accent meant a green button colour turned every category label green and
            // made it unreadable against the page — the accent owns fills, not words.
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = group.products.size.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Lazy-list key fragment for the uncategorised bucket, which has a null category id. */
private const val UNCATEGORISED_KEY = "uncategorised"

/**
 * One product in a list.
 *
 * `internal` rather than private because the folder view ([FolderInventory]) renders the same row
 * inside a category — the two layouts must look identical or switching between them would feel
 * like moving to a different app.
 */
@Composable
internal fun ProductRow(
    product: Product,
    onClick: () -> Unit,
    onQuickRestock: () -> Unit,
    onQuickRemoveStock: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavourite: (Product) -> Unit = {},
) {
    Card(
        onClick = onClick,
        // `animateContentSize` lets the card grow smoothly when a product's photo finishes
        // loading or its quantity badge widens, instead of jumping between two heights.
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = AppMotion.expand()),
        colors = CardDefaults.cardColors(
            containerColor = if (product.isOutOfStock) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            // 8 dp vertical rather than 10: with the star on the chips' row the card's height is
            // set by its 48 dp thumbnail, so this only trims the breathing room around it.
            // Horizontal stays at 12, which is what keeps the text off the card edge.
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Falls back to the boxed icon when the product has no photo.
            //
            // 40 dp rather than 48: the thumbnail is what sets this row's height now that the
            // star and the chips share a line, so shrinking it is the only change that actually
            // makes the list denser. The corner radius drops with it — 12 dp on a 40 dp square
            // read as a circle rather than as a rounded tile.
            ProductThumbnail(
                fileName = product.imagePath,
                size = 40.dp,
                cornerRadius = 10.dp
            )

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (product.favourite) {
                        // A small filled star beside the name, so a favourited product is
                        // recognisable at a glance in a list. Only drawn when true: an outline on
                        // every other row would add visual noise to the 90% of products that are
                        // not favourites, and the toggle below is always available.
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        product.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    product.barcode,
                    style = MaterialTheme.typography.bodySmall,
                    // Tightened line spacing. The three lines of this column — title, barcode,
                    // qty/price — were 135 px of the card's 171 px, so the stacked text, not any
                    // control, is what makes rows tall. Trimming the leading on the two small
                    // lines is what actually buys density back.
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.inventory_quantity, product.quantity),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            product.isOutOfStock -> MaterialTheme.colorScheme.error
                            product.isLowStock -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text("·", style = MaterialTheme.typography.bodyMedium, lineHeight = 16.sp)
                    Text(
                        formatCents(product.priceCents),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 16.sp,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                // Star and quick-stock chips share one row rather than stacking.
                //
                // Stacked, the star's IconButton alone is a 48 dp tap target sitting above a
                // ~32 dp chip, which made every row roughly 100 dp tall — so a list that fits
                // about eight products only showed five. Side by side they cost the row nothing
                // beyond the height it already needed for the chips.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallIconButton(
                        onClick = { onToggleFavourite(product) },
                        icon = if (product.favourite) Icons.Default.Star else Icons.Default.StarBorder,
                        // Names the action, not the state: a description that read "Favourite" on
                        // an already-favourited row would tell a screen-reader user nothing about
                        // what tapping it does.
                        contentDescription = stringResource(
                            if (product.favourite) {
                                R.string.inventory_unfavourite
                            } else {
                                R.string.inventory_favourite
                            }
                        ),
                        tint = if (product.favourite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    // Compact icon buttons rather than AssistChips.
                    //
                    // The chips were the tallest thing in this row — an AssistChip is ~36 dp
                    // regardless of its label — so they, not the thumbnail, set the card's
                    // height. Icons carry the same two actions in half the vertical space, and
                    // the meaning is already unambiguous on a stock row: plus adds one, minus
                    // takes one away.
                    //
                    // The tap target is still 40 dp because the glyphs are icons *inside* a
                    // Button-shaped touch area rather than bare 24 dp icons; going smaller would
                    // put a destructive action (minus) within a mis-tap of a normal one.
                    SmallIconButton(
                        onClick = onQuickRestock,
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.inventory_quick_add_one),
                    )
                    SmallIconButton(
                        onClick = onQuickRemoveStock,
                        icon = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.inventory_quick_remove_one),
                        enabled = !product.isOutOfStock,
                    )
                }
            }
        }
    }
}

/**
 * A 40 dp icon button for a row's quick actions.
 *
 * Deliberately not `IconButton`: the Material default is 48 dp, which is the accessible minimum
 * but also what made these rows tall. 40 dp stays comfortably tappable for a thumb while giving
 * back the height a dense list needs, and its tint carries the disabled state for the minus
 * button when a product is already out of stock.
 */
@Composable
private fun SmallIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        },
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                // A caller-supplied tint wins, so the star can show its filled/empty state in the
                // accent colour while the stock buttons stay neutral. Disabled still greys out.
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    tint != null -> tint
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(isEmptyCatalog: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(56.dp))
        Text(
            stringResource(
                if (isEmptyCatalog) R.string.inventory_empty_title
                else R.string.inventory_no_matches_title
            ),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(
                if (isEmptyCatalog) R.string.inventory_empty_body
                else R.string.inventory_no_matches_body
            ),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
