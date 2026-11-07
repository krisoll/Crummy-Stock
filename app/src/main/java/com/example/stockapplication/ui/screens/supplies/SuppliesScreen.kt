package com.example.stockapplication.ui.screens.supplies

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.stockapplication.R
import com.example.stockapplication.data.Material
import com.example.stockapplication.data.MaterialEntry
import com.example.stockapplication.data.NewMaterialEntry
import com.example.stockapplication.data.Provider
import com.example.stockapplication.ui.theme.AppMotion
import com.example.stockapplication.ui.viewmodel.SuppliesUiState

/** The three sections of the Supplies destination. */
private enum class SuppliesTab(
    val labelRes: Int,
    val fabLabelRes: Int,
    val fabIcon: ImageVector,
) {
    PROVIDERS(
        labelRes = R.string.supplies_tab_providers,
        fabLabelRes = R.string.supplies_provider_new,
        fabIcon = Icons.Default.Add,
    ),
    MATERIALS(
        labelRes = R.string.supplies_tab_materials,
        fabLabelRes = R.string.supplies_material_new,
        fabIcon = Icons.Default.Inventory2,
    ),
    ENTRIES(
        labelRes = R.string.supplies_tab_entries,
        fabLabelRes = R.string.supplies_entry_new,
        fabIcon = Icons.AutoMirrored.Filled.ReceiptLong,
    ),
}

/**
 * The Supplies destination: providers, the separate materials inventory, and purchase entries.
 *
 * The editors are overlays inside this destination rather than root-level screens, so the tab that
 * opened one keeps its search and filter state underneath.
 */
