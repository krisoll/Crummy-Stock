package com.example.stockapplication.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.Category

/**
 * Chooses one category for a product, with an inline "new category" field.
 *
 * Matching is delegated to the repository (case-insensitive), so creating a duplicate simply
 * selects the existing category rather than creating a second one.
 */
@Composable
fun CategoryPicker(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isAdding by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf("") }

    fun submit() {
        val clean = draftName.trim()
        if (clean.isNotEmpty()) onCreate(clean)
        draftName = ""
        isAdding = false
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.category_label),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { isAdding = !isAdding }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.category_new))
            }
        }

        if (isAdding) {
            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                singleLine = true,
                label = { Text(stringResource(R.string.category_new_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    IconButton(onClick = { submit() }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.category_add)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                    label = { Text(stringResource(R.string.category_none)) }
                )
            }
            items(categories, key = { it.id }) { category ->
                FilterChip(
                    selected = selectedId == category.id,
                    onClick = { onSelect(category.id) },
                    label = { Text(category.name) }
                )
            }
        }
    }
}
