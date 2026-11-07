package com.example.stockapplication.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.Tag

/**
 * The multi-select tag picker shown under the category field in the product form.
 *
 * A wrapping row of [FilterChip]s rather than a dropdown or a list: the set is small (a shop has a
 * handful of tags), and seeing every option at once is what makes "several of these" obvious — a
 * dropdown would imply one choice, which is exactly the distinction between a tag and a category
 * that is easy to get wrong.
 *
 * The caller owns the selection. Tapping a chip reports the id and lets the caller toggle it,
 * because removing a tag is the same gesture as adding one and a separate "clear" affordance would
 * be a second way to do the same thing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChipPicker(
    tags: List<Tag>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(R.string.product_tags_label),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            stringResource(R.string.product_tags_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (tags.isEmpty()) {
            // Said rather than left blank: an empty row would look like a rendering bug, and the
            // fix — create some in the tag manager — is not discoverable from here.
            Text(
                stringResource(R.string.product_tags_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    val selected = tag.id in selectedIds
                    FilterChip(
                        selected = selected,
                        onClick = { onToggle(tag.id) },
                        label = { Text(tag.name) },
                        // The tick is what makes "several selected" readable at a glance; colour
                        // alone would not survive a user-chosen highlight colour.
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
                        }
                    )
                }
            }
        }
    }
}
