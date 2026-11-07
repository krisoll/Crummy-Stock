package com.example.stockapplication.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stockapplication.R
import com.example.stockapplication.StockApplication
import com.example.stockapplication.data.CancelResult
import com.example.stockapplication.data.Category
import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.BackupContents
import com.example.stockapplication.data.BackupArchive
import com.example.stockapplication.data.DatabaseSnapshot
import com.example.stockapplication.data.toJson
import com.example.stockapplication.data.DayOrders
import com.example.stockapplication.data.DaySales
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.ProductImages
import com.example.stockapplication.data.Promo
import com.example.stockapplication.data.Sale
import com.example.stockapplication.data.SaleOrder
import com.example.stockapplication.data.SaleResult
import com.example.stockapplication.data.RenameCategoryResult
import com.example.stockapplication.data.StockRepository
import com.example.stockapplication.data.Tag
import com.example.stockapplication.data.ProductGroup
import com.example.stockapplication.data.groupOrdersByDay
import com.example.stockapplication.data.groupProductsByCategory
import com.example.stockapplication.data.groupSalesByDay
import com.example.stockapplication.data.matchesAnyTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One-off messages shown as a snackbar.
 *
 * These are modelled as data rather than pre-formatted strings so that localisation happens at the
 * very last moment, against the user's current locale (see [toText]).
 */
sealed interface InventoryMessage {
    data class StockAdded(val name: String, val quantity: Int) : InventoryMessage
    data class StockRestocked(val name: String, val quantity: Int) : InventoryMessage
    data class Sold(val name: String, val remaining: Int) : InventoryMessage
    data class OutOfStock(val name: String) : InventoryMessage
    data class NotFound(val barcode: String) : InventoryMessage
    data class SaleCancelled(val name: String, val quantity: Int) : InventoryMessage

    /** A user-facing message that is already resolved to text (e.g. one produced by the repository). */
    data class SaleCancelFailed(val reason: String) : InventoryMessage
    data class Info(val text: String) : InventoryMessage
    data class Error(val text: String) : InventoryMessage

    /** Built from string resources at the point the ViewModel wants to show it. */
    data class Res(@param:StringRes val resId: Int, val args: List<Any> = emptyList()) :
        InventoryMessage {
        constructor(resId: Int, vararg args: Any) : this(resId, args.toList())
    }
}

/** Brings `@StringRes` into scope without pulling a Compose-only dependency into this file. */
private typealias StringRes = androidx.annotation.StringRes

/** Resolves this message against the current locale. */
fun InventoryMessage.toText(context: Context): String = when (this) {
    is InventoryMessage.StockAdded ->
        context.getString(R.string.message_stock_added, quantity, name)
    is InventoryMessage.StockRestocked ->
        context.getString(R.string.message_stock_restocked, quantity, name)
    is InventoryMessage.Sold ->
        context.getString(R.string.message_sold_one, name, remaining)
    is InventoryMessage.OutOfStock -> context.getString(R.string.message_out_of_stock, name)
    is InventoryMessage.NotFound -> context.getString(R.string.message_barcode_not_found, barcode)
    is InventoryMessage.SaleCancelled ->
        context.getString(R.string.message_sale_cancelled, quantity, name)
    is InventoryMessage.SaleCancelFailed -> reason
    is InventoryMessage.Info -> text
    is InventoryMessage.Error -> text
    is InventoryMessage.Res -> context.getString(resId, *args.toTypedArray())
}

enum class InventoryFilter { ALL, LOW, OUT }

data class InventoryUiState(
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filter: InventoryFilter = InventoryFilter.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    /**
     * Tag ids the list is filtered to, OR'd together. Empty means "any tag".
     *
     * Part of the UI state rather than the ViewModel's own flows because it is a *view* of the
     * same list as [filter] and [searchQuery] — all three narrow one result set, and keeping them
     * together means [visibleProducts] stays a pure function of this object.
     */
    val selectedTagIds: Set<Long> = emptySet(),
    /** productId → its tag ids, so filtering never needs a lookup per row. */
    val tagIdsByProduct: Map<Long, Set<Long>> = emptyMap(),
) {

    /**
     * The visible products grouped under their categories, ready for the sectioned list.
     *
     * Grouping happens after filtering, so the section headings describe exactly what is on screen
     * rather than the whole catalogue.
     */
    val groupedProducts: List<ProductGroup>
        get() = groupProductsByCategory(visibleProducts, categories)

    val visibleProducts: List<Product>
        get() = products.filter { product ->
            val matchesFilter = when (filter) {
                InventoryFilter.ALL -> true
                InventoryFilter.LOW -> product.isLowStock
                InventoryFilter.OUT -> product.isOutOfStock
            }
            val matchesQuery = searchQuery.isBlank() ||
                product.name.contains(searchQuery, ignoreCase = true) ||
                product.barcode.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery &&
                matchesAnyTag(product, selectedTagIds, tagIdsByProduct)
        }

    val totalUnits: Int get() = products.sumOf { it.quantity }
    val totalValueCents: Long get() = products.sumOf { it.quantity * it.priceCents }
    val lowStockCount: Int get() = products.count { it.isLowStock || it.isOutOfStock }
}

