package com.example.stockapplication.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stockapplication.R
import com.example.stockapplication.StockApplication
import com.example.stockapplication.data.DeleteEntryResult
import com.example.stockapplication.data.Material
import com.example.stockapplication.data.MaterialEntry
import com.example.stockapplication.data.MaterialEntryWithLines
import com.example.stockapplication.data.MaterialRow
import com.example.stockapplication.data.NewMaterialEntry
import com.example.stockapplication.data.NewMaterialLine
import com.example.stockapplication.data.Provider
import com.example.stockapplication.data.ProviderSummary
import com.example.stockapplication.data.RecordEntryResult
import com.example.stockapplication.data.SaveMaterialResult
import com.example.stockapplication.data.SaveProviderResult
import com.example.stockapplication.data.SuppliesRepository
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One-off supplies messages, modelled as resources so the snackbar is translated at the moment it
 * is shown rather than when the write finished.
 */
sealed interface SuppliesMessage {
    data class Res(@param:StringRes val resId: Int, val args: List<Any> = emptyList()) :
        SuppliesMessage {
        constructor(resId: Int, vararg args: Any) : this(resId, args.toList())
    }
}

/** Resolves this message against the current locale. */
fun SuppliesMessage.toText(context: Context): String = when (this) {
    is SuppliesMessage.Res -> context.getString(resId, *args.toTypedArray())
}

/**
 * Everything the supplies destination renders.
 *
 * The catalogues are small (a shop has tens of suppliers and materials, not thousands), so the
 * search and stock filters live in the screen and this object only carries the source lists and
 * the derived summary numbers both tabs display.
 */
data class SuppliesUiState(
    val providers: List<ProviderSummary> = emptyList(),
    val materials: List<MaterialRow> = emptyList(),
    val entries: List<MaterialEntryWithLines> = emptyList(),
    val isLoading: Boolean = true,
) {
    /**
     * Materials in the positive-but-low band, matching the Low filter chip. Out-of-stock rows are
     * reported by their own badge and the Out filter, so folding them in here would make the
     * summary tile disagree with the list it opens.
     */
    val lowStockCount: Int
        get() = materials.count { it.material.isLowStock }

    /**
     * Stock valued at the most recent purchase price. Materials without a purchase contribute
     * zero rather than being valued at a guessed price.
     */
    val inventoryValueCents: Long
        get() = materials.sumOf { row ->
            row.material.stock.toLong() * (row.lastUnitCostCents ?: 0L)
        }

    /**
     * Spend in the calendar month containing now, using the device's local month boundaries.
     *
     * The Entries summary is a read-only aggregate, so it is derived here instead of stored. That
     * keeps a backdated purchase and a restored backup both correct without a migration.
     */
    val currentMonthSpendCents: Long
        get() {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            return entries.sumOf { row ->
                calendar.timeInMillis = row.entry.purchasedAt
                if (calendar.get(Calendar.YEAR) == year && calendar.get(Calendar.MONTH) == month) {
                    row.entry.totalCents
                } else {
                    0L
                }
            }
        }
}

class SuppliesViewModel(private val repository: SuppliesRepository) : ViewModel() {

    private val _messages = MutableStateFlow<SuppliesMessage?>(null)
    val messages: StateFlow<SuppliesMessage?> = _messages.asStateFlow()

