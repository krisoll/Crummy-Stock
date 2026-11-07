package com.example.stockapplication.ui.screens.supplies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.Material
import com.example.stockapplication.data.MaterialUnit

/**
 * Create / edit form for a material.
 *
 * Stock is edited here as a **correction**, not as a purchase: the helper text under the fields
 * says so, and the entry flow is the only path that records a cost. The repository clamps both
 * numbers at zero, while the form rejects a blank or non-numeric value before saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaterialEditScreen(
    existing: Material?,
    onSave: (Material, (Material?) -> Unit) -> Unit,
    onDelete: (Material) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEdit = existing != null
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var unit by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.unit ?: MaterialUnit.PIECE)
    }
    var customUnit by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.customUnit.orEmpty())
    }
    var stockText by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.stock?.toString() ?: "0")
    }
    var thresholdText by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.lowStockThreshold?.toString() ?: "5")
    }
    var notes by rememberSaveable(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var unitMenuExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val stock = stockText.toIntOrNull()
    val threshold = thresholdText.toIntOrNull()
    val customUnitMissing = unit == MaterialUnit.OTHER && customUnit.isBlank()
    val isValid = name.isNotBlank() &&
        stock != null && stock >= 0 &&
        threshold != null && threshold >= 0 &&
        !customUnitMissing

    fun currentMaterial(): Material {
        val values = Material(
            id = existing?.id ?: 0L,
            name = name,
            unit = unit,
            customUnit = customUnit,
            stock = stock ?: 0,
            lowStockThreshold = threshold ?: 0,
            notes = notes,
        )
        return values
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isEdit) {
                                R.string.supplies_material_edit_title
                            } else {
                                R.string.supplies_material_new
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.supplies_material_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = name.isBlank(),
                supportingText = if (name.isBlank()) {
                    { Text(stringResource(R.string.supplies_material_name_required)) }
                } else {
                    null
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.supplies_material_unit),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    OutlinedButton(
                        onClick = { unitMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            materialUnitLabel(unit, customUnit),
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = unitMenuExpanded,
                        onDismissRequest = { unitMenuExpanded = false },
                    ) {
                        MaterialUnit.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes)) },
                                onClick = {
                                    unit = option
                                    unitMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            if (unit == MaterialUnit.OTHER) {
                OutlinedTextField(
                    value = customUnit,
                    onValueChange = { customUnit = it },
                    label = { Text(stringResource(R.string.supplies_material_custom_unit)) },
                    placeholder = {
                        Text(stringResource(R.string.supplies_material_custom_unit_placeholder))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = customUnitMissing,
                    supportingText = if (customUnitMissing) {
                        { Text(stringResource(R.string.supplies_material_custom_unit_required)) }
                    } else {
                        null
                    },
                )
            }

            OutlinedTextField(
                value = stockText,
                onValueChange = { stockText = it.filter(Char::isDigit).take(9) },
                label = { Text(stringResource(R.string.supplies_material_stock)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                isError = stock == null,
                supportingText = {
                    Text(stringResource(R.string.supplies_material_stock_hint))
                },
            )

            OutlinedTextField(
                value = thresholdText,
                onValueChange = { thresholdText = it.filter(Char::isDigit).take(9) },
                label = { Text(stringResource(R.string.supplies_material_threshold)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                isError = threshold == null,
                supportingText = {
                    Text(stringResource(R.string.supplies_material_threshold_hint))
                },
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.supplies_material_notes)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(R.string.supplies_material_stock_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        saving = true
                        onSave(currentMaterial()) { saved ->
                            saving = false
                            if (saved != null) onBack()
                        }
                    },
                    enabled = isValid && !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }

    if (showDeleteDialog && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.supplies_material_delete_title)) },
            text = {
                Text(stringResource(R.string.supplies_material_delete_body, existing.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(existing)
                        onBack()
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