@Composable
fun SuppliesScreen(
    state: SuppliesUiState,
    onSaveProvider: (Provider, (Provider?) -> Unit) -> Unit,    onDeleteProvider: (Provider) -> Unit,
    onSaveMaterial: (Material, (Material?) -> Unit) -> Unit,
    onDeleteMaterial: (Material) -> Unit,
    onAdjustStock: (Material, Int) -> Unit,
    onRecordEntry: (NewMaterialEntry, (Boolean) -> Unit) -> Unit,
    onDeleteEntry: (MaterialEntry, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The app is free and open source, so nothing gates a write any more. Kept as a local with the
    // same name the call sites below already use: removing the parameter rather than rewriting
    // fifteen `if (gateReadOnly())` blocks keeps this change reviewable, and leaves the structure
    // intact if a gate is ever wanted again.
    val gateReadOnly: () -> Boolean = { true }

    var tab by rememberSaveable { mutableStateOf(SuppliesTab.PROVIDERS) }
    var creatingProvider by rememberSaveable { mutableStateOf(false) }
    var editingProviderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creatingMaterial by rememberSaveable { mutableStateOf(false) }
    var editingMaterialId by rememberSaveable { mutableStateOf<Long?>(null) }
    var adjustingMaterialId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creatingEntry by rememberSaveable { mutableStateOf(false) }
    var detailEntryId by rememberSaveable { mutableStateOf<String?>(null) }

    val editingProvider = editingProviderId?.let { id ->
        state.providers.firstOrNull { it.id == id }?.asProvider()
    }
    val editingMaterial = editingMaterialId?.let { id ->
        state.materials.firstOrNull { it.material.id == id }?.material
    }
    val adjustingMaterial = adjustingMaterialId?.let { id ->
        state.materials.firstOrNull { it.material.id == id }?.material
    }
    val detailEntry = detailEntryId?.let { id ->
        state.entries.firstOrNull { it.entry.entryId == id }
    }

    val providerEditorOpen = creatingProvider || editingProvider != null
    val materialEditorOpen = creatingMaterial || editingMaterial != null

    // A provider, material or entry can disappear underneath an open screen after a backup restore
    // or an entry deletion. Once the first load has landed, a missing row means the screen has
    // nothing to show, so drop the id rather than leaving a stale form open.
    LaunchedEffect(state.isLoading, editingProviderId, state.providers) {
        if (!state.isLoading && editingProviderId != null && editingProvider == null) {
            editingProviderId = null
        }
    }
    LaunchedEffect(state.isLoading, editingMaterialId, state.materials) {
        if (!state.isLoading && editingMaterialId != null && editingMaterial == null) {
            editingMaterialId = null
        }
    }
    LaunchedEffect(state.isLoading, adjustingMaterialId, state.materials) {
        if (!state.isLoading && adjustingMaterialId != null && adjustingMaterial == null) {
            adjustingMaterialId = null
        }
    }
    LaunchedEffect(state.isLoading, detailEntryId, state.entries) {
        if (!state.isLoading && detailEntryId != null && detailEntry == null) {
            detailEntryId = null
        }
    }

    fun closeProviderEditor() {
        creatingProvider = false
        editingProviderId = null
    }

    fun closeMaterialEditor() {
        creatingMaterial = false
        editingMaterialId = null
    }

    fun closeEntryEditor() {
        creatingEntry = false
    }

    // Declared from the bottom of the stack upwards: the last registered handler wins, so a picker
    // or form sees Back before the content underneath it. The entry editor and detail are mutually
    // exclusive because the full-screen overlay hides the tab bar.
    BackHandler(enabled = providerEditorOpen) { closeProviderEditor() }
    BackHandler(enabled = materialEditorOpen) { closeMaterialEditor() }
    BackHandler(enabled = adjustingMaterial != null) { adjustingMaterialId = null }
    BackHandler(enabled = creatingEntry) { closeEntryEditor() }
    BackHandler(enabled = detailEntry != null) { detailEntryId = null }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            // The root Scaffold already consumed the system bars; a nested one must not pad for
            // them again. The tab row handles the status bar itself.
            contentWindowInsets = WindowInsets(0),
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        when (tab) {
                            SuppliesTab.PROVIDERS -> {
                                if (gateReadOnly()) {
                                    closeProviderEditor()
                                    creatingProvider = true
                                }
                            }

                            SuppliesTab.MATERIALS -> {
                                if (gateReadOnly()) {
                                    closeMaterialEditor()
                                    creatingMaterial = true
                                }
                            }

                            SuppliesTab.ENTRIES -> {
                                if (gateReadOnly()) {
                                    closeEntryEditor()
                                    detailEntryId = null
                                    creatingEntry = true
                                }
                            }
                        }
                    },
                    icon = { Icon(tab.fabIcon, contentDescription = null) },
                    text = { Text(stringResource(tab.fabLabelRes)) },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                PrimaryTabRow(
                    selectedTabIndex = tab.ordinal,
                    modifier = Modifier.statusBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    SuppliesTab.entries.forEach { option ->
                        Tab(
                            selected = option == tab,
                            onClick = { tab = option },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text(stringResource(option.labelRes)) },
                        )
                    }
                }

                when (tab) {
                    SuppliesTab.PROVIDERS -> ProvidersTab(
                        providers = state.providers,
                        isLoading = state.isLoading,
                        onEdit = { provider ->
                            if (gateReadOnly()) {
                                creatingProvider = false
                                editingProviderId = provider.id
                            }
                        },
                        onDelete = { provider ->
                            if (gateReadOnly()) onDeleteProvider(provider)
                        },
                    )

                    SuppliesTab.MATERIALS -> MaterialsTab(
                        materials = state.materials,
                        isLoading = state.isLoading,
                        lowStockCount = state.lowStockCount,
                        inventoryValueCents = state.inventoryValueCents,
                        onEdit = { material ->
                            if (gateReadOnly()) {
                                creatingMaterial = false
                                editingMaterialId = material.id
                            }
                        },
                        onAdjustStock = { material ->
                            if (gateReadOnly()) adjustingMaterialId = material.id
                        },
                        onDelete = { material ->
                            if (gateReadOnly()) onDeleteMaterial(material)
                        },
                    )

                    SuppliesTab.ENTRIES -> EntriesTab(
                        entries = state.entries,
                        providers = state.providers,
                        currentMonthSpendCents = state.currentMonthSpendCents,
                        isLoading = state.isLoading,
                        onOpenEntry = { row -> detailEntryId = row.entry.entryId },
                    )
                }
            }
        }

        val editorEnterMillis = AppMotion.screenEnterMillis()
        val editorEnter = tween<Float>(editorEnterMillis)
        val editorSlide = tween<IntOffset>(editorEnterMillis, easing = AppMotion.Emphasised)
        val editorExit = tween<Float>(AppMotion.screenExitMillis())

        AnimatedContent(
            targetState = providerEditorOpen,
            transitionSpec = {
                (fadeIn(animationSpec = editorEnter) +
                    slideInVertically(
                        animationSpec = editorSlide,
                        initialOffsetY = { it },
                    )) togetherWith fadeOut(animationSpec = editorExit)
            },
            modifier = if (providerEditorOpen) Modifier.fillMaxSize() else Modifier.size(0.dp),
            label = "provider-editor",
        ) { open ->
            if (open) {
                ProviderEditScreen(
                    existing = editingProvider,
                    onSave = { provider, onFinished ->
                        if (gateReadOnly()) {
                            onSaveProvider(provider, onFinished)
                        } else {
                            onFinished(null)
                        }
                    },
                    onDelete = { provider ->
                        if (gateReadOnly()) onDeleteProvider(provider)
                    },
                    onBack = ::closeProviderEditor,
                )
            }
        }

        AnimatedContent(
            targetState = materialEditorOpen,
            transitionSpec = {
                (fadeIn(animationSpec = editorEnter) +
                    slideInVertically(
                        animationSpec = editorSlide,
                        initialOffsetY = { it },
                    )) togetherWith fadeOut(animationSpec = editorExit)
            },
            modifier = if (materialEditorOpen) Modifier.fillMaxSize() else Modifier.size(0.dp),
            label = "material-editor",
        ) { open ->
            if (open) {
                MaterialEditScreen(
                    existing = editingMaterial,
                    onSave = { material, onFinished ->
                        if (gateReadOnly()) {
                            onSaveMaterial(material, onFinished)
                        } else {
                            onFinished(null)
                        }
                    },
                    onDelete = { material ->
                        if (gateReadOnly()) onDeleteMaterial(material)
                    },
                    onBack = ::closeMaterialEditor,
                )
            }
        }

        AnimatedContent(
            targetState = creatingEntry,
            transitionSpec = {
                (fadeIn(animationSpec = editorEnter) +
                    slideInVertically(
                        animationSpec = editorSlide,
                        initialOffsetY = { it },
                    )) togetherWith fadeOut(animationSpec = editorExit)
            },
            modifier = if (creatingEntry) Modifier.fillMaxSize() else Modifier.size(0.dp),
            label = "entry-editor",
        ) { open ->
            if (open) {
                EntryEditorScreen(
                    providers = state.providers,
                    materials = state.materials,
                    onSave = { draft, onFinished ->
                        if (gateReadOnly()) {
                            onRecordEntry(draft, onFinished)
                        } else {
                            onFinished(false)
                        }
                    },
                    onCreateProvider = { provider, onSaved ->
                        if (gateReadOnly()) {
                            onSaveProvider(provider, onSaved)
                        } else {
                            onSaved(null)
                        }
                    },
                    onCreateMaterial = { material, onSaved ->
                        if (gateReadOnly()) {
                            onSaveMaterial(material, onSaved)
                        } else {
                            onSaved(null)
                        }
                    },
                    onBack = ::closeEntryEditor,
                )
            }
        }

        AnimatedContent(
            targetState = detailEntry,
            transitionSpec = {
                (fadeIn(animationSpec = editorEnter) +
                    slideInVertically(
                        animationSpec = editorSlide,
                        initialOffsetY = { it },
                    )) togetherWith fadeOut(animationSpec = editorExit)
            },
            modifier = if (detailEntry != null) Modifier.fillMaxSize() else Modifier.size(0.dp),
            label = "entry-detail",
        ) { entry ->
            if (entry != null) {
                EntryDetailScreen(
                    entry = entry,
                    onDelete = { materialEntry, onFinished ->
                        if (gateReadOnly()) {
                            onDeleteEntry(materialEntry, onFinished)
                        } else {
                            onFinished(false)
                        }
                    },
                    onBack = { detailEntryId = null },
                )
            }
        }
    }

    adjustingMaterial?.let { material ->
        AdjustStockDialog(
            material = material,
            onConfirm = { newStock ->
                if (gateReadOnly()) {
                    onAdjustStock(material, newStock)
                    adjustingMaterialId = null
                }
            },
            onDismiss = { adjustingMaterialId = null },
        )
    }
}

/** Manual stock correction. The copy makes clear this is not how a purchase is recorded. */
@Composable
private fun AdjustStockDialog(
    material: Material,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var stockText by remember(material.id) { mutableStateOf(material.stock.toString()) }
    val newStock = stockText.toIntOrNull()
    val unit = materialUnitLabel(material.unit, material.customUnit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.supplies_material_adjust_title)) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        R.string.supplies_material_adjust_body,
                        material.stock,
                        unit,
                    )
                )
                OutlinedTextField(
                    value = stockText,
                    onValueChange = { stockText = it.filter(Char::isDigit).take(9) },
                    label = { Text(stringResource(R.string.supplies_material_new_stock)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = newStock == null,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { newStock?.let(onConfirm) },
                enabled = newStock != null,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
