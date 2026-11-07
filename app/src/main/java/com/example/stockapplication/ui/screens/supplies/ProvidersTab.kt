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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.Provider
import com.example.stockapplication.data.ProviderSummary
import com.example.stockapplication.data.formatCents
import com.example.stockapplication.ui.components.rememberRevealModifier
import com.example.stockapplication.ui.theme.AppMotion

private enum class ProvidersPhase { LOADING, EMPTY, LIST }

/**
 * The Providers tab: a searchable address book with each provider's purchase count and spend.
 *
 * Delete is offered from the row overflow, but it is not destructive to history: the entry rows
 * keep the provider name snapshot, which the confirmation spells out.
 */
@Composable
internal fun ProvidersTab(
    providers: List<ProviderSummary>,
    isLoading: Boolean,
    onEdit: (ProviderSummary) -> Unit,
    onDelete: (Provider) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Provider?>(null) }

    val visible = remember(providers, query) {
        if (query.isBlank()) {
            providers
        } else {
            providers.filter { provider ->
                provider.name.contains(query, ignoreCase = true) ||
                    provider.phone.contains(query, ignoreCase = true)
            }
        }
    }

    val phase = when {
        isLoading -> ProvidersPhase.LOADING
        visible.isEmpty() -> ProvidersPhase.EMPTY
        else -> ProvidersPhase.LIST
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
                label = stringResource(R.string.supplies_provider_summary_label),
                value = providers.size.toString(),
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
            placeholder = { Text(stringResource(R.string.supplies_provider_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )

        HorizontalDivider(Modifier.padding(top = 12.dp))

        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                fadeIn(animationSpec = phaseEnter) togetherWith fadeOut(animationSpec = phaseExit)
            },
            modifier = Modifier.weight(1f),
            label = "providers-phase",
        ) { current ->
            when (current) {
                ProvidersPhase.LOADING -> SuppliesLoadingState()
                ProvidersPhase.EMPTY -> SuppliesEmptyState(
                    icon = Icons.Default.LocalShipping,
                    title = stringResource(
                        if (providers.isEmpty()) {
                            R.string.supplies_provider_empty_title
                        } else {
                            R.string.supplies_provider_no_matches_title
                        }
                    ),
                    body = stringResource(
                        if (providers.isEmpty()) {
                            R.string.supplies_provider_empty_body
                        } else {
                            R.string.supplies_provider_no_matches_body
                        }
                    ),
                )

                ProvidersPhase.LIST -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(visible, key = { _, provider -> provider.id }) { index, provider ->
                        ProviderCard(
                            provider = provider,
                            onClick = { onEdit(provider) },
                            onDelete = { pendingDelete = provider.asProvider() },
                            modifier = rememberRevealModifier(index, staggerMillis),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { provider ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.supplies_provider_delete_title)) },
            text = {
                Text(stringResource(R.string.supplies_provider_delete_body, provider.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(provider)
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
private fun ProviderCard(
    provider: ProviderSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Text(
                    provider.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (provider.phone.isNotBlank()) {
                    Text(
                        provider.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val purchases = pluralStringResource(
                    R.plurals.supplies_provider_purchases,
                    provider.entryCount,
                    provider.entryCount,
                )
                Text(
                    stringResource(
                        R.string.supplies_provider_purchases_total,
                        purchases,
                        stringResource(
                            R.string.supplies_provider_total,
                            formatCents(provider.totalSpentCents),
                        ),
                    ),
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
                        text = { Text(stringResource(R.string.product_edit_title)) },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onClick()
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
