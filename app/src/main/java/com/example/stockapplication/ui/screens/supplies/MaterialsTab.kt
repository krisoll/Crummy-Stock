package com.example.stockapplication.ui.screens.supplies

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.Material
import com.example.stockapplication.data.MaterialRow
import com.example.stockapplication.data.formatCents
import com.example.stockapplication.ui.components.rememberRevealModifier
import com.example.stockapplication.ui.theme.AppMotion

private enum class MaterialsPhase { LOADING, EMPTY, LIST }

/** The three ways the material list can be narrowed, matching the inventory chips. */
internal enum class MaterialFilter { ALL, LOW, OUT }

/**
 * The Materials tab: the separate supplies inventory, with last paid cost and low-stock filters.
 *
 * Stock is shown as authoritative. A material with no purchase history still exists and counts in
 * the catalogue; it simply contributes nothing to the stock-value tile until it is costed.
 */
@Composable
internal fun MaterialsTab(
    materials: List<MaterialRow>,
    isLoading: Boolean,
    lowStockCount: Int,
    inventoryValueCents: Long,
    onEdit: (Material) -> Unit,
    onAdjustStock: (Material) -> Unit,
    onDelete: (Material) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(MaterialFilter.ALL) }
    var pendingDelete by remember { mutableStateOf<Material?>(null) }

    val visible = remember(materials, query, filter) {
        materials.map { it.material }.filter { material ->
            val matchesFilter = when (filter) {
                MaterialFilter.ALL -> true
                MaterialFilter.LOW -> material.isLowStock
                MaterialFilter.OUT -> material.isOutOfStock
            }
            val matchesQuery = query.isBlank() ||
                material.name.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    val phase = when {
        isLoading -> MaterialsPhase.LOADING
        visible.isEmpty() -> MaterialsPhase.EMPTY
        else -> MaterialsPhase.LIST
    }
    val staggerMillis = AppMotion.stagger()
    val phaseEnter = AppMotion.enterFade()
    val phaseExit = AppMotion.exitFade()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SuppliesSummaryTile(
                label = stringResource(R.string.supplies_material_summary_items),
                value = materials.size.toString(),
                modifier = Modifier.weight(1f),
            )
            SuppliesSummaryTile(
                label = stringResource(R.string.supplies_material_summary_low),
                value = lowStockCount.toString(),
                modifier = Modifier.weight(1f),
                // Tapping the tile is the shortcut to the list it describes. It toggles back to
                // All so the same target can undo itself.
                onClick = {
                    filter = if (filter == MaterialFilter.LOW) {
                        MaterialFilter.ALL
                    } else {
                        MaterialFilter.LOW
                    }
                },
            )
            SuppliesSummaryTile(
                label = stringResource(R.string.supplies_material_summary_value),
                value = formatCents(inventoryValueCents),
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.supplies_material_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter == MaterialFilter.ALL,
                onClick = { filter = MaterialFilter.ALL },
                label = {
                    Text(stringResource(R.string.supplies_material_filter_all, materials.size))
                },
                colors = FilterChipDefaults.filterChipColors(),
            )
            FilterChip(
                selected = filter == MaterialFilter.LOW,
                onClick = { filter = MaterialFilter.LOW },
                label = { Text(stringResource(R.string.supplies_material_filter_low)) },
                colors = FilterChipDefaults.filterChipColors(),
            )
            FilterChip(
                selected = filter == MaterialFilter.OUT,
                onClick = { filter = MaterialFilter.OUT },
                label = { Text(stringResource(R.string.supplies_material_filter_out)) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }

        HorizontalDivider()

        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                fadeIn(animationSpec = phaseEnter) togetherWith fadeOut(animationSpec = phaseExit)
            },
            modifier = Modifier.weight(1f),
            label = "materials-phase",
        ) { current ->
            when (current) {
                MaterialsPhase.LOADING -> SuppliesLoadingState()
                MaterialsPhase.EMPTY -> SuppliesEmptyState(
                    icon = Icons.Default.Inventory2,
                    title = stringResource(
                        if (materials.isEmpty()) {
                            R.string.supplies_material_empty_title
                        } else {
                            R.string.supplies_material_no_matches_title
                        }
                    ),
                    body = stringResource(
                        if (materials.isEmpty()) {
                            R.string.supplies_material_empty_body
                        } else {
                            R.string.supplies_material_no_matches_body
                        }
                    ),
                )

                MaterialsPhase.LIST -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(visible, key = { _, material -> material.id }) { index, material ->
                        val row = materials.firstOrNull { it.material.id == material.id }
                        MaterialCard(
                            material = material,
                            lastUnitCostCents = row?.lastUnitCostCents,
                            onClick = { onEdit(material) },
                            onAdjustStock = { onAdjustStock(material) },
                            onDelete = { pendingDelete = material },
                            modifier = rememberRevealModifier(index, staggerMillis),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { material ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.supplies_material_delete_title)) },
            text = {
                Text(stringResource(R.string.supplies_material_delete_body, material.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(material)
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun MaterialCard(
    material: Material,
    lastUnitCostCents: Long?,
    onClick: () -> Unit,
    onAdjustStock: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val unit = materialUnitLabel(material.unit, material.customUnit)

    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        material.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (material.isOutOfStock || material.isLowStock) {
                        MaterialStockBadge(outOfStock = material.isOutOfStock)
                    }
                }

                Text(
                    unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    stringResource(
                        R.string.supplies_material_stock_with_unit,
                        material.stock,
                        unit,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        material.isOutOfStock -> MaterialTheme.colorScheme.error
                        material.isLowStock -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )

                Text(
                    if (lastUnitCostCents == null) {
                        stringResource(R.string.supplies_material_no_purchases)
                    } else {
                        stringResource(
                            R.string.supplies_material_last_cost,
                            formatCents(lastUnitCostCents),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.supplies_more_options),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.supplies_material_adjust)) },
                        leadingIcon = {
                            Icon(Icons.Default.Inventory2, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onAdjustStock()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