    val uiState: StateFlow<SuppliesUiState> = combine(
        repository.observeProviders(),
        repository.observeMaterials(),
        repository.observeMaterialEntriesWithLines(),
    ) { providers, materials, entries ->
        SuppliesUiState(
            providers = providers,
            materials = materials,
            entries = entries,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SuppliesUiState(),
    )

    fun clearMessage() {
        _messages.value = null
    }

    // ---- Providers ---------------------------------------------------------

    /**
     * Creates or updates a provider.
     *
     * [onFinished] receives the saved provider, or null when the write was rejected, so a form can
     * stay open on a duplicate name and a picker can select a newly created row immediately.
     */
    fun saveProvider(provider: Provider, onFinished: (Provider?) -> Unit = {}) {
        viewModelScope.launch {
            val attempt = runCatching { repository.saveProvider(provider) }
            val result = attempt.getOrNull()
            if (attempt.isFailure) {
                _messages.value = SuppliesMessage.Res(R.string.message_could_not_save)
            } else {
                _messages.value = when (result) {
                    is SaveProviderResult.Success ->
                        SuppliesMessage.Res(R.string.supplies_provider_saved, result.provider.name)

                    SaveProviderResult.EmptyName ->
                        SuppliesMessage.Res(R.string.supplies_provider_name_required)

                    is SaveProviderResult.DuplicateName ->
                        SuppliesMessage.Res(R.string.supplies_provider_duplicate, result.name)

                    SaveProviderResult.NotFound ->
                        SuppliesMessage.Res(R.string.message_could_not_save)

                    null -> SuppliesMessage.Res(R.string.message_could_not_save)
                }
            }
            onFinished((result as? SaveProviderResult.Success)?.provider)
        }
    }

    fun deleteProvider(provider: Provider) {
        viewModelScope.launch {
            runCatching { repository.deleteProvider(provider.id) }
                .onSuccess {
                    _messages.value =
                        SuppliesMessage.Res(R.string.supplies_provider_deleted, provider.name)
                }
                .onFailure {
                    _messages.value = SuppliesMessage.Res(R.string.message_could_not_delete)
                }
        }
    }

    // ---- Materials ---------------------------------------------------------

    /** Creates or updates a material; see [saveProvider] for the completion callback contract. */
    fun saveMaterial(material: Material, onFinished: (Material?) -> Unit = {}) {
        viewModelScope.launch {
            val attempt = runCatching { repository.saveMaterial(material) }
            val result = attempt.getOrNull()
            if (attempt.isFailure) {
                _messages.value = SuppliesMessage.Res(R.string.message_could_not_save)
            } else {
                _messages.value = when (result) {
                    is SaveMaterialResult.Success ->
                        SuppliesMessage.Res(R.string.supplies_material_saved, result.material.name)

                    SaveMaterialResult.EmptyName ->
                        SuppliesMessage.Res(R.string.supplies_material_name_required)

                    SaveMaterialResult.CustomUnitRequired ->
                        SuppliesMessage.Res(R.string.supplies_material_custom_unit_required)

                    is SaveMaterialResult.DuplicateName ->
                        SuppliesMessage.Res(R.string.supplies_material_duplicate, result.name)

                    SaveMaterialResult.NotFound ->
                        SuppliesMessage.Res(R.string.message_could_not_save)

                    null -> SuppliesMessage.Res(R.string.message_could_not_save)
                }
            }
            onFinished((result as? SaveMaterialResult.Success)?.material)
        }
    }

    fun deleteMaterial(material: Material) {
        viewModelScope.launch {
            runCatching { repository.deleteMaterial(material.id) }
                .onSuccess {
                    _messages.value =
                        SuppliesMessage.Res(R.string.supplies_material_deleted, material.name)
                }
                .onFailure {
                    _messages.value = SuppliesMessage.Res(R.string.message_could_not_delete)
                }
        }
    }

    /** Applies a manual stock correction. Purchases must use an entry so they carry a cost. */
    fun adjustStock(material: Material, newStock: Int) {
        viewModelScope.launch {
            runCatching { repository.adjustStock(material.id, newStock) }
                .onSuccess { updated ->
                    _messages.value = if (updated == null) {
                        SuppliesMessage.Res(R.string.message_could_not_save)
                    } else {
                        SuppliesMessage.Res(
                            R.string.supplies_material_stock_updated,
                            updated.name,
                        )
                    }
                }
                .onFailure {
                    _messages.value = SuppliesMessage.Res(R.string.message_could_not_save)
                }
        }
    }

    // ---- Entries -----------------------------------------------------------

    /**
     * Records a purchase and increases the stock of every material on it, all-or-nothing.
     *
     * The repository validates the lines again, so a provider or material deleted between opening
     * the editor and pressing save is reported rather than half-written. [onFinished] reports
     * whether the editor may close; on a rejected write the draft stays on screen.
     */
    fun recordEntry(
        draft: NewMaterialEntry,
        onFinished: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val attempt = runCatching {
                repository.recordEntry(
                    providerId = draft.providerId,
                    purchasedAt = draft.purchasedAt,
                    reference = draft.reference,
                    note = draft.note,
                    lines = draft.lines,
                )
            }
            val result = attempt.getOrNull()
            _messages.value = when (result) {
                is RecordEntryResult.Success ->
                    SuppliesMessage.Res(R.string.supplies_entry_saved)

                RecordEntryResult.Empty ->
                    SuppliesMessage.Res(R.string.supplies_entry_empty)

                RecordEntryResult.ProviderNotFound ->
                    SuppliesMessage.Res(R.string.supplies_entry_provider_missing)

                is RecordEntryResult.MaterialNotFound ->
                    SuppliesMessage.Res(R.string.supplies_entry_material_missing)

                is RecordEntryResult.DuplicateMaterial ->
                    SuppliesMessage.Res(R.string.supplies_entry_duplicate_material, result.name)

                is RecordEntryResult.InvalidLine ->
                    SuppliesMessage.Res(R.string.supplies_entry_invalid_line, result.materialName)

                RecordEntryResult.TotalTooLarge ->
                    SuppliesMessage.Res(R.string.supplies_entry_total_too_large)

                is RecordEntryResult.StockTooLarge ->
                    SuppliesMessage.Res(R.string.supplies_entry_stock_too_large, result.materialName)

                null -> SuppliesMessage.Res(R.string.message_could_not_save)
            }
            onFinished(result is RecordEntryResult.Success)
        }
    }

    /**
     * Deletes a purchase and reverses its stock.
     *
     * A purchase whose stock has already been consumed is blocked by the DAO; the message names
     * the material and how much is still available, and [onFinished] stays false so the detail
     * screen remains open.
     */
    fun deleteEntry(entry: MaterialEntry, onFinished: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val attempt = runCatching { repository.deleteEntry(entry.entryId) }
            val result = attempt.getOrNull()
            _messages.value = when (result) {
                is DeleteEntryResult.Success ->
                    SuppliesMessage.Res(R.string.supplies_entry_deleted)

                DeleteEntryResult.NotFound ->
                    SuppliesMessage.Res(R.string.message_could_not_delete)

                is DeleteEntryResult.InsufficientStock ->
                    SuppliesMessage.Res(
                        R.string.supplies_entry_delete_blocked,
                        result.materialName,
                        result.available,
                        result.requested,
                    )

                null -> SuppliesMessage.Res(R.string.message_could_not_delete)
            }
            onFinished(result is DeleteEntryResult.Success)
        }
    }

    companion object {
        /** Builds the supplies ViewModel with the repository from [StockApplication]. */
        fun factory(app: StockApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer { SuppliesViewModel(app.suppliesRepository) }
        }
    }
}
