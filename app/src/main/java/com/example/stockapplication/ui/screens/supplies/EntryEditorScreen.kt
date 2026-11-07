package com.example.stockapplication.ui.screens.supplies

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.Material
import com.example.stockapplication.data.MaterialRow
import com.example.stockapplication.data.NewMaterialEntry
import com.example.stockapplication.data.NewMaterialLine
import com.example.stockapplication.data.Provider
import com.example.stockapplication.data.ProviderSummary
import com.example.stockapplication.data.formatCents
import com.example.stockapplication.ui.components.SimpleDatePickerDialog
import com.example.stockapplication.ui.components.formatDateLabel
import com.example.stockapplication.ui.components.localDayStart
import com.example.stockapplication.ui.screens.formatPriceInput
import com.example.stockapplication.ui.screens.sanitizeMoneyInput
import com.example.stockapplication.ui.screens.toCentsOrNull
import kotlinx.coroutines.delay

/** One editable line in the purchase draft. Text is kept raw so a half-typed amount survives. */
internal data class EntryLineDraft(
    val materialId: Long,
    val quantityText: String = "1",
    val unitCostText: String = "",
)

/**
 * Rotation-safe storage for the line drafts.
 *
 * Ids and numeric text cannot contain a semicolon, so a compact encoding is safer than trying to
 * make a nested object saveable. A malformed entry is dropped rather than restoring a broken row.
 */
private val EntryLineDraftListSaver: Saver<List<EntryLineDraft>, ArrayList<String>> = Saver(
    save = { drafts ->
        ArrayList(
            drafts.map { draft ->
                "${draft.materialId};${draft.quantityText};${draft.unitCostText}"
            }
        )
    },
    restore = { saved ->
        saved.mapNotNull { encoded ->
            val parts = encoded.split(';')
            if (parts.size != 3) return@mapNotNull null
            val id = parts[0].toLongOrNull() ?: return@mapNotNull null
            EntryLineDraft(
                materialId = id,
                quantityText = parts[1],
                unitCostText = parts[2],
            )
        }
    },
)

/** Multiplies without silently wrapping on absurd input; null means "not a valid line yet". */
internal fun lineTotalCents(quantity: Int?, unitCostCents: Long?): Long? {
    if (quantity == null || quantity <= 0) return null
    val cost = unitCostCents ?: return null
    if (cost < 0L) return null
    if (cost != 0L && quantity.toLong() > Long.MAX_VALUE / cost) return null
    return quantity.toLong() * cost
}

