package com.example.stockapplication.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun observeProducts(): Flow<List<Product>>

    // ---- Promos ------------------------------------------------------------

    /**
     * Every promo, newest id last.
     *
     * Not filtered by date or `enabled` here: the list screen needs to show expired and switched
     * off promos too, and the cart filters through [PromoEngine] which owns that policy. Filtering
     * in SQL would put the rule in two places.
     */
    @Query("SELECT * FROM promos ORDER BY id ASC")
    fun observePromos(): Flow<List<Promo>>

    @Query("SELECT * FROM promos")
    suspend fun snapshotPromos(): List<Promo>

    @Query("SELECT * FROM promos WHERE id = :id")
    suspend fun findPromo(id: Long): Promo?

    @Insert
    suspend fun insertPromo(promo: Promo): Long

    @Update
    suspend fun updatePromo(promo: Promo)

    @Delete
    suspend fun deletePromo(promo: Promo)

    /**
     * Removes every reference to a category from the promos that target it.
     *
     * Called when a category is deleted. The promo is **disabled rather than deleted**: the
     * cashier wrote it, and silently vanishing a discount is worse than a promo that is visibly
     * off and can be repointed at another category.
     */
    @Query("UPDATE promos SET enabled = 0 WHERE categoryId = :categoryId")
    suspend fun disablePromosForCategory(categoryId: Long)


    // ---- Categories --------------------------------------------------------

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findCategoryByName(name: String): Category?

    @Insert
    suspend fun insertCategory(category: Category): Long

    /**
     * Renames a category in place.
     *
     * `name` is UNIQUE, so renaming onto a name another category already holds throws rather than
     * merging the two buckets. Callers check for the clash first — see
     * `StockRepository.renameCategory` — so the constraint is a backstop, not the error path.
     *
     * The id is left alone, which is the whole point: products reference the category by id, so
     * they follow the rename without being touched.
     */
    @Query("UPDATE categories SET name = :name WHERE id = :categoryId")
    suspend fun renameCategoryRow(categoryId: Long, name: String)

    @Query("UPDATE products SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategoryFromProducts(categoryId: Long)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategoryRow(categoryId: Long)

    /**
     * Deletes a category, leaving its products in inventory as uncategorised.
     *
     * Deleting the products themselves would silently destroy stock records, so the references are
     * nulled first and both steps run in one transaction.
     */
    @Transaction
    suspend fun deleteCategory(categoryId: Long) {
        clearCategoryFromProducts(categoryId)
        disablePromosForCategory(categoryId)
        deleteCategoryRow(categoryId)
    }

    // ---- Tags --------------------------------------------------------------
    //
    // The optional second classification. All of it works whether or not the feature is switched
    // on: `tagsEnabled` is a view setting, so the rows must exist and round-trip through a backup
    // even on a device that is not currently showing them.

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags")
    suspend fun snapshotTags(): List<Tag>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findTagByName(name: String): Tag?

    @Insert
    suspend fun insertTag(tag: Tag): Long

    /**
     * Renames a tag in place, leaving its assignments alone.
     *
     * Same contract as [renameCategoryRow]: `name` is UNIQUE, so a clash throws and the caller is
     * expected to have checked first.
     */
    @Query("UPDATE tags SET name = :name WHERE id = :tagId")
    suspend fun renameTagRow(tagId: Long, name: String)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTagRow(tagId: Long)

    @Query("DELETE FROM product_tags WHERE tagId = :tagId")
    suspend fun clearTagAssignments(tagId: Long)

    /**
     * Deletes a tag and every assignment of it.
     *
     * The products themselves are untouched — a tag is a label, not a container, so removing it
     * must never remove stock. The explicit `clearTagAssignments` is belt-and-braces: the foreign
     * key cascades, but relying on it would make the delete behave differently on a database built
     * before the cascade existed.
     */
    @Transaction
    suspend fun deleteTag(tagId: Long) {
        clearTagAssignments(tagId)
        deleteTagRow(tagId)
    }

    /** Every assignment, for building the product→tags map the UI needs. */
    @Query("SELECT * FROM product_tags")
    fun observeProductTags(): Flow<List<ProductTag>>

    @Query("SELECT * FROM product_tags")
    suspend fun snapshotProductTags(): List<ProductTag>

    @Query("SELECT * FROM product_tags WHERE productId = :productId")
    suspend fun tagsForProduct(productId: Long): List<ProductTag>

    @Insert
    suspend fun insertProductTag(productTag: ProductTag)

    @Query("DELETE FROM product_tags WHERE productId = :productId")
    suspend fun clearTagsForProduct(productId: Long)

    /**
     * Replaces a product's whole tag set in one transaction.
     *
     * Replace rather than diff: the editor submits the complete selection, and computing the delta
     * would mean reading the old set inside the transaction for no benefit. Both statements run
     * together so no reader can observe a product that briefly has no tags at all.
     */
    @Transaction
    suspend fun setTagsForProduct(productId: Long, tagIds: Collection<Long>) {
        clearTagsForProduct(productId)
        // `distinct` guards the composite primary key: submitting the same id twice would abort
        // the transaction rather than silently de-duplicating.
        tagIds.distinct().forEach { tagId ->
            insertProductTag(ProductTag(productId = productId, tagId = tagId))
        }
    }

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeProduct(id: Long): Flow<Product?>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): Product?

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun findById(id: Long): Product?

    @Insert
    suspend fun insertProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    /**
     * Stars or unstars a product.
     *
     * A targeted `UPDATE` rather than the `@Update`-of-a-copied-row pattern used elsewhere, because
     * those are read-modify-write and this is a single column: restocking from one screen while
     * starring from another would otherwise silently revert whichever write landed second.
     */
    @Query("UPDATE products SET favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)

    @Query("SELECT * FROM sales ORDER BY soldAt DESC")
    fun observeSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sale_orders ORDER BY soldAt DESC")
    fun observeOrders(): Flow<List<SaleOrder>>

    /**
     * Every line item that belongs to a cart checkout, newest first.
     *
     * Legacy single-item sales (written before carts existed) carry an empty `orderId` and are
     * excluded — they have no order header to group under.
     */
    @Query("SELECT * FROM sales WHERE orderId != '' ORDER BY soldAt DESC")
    fun observeOrderLines(): Flow<List<Sale>>

    @Insert
    suspend fun insertOrder(order: SaleOrder)

    @Query("SELECT * FROM sale_orders WHERE orderId = :orderId")
    suspend fun findOrder(orderId: String): SaleOrder?

    @Query("SELECT * FROM sales ORDER BY soldAt DESC LIMIT :limit")
    fun observeRecentSales(limit: Int): Flow<List<Sale>>

    @Insert
    suspend fun insertSale(sale: Sale): Long

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun findSale(id: Long): Sale?

    @Query("SELECT * FROM sales WHERE orderId = :orderId")
    suspend fun findSalesForOrder(orderId: String): List<Sale>

    @Delete
    suspend fun deleteSale(sale: Sale)

    /**
     * Decrements the product's stock and records the sale atomically, so inventory and the
     * sales log can never drift apart.
     */
    @Transaction
    suspend fun completeSale(sale: Sale): SaleResult {
        val product = findById(sale.productId) ?: return SaleResult.ProductNotFound
        if (product.quantity < sale.quantity) {
            return SaleResult.InsufficientStock(product)
        }
        val updated = product.copy(quantity = product.quantity - sale.quantity)
        updateProduct(updated)
        insertSale(sale)
        return SaleResult.Success(updated)
    }

    /**
     * Records a whole cart as one order: every line's stock is decremented and its [Sale] row
     * written, plus the [SaleOrder] summary. All-or-nothing, so a partially-filled cart can never
     * leave inventory and the ledger disagreeing.
     */
    @Transaction
    suspend fun completeOrder(order: SaleOrder, lines: List<Sale>): OrderResult {
        // Validate everything first — a failure halfway through must not consume stock.
        val products = mutableListOf<Product>()
        for (line in lines) {
            val product = findById(line.productId)
                ?: return OrderResult.ProductNotFound(line.productName)
            if (product.quantity < line.quantity) {
                return OrderResult.InsufficientStock(product, line.quantity)
            }
            products += product
        }

        products.forEachIndexed { index, product ->
            val line = lines[index]
            updateProduct(product.copy(quantity = product.quantity - line.quantity))
            insertSale(line)
        }
        insertOrder(order)
        return OrderResult.Success(order)
    }

    /**
     * Reverses a sale: deletes it from the ledger and returns the units to inventory.
     * If the product itself was deleted in the meantime the stock cannot be restored, which is
     * reported through [CancelResult.ProductMissing] while the sale row is still removed.
     */
    @Transaction
    suspend fun cancelSale(saleId: Long): CancelResult {
        val sale = findSale(saleId) ?: return CancelResult.NotFound
        deleteSale(sale)
        val product = findById(sale.productId)
            ?: return CancelResult.ProductMissing(sale)
        val updated = product.copy(quantity = product.quantity + sale.quantity)
        updateProduct(updated)
        return CancelResult.Success(sale, updated)
    }

    /**
     * Voids every line of a cart sale at once and restores all its stock, so a refunded order
     * doesn't leave orphaned lines or half-restored inventory.
     */
    @Transaction
    suspend fun cancelOrder(orderId: String): Int {
        val lines = findSalesForOrder(orderId)
        lines.forEach { line ->
            deleteSale(line)
            findById(line.productId)?.let { product ->
                updateProduct(product.copy(quantity = product.quantity + line.quantity))
            }
        }
        findOrder(orderId)?.let { deleteOrder(it) }
        return lines.size
    }

    @Delete
    suspend fun deleteOrder(order: SaleOrder)

    // ---- Providers ---------------------------------------------------------

    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.phone AS phone,
               p.address AS address, p.notes AS notes,
               COUNT(e.entryId) AS entryCount,
               COALESCE(SUM(e.totalCents), 0) AS totalSpentCents
        FROM providers p
        LEFT JOIN material_entries e ON e.providerId = p.id
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE ASC
        """
    )
    fun observeProviders(): Flow<List<ProviderSummary>>

    @Query("SELECT * FROM providers ORDER BY name COLLATE NOCASE ASC")
    suspend fun snapshotProviders(): List<Provider>

    @Query("SELECT * FROM providers WHERE id = :providerId")
    suspend fun findProvider(providerId: Long): Provider?

    @Query("SELECT * FROM providers WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findProviderByName(name: String): Provider?

    @Insert
    suspend fun insertProvider(provider: Provider): Long

    @Update
    suspend fun updateProvider(provider: Provider)

    @Query("UPDATE material_entries SET providerId = NULL WHERE providerId = :providerId")
    suspend fun clearProviderFromEntries(providerId: Long)

    @Query("DELETE FROM providers WHERE id = :providerId")
    suspend fun deleteProviderRow(providerId: Long)

    /**
     * Deletes a provider while leaving every purchase readable.
     *
     * The explicit `providerId = NULL` is belt-and-braces on top of the foreign key: it keeps the
     * behaviour identical on a database created before the cascade existed, and the name
     * snapshotted on each entry is what the UI actually shows.
     */
    @Transaction
    suspend fun deleteProvider(providerId: Long) {
        clearProviderFromEntries(providerId)
        deleteProviderRow(providerId)
    }

    // ---- Materials ---------------------------------------------------------

    /**
     * Materials with their derived last paid unit cost.
     *
     * The subquery orders by purchase date rather than line id, so backdating an entry correctly
     * becomes "the latest purchase" even though its row was inserted last.
     */
    @Query(
        """
        SELECT m.*,
               (SELECT l.unitCostCents
                FROM material_entry_lines l
                INNER JOIN material_entries e ON e.entryId = l.entryId
                WHERE l.materialId = m.id
                ORDER BY e.purchasedAt DESC, l.id DESC
                LIMIT 1) AS lastUnitCostCents
        FROM materials m
        ORDER BY m.name COLLATE NOCASE ASC
        """
    )
    fun observeMaterials(): Flow<List<MaterialRow>>

    @Query("SELECT * FROM materials ORDER BY name COLLATE NOCASE ASC")
    suspend fun snapshotMaterials(): List<Material>

    @Query("SELECT * FROM materials WHERE id = :materialId")
    suspend fun findMaterial(materialId: Long): Material?

    @Query("SELECT * FROM materials WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findMaterialByName(name: String): Material?

    @Insert
    suspend fun insertMaterial(material: Material): Long

    @Update
    suspend fun updateMaterial(material: Material)

    @Query("UPDATE materials SET stock = :stock WHERE id = :materialId")
    suspend fun setMaterialStock(materialId: Long, stock: Int)

    @Query("UPDATE material_entry_lines SET materialId = NULL WHERE materialId = :materialId")
    suspend fun clearMaterialFromEntryLines(materialId: Long)

    @Query("DELETE FROM materials WHERE id = :materialId")
    suspend fun deleteMaterialRow(materialId: Long)

    /** Deletes a material but keeps the name and unit snapshotted on past purchase lines. */
    @Transaction
    suspend fun deleteMaterial(materialId: Long) {
        clearMaterialFromEntryLines(materialId)
        deleteMaterialRow(materialId)
    }

    @Query(
        """
        SELECT l.unitCostCents
        FROM material_entry_lines l
        INNER JOIN material_entries e ON e.entryId = l.entryId
        WHERE l.materialId = :materialId
        ORDER BY e.purchasedAt DESC, l.id DESC
        LIMIT 1
        """
    )
    suspend fun lastUnitCost(materialId: Long): Long?

    // ---- Material entries --------------------------------------------------

    @Query("SELECT * FROM material_entries ORDER BY purchasedAt DESC, entryId DESC")
    fun observeMaterialEntries(): Flow<List<MaterialEntry>>

    /**
     * Purchases with their lines, newest first.
     *
     * The supplies lists are small (a shop records tens of purchases a month, not thousands), so
     * loading the lines alongside the headers costs little and lets the detail screen open without
     * a second asynchronous lookup.
     */
    @Transaction
    @Query("SELECT * FROM material_entries ORDER BY purchasedAt DESC, entryId DESC")
    fun observeMaterialEntriesWithLines(): Flow<List<MaterialEntryWithLines>>

    @Query(
        "SELECT * FROM material_entries WHERE providerId = :providerId " +
            "ORDER BY purchasedAt DESC, entryId DESC"
    )
    fun observeMaterialEntriesForProvider(providerId: Long): Flow<List<MaterialEntry>>

    @Transaction
    @Query("SELECT * FROM material_entries WHERE entryId = :entryId")
    fun observeMaterialEntry(entryId: String): Flow<MaterialEntryWithLines?>

    @Query("SELECT * FROM material_entries WHERE entryId = :entryId")
    suspend fun findMaterialEntry(entryId: String): MaterialEntry?

    @Query("SELECT * FROM material_entry_lines WHERE entryId = :entryId ORDER BY id ASC")
    suspend fun findMaterialEntryLines(entryId: String): List<MaterialEntryLine>

    @Query("SELECT * FROM material_entries")
    suspend fun snapshotMaterialEntries(): List<MaterialEntry>

    @Query("SELECT * FROM material_entry_lines")
    suspend fun snapshotMaterialEntryLines(): List<MaterialEntryLine>

    @Insert
    suspend fun insertMaterialEntry(entry: MaterialEntry)

    @Insert
    suspend fun insertMaterialEntryLine(line: MaterialEntryLine): Long

    @Query("DELETE FROM material_entry_lines WHERE entryId = :entryId")
    suspend fun deleteMaterialEntryLines(entryId: String)

    @Query("DELETE FROM material_entries WHERE entryId = :entryId")
    suspend fun deleteMaterialEntryRow(entryId: String)

    /**
     * Records one purchase and increases each material's stock, all-or-nothing.
     *
     * Every validation happens before the first write: a duplicate line, a vanished material or a
     * negative cost must not leave a half-written invoice with some stock already added.
     */
    @Transaction
    suspend fun recordMaterialEntry(
        entryId: String,
        providerId: Long,
        purchasedAt: Long,
        reference: String = "",
        note: String = "",
        lines: List<NewMaterialLine>,
    ): RecordEntryResult {
        if (lines.isEmpty()) return RecordEntryResult.Empty
        val provider = findProvider(providerId) ?: return RecordEntryResult.ProviderNotFound

        val seen = HashSet<Long>()
        var totalCents = 0L
        val prepared = ArrayList<PreparedMaterialLine>(lines.size)
        for (line in lines) {
            val material = findMaterial(line.materialId)
                ?: return RecordEntryResult.MaterialNotFound(line.materialId)
            if (!seen.add(material.id)) {
                return RecordEntryResult.DuplicateMaterial(material.name)
            }
            if (line.quantity <= 0 || line.unitCostCents < 0L) {
                return RecordEntryResult.InvalidLine(
                    materialName = material.name,
                    quantity = line.quantity,
                    unitCostCents = line.unitCostCents,
                )
            }
            if (line.unitCostCents != 0L &&
                line.quantity.toLong() > Long.MAX_VALUE / line.unitCostCents
            ) {
                return RecordEntryResult.TotalTooLarge
            }
            val lineTotal = line.quantity.toLong() * line.unitCostCents
            if (lineTotal > Long.MAX_VALUE - totalCents) {
                return RecordEntryResult.TotalTooLarge
            }
            if (line.quantity > Int.MAX_VALUE - material.stock) {
                return RecordEntryResult.StockTooLarge(material.name)
            }
            totalCents += lineTotal
            prepared += PreparedMaterialLine(material, line.quantity, line.unitCostCents)
        }

        val entry = MaterialEntry(
            entryId = entryId,
            providerId = provider.id,
            providerName = provider.name,
            purchasedAt = purchasedAt,
            reference = reference.trim(),
            note = note.trim(),
            totalCents = totalCents,
            lineCount = prepared.size,
        )
        insertMaterialEntry(entry)
        for (line in prepared) {
            insertMaterialEntryLine(
                MaterialEntryLine(
                    entryId = entry.entryId,
                    materialId = line.material.id,
                    materialName = line.material.name,
                    unit = line.material.unit,
                    customUnit = line.material.customUnit,
                    quantity = line.quantity,
                    unitCostCents = line.unitCostCents,
                )
            )
            updateMaterial(
                line.material.copy(stock = line.material.stock + line.quantity)
            )
        }
        return RecordEntryResult.Success(entry)
    }

    /**
     * Deletes a purchase and reverses exactly the stock it added.
     *
     * Blocked when a material no longer holds enough units, because that purchase has already been
     * consumed. Nothing is written in that case — not even the lines — so the user can retry after
     * correcting the stock.
     */
    @Transaction
    suspend fun deleteMaterialEntry(entryId: String): DeleteEntryResult {
        val entry = findMaterialEntry(entryId) ?: return DeleteEntryResult.NotFound
        val lines = findMaterialEntryLines(entryId)

        val quantities = LinkedHashMap<Long, Int>()
        for (line in lines) {
            val materialId = line.materialId ?: continue
            quantities[materialId] = (quantities[materialId] ?: 0) + line.quantity
        }

        for ((materialId, requested) in quantities) {
            val material = findMaterial(materialId) ?: continue
            if (material.stock < requested) {
                return DeleteEntryResult.InsufficientStock(
                    materialName = material.name,
                    available = material.stock,
                    requested = requested,
                )
            }
        }

        for ((materialId, requested) in quantities) {
            val material = findMaterial(materialId) ?: continue
            updateMaterial(material.copy(stock = material.stock - requested))
        }
        deleteMaterialEntryLines(entryId)
        deleteMaterialEntryRow(entryId)
        return DeleteEntryResult.Success(entry)
    }

    // ---- Full snapshot (JSON backup) ---------------------------------------

    @Query("SELECT * FROM categories")
    suspend fun snapshotCategories(): List<Category>

    @Query("SELECT * FROM products")
    suspend fun snapshotProducts(): List<Product>

    @Query("SELECT * FROM sale_orders")
    suspend fun snapshotOrders(): List<SaleOrder>

    @Query("SELECT * FROM sales")
    suspend fun snapshotSales(): List<Sale>

    /**
     * Every row of every table, read inside a single transaction.
     *
     * The transaction is the point: without it a sale committed between the product read and the
     * sales read would produce a backup whose `products[].quantity` disagrees with its own ledger,
     * and no amount of care in the serialiser could fix that afterwards.
     *
     * Sale rows carry an empty `orderId` when they predate carts; they are split out here rather
     * than filtered in the exporter, so the split can be unit-tested directly.
     *
     * @param appVersionName recorded for support/debugging only — a reader must never branch on it.
     * @param includePromos when true the promo rules are included, so a restore re-arms the
     *   discounts they describe. Off by default at the call site: a backup is usually a *record* of
     *   sales already made, and silently reviving live discounts on restore is not what someone
     *   expects from "restore my data".
     */
    @Transaction
    suspend fun snapshot(
        settings: AppSettings,
        appVersionName: String,
        includePromos: Boolean = false,
    ): DatabaseSnapshot {
        val sales = snapshotSales()
        return DatabaseSnapshot(
            schemaVersion = StockDatabase.SCHEMA_VERSION,
            appVersionName = appVersionName,
            categories = snapshotCategories(),
            products = snapshotProducts(),
            orders = snapshotOrders(),
            saleLines = sales.filter { it.orderId.isNotEmpty() },
            legacySales = sales.filter { it.orderId.isEmpty() },
            settings = settings,
            promos = if (includePromos) snapshotPromos() else emptyList(),
            // Tags are always exported, whatever the `tagsEnabled` setting says. The setting
            // decides whether *this* device shows them; the file is meant to be a complete copy
            // of the data, and dropping them would make a backup taken with the feature off
            // silently destroy the tags of a device that had it on.
            tags = snapshotTags(),
            productTags = snapshotProductTags(),
            providers = snapshotProviders(),
            materials = snapshotMaterials(),
            materialEntries = snapshotMaterialEntries(),
            materialEntryLines = snapshotMaterialEntryLines(),
        )
    }

    // ---- Restore (JSON backup import) --------------------------------------

    /**
     * Writes a parsed backup into this database, all-or-nothing.
     *
     * **Every id is remapped.** The ids in the file are only meaningful inside that file; the
     * target already has rows and its autoincrement counters are elsewhere entirely. So categories
     * and products are inserted through the normal autoGenerate path and the old→new id mapping is
     * carried forward: `products[].categoryId` is translated through it, and every sale line is
     * rebuilt against the new product id. An order whose `orderId` already exists is merged into
     * the existing one by re-using the id — this is what makes importing the same file twice a
     * no-op rather than a duplicate ledger.
     *
     * A line whose product was not in the file (deleted before the export, or skipped as a
     * duplicate) cannot be inserted: `sales.productId` is a foreign key. Those lines are dropped
     * and counted in [ImportResult.orphanedLines] instead of failing the whole restore.
     *
     * @param contents the parsed document, see [JsonImport.readBackup].
     * @param replace when true the four tables are emptied first, so the database ends up exactly
     *   as it was on the exporting device. When false the backup is merged into the existing data.
     */
    @Transaction
    suspend fun restore(contents: BackupContents, replace: Boolean): ImportResult {
        if (replace) {
            // Order matters only for foreign keys; categories and tags are free. The assignment
            // table goes before tags and products, and purchase lines before their entries,
            // materials and providers — the cascades would handle it, but clearing in dependency
            // order keeps the statements honest on a database built before the cascade existed.
            clearAllProductTags()
            clearAllSales()
            clearAllOrders()
            clearAllProducts()
            clearAllCategories()
            clearAllTags()
            clearAllMaterialEntryLines()
            clearAllMaterialEntries()
            clearAllMaterials()
            clearAllProviders()
            clearAllPromos()
        }

        var categoriesAdded = 0
        val categoryIds = HashMap<Long, Long>()
        for (category in contents.categories) {
            // Re-use a category of the same name (it is unique in the table) rather than
            // dropping the row or creating a near-duplicate.
            val id = findCategoryByName(category.name)?.id
                ?: insertCategory(Category(name = category.name)).also { categoriesAdded++ }
            categoryIds[category.id] = id
        }

        var productsAdded = 0
        var productsUpdated = 0
        val productIds = HashMap<Long, Long>()
        for (product in contents.products) {
            val existing = findByBarcode(product.barcode)
            if (existing != null) {
                // Restoring must not resurrect stock that was already sold on this device after
                // the backup was taken, so quantity is only overwritten when replacing outright.
                updateProduct(
                    existing.copy(
                        name = product.name,
                        priceCents = product.priceCents,
                        quantity = if (replace) product.quantity else existing.quantity,
                        categoryId = product.categoryId?.let { categoryIds[it] } ?: existing.categoryId,
                        imagePath = product.imagePath ?: existing.imagePath,
                    )
                )
                productIds[product.id] = existing.id
                productsUpdated++
            } else {
                val id = insertProduct(
                    product.copy(
                        id = 0L,
                        categoryId = product.categoryId?.let { categoryIds[it] },
                    )
                )
                productIds[product.id] = id
                productsAdded++
            }
        }

        // ---- Promos -------------------------------------------------------
        // A restored promo is re-pointed at the ids this device actually wrote. A category or
        // product that did not survive the read leaves the rule dangling, so it is skipped rather
        // than inserted broken. On merge, an identical existing rule is not inserted twice; the
        // name alone is not enough because two promos may legitimately share one.
        var promosAdded = 0
        val existingPromoKeys = if (replace) {
            HashSet<String>()
        } else {
            snapshotPromos().mapTo(HashSet()) { it.importKey() }
        }
        for (promo in contents.promos) {
            val newCategoryId = promo.categoryId?.let { categoryIds[it] }
            if (promo.kind == PromoKind.CATEGORY && newCategoryId == null) continue
            val newProductIds = promo.productIdList.mapNotNull { productIds[it] }.distinct()
            if (promo.kind == PromoKind.PRODUCTS && newProductIds.isEmpty()) continue
            val restored = promo.copy(
                id = 0L,
                categoryId = if (promo.kind == PromoKind.CATEGORY) newCategoryId else null,
                productIdsCsv = if (promo.kind == PromoKind.PRODUCTS) {
                    newProductIds.joinToString(",")
                } else {
                    ""
                },
            )
            if (!existingPromoKeys.add(restored.importKey())) continue
            insertPromo(restored)
            promosAdded++
        }

        // ---- Tags ---------------------------------------------------------
        // After products, because the assignments reference them. Tags are matched by name the way
        // categories are, so importing the same file twice re-uses the existing rows instead of
        // failing on the UNIQUE index.
        val tagIds = HashMap<Long, Long>()
        for (tag in contents.tags) {
            val id = findTagByName(tag.name)?.id ?: insertTag(Tag(name = tag.name))
            tagIds[tag.id] = id
        }
        for (assignment in contents.productTags) {
            // An assignment is only meaningful if both ends survived the remap: a product that was
            // skipped as a duplicate, or a tag whose row never went in, leaves nothing to point at.
            val newProductId = productIds[assignment.productId] ?: continue
            val newTagId = tagIds[assignment.tagId] ?: continue
            runCatching { insertProductTag(ProductTag(newProductId, newTagId)) }
        }

        // ---- Supplies -----------------------------------------------------
        // Providers and materials are matched by name, like categories and tags, so importing the
        // same file twice updates the same rows instead of failing on their UNIQUE names.
        var providersAdded = 0
        var providersUpdated = 0
        val providerIds = HashMap<Long, Long>()
        for (provider in contents.providers) {
            val existing = findProviderByName(provider.name)
            if (existing != null) {
                updateProvider(
                    existing.copy(
                        name = provider.name,
                        phone = provider.phone,
                        address = provider.address,
                        notes = provider.notes,
                    )
                )
                providerIds[provider.id] = existing.id
                providersUpdated++
            } else {
                val id = insertProvider(provider.copy(id = 0L))
                providerIds[provider.id] = id
                providersAdded++
            }
        }

        var materialsAdded = 0
        var materialsUpdated = 0
        val materialIds = HashMap<Long, Long>()
        for (material in contents.materials) {
            val existing = findMaterialByName(material.name)
            if (existing != null) {
                updateMaterial(
                    existing.copy(
                        name = material.name,
                        unit = material.unit,
                        customUnit = material.customUnit,
                        // A merge must not resurrect stock used on this device after the backup
                        // was taken. Replacing outright is the one case where the file wins.
                        stock = if (replace) material.stock else existing.stock,
                        lowStockThreshold = material.lowStockThreshold,
                        notes = material.notes,
                    )
                )
                materialIds[material.id] = existing.id
                materialsUpdated++
            } else {
                val id = insertMaterial(material.copy(id = 0L))
                materialIds[material.id] = id
                materialsAdded++
            }
        }

        var entriesAdded = 0
        var entryLinesAdded = 0
        // **Stock is authoritative; entries are never replayed.** The material rows above already
        // carry the stock from the file (or this device's stock on merge). Adding each line's
        // quantity again would double-count every purchase.
        val entryLinesByEntry = contents.materialEntryLines.groupBy { it.entryId }
        for (entry in contents.materialEntries) {
            // entryId is the business key, so a repeated import skips the header *and* its lines.
            if (findMaterialEntry(entry.entryId) != null) continue
            insertMaterialEntry(
                entry.copy(
                    providerId = entry.providerId?.let { providerIds[it] },
                    // The snapshot name in the file is authoritative even when the provider row
                    // was deleted before the export.
                    providerName = entry.providerName,
                )
            )
            entriesAdded++
            for (line in entryLinesByEntry[entry.entryId].orEmpty()) {
                insertMaterialEntryLine(
                    line.copy(
                        id = 0L,
                        entryId = entry.entryId,
                        // A line whose material was deleted before the export becomes an unlinked
                        // historical line; its snapshotted name and unit still render correctly.
                        materialId = line.materialId?.let { materialIds[it] },
                    )
                )
                entryLinesAdded++
            }
        }

        var ordersAdded = 0
        var linesAdded = 0
        var orphaned = 0

        // Lines are grouped by order so the whole import can be driven from the orders the reader
        // validated; a line whose order was dropped is silently skipped by never being reached.
        val linesByOrder = contents.lines.groupBy { it.orderId }
        for (order in contents.orders) {
            if (findOrder(order.orderId) == null) {
                insertOrder(order)
                ordersAdded++
            }
            for (line in linesByOrder[order.orderId].orEmpty()) {
                val newProductId = productIds[line.productId]
                if (newProductId == null) {
                    orphaned++
                    continue
                }
                insertSale(
                    line.copy(
                        // Ids are reassigned; only the orderId, which is the real foreign key
                        // between a line and its receipt, is preserved.
                        id = 0L,
                        productId = newProductId,
                        orderId = order.orderId,
                    )
                )
                linesAdded++
            }
        }

        return ImportResult(
            categoriesAdded = categoriesAdded,
            productsAdded = productsAdded,
            productsUpdated = productsUpdated,
            ordersAdded = ordersAdded,
            linesAdded = linesAdded,
            orphanedLines = orphaned,
            skipped = contents.notes,
            replaced = replace,
            providersAdded = providersAdded,
            providersUpdated = providersUpdated,
            materialsAdded = materialsAdded,
            materialsUpdated = materialsUpdated,
            entriesAdded = entriesAdded,
            entryLinesAdded = entryLinesAdded,
            promosAdded = promosAdded,
        )
    }

    @Query("DELETE FROM sales")
    suspend fun clearAllSales()

    @Query("DELETE FROM sale_orders")
    suspend fun clearAllOrders()

    @Query("DELETE FROM products")
    suspend fun clearAllProducts()

    @Query("DELETE FROM categories")
    suspend fun clearAllCategories()

    @Query("DELETE FROM tags")
    suspend fun clearAllTags()

    @Query("DELETE FROM product_tags")
    suspend fun clearAllProductTags()

    @Query("DELETE FROM material_entry_lines")
    suspend fun clearAllMaterialEntryLines()

    @Query("DELETE FROM material_entries")
    suspend fun clearAllMaterialEntries()

    @Query("DELETE FROM materials")
    suspend fun clearAllMaterials()

    @Query("DELETE FROM providers")
    suspend fun clearAllProviders()

    @Query("DELETE FROM promos")
    suspend fun clearAllPromos()

    /** Adds stock back to an existing product (used when re-supplying via scanner or form). */
    @Transaction
    suspend fun restock(productId: Long, amount: Int): Product? {
        if (amount <= 0) return findById(productId)
        val product = findById(productId) ?: return null
        val updated = product.copy(quantity = product.quantity + amount)
        updateProduct(updated)
        return updated
    }
}

/** Outcome of an attempted sale. */
sealed interface SaleResult {
    data class Success(val product: Product) : SaleResult
    data class InsufficientStock(val product: Product) : SaleResult
    data object ProductNotFound : SaleResult
}

/** Outcome of attempting to complete a whole cart. */
sealed interface OrderResult {
    data class Success(val order: SaleOrder) : OrderResult
    data class InsufficientStock(val product: Product, val requested: Int) : OrderResult
    data class ProductNotFound(val productName: String) : OrderResult
}

/** Outcome of cancelling / voiding a previously recorded sale. */
sealed interface CancelResult {
    data class Success(val sale: Sale, val product: Product) : CancelResult
    data class ProductMissing(val sale: Sale) : CancelResult
    data object NotFound : CancelResult
}

/**
 * What a JSON restore actually wrote.
 *
 * The counts are of *rows this device did not already have*, so importing the same file twice
 * reports zero everywhere instead of doubling the ledger. [skipped] carries the reasons rows were
 * unreadable; [orphanedLines] counts lines that parsed but referenced a product that could not be
 * imported, and is kept separate because it is the one loss the user cannot see in the file.
 */
data class ImportResult(
    val categoriesAdded: Int,
    val productsAdded: Int,
    val productsUpdated: Int,
    val ordersAdded: Int,
    val linesAdded: Int,
    val orphanedLines: Int,
    /** Rows the reader dropped, with the reason. Empty when the file imported in full. */
    val skipped: List<String>,
    /** True when the database was emptied first, rather than merged into. */
    val replaced: Boolean,
    /** Supply rows added by this restore. Kept separate so the dialog can describe them. */
    val providersAdded: Int = 0,
    val providersUpdated: Int = 0,
    val materialsAdded: Int = 0,
    val materialsUpdated: Int = 0,
    val entriesAdded: Int = 0,
    val entryLinesAdded: Int = 0,
    /** Promo rules added by this restore. */
    val promosAdded: Int = 0,
) {
    val totalAdded: Int
        get() = categoriesAdded + productsAdded + ordersAdded + linesAdded +
            providersAdded + materialsAdded + entriesAdded + entryLinesAdded + promosAdded
}

/** Identity of a promo for "is this rule already here?" during a merge restore. */
private fun Promo.importKey(): String = listOf(
    name.trim().lowercase(),
    kind.name,
    categoryId?.toString().orEmpty(),
    productIdsCsv,
    thresholdQuantity.toString(),
    discountType.name,
    discountPercentBasisPoints.toString(),
    discountFixedCents.toString(),
    enabled.toString(),
    startsAt?.toString().orEmpty(),
    endsAt?.toString().orEmpty(),
).joinToString("|")
