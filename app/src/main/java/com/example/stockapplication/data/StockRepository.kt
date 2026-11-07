package com.example.stockapplication.data

import android.content.Context
import com.example.stockapplication.R
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicLong

/**
 * Single entry point to inventory + sales data. Keeping the logic here means the ViewModels
 * stay thin and the sale/restock rules live in one testable place.
 */
class StockRepository(private val dao: StockDao) {

    fun observeProducts(): Flow<List<Product>> = dao.observeProducts()

    // ---- Promos -----------------------------------------------------------

    /**
     * Every promo, live.
     *
     * Unfiltered on purpose: the promos tab shows inactive and expired ones, and the cart filters
     * through [PromoEngine]. Filtering here too would mean two places decide what "active" means.
     */
    fun observePromos(): Flow<List<Promo>> = dao.observePromos()

    suspend fun findPromo(id: Long): Promo? = dao.findPromo(id)

    /**
     * Creates or updates a promo.
     *
     * Normalises on the way in — the name is trimmed and a category id is dropped for a product
     * promo — so a row in the table can never hold a value the engine has to defend against.
     *
     * @return the saved promo, with its id filled in for a new one.
     */
    suspend fun savePromo(promo: Promo): Promo {
        val clean = normalisePromo(promo)
        return if (clean.id == 0L) {
            val id = dao.insertPromo(clean)
            clean.copy(id = id)
        } else {
            dao.updatePromo(clean)
            clean
        }
    }

    suspend fun deletePromo(promo: Promo) = dao.deletePromo(promo)

    /**
     * Fills in the half of a promo that its [Promo.kind] does not use.
     *
     * Without this, switching a promo from *Category* to *Products* and back would leave a stale
     * `categoryId` behind, and the engine — which trusts the stored row — would start matching on
     * the wrong field if the kind were ever flipped by a hand-edited database.
     */
    private fun normalisePromo(promo: Promo): Promo {
        val base = promo.copy(
            name = promo.name.trim(),
            thresholdQuantity = promo.thresholdQuantity.coerceAtLeast(1),
            discountPercentBasisPoints = promo.discountPercentBasisPoints.coerceIn(0, 10_000),
            discountFixedCents = promo.discountFixedCents.coerceAtLeast(0L),
        )
        return when (base.kind) {
            PromoKind.CATEGORY -> base.copy(productIdsCsv = "")
            PromoKind.PRODUCTS -> base.copy(categoryId = null)
        }
    }

    fun observeProduct(id: Long): Flow<Product?> = dao.observeProduct(id)

    fun observeSales(): Flow<List<Sale>> = dao.observeSales()

    fun observeRecentSales(limit: Int = 20): Flow<List<Sale>> = dao.observeRecentSales(limit)

    suspend fun findByBarcode(barcode: String): Product? =
        dao.findByBarcode(barcode.trim())

    // ---- Categories -------------------------------------------------------

    fun observeCategories(): Flow<List<Category>> = dao.observeCategories()

