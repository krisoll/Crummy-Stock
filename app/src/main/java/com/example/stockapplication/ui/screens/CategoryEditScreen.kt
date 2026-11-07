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
import androidx.compose.material.icons.filled.Sell
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
import com.example.stockapplication.data.Category
import com.example.stockapplication.data.Product

/**
 * The category manager: a full-screen panel listing every category, with rename, delete and add.
 *
 * It replaces nothing — before this existed the only way to make a category was the inline field
 * in the product editor, and there was no way at all to rename or delete one without clearing the
 * whole setup. So the rules that used to be implicit get said out loud here:
 *
 * - A category is renamed **in place**, so the products that point at it follow along. Nothing is
 *   re-assigned, and a rename can never silently detach stock.
 * - Deleting a category keeps its products. They become uncategorised rather than being removed,
 *   which is what [com.example.stockapplication.data.StockDao.deleteCategory] guarantees; the
 *   confirmation dialog says so before the fact, because the alternative (losing stock records to a
 *   tidying-up gesture) is unrecoverable.
 *
 * Editing happens one row at a time rather than in a second dialog stack: the row swaps its label
 * for a text field, so the list stays visible and the user can see the name they are changing
 * against its neighbours — which is exactly what they need to tell two similar names apart.
 *
 * @param products used only to show how many products each category holds, so the consequence of a
 *   delete is visible before it is confirmed.
 */
@Composable
fun CategoryEditScreen(
    categories: List<Category>,
    products: List<Product>,
    onBack: () -> Unit,
    onRename: (Category, String) -> Unit,
    onDelete: (Category) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The row currently being renamed, by id rather than index: the list is sorted alphabetically
    // and a rename re-sorts it, so an index would point at a different row the moment the name
    // changes.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }

    // A rename in flight is the only unsaved state here, and it is cheap to discard, so Back
    // cancels the edit before it leaves the page rather than dropping the typing silently.
    BackHandler(enabled = editingId != null) {
        editingId = null
        draftName = ""
    }

    // Counted once for the whole list instead of per row, so a category with 500 products does not
    // re-scan the product list 500 times.
    val countsByCategory = remember(products) {
        products.groupingBy { it.categoryId }.eachCount()
    }

    fun commitRename(category: Category) {
        val clean = draftName.trim()
        // A blank name is not submitted, and an unchanged one is not a rename: either way the row
        // just closes, which is less jarring than a message about a no-op.
        if (clean.isEmpty() || clean == category.name) {
            editingId = null
            draftName = ""
            return
        }
        onRename(category, clean)
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
                        stringResource(R.string.category_edit_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.category_edit_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Pinned above the list: this is the field people fill in repeatedly when setting a
            // catalogue up, so it should never scroll out of reach.
            NewCategoryField(
                onCreate = onCreate,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            HorizontalDivider()

            if (categories.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                ) {
                    Icon(
                        Icons.Default.Sell,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        stringResource(R.string.category_edit_empty),
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
                    items(categories, key = { it.id }) { category ->
                        CategoryRow(
                            category = category,
                            productCount = countsByCategory[category.id] ?: 0,
                            isEditing = editingId == category.id,
                            draftName = if (editingId == category.id) draftName else category.name,
                            onDraftChange = { draftName = it },
                            onStartEdit = {
                                editingId = category.id
                                draftName = category.name
                            },
                            onCancelEdit = {
                                editingId = null
                                draftName = ""
                            },
                            onCommitEdit = { commitRename(category) },
                            onDelete = { pendingDelete = category },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.category_delete)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Names the category, because the dialog is the last point at which the user
                    // can still notice they picked the wrong row.
                    Text(
                        category.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(stringResource(R.string.category_delete_body))
                    Text(
                        stringResource(
                            R.string.category_product_count,
                            countsByCategory[category.id] ?: 0
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    // Deleting the row being renamed would leave the editor pointed at a category
                    // that no longer exists.
                    if (editingId == category.id) {
                        editingId = null
                        draftName = ""
                    }
                    onDelete(category)
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
 * The "add a category" field, pinned above the list so it is reachable without scrolling.
 *
 * Submits on IME Done as well as on the button: this is a field people fill in repeatedly when
 * setting a catalogue up, and reaching for a button after each one doubles the work.
 */
@Composable
private fun NewCategoryField(
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
        label = { Text(stringResource(R.string.category_new_hint)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            IconButton(onClick = { submit() }, enabled = name.isNotBlank()) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.category_add)
                )
            }
        }
    )
}

@Composable
private fun CategoryRow(
    category: Category,
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
                    label = { Text(stringResource(R.string.category_rename_label)) },
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
                        category.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.category_product_count, productCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onStartEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.category_rename)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.category_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
