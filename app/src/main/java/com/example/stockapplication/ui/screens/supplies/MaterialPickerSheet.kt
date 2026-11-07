package com.example.stockapplication.ui.screens.supplies

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.stockapplication.R
import com.example.stockapplication.data.Material
import com.example.stockapplication.data.MaterialRow
import com.example.stockapplication.data.MaterialUnit
import com.example.stockapplication.data.formatCents

/**
 * Searchable material picker used by the purchase editor.
 *
 * Rows show the current stock and the most recent paid cost so the quantities and prices typed into
 * the invoice can be checked against what the shop already has. A material can be created inline;
 * the quick form covers name and unit, which are the only fields needed before a first purchase.
 */
@Composable
internal fun MaterialPickerDialog(
    materials: List<MaterialRow>,
    onPick: (MaterialRow) -> Unit,
    onCreate: (Material, (Material?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(MaterialUnit.PIECE) }
    var customUnit by rememberSaveable { mutableStateOf("") }
    var unitMenuExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val customUnitMissing = unit == MaterialUnit.OTHER && customUnit.isBlank()
    val visible = remember(materials, query) {
        if (query.isBlank()) {
            materials
        } else {
            materials.filter { row ->
                row.material.name.contains(query, ignoreCase = true)
            }
        }
    }

    BackHandler(enabled = creating) { creating = false }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            if (creating) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    SuppliesPickerHeader(
                        title = stringResource(R.string.supplies_material_new),
                        onClose = { creating = false },
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
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
                                label = {
                                    Text(stringResource(R.string.supplies_material_custom_unit))
                                },
                                placeholder = {
                                    Text(
                                        stringResource(
                                            R.string.supplies_material_custom_unit_placeholder
                                        )
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                isError = customUnitMissing,
                                supportingText = if (customUnitMissing) {
                                    {
                                        Text(
                                            stringResource(
                                                R.string.supplies_material_custom_unit_required
                                            )
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = { creating = false },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Button(
                            onClick = {
                                saving = true
                                onCreate(
                                    Material(
                                        name = name.trim(),
                                        unit = unit,
                                        customUnit = if (unit == MaterialUnit.OTHER) {
                                            customUnit.trim()
                                        } else {
                                            ""
                                        },
                                    )
                                ) { saved ->
                                    saving = false
                                    if (saved != null) {
                                        onPick(MaterialRow(saved, lastUnitCostCents = null))
                                    }
                                }
                            },
                            enabled = name.isNotBlank() && !customUnitMissing && !saving,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    SuppliesPickerHeader(
                        title = stringResource(R.string.supplies_entry_pick_material_title),
                        onClose = onDismiss,
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(stringResource(R.string.supplies_entry_search_materials))
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))

                    if (visible.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    stringResource(
                                        if (materials.isEmpty()) {
                                            R.string.supplies_entry_no_materials
                                        } else {
                                            R.string.supplies_entry_no_material_matches
                                        }
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    stringResource(R.string.supplies_entry_create_material_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(visible, key = { it.material.id }) { row ->
                                MaterialPickerRow(
                                    row = row,
                                    onPick = { onPick(row) },
                                )
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    TextButton(
                        onClick = { creating = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(
                            stringResource(R.string.supplies_material_new),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialPickerRow(row: MaterialRow, onPick: () -> Unit) {
    val material = row.material
    val unit = materialUnitLabel(material.unit, material.customUnit)

    Surface(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    material.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.supplies_entry_picker_stock, material.stock, unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (row.lastUnitCostCents == null) {
                    stringResource(R.string.supplies_material_no_purchases)
                } else {
                    stringResource(
                        R.string.supplies_material_last_cost,
                        formatCents(row.lastUnitCostCents),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
