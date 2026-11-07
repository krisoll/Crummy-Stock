package com.example.stockapplication.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.Tag

/**
 * Manages the tag vocabulary: create, rename and delete.
 *
 * Deliberately the same shape as [CategoryEditScreen] — same pinned "add" field, same in-place
 * rename, same counted delete confirmation — because from the user's side these are two lists of
 * names they curate the same way. Only the *effect* of a tag differs: deleting one never touches
 * stock, it just removes a label.
 *
 * Reachable only while the tagging feature is switched on (Config → Tags).
 *
 * @param products used purely to count how many products carry each tag, so the delete dialog can
 *   say what is about to be unlabelled.
 */
@Composable
fun TagEditScreen(
    tags: List<Tag>,
    tagIdsByProduct: Map<Long, Set<Long>>,
    onBack: () -> Unit,
    onRename: (Tag, String) -> Unit,
    onDelete: (Tag) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Edited by id rather than index for the same reason as the category screen: the list is
    // alphabetical and a rename re-sorts it, so an index would point at a different row.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Tag?>(null) }

    BackHandler(enabled = editingId != null) {
        editingId = null
        draftName = ""
    }

    // Counted once for the whole list: a tag on 500 products would otherwise re-scan the map 500
    // times, once per row.
    val countsByTag = remember(tagIdsByProduct) {
        val counts = HashMap<Long, Int>()
        tagIdsByProduct.values.forEach { ids ->
            ids.forEach { tagId -> counts[tagId] = (counts[tagId] ?: 0) + 1 }
        }
        counts
    }

    fun commitRename(tag: Tag) {
        val clean = draftName.trim()
        // A blank name is not submitted and an unchanged one is not a rename: either way the row
        // just closes, which beats a message about a no-op.
        if (clean.isEmpty() || clean == tag.name) {
            editingId = null
            draftName = ""
            return
        }
        onRename(tag, clean)
        editingId = null
        draftName = ""
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ---- Header ------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .padding(8.dp)
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.tags_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.tags_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Pinned above the list: this is the field filled in repeatedly when setting a
            // catalogue up, so it should never scroll out of reach.
            NewTagField(
                onCreate = onCreate,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            HorizontalDivider()

            if (tags.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                ) {
                    Icon(
                        Icons.Default.LocalOffer,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        stringResource(R.string.tag_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tags, key = { it.id }) { tag ->
                        TagRow(
                            tag = tag,
                            productCount = countsByTag[tag.id] ?: 0,
                            isEditing = editingId == tag.id,
                            draftName = if (editingId == tag.id) draftName else tag.name,
                            onDraftChange = { draftName = it },
                            onStartEdit = {
                                editingId = tag.id
                                draftName = tag.name
                            },
                            onCancelEdit = {
                                editingId = null
                                draftName = ""
                            },
                            onCommitEdit = { commitRename(tag) },
                            onDelete = { pendingDelete = tag },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.tag_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // The wording says explicitly that the products survive: "delete a tag" reads
                    // as "delete a group of products" to anyone used to categories.
                    Text(
                        stringResource(R.string.tag_delete_body, tag.name),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.tag_product_count, countsByTag[tag.id] ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    // Deleting the row being renamed would leave the editor pointed at a tag that
                    // no longer exists.
                    if (editingId == tag.id) {
                        editingId = null
                        draftName = ""
                    }
                    onDelete(tag)
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * The "add a tag" field, pinned above the list.
 *
 * Submits on IME Done as well as on the button, for the same reason as the category field: setting
 * a catalogue up means typing several in a row, and reaching for a button each time doubles it.
 */
@Composable
private fun NewTagField(
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }

    fun submit() {
        val clean = name.trim()
        if (clean.isEmpty()) return
        onCreate(clean)
        name = ""
    }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.tag_new_label)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            IconButton(onClick = { submit() }, enabled = name.isNotBlank()) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.tag_add)
                )
            }
        }
    )
}

@Composable
private fun TagRow(
    tag: Tag,
    productCount: Int,
    isEditing: Boolean,
    draftName: String,
    onDraftChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onCommitEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        if (isEditing) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.tag_rename_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCommitEdit() })
                )
                IconButton(onClick = onCommitEdit) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.action_save)
                    )
                }
                IconButton(onClick = onCancelEdit) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_cancel)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tag.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.tag_product_count, productCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onStartEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.tag_rename)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.tag_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