/**
 * New-purchase editor.
 *
 * Everything that can invalidate a save is checked here for feedback (provider chosen, whole
 * quantity, non-negative cost) and checked again by the DAO inside the transaction, so a provider
 * or material deleted underneath the draft cannot leave a half-written invoice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryEditorScreen(
    providers: List<ProviderSummary>,
    materials: List<MaterialRow>,
    onSave: (NewMaterialEntry, (Boolean) -> Unit) -> Unit,
    onCreateProvider: (Provider, (Provider?) -> Unit) -> Unit,
    onCreateMaterial: (Material, (Material?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var providerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var purchasedAt by rememberSaveable {
        mutableStateOf(localDayStart(System.currentTimeMillis()))
    }
    var reference by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var lineDrafts by rememberSaveable(stateSaver = EntryLineDraftListSaver) {
        mutableStateOf(emptyList<EntryLineDraft>())
    }
    var showProviderPicker by rememberSaveable { mutableStateOf(false) }
    var showMaterialPicker by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var highlightedMaterialId by remember { mutableStateOf<Long?>(null) }

    val selectedProvider = providerId?.let { id -> providers.firstOrNull { it.id == id } }

    val lineInfo = lineDrafts.map { draft ->
        val row = materials.firstOrNull { it.material.id == draft.materialId }
        val quantity = draft.quantityText.toIntOrNull()?.takeIf { it > 0 }
        val cost = draft.unitCostText.toCentsOrNull()
        EntryLineInfo(
            draft = draft,
            row = row,
            quantity = quantity,
            costCents = cost,
            totalCents = if (row == null) null else lineTotalCents(quantity, cost),
        )
    }
    val linesValid = lineInfo.isNotEmpty() && lineInfo.all { it.totalCents != null }
    val totalCents: Long? = if (linesValid) {
        runCatching {
            lineInfo.fold(0L) { running, line -> Math.addExact(running, line.totalCents!!) }
        }.getOrNull()
    } else {
        null
    }
    val canSave = providerId != null &&
        selectedProvider != null &&
        linesValid &&
        totalCents != null &&
        !saving

    LaunchedEffect(highlightedMaterialId) {
        if (highlightedMaterialId != null) {
            delay(1_500)
            highlightedMaterialId = null
        }
    }

    fun saveEntry() {
        val id = providerId ?: return
        if (!canSave) return
        val lines = lineInfo.mapNotNull { info ->
            val material = info.row?.material ?: return@mapNotNull null
            val quantity = info.quantity ?: return@mapNotNull null
            val cost = info.costCents ?: return@mapNotNull null
            NewMaterialLine(
                materialId = material.id,
                quantity = quantity,
                unitCostCents = cost,
            )
        }
        saving = true
        onSave(
            NewMaterialEntry(
                providerId = id,
                purchasedAt = purchasedAt,
                reference = reference.trim(),
                note = note.trim(),
                lines = lines,
            )
        ) { success ->
            saving = false
            if (success) onBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.supplies_entry_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = ::saveEntry,
                        enabled = canSave,
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { showProviderPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.LocalShipping, contentDescription = null)
                Text(
                    selectedProvider?.name
                        ?: stringResource(R.string.supplies_entry_select_provider),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Text(
                    formatDateLabel(purchasedAt),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    textAlign = TextAlign.Start,
                )
            }

            OutlinedTextField(
                value = reference,
                onValueChange = { reference = it },
                label = { Text(stringResource(R.string.supplies_entry_reference)) },
                placeholder = {
                    Text(stringResource(R.string.supplies_entry_reference_placeholder))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.supplies_entry_note)) },
                placeholder = { Text(stringResource(R.string.supplies_entry_note_placeholder)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.supplies_entry_lines_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showMaterialPicker = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(
                        stringResource(R.string.supplies_entry_add_material),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            if (lineDrafts.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Text(
                        stringResource(R.string.supplies_entry_no_lines),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                lineInfo.forEach { info ->
                    EntryLineCard(
                        info = info,
                        highlighted = highlightedMaterialId == info.draft.materialId,
                        onQuantityChange = { value ->
                            lineDrafts = lineDrafts.map { draft ->
                                if (draft.materialId == info.draft.materialId) {
                                    draft.copy(quantityText = value)
                                } else {
                                    draft
                                }
                            }
                        },
                        onCostChange = { value ->
                            lineDrafts = lineDrafts.map { draft ->
                                if (draft.materialId == info.draft.materialId) {
                                    draft.copy(unitCostText = value)
                                } else {
                                    draft
                                }
                            }
                        },
                        onRemove = {
                            lineDrafts = lineDrafts.filterNot {
                                it.materialId == info.draft.materialId
                            }
                        },
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.supplies_entry_total),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatCents(totalCents ?: 0L),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                stringResource(R.string.supplies_entry_stock_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showProviderPicker) {
        ProviderPickerDialog(
            providers = providers,
            onPick = { provider ->
                providerId = provider.id
                showProviderPicker = false
            },
            onCreate = onCreateProvider,
            onDismiss = { showProviderPicker = false },
        )
    }

    if (showMaterialPicker) {
        MaterialPickerDialog(
            materials = materials,
            onPick = { row ->
                val existing = lineDrafts.any { it.materialId == row.material.id }
                if (existing) {
                    highlightedMaterialId = row.material.id
                } else {
                    lineDrafts = lineDrafts + EntryLineDraft(
                        materialId = row.material.id,
                        quantityText = "1",
                        unitCostText = row.lastUnitCostCents
                            ?.let(::formatPriceInput)
                            .orEmpty(),
                    )
                }
                showMaterialPicker = false
            },
            onCreate = onCreateMaterial,
            onDismiss = { showMaterialPicker = false },
        )
    }

    if (showDatePicker) {
        SimpleDatePickerDialog(
            initial = purchasedAt,
            latestSelectableMillis = localDayStart(System.currentTimeMillis()),
            onPick = { picked ->
                purchasedAt = picked
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/** A draft line with its resolved material and parsed numbers. */
private data class EntryLineInfo(
    val draft: EntryLineDraft,
    val row: MaterialRow?,
    val quantity: Int?,
    val costCents: Long?,
    val totalCents: Long?,
)

@Composable
private fun EntryLineCard(
    info: EntryLineInfo,
    highlighted: Boolean,
    onQuantityChange: (String) -> Unit,
    onCostChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val material = info.row?.material
    val unit = material?.let { materialUnitLabel(it.unit, it.customUnit) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (highlighted) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        material?.name ?: stringResource(R.string.supplies_entry_material_unavailable),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (material == null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (unit != null) {
                        Text(
                            unit,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.supplies_entry_remove_line),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = info.draft.quantityText,
                    onValueChange = { raw ->
                        onQuantityChange(raw.filter(Char::isDigit).take(9))
                    },
                    label = { Text(stringResource(R.string.supplies_entry_quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = info.quantity == null,
                    enabled = material != null,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = info.draft.unitCostText,
                    onValueChange = { raw ->
                        onCostChange(sanitizeMoneyInput(raw).take(12))
                    },
                    label = { Text(stringResource(R.string.supplies_entry_unit_cost)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = info.costCents == null,
                    enabled = material != null,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.supplies_entry_line_total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    info.totalCents?.let { formatCents(it) } ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