class InventoryViewModel(private val repository: StockRepository) : ViewModel() {

    private val _filter = MutableStateFlow(InventoryFilter.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(true)

    private val _messages = MutableStateFlow<InventoryMessage?>(null)
    val messages: StateFlow<InventoryMessage?> = _messages.asStateFlow()

    private val _pendingScan = MutableStateFlow<String?>(null)

    /** Set when a scanned barcode is not in the catalog and the UI should offer to add it. */
    val pendingScan: StateFlow<String?> = _pendingScan.asStateFlow()

    private val _selectedTagIds = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * The tag chips currently narrowing the list. Held here rather than in `InventoryScreen` so it
     * survives a tab switch, and so the filter runs through the same state the list renders from.
     */
    val selectedTagIds: StateFlow<Set<Long>> = _selectedTagIds.asStateFlow()

    /** Adds or removes one tag from the filter. See [matchesAnyTag] for the OR semantics. */
    fun toggleTagFilter(tagId: Long) {
        _selectedTagIds.value = _selectedTagIds.value.let { current ->
            if (tagId in current) current - tagId else current + tagId
        }
    }

    /**
     * productId → tagIds, so a list of products can be rendered without a query per row.
     *
     * Grouped once here rather than per row: the inventory can hold thousands of products, and
     * rebuilding this map inside a row would make scrolling quadratic.
     *
     * Declared above [uiState] because the filter consumes it — a property initialised later in
     * source order would still be unset when the `combine` block is built.
     */
    val tagIdsByProduct: StateFlow<Map<Long, Set<Long>>> = repository.productTags
        .map { assignments ->
            assignments.groupBy({ it.productId }, { it.tagId })
                .mapValues { (_, ids) -> ids.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val uiState: StateFlow<InventoryUiState> = combine(
        repository.observeProducts(),
        repository.observeCategories(),
        _filter,
        _searchQuery,
        _isLoading,
        // `kotlinx.coroutines` ships typed `combine` overloads only up to five flows, so the sixth
        // forces the vararg form and the explicit casts below. The casts are safe because the
        // order of the arguments is fixed right here and every source is a typed flow.
        combine(_selectedTagIds, tagIdsByProduct) { selected, byProduct -> selected to byProduct },
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        InventoryUiState(
            products = values[0] as List<Product>,
            categories = values[1] as List<Category>,
            filter = values[2] as InventoryFilter,
            searchQuery = values[3] as String,
            isLoading = values[4] as Boolean,
            selectedTagIds = (values[5] as Pair<Set<Long>, Map<Long, Set<Long>>>).first,
            tagIdsByProduct = (values[5] as Pair<Set<Long>, Map<Long, Set<Long>>>).second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryUiState()
    )

    val sales: StateFlow<List<Sale>> = repository.observeSales().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    /** Sales bucketed into local calendar days, newest day first. */
    val salesByDay: StateFlow<List<DaySales>> = repository.observeSales().map(::groupSalesByDay)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Completed checkouts, paired with their line items and bucketed by day.
     *
     * The history screen lists these rather than raw sale rows so one cart is one entry.
     */
    /** User-defined product categories, alphabetical. */
    val categories: StateFlow<List<Category>> = repository.observeCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    /**
     * Every promo, including disabled and expired ones.
     *
     * The Promos tab is a management screen, so it shows the whole set; only the cart filters by
     * whether a promo is active, and it does so through [PromoEngine].
     */
    val promos: StateFlow<List<Promo>> = repository.observePromos().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val ordersByDay: StateFlow<List<DayOrders>> =
        combine(
            repository.observeOrders(),
            repository.observeOrderLines(),
        ) { orders, lines -> groupOrdersByDay(orders, lines) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    init {
        viewModelScope.launch {
            repository.observeProducts().collect { _isLoading.value = false }
        }
    }

    fun setFilter(filter: InventoryFilter) { _filter.value = filter }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun clearMessage() { _messages.value = null }

    // ---- Promos -----------------------------------------------------------

    /** Creates or updates a promo and reports it with a snackbar. */
    fun savePromo(promo: Promo) {
        viewModelScope.launch {
            runCatching { repository.savePromo(promo) }
                .onSuccess { saved ->
                    _messages.value = InventoryMessage.Res(
                        R.string.promo_saved,
                        listOf(saved.name),
                    )
                }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_save)
                }
        }
    }

    fun deletePromo(promo: Promo) {
        viewModelScope.launch {
            runCatching { repository.deletePromo(promo) }
                .onSuccess {
                    _messages.value = InventoryMessage.Res(
                        R.string.promo_deleted,
                        listOf(promo.name),
                    )
                }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_delete)
                }
        }
    }

    /** Flips a promo on or off from the list, without opening the editor. */
    fun setPromoEnabled(promo: Promo, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.savePromo(promo.copy(enabled = enabled)) }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_save)
                }
        }
    }

    fun clearPendingScan() { _pendingScan.value = null }

    /**
     * Reads the whole database for the JSON backup.
     *
     * Lives here rather than in the composable so it runs in [viewModelScope] and therefore
     * survives a rotation mid-export — a backup that vanished because the user turned the phone
     * would be a confusing failure.
     */
    suspend fun snapshotDatabase(
        settings: AppSettings,
        appVersionName: String,
        includePromos: Boolean = false,
    ): DatabaseSnapshot = repository.snapshot(settings, appVersionName, includePromos)

    /**
     * Builds the complete backup zip in memory.
     *
     * The single implementation shared by the file export and the Wi-Fi transfer, so the two can
     * never disagree about what a backup contains. Before this existed the snapshot-and-serialise
     * block lived inline in `exportJsonBackup`, and a network transfer would have had to copy it.
     *
     * @param includeImages bundle the product photos into the archive rather than JSON alone.
     * @return the archive bytes, ready to be written to a file or sent over a socket.
     */
    suspend fun buildBackupArchive(
        context: Context,
        settings: AppSettings,
        appVersionName: String,
        includePromos: Boolean,
        includeImages: Boolean,
    ): ByteArray = withContext(Dispatchers.IO) {
        // Snapshot first, then serialise: the read is the slow part and it must be transactional,
        // but the JSON itself is pure string building.
        val snapshot = snapshotDatabase(settings, appVersionName, includePromos)
        val json = snapshot.toJson()
        if (!includeImages) {
            // Even without photos the caller gets a zip, so the receiver has one format to handle
            // and the desktop app never has to guess which it was sent.
            return@withContext BackupArchive.write(json, emptyMap())
        }
        // Only photos the snapshot actually references: an orphaned file left behind by an older
        // delete would otherwise bloat every backup forever.
        val files = snapshot.products.mapNotNull { product ->
            product.imagePath?.let { name ->
                ProductImages.imageFile(context, name)
                    ?.takeIf { it.isFile }
                    ?.let { name to it }
            }
        }.toMap()
        BackupArchive.write(json, files)
    }

    /**
     * Writes a parsed backup into the database and reports the result as a one-off message.
     *
     * Runs in [viewModelScope], like [snapshotDatabase], so a restore survives the user rotating
     * the phone halfway through — a half-written database with no completion message would be the
     * worst possible outcome here.
     *
     * When rows had to be dropped the message says how many and why, rather than reporting a
     * clean success: a silently lossy restore is worse than a noisy one.
     */
    fun restoreBackup(contents: BackupContents, replace: Boolean) {
        viewModelScope.launch {
            runCatching { repository.restore(contents, replace) }
                .onSuccess { result ->
                    _messages.value = when {
                        result.orphanedLines > 0 || result.skipped.isNotEmpty() ->
                            InventoryMessage.Res(
                                R.string.message_import_partial,
                                result.totalAdded,
                                result.skipped.size + result.orphanedLines,
                            )
                        else -> InventoryMessage.Res(
                            if (replace) R.string.message_import_replaced
                            else R.string.message_import_merged,
                            result.totalAdded,
                        )
                    }
                }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_import_failed)
                }
        }
    }

    fun addProduct(
        barcode: String,
        name: String,
        priceCents: Long,
        quantity: Int,
        context: Context,
        categoryId: Long? = null,
        imagePath: String? = null,
        tagIds: Set<Long> = emptySet(),
    ) {
        viewModelScope.launch {
            runCatching {
                // The repository validates the input and needs a Context to localise its errors.
                repository.addOrRestock(
                    barcode = barcode,
                    name = name,
                    priceCents = priceCents,
                    quantity = quantity,
                    context = context,
                    categoryId = categoryId,
                    imagePath = imagePath,
                    tagIds = tagIds,
                )
            }
                .onSuccess { result ->
                    _messages.value = when (result) {
                        is StockRepository.AddResult.Created ->
                            InventoryMessage.StockAdded(result.product.name, quantity)
                        is StockRepository.AddResult.Restocked ->
                            InventoryMessage.StockRestocked(result.product.name, quantity)
                        is StockRepository.AddResult.Error -> InventoryMessage.Error(result.message)
                    }
                }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_save)
                }
        }
    }

    fun updateProduct(product: Product, context: Context? = null) {
        viewModelScope.launch {
            runCatching { repository.updateProduct(product, context) }
                .onSuccess {
                    _messages.value = InventoryMessage.Res(R.string.message_product_saved, product.name)
                }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_save)
                }
        }
    }

    fun deleteProduct(product: Product, context: Context? = null) {
        viewModelScope.launch {
            runCatching { repository.deleteProduct(product, context) }
                .onSuccess {
                    _messages.value =
                        InventoryMessage.Res(R.string.message_product_deleted, product.name)
                }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_delete)
                }
        }
    }

    /**
     * Voids a whole checkout: every line is removed from the ledger, the order header is deleted
     * and all its stock goes back to inventory in a single transaction.
     */
    fun cancelOrder(order: SaleOrder) {
        viewModelScope.launch {
            runCatching { repository.cancelOrder(order.orderId) }
                .onSuccess {
                    _messages.value =
                        InventoryMessage.Res(R.string.message_order_cancelled, order.itemCount)
                }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_cancel_sale)
                }
        }
    }

    /**
     * Voids a recorded sale and returns its units to inventory. This is how a mis-scan or a
     * refund is handled.
     */
    fun cancelSale(sale: Sale) {
        viewModelScope.launch {
            runCatching { repository.cancelSale(sale.id) }
                .onSuccess { result ->
                    _messages.value = when (result) {
                        is CancelResult.Success ->
                            InventoryMessage.SaleCancelled(result.sale.productName, result.sale.quantity)
                        is CancelResult.ProductMissing -> InventoryMessage.Res(
                            R.string.message_sale_product_missing,
                            result.sale.productName
                        )
                        CancelResult.NotFound ->
                            InventoryMessage.Res(R.string.message_sale_not_found)
                    }
                }
                .onFailure {
                    _messages.value = it.message
                        ?.let { message -> InventoryMessage.SaleCancelFailed(message) }
                        ?: InventoryMessage.Res(R.string.message_could_not_cancel_sale)
                }
        }
    }

    fun restock(product: Product, amount: Int) {
        viewModelScope.launch {
            runCatching { repository.restock(product.id, amount) }
                .onSuccess {
                    _messages.value = InventoryMessage.StockRestocked(product.name, amount)
                }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_restock)
                }
        }
    }

    /** Creates a category, silently ignoring empty or duplicate names. */
    fun createCategory(name: String) {
        viewModelScope.launch {
            runCatching { repository.createCategory(name) }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_save)
                }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            runCatching { repository.deleteCategory(category.id) }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_delete)
                }
        }
    }

    /**
     * Renames a category, reporting a blank or duplicate name rather than failing silently.
     *
     * The repository decides *what* happened; translating that into a string happens here so the
     * screen stays free of resource lookups and so the same wording is used wherever a category is
     * renamed. A clash is the common case — two categories called "Drinks" look like a bug — so it
     * gets a specific message instead of the generic "could not save".
     */
    fun renameCategory(category: Category, name: String) {
        viewModelScope.launch {
            runCatching { repository.renameCategory(category.id, name) }
                .onSuccess { result ->
                    _messages.value = when (result) {
                        RenameCategoryResult.Renamed -> null
                        RenameCategoryResult.Empty ->
                            InventoryMessage.Res(R.string.category_rename_empty)

                        RenameCategoryResult.Duplicate ->
                            InventoryMessage.Res(R.string.category_rename_duplicate, name.trim())
                    }
                }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_save)
                }
        }
    }

    // ---- Tags --------------------------------------------------------------
    // Mirrors the category methods above, including the wording of the clashes: from the user's
    // side a tag is renamed by the same gesture, so a duplicate "Fragile" should complain the same
    // way a duplicate "Drinks" does.

    /** Every tag, alphabetical. Always observed; the *screen* decides whether to draw them. */
    val tags: StateFlow<List<Tag>> = repository.tags.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    /** Creates a tag, silently ignoring empty or duplicate names. */
    fun createTag(name: String) {
        viewModelScope.launch {
            runCatching { repository.createTag(name) }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_save)
                }
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            runCatching { repository.deleteTag(tag.id) }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_delete)
                }
        }
    }

    /** Renames a tag, reporting a blank or duplicate name rather than failing silently. */
    fun renameTag(tag: Tag, name: String) {
        viewModelScope.launch {
            runCatching { repository.renameTag(tag.id, name) }
                .onSuccess { result ->
                    _messages.value = when (result) {
                        RenameCategoryResult.Renamed -> null
                        RenameCategoryResult.Empty ->
                            InventoryMessage.Res(R.string.tag_rename_empty)

                        RenameCategoryResult.Duplicate ->
                            InventoryMessage.Res(R.string.tag_rename_duplicate, name.trim())
                    }
                }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_save)
                }
        }
    }

    /** Replaces a product's tag set from the editor. */
    fun setProductTags(productId: Long, tagIds: Set<Long>) {
        viewModelScope.launch {
            runCatching { repository.setTagsForProduct(productId, tagIds) }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_save)
                }
        }
    }

    /**
     * Copies a newly picked photo into private storage.
     *
     * This lives here rather than in the composable so the file I/O survives configuration
     * changes and never blocks the main thread. Returns the stored file name, or null on failure.
     */
    suspend fun savePickedImage(context: Context, uri: Uri, sourceId: Long = 0L): String? =
        ProductImages.saveImage(context, uri, sourceId)

    /** Resolves a stored photo name to a file the UI can load. */
    fun productImageFile(context: Context, fileName: String?): File? =
        ProductImages.imageFile(context, fileName)

    /**
     * Adjusts stock down by [amount] without recording a sale.
     *
     * This is the inventory-correction path (breakage, miscount, shrinkage). Real sales must go
     * through the checkout cart so they appear in history and affect revenue totals.
     */
    fun removeStock(product: Product, amount: Int) {
        viewModelScope.launch {
            runCatching { repository.updateProduct(product.copy(quantity = product.quantity - amount)) }
                .onSuccess {
                    _messages.value = InventoryMessage.Res(
                        R.string.message_stock_removed,
                        amount,
                        product.name,
                    )
                }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_save)
                }
        }
    }

    /**
     * Stars or unstars a product.
     *
     * Silent on success, unlike [removeStock]: this is a browse-time preference rather than a data
     * change the user needs confirming, and a snackbar on every star would be noise. A failure is
     * still reported, because a star that silently does not stick is worse than one that says so.
     */
    fun toggleFavourite(product: Product) {
        viewModelScope.launch {
            runCatching { repository.setFavourite(product.id, !product.favourite) }
                .onFailure {
                    _messages.value = InventoryMessage.Res(R.string.message_could_not_save)
                }
        }
    }

    /**
     * Handles a scanned code coming from the camera.
     *
     * @param sell when true the scanned unit is sold immediately (subtracting one from stock);
     *             when false the code is treated as a restock, or surfaced for adding a new product.
     */
    fun onBarcodeScanned(barcode: String, sell: Boolean) {
        val clean = barcode.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            if (sell) {
                when (val outcome = repository.sellByBarcode(clean, 1)) {
                    is StockRepository.SellByBarcodeOutcome.UnknownBarcode -> {
                        _pendingScan.value = outcome.barcode
                        _messages.value = InventoryMessage.NotFound(outcome.barcode)
                    }
                    is StockRepository.SellByBarcodeOutcome.Done -> {
                        _messages.value = when (val result = outcome.result) {
                            is SaleResult.Success ->
                                InventoryMessage.Sold(result.product.name, result.product.quantity)
                            is SaleResult.InsufficientStock ->
                                InventoryMessage.OutOfStock(result.product.name)
                            SaleResult.ProductNotFound ->
                                InventoryMessage.NotFound(clean)
                        }
                    }
                }
            } else {
                val existing = repository.findByBarcode(clean)
                if (existing == null) {
                    _pendingScan.value = clean
                } else {
                    repository.restock(existing.id, 1)
                    _messages.value = InventoryMessage.StockRestocked(existing.name, 1)
                }
            }
        }
    }
}

/** Builds ViewModels with the repository from [StockApplication]. */
object AppViewModelFactory {
    fun factory(app: StockApplication): ViewModelProvider.Factory = viewModelFactory {
        initializer { InventoryViewModel(app.repository) }
    }
}