    /**
     * Creates a category, or returns the existing one when the name already exists. Matching is
     * case-insensitive, so "Drinks" and "drinks" don't become two separate buckets.
     */
    suspend fun createCategory(name: String): Category? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        dao.findCategoryByName(clean)?.let { return it }
        val id = dao.insertCategory(Category(name = clean))
        return Category(id = id, name = clean)
    }

    suspend fun deleteCategory(categoryId: Long) = dao.deleteCategory(categoryId)

    /**
     * Renames a category, or reports why it could not be.
     *
     * The duplicate check lives here rather than in the DAO so the UI can tell "you already have a
     * category called that" apart from "the write failed": `name` is UNIQUE, and letting the
     * constraint throw would surface a raw SQLite error for what is really a typo. Matching is
     * case-insensitive, the same rule [createCategory] uses, so "Drinks" cannot be renamed to
     * "drinks" to make a second bucket.
     */
    suspend fun renameCategory(categoryId: Long, name: String): RenameCategoryResult {
        val clean = name.trim()
        if (clean.isEmpty()) return RenameCategoryResult.Empty
        val existing = dao.findCategoryByName(clean)
        // Renaming a category to its own name (or its own case) is a no-op, not a clash.
        if (existing != null && existing.id != categoryId) return RenameCategoryResult.Duplicate
        dao.renameCategoryRow(categoryId, clean)
        return RenameCategoryResult.Renamed
    }

    // ---- Tags --------------------------------------------------------------
    // Deliberately the same shapes as the category methods above: a tag is created, renamed and
    // deleted exactly like a category from the user's point of view. Only the *relationship* to
    // products differs (many-to-many rather than one), which lives in the DAO and not here.

    val tags: Flow<List<Tag>> get() = dao.observeTags()

    /** Every product→tag assignment, for building the per-product tag map in the UI. */
    val productTags: Flow<List<ProductTag>> get() = dao.observeProductTags()

    /**
     * Creates a tag, or returns the existing one when the name already exists.
     *
     * Case-insensitive, matching [createCategory]: "Fragile" and "fragile" are one tag.
     */
    suspend fun createTag(name: String): Tag? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        dao.findTagByName(clean)?.let { return it }
        val id = dao.insertTag(Tag(name = clean))
        return Tag(id = id, name = clean)
    }

    /**
     * Deletes a tag and its assignments. The products keep their own tags and their category.
     */
    suspend fun deleteTag(tagId: Long) = dao.deleteTag(tagId)

    /**
     * Renames a tag, or reports why it could not be.
     *
     * Same clash handling as [renameCategory], and for the same reason: `name` is UNIQUE and the
     * user needs "you already have a tag called that" rather than a raw SQLite error.
     */
    suspend fun renameTag(tagId: Long, name: String): RenameCategoryResult {
        val clean = name.trim()
        if (clean.isEmpty()) return RenameCategoryResult.Empty
        val existing = dao.findTagByName(clean)
        if (existing != null && existing.id != tagId) return RenameCategoryResult.Duplicate
        dao.renameTagRow(tagId, clean)
        return RenameCategoryResult.Renamed
    }

    /** Replaces a product's whole tag set. See [StockDao.setTagsForProduct]. */
    suspend fun setTagsForProduct(productId: Long, tagIds: Collection<Long>) =
        dao.setTagsForProduct(productId, tagIds)

    /**
     * Adds a brand new product. If a product with the same barcode already exists its stock is
     * increased instead, so scanning the same label twice never creates duplicates.
     */
    suspend fun addOrRestock(
        barcode: String,
        name: String,
        priceCents: Long,
        quantity: Int,
        context: Context,
        categoryId: Long? = null,
        imagePath: String? = null,
        /** Tags chosen in the form. Applied only to a *newly created* product, never a restock. */
        tagIds: Set<Long> = emptySet(),
    ): AddResult {
        val cleanBarcode = barcode.trim()
        val cleanName = name.trim()
        require(cleanBarcode.isNotEmpty()) { context.getString(R.string.error_barcode_empty) }
        require(cleanName.isNotEmpty()) { context.getString(R.string.error_name_empty) }
        require(quantity > 0) { context.getString(R.string.error_quantity_positive) }

        val existing = dao.findByBarcode(cleanBarcode)
        return if (existing != null) {
            val updated = dao.restock(existing.id, quantity)
                ?: return AddResult.Error(
                    context.getString(R.string.error_could_not_restock_product, existing.name)
                )
            AddResult.Restocked(updated)
        } else {
            val id = dao.insertProduct(
                Product(
                    barcode = cleanBarcode,
                    name = cleanName,
                    priceCents = priceCents.coerceAtLeast(0L),
                    quantity = quantity,
                    categoryId = categoryId,
                    imagePath = imagePath?.takeIf { it.isNotBlank() },
                )
            )
            // Written after the insert because the assignment needs the product's real id, and
            // only for a new product: restocking an existing one must not silently replace the
            // tags it already carries with whatever the form happened to submit.
            if (tagIds.isNotEmpty()) dao.setTagsForProduct(id, tagIds)
            AddResult.Created(
                dao.findById(id) ?: error("Inserted product disappeared")
            )
        }
    }

    /**
     * Saves an edited product.
     *
     * When [context] is supplied and the photo changed, the old photo file is deleted so replaced
     * and removed images don't pile up in private storage. Products without a photo are unaffected.
     */
    suspend fun updateProduct(product: Product, context: Context? = null) {
        val previousImage = if (context != null) dao.findById(product.id)?.imagePath else null
        dao.updateProduct(
            product.copy(
                barcode = product.barcode.trim(),
                name = product.name.trim(),
                priceCents = product.priceCents.coerceAtLeast(0L),
                quantity = product.quantity.coerceAtLeast(0),
                imagePath = product.imagePath?.takeIf { it.isNotBlank() },
            )
        )
        if (context != null && previousImage != null && previousImage != product.imagePath) {
            ProductImages.deleteImage(context, previousImage)
        }
    }

    /** Removes a product and the photo file that belonged to it. */
    suspend fun deleteProduct(product: Product, context: Context? = null) {
        dao.deleteProduct(product)
        if (context != null) ProductImages.deleteImage(context, product.imagePath)
    }

    suspend fun restock(productId: Long, amount: Int): Product? = dao.restock(productId, amount)

    /**
     * Stars or unstars a product.
     *
     * Writes only the one column, so it cannot race with a restock or an edit that is saving at the
     * same moment — see [StockDao.setFavourite].
     */
    suspend fun setFavourite(productId: Long, favourite: Boolean) =
        dao.setFavourite(productId, favourite)

    /** Records a sale of [quantity] units and subtracts them from inventory. */
    suspend fun sell(productId: Long, quantity: Int = 1): SaleResult {
        if (quantity <= 0) return SaleResult.ProductNotFound
        val product = dao.findById(productId) ?: return SaleResult.ProductNotFound
        return dao.completeSale(
            Sale(
                productId = product.id,
                productName = product.name,
                barcode = product.barcode,
                quantity = quantity,
                unitPriceCents = product.priceCents,
                soldAt = nowProvider()
            )
        )
    }

    /** Sell by scanning a label. Returns [SellByBarcodeOutcome.UnknownBarcode] if not stocked. */
    suspend fun sellByBarcode(barcode: String, quantity: Int = 1): SellByBarcodeOutcome {
        val product = dao.findByBarcode(barcode.trim())
            ?: return SellByBarcodeOutcome.UnknownBarcode(barcode.trim())
        return SellByBarcodeOutcome.Done(product, dao.completeSale(
            Sale(
                productId = product.id,
                productName = product.name,
                barcode = product.barcode,
                quantity = quantity,
                unitPriceCents = product.priceCents,
                soldAt = nowProvider()
            )
        ))
    }

    /**
     * Voids a sale and puts the units back into inventory. Use this for refunds or for a
     * mis-scan that charged the wrong product.
     */
    suspend fun cancelSale(saleId: Long): CancelResult = dao.cancelSale(saleId)

    fun observeOrders(): Flow<List<SaleOrder>> = dao.observeOrders()

    fun observeOrderLines(): Flow<List<Sale>> = dao.observeOrderLines()

    /**
     * Checks out a whole cart.
     *
     * Totals are computed here from the current tax/card-fee settings rather than taken from the
     * caller, so the figures written to the ledger can't disagree with the configured rates.
     * Lines that would oversell are rejected before any stock moves — see [StockDao.completeOrder].
     *
     * @param note optional cashier memo stored on the order; trimmed, and blank means none.
     * @param promoDiscountCents promo saving decided by [PromoEngine] before the call. Passed in
     *   rather than recomputed here so the amount on the receipt is exactly the amount the cashier
     *   was shown — recomputing would let a promo edited mid-sale change the bill after the fact.
     * @param promoName name of that promo, snapshotted onto the order for the receipt and history.
     */
    suspend fun checkout(
        lines: List<CartLine>,
        paymentMethod: PaymentMethod,
        taxRateBasisPoints: Int,
        cardFeeRateBasisPoints: Int,
        cardFeeFixedCents: Long = 0L,
        note: String = "",
        promoDiscountCents: Long = 0L,
        promoName: String = "",
    ): CheckoutResult {
        if (lines.isEmpty()) return CheckoutResult.EmptyCart

        val subtotal = lines.sumOf { it.unitPriceCents * it.quantity }
        val totals = computeTotals(
            itemsSubtotalCents = subtotal,
            taxRateBasisPoints = taxRateBasisPoints,
            cardFeeRateBasisPoints = cardFeeRateBasisPoints,
            paymentMethod = paymentMethod,
            cardFeeFixedCents = cardFeeFixedCents,
            promoDiscountCents = promoDiscountCents,
        )
        val now = nowProvider()
        val orderId = "ORD-" + now.toString(36) + "-" + (orderCounter.incrementAndGet())

        val sales = lines.map { line ->
            Sale(
                productId = line.productId,
                productName = line.productName,
                barcode = line.barcode,
                quantity = line.quantity,
                unitPriceCents = line.unitPriceCents,
                soldAt = now,
                orderId = orderId,
            )
        }

        val order = SaleOrder(
            orderId = orderId,
            soldAt = now,
            paymentMethod = paymentMethod,
            itemsSubtotalCents = totals.itemsSubtotalCents,
            promoDiscountCents = totals.promoDiscountCents,
            // The name only travels when money actually came off, so a zero-value match cannot
            // leave a promo name on an order that was not discounted.
            promoName = if (totals.promoDiscountCents > 0L) promoName.trim() else "",
            taxRateBasisPoints = taxRateBasisPoints,
            taxCents = totals.taxCents,
            cardFeeRateBasisPoints = if (paymentMethod == PaymentMethod.CARD) {
                cardFeeRateBasisPoints
            } else 0,
            cardFeeFixedCents = totals.cardFeeFixedCents,
            cardFeeCents = totals.cardFeeCents,
            totalCents = totals.totalCents,
            itemCount = lines.sumOf { it.quantity },
            note = note.trim().take(MaxOrderNoteLength),
        )

        return when (val result = dao.completeOrder(order, sales)) {
            is OrderResult.Success -> CheckoutResult.Success(result.order)
            is OrderResult.InsufficientStock -> CheckoutResult.InsufficientStock(
                productName = result.product.name,
                available = result.product.quantity,
                requested = result.requested,
            )
            is OrderResult.ProductNotFound -> CheckoutResult.ProductNotFound(result.productName)
        }
    }

    suspend fun cancelOrder(orderId: String): Int = dao.cancelOrder(orderId)

    /**
     * Reads the whole database into one consistent [DatabaseSnapshot] for the JSON backup.
     *
     * Passing [settings] in (rather than reaching for DataStore here) keeps the repository free of
     * a Context for this path and lets tests supply whatever rates they want to see exported.
     */
    /**
     * Reads the whole database into one consistent [DatabaseSnapshot] for the JSON backup.
     *
     * Passing [settings] in (rather than reaching for DataStore here) keeps the repository free of
     * a Context for this path and lets tests supply whatever rates they want to see exported.
     *
     * @param includePromos carries the promo rules into the file. Off by default: restoring a
     *   backup that contains promos re-arms those discounts, which is a side effect most people
     *   restoring a backup are not expecting.
     */
    suspend fun snapshot(
        settings: AppSettings,
        appVersionName: String,
        includePromos: Boolean = false,
    ): DatabaseSnapshot = dao.snapshot(settings, appVersionName, includePromos)

    /**
     * Writes a parsed backup into the database.
     *
     * Deliberately takes [BackupContents] rather than a `String`: reading and validating the file
     * is [JsonImport]'s job and needs no database, so this method is only about the transaction.
     * See [StockDao.restore] for what "merge" and "replace" mean row by row.
     *
     * @param replace empty the four tables first instead of merging into them.
     */
    suspend fun restore(contents: BackupContents, replace: Boolean): ImportResult =
        dao.restore(contents, replace)

    private val orderCounter = AtomicLong(0L)

    /** Swappable in tests so sale timestamps are deterministic. */
    var nowProvider: () -> Long = { System.currentTimeMillis() }

    sealed interface AddResult {
        data class Created(val product: Product) : AddResult
        data class Restocked(val product: Product) : AddResult
        data class Error(val message: String) : AddResult
    }

    sealed interface SellByBarcodeOutcome {
        data class Done(val product: Product, val result: SaleResult) : SellByBarcodeOutcome
        data class UnknownBarcode(val barcode: String) : SellByBarcodeOutcome
    }

    sealed interface CheckoutResult {
        data class Success(val order: SaleOrder) : CheckoutResult
        data object EmptyCart : CheckoutResult
        data class InsufficientStock(
            val productName: String,
            val available: Int,
            val requested: Int,
        ) : CheckoutResult
        data class ProductNotFound(val productName: String) : CheckoutResult
    }
}

