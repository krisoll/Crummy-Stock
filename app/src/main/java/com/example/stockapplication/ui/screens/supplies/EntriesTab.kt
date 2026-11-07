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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.stockapplication.data.MaterialEntryWithLines
import com.example.stockapplication.data.ProviderSummary
import com.example.stockapplication.data.formatCents
import com.example.stockapplication.ui.components.formatDateLabel
import com.example.stockapplication.ui.components.rememberRevealModifier
import com.example.stockapplication.ui.theme.AppMotion

private enum class EntriesPhase { LOADING, EMPTY, LIST }

/**
 * Purchase history: newest first, filterable by provider and by reference/note/provider text.
 *
 * The summary is current-month spend and total entries; both are derived from the same list, so
 * they cannot disagree with the cards below them.
 */
@Composable
internal fun EntriesTab(
    entries: List<MaterialEntryWithLines>,
    providers: List<ProviderSummary>,
    currentMonthSpendCents: Long,
    isLoading: Boolean,
    onOpenEntry: (MaterialEntryWithLines) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var providerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var providerMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(providerId, providers, isLoading) {
        if (!isLoading && providerId != null && providers.none { it.id == providerId }) {
            providerId = null
        }
    }

    val selectedProvider = providerId?.let { id -> providers.firstOrNull { it.id == id } }
    val visible = remember(entries, query, providerId) {
        entries.filter { row ->
            val matchesProvider = providerId == null || row.entry.providerId == providerId
            val matchesQuery = query.isBlank() ||
                row.entry.providerName.contains(query, ignoreCase = true) ||
                row.entry.reference.contains(query, ignoreCase = true) ||
                row.entry.note.contains(query, ignoreCase = true)
            matchesProvider && matchesQuery
        }
    }

    val phase = when {
        isLoading -> EntriesPhase.LOADING
        visible.isEmpty() -> EntriesPhase.EMPTY
        else -> EntriesPhase.LIST
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
                label = stringResource(R.string.supplies_entry_summary_month),
                value = formatCents(currentMonthSpendCents),
                modifier = Modifier.weight(1f),
            )
            SuppliesSummaryTile(
                label = stringResource(R.string.supplies_entry_summary_count),
                value = entries.size.toString(),
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
            placeholder = { Text(stringResource(R.string.supplies_entry_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            OutlinedButton(
                onClick = { providerMenuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    selectedProvider?.name
                        ?: stringResource(R.string.supplies_entry_filter_all_providers),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = providerMenuExpanded,
                onDismissRequest = { providerMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.supplies_entry_filter_all_providers))
                    },
                    onClick = {
                        providerId = null
                        providerMenuExpanded = false
                    },
                )
                providers.forEach { provider ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                provider.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            providerId = provider.id
                            providerMenuExpanded = false
                        },
                    )
                }
            }
        }

        HorizontalDivider(Modifier.padding(top = 6.dp))

        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                fadeIn(animationSpec = phaseEnter) togetherWith fadeOut(animationSpec = phaseExit)
            },
            modifier = Modifier.weight(1f),
            label = "entries-phase",
        ) { current ->
            when (current) {
                EntriesPhase.LOADING -> SuppliesLoadingState()
                EntriesPhase.EMPTY -> SuppliesEmptyState(
                    icon = if (entries.isEmpty()) {
                        Icons.AutoMirrored.Filled.ReceiptLong
                    } else {
                        Icons.Default.SearchOff
                    },
                    title = stringResource(
                        if (entries.isEmpty()) {
                            R.string.supplies_entry_empty_title
                        } else {
                            R.string.supplies_entry_no_matches_title
                        }
                    ),
                    body = stringResource(
                        if (entries.isEmpty()) {
                            R.string.supplies_entry_empty_body
                        } else {
                            R.string.supplies_entry_no_matches_body
                        }
                    ),
                )

                EntriesPhase.LIST -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        visible,
                        key = { _, row -> row.entry.entryId },
                    ) { index, row ->
                        EntryCard(
                            row = row,
                            onClick = { onOpenEntry(row) },
                            modifier = rememberRevealModifier(index, staggerMillis),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(
    row: MaterialEntryWithLines,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = row.entry

    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    formatDateLabel(entry.purchasedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatCents(entry.totalCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                entry.providerName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.reference.isNotBlank()) {
                Text(
                    entry.reference,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                pluralStringResource(
                    R.plurals.supplies_entry_lines,
                    entry.lineCount,
                    entry.lineCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