/** Generates the default barcode for a locally-created product (used by the "new item" form). */
object BarcodeGenerator {
    private val counter = AtomicLong(System.currentTimeMillis() % 100_000_000L)

    fun next(): String = "LOC-" + counter.incrementAndGet().toString().padStart(8, '0')
}

/**
 * Whether money labels show their cents. Mirrors [AppSettings.showMoneyDecimals].
 *
 * Held in a process-wide value rather than threaded through every call site: the alternative is
 * ~60 changes across screens and exporters, and the exports would then disagree with the screen
 * whenever a caller forgot the flag. [StockApp] keeps this in step with the stored setting, so it
 * is only one composition behind at worst.
 *
 * Defaults to `true` so anything that formats before the settings have loaded (a unit test, the
 * first frame) shows the full amount rather than silently hiding cents.
 */
@Volatile
var showCents: Boolean = true

/**
 * The currency symbol written in front of every amount. Mirrors `AppSettings.currency`.
 *
 * Same reasoning and same mechanism as [showCents] just above: `formatCents` is reached from
 * dozens of non-composable places — the PDF renderer, the CSV exporters, the repository's own
 * error strings — and threading a parameter through all of them would guarantee that some call
 * site (usually an exporter) disagreed with the screen. [StockApp] keeps this in step with the
 * stored setting.
 *
 * Defaults to "$" so anything that formats before the settings have loaded shows the same symbol
 * a fresh install has always shown.
 */
@Volatile
var currencySymbol: String = Currency.fromId(DefaultCurrencyId).symbol

/**
 * Formats cents as a currency string, e.g. 1299 -> "$12.99" (or "$13" with cents hidden).
 *
 * The symbol is part of the output on purpose: every amount in the app should read as money, and
 * a caller that had to remember to prepend it would eventually forget. The **sign goes before the
 * symbol** (`-$12.99`), which is the convention for the prefix currencies this app offers.
 *
 * Rounding when cents are hidden is half-up on the absolute value, so 1250 -> "$13" and
 * -1250 -> "-$13" stay symmetric — the sign is applied after rounding, never by truncation, which
 * would put -1299 and 1299 on different sides of the same whole number.
 */
fun formatCents(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    val amount = if (!showCents) {
        (abs + 50).div(100).toString()
    } else {
        "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }
    return "$sign$currencySymbol$amount"
}
