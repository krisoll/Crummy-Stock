package com.example.stockapplication.data

/**
 * Reads a [JSON_FORMAT] backup back into domain objects.
 *
 * This is the counterpart to [toJson] and is deliberately *not* the inverse of the database
 * schema: it stops at `Category` / `Product` / `SaleOrder` / `Sale`, and it never talks to Room.
 * Keeping the parsing here means the whole format can be round-tripped in a plain JVM test, and
 * the only code that has to be trusted with a transaction is [StockDao.restore].
 *
 * ## What it refuses
 *
 * Only two things are fatal: the file is not a StockApplication backup ([JSON_FORMAT] does not
 * match), or its layout version is newer than [JSON_VERSION]. Everything else is recovered as far
 * as possible:
 *
 * - Fields the reader does not know are ignored, so an older build can read a newer file's
 *   payload as long as the version was not bumped.
 * - Rows that violate a constraint are **dropped and counted** rather than aborting the import:
 *   a duplicate barcode, a product pointing at a category that is not in the file, two orders
 *   sharing an id. Refusing the whole file because one row is bad would be worse than importing
 *   the rest and saying what was skipped.
 * - `sales[]` is not trusted for identity: it duplicates `orders[].lines` by design (see
 *   [toJson]), so the two are merged and the nested copy wins.
 *
 * ## What it never does
 *
 * It does not read `*Formatted` / `*Iso` presentation fields. Those exist for humans; the raw
 * integer next to them is the value. Parsing the pretty copy would let a locale change alter the
 * numbers.
 */
object JsonImport {

    /**
     * Parses [text] into a [BackupContents] ready to hand to [StockDao.restore].
     *
     * @throws JsonParseException when the document is not valid JSON, or [BackupFormatException]
     *   when it is JSON but not a backup this build understands.
     */
    fun readBackup(text: String): BackupContents {
        val root = JsonParser.parse(text)

        val document = root as? Map<*, *>
            ?: throw BackupFormatException("The file is not a backup: the top level is not an object.")
        @Suppress("UNCHECKED_CAST")
        val data = document as Map<String, Any?>

        val format = data.string("format")
        if (format != JSON_FORMAT) {
            throw BackupFormatException(
                "The file is not a StockApplication backup" +
                    (format?.let { " (found \"$it\")" } ?: " (no \"format\" field)")
            )
        }

        val version = data.int("version") ?: 1
        if (version > JSON_VERSION) {
            throw BackupFormatException(
                "This backup was written by a newer version of the app " +
                    "(format version $version, this build reads up to $JSON_VERSION)."
            )
        }

        val notes = Notes()
        val categories = readCategories(data.objArray("categories"), notes)
        val products = readProducts(data.objArray("products"), categories.map { it.id }.toSet(), notes)
        val promos = readPromos(
            rows = data.objArray("promos"),
            categoryIds = categories.map { it.id }.toSet(),
            productIds = products.map { it.id }.toSet(),
            notes = notes,
        )
        val lines = readSales(data, notes)
        // Only the orders whose lines actually made it in: a header with no lines would render as
        // an empty row on the history screen and confuse the totals.
        val orders = readOrders(data.objArray("orders"), lines.map { it.orderId }.toSet(), notes)

        val providers = readProviders(data.objArray("providers"), notes)
        val materials = readMaterials(data.objArray("materials"), notes)
        val materialLines = readMaterialEntryLines(
            data = data,
            materialNames = materials.associate { it.id to it.name },
            notes = notes,
        )
        val materialEntries = readMaterialEntries(
            rows = data.objArray("materialEntries"),
            providers = providers,
            materialLines = materialLines,
            notes = notes,
        )
        val entryIds = materialEntries.map { it.entryId }.toSet()
        val keptMaterialLines = materialLines.filter { it.entryId in entryIds }

        return BackupContents(
            schemaVersion = data.int("schemaVersion"),
            exportedAt = data.long("exportedAt"),
            categories = categories,
            products = products,
            orders = orders,
            lines = lines,
            notes = notes.toList(),
            settings = readSettings(data),
            tags = readTags(data.objArray("tags"), notes),
            productTags = readProductTags(data.objArray("productTags")),
            providers = providers,
            materials = materials,
            materialEntries = materialEntries,
            materialEntryLines = keptMaterialLines,
            promos = promos,
        )
    }

    /**
     * Reads the `tags` array. A missing one is simply "this file predates tags".
     *
     * Same rules as categories: a row without an id or with a blank name is dropped and noted, and
     * duplicate ids are refused rather than producing two rows that would both be inserted and
     * clash on the UNIQUE name index.
     */
    private fun readTags(
        rows: List<Map<String, Any?>>,
        notes: Notes,
    ): List<Tag> {
        val seenIds = HashSet<Long>()
        val seenNames = HashSet<String>()
        return rows.mapIndexedNotNull { index, row ->
            val id = row.long("id") ?: run {
                notes.skip("tag #$index", "no id")
                return@mapIndexedNotNull null
            }
            val name = row.string("name")?.trim().orEmpty()
            when {
                name.isEmpty() -> {
                    notes.skip("tag #$index", "blank name")
                    null
                }
                !seenIds.add(id) -> {
                    notes.skip("tag #$index", "duplicate id $id")
                    null
                }
                !seenNames.add(name.lowercase()) -> {
                    notes.skip("tag \"$name\"", "duplicate name")
                    null
                }
                else -> Tag(id = id, name = name)
            }
        }
    }

    /** Reads the `providers` array. A missing one means the file predates supplies. */
    private fun readProviders(
        rows: List<Map<String, Any?>>,
        notes: Notes,
    ): List<Provider> {
        val seenIds = HashSet<Long>()
        val seenNames = HashSet<String>()
        return rows.mapIndexedNotNull { index, row ->
            val id = row.long("id") ?: run {
                notes.skip("provider #$index", "no id")
                return@mapIndexedNotNull null
            }
            val name = row.string("name")?.trim().orEmpty()
            when {
                name.isEmpty() -> {
                    notes.skip("provider #$index", "blank name")
                    null
                }
                !seenIds.add(id) -> {
                    notes.skip("provider #$index", "duplicate id $id")
                    null
                }
                !seenNames.add(name.lowercase()) -> {
                    notes.skip("provider \"$name\"", "duplicate name")
                    null
                }
                else -> Provider(
                    id = id,
                    name = name,
                    phone = row.string("phone")?.trim().orEmpty(),
                    address = row.string("address")?.trim().orEmpty(),
                    notes = row.string("notes")?.trim().orEmpty(),
                )
            }
        }
    }

    /** Reads the `materials` array. Missing arrays import as an empty supplies catalogue. */
    private fun readMaterials(
        rows: List<Map<String, Any?>>,
        notes: Notes,
    ): List<Material> {
        val seenIds = HashSet<Long>()
        val seenNames = HashSet<String>()
        return rows.mapIndexedNotNull { index, row ->
            val id = row.long("id") ?: run {
                notes.skip("material #$index", "no id")
                return@mapIndexedNotNull null
            }
            val name = row.string("name")?.trim().orEmpty()
            when {
                name.isEmpty() -> {
                    notes.skip("material #$index", "blank name")
                    null
                }
                !seenIds.add(id) -> {
                    notes.skip("material #$index", "duplicate id $id")
                    null
                }
                !seenNames.add(name.lowercase()) -> {
                    notes.skip("material \"$name\"", "duplicate name")
                    null
                }
                else -> {
                    val rawUnit = row.string("unit")?.trim().orEmpty()
                    val knownUnit = MaterialUnit.entries.firstOrNull {
                        it.name.equals(rawUnit, ignoreCase = true)
                    }
                    val unit = knownUnit ?: MaterialUnit.OTHER
                    val customUnit = row.string("customUnit")?.trim().orEmpty()
                    Material(
                        id = id,
                        name = name,
                        unit = unit,
                        // A unit from a newer build is kept verbatim as the custom label rather
                        // than silently collapsing to "piece".
                        customUnit = customUnit.ifEmpty {
                            if (knownUnit == null && rawUnit.isNotEmpty()) rawUnit else ""
                        },
                        stock = (row.int("stock") ?: 0).coerceAtLeast(0),
                        lowStockThreshold = (row.int("lowStockThreshold") ?: 5)
                            .coerceAtLeast(0),
                        notes = row.string("notes")?.trim().orEmpty(),
                    )
                }
            }
        }
    }

    /**
     * Reads every purchase line from both the nested and flat arrays.
     *
     * The nested copy wins, exactly like sale lines. A line with no id or no entry header is
     * unreadable and is dropped; so is a second line for the same material in one entry, which
     * would otherwise abort the restore on the unique index.
     */
    private fun readMaterialEntryLines(
        data: Map<String, Any?>,
        materialNames: Map<Long, String>,
        notes: Notes,
    ): List<MaterialEntryLine> {
        val byId = LinkedHashMap<Long, MaterialEntryLine>()

        fun add(row: Map<String, Any?>, parentEntryId: String?, label: String) {
            val id = row.long("id") ?: run {
                notes.skip(label, "no id")
                return
            }
            val entryId = (parentEntryId ?: row.string("entryId")).orEmpty().trim()
            if (entryId.isEmpty()) {
                notes.skip(label, "no entryId")
                return
            }
            val materialId = row.long("materialId")
            val materialName = row.string("materialName")?.trim().orEmpty().ifEmpty {
                materialId?.let { materialNames[it] }.orEmpty()
            }
            if (materialName.isEmpty()) {
                notes.skip(label, "no material name")
                return
            }
            val quantity = row.int("quantity") ?: 1
            if (quantity <= 0) {
                notes.skip(label, "non-positive quantity")
                return
            }
            val unitCostCents = (row.long("unitCostCents") ?: 0L).coerceAtLeast(0L)
            if (unitCostCents != 0L && quantity.toLong() > Long.MAX_VALUE / unitCostCents) {
                notes.skip(label, "line total is too large")
                return
            }

            val rawUnit = row.string("unit")?.trim().orEmpty()
            val knownUnit = MaterialUnit.entries.firstOrNull {
                it.name.equals(rawUnit, ignoreCase = true)
            }
            val unit = knownUnit ?: MaterialUnit.OTHER
            val customUnit = row.string("customUnit")?.trim().orEmpty()
            val line = MaterialEntryLine(
                id = id,
                entryId = entryId,
                materialId = materialId,
                materialName = materialName,
                unit = unit,
                customUnit = customUnit.ifEmpty {
                    if (knownUnit == null && rawUnit.isNotEmpty()) rawUnit else ""
                },
                quantity = quantity,
                unitCostCents = unitCostCents,
            )
            // Nested is read first and wins over the flat duplicate.
            byId.getOrPut(id) { line }
        }

        data.objArray("materialEntries").forEachIndexed { entryIndex, entry ->
            val entryId = entry.string("entryId").orEmpty()
            val lines = when (val value = entry["lines"]) {
                null, JsonParser.JsonNull -> return@forEachIndexed
                is List<*> -> value
                else -> {
                    notes.skip("material entry #$entryIndex", "\"lines\" is not an array")
                    return@forEachIndexed
                }
            }
            lines.forEachIndexed { lineIndex, item ->
                @Suppress("UNCHECKED_CAST")
                val row = item as? Map<String, Any?>
                if (row == null) {
                    notes.skip(
                        "material entry #$entryIndex line #$lineIndex",
                        "not an object"
                    )
                } else {
                    add(row, entryId, "material entry #$entryIndex line #$lineIndex")
                }
            }
        }

        data.objArray("materialEntryLines").forEachIndexed { index, row ->
            add(row, parentEntryId = null, label = "material line #$index")
        }

        // (entryId, materialId) is unique in the table. NULL material ids are allowed to repeat,
        // because SQLite treats each NULL as distinct; those are lines whose material was deleted.
        val seen = HashSet<Pair<String, Long>>()
        return byId.values.filter { line ->
            val materialId = line.materialId ?: return@filter true
            if (seen.add(line.entryId to materialId)) {
                true
            } else {
                notes.skip(
                    "material line \"${line.materialName}\"",
                    "duplicate material in entry ${line.entryId}"
                )
                false
            }
        }
    }

    /**
     * Reads purchase headers whose lines survived.
     *
     * The stored totals are deliberately ignored: the total is defined as the sum of its lines, and
     * recomputing it means a dropped or hand-edited line cannot leave a header that disagrees with
     * what the detail screen shows. An entry with no readable lines is dropped, like an order with
     * no lines.
     */
    private fun readMaterialEntries(
        rows: List<Map<String, Any?>>,
        providers: List<Provider>,
        materialLines: List<MaterialEntryLine>,
        notes: Notes,
    ): List<MaterialEntry> {
        val providerNames = providers.associate { it.id to it.name }
        val linesByEntry = materialLines.groupBy { it.entryId }
        val seen = HashSet<String>()
        return rows.mapIndexedNotNull { index, row ->
            val entryId = row.string("entryId")?.trim().orEmpty()
            if (entryId.isEmpty()) {
                notes.skip("material entry #$index", "no entryId")
                return@mapIndexedNotNull null
            }
            if (!seen.add(entryId)) {
                notes.skip("material entry $entryId", "duplicate entryId")
                return@mapIndexedNotNull null
            }
            val lines = linesByEntry[entryId].orEmpty()
            if (lines.isEmpty()) {
                notes.skip("material entry $entryId", "none of its lines could be read")
                return@mapIndexedNotNull null
            }
            val providerId = row.long("providerId")
            val providerName = row.string("providerName")?.trim().orEmpty().ifEmpty {
                providerId?.let { providerNames[it] }.orEmpty()
            }
            if (providerName.isEmpty()) {
                notes.skip("material entry $entryId", "no provider name")
                return@mapIndexedNotNull null
            }
            var totalCents = 0L
            for (line in lines) {
                val lineTotal = line.totalCents
                if (lineTotal > Long.MAX_VALUE - totalCents) {
                    notes.skip("material entry $entryId", "total is too large")
                    return@mapIndexedNotNull null
                }
                totalCents += lineTotal
            }
            MaterialEntry(
                entryId = entryId,
                // A provider id that is not in the file is dangling. The snapshotted name still
                // renders, so the purchase is imported unlinked rather than dropped.
                providerId = providerId?.takeIf { it in providerNames },
                providerName = providerName,
                purchasedAt = row.long("purchasedAt") ?: 0L,
                reference = row.string("reference")?.trim().orEmpty(),
                note = row.string("note")?.trim().orEmpty(),
                totalCents = totalCents,
                lineCount = lines.size,
            )
        }
    }

    /**
     * Reads the `productTags` array.
     *
     * Kept deliberately quiet: an assignment pointing at a product or tag that did not survive the
     * restore is simply dropped by [StockDao.restore], because it is not user-visible data on its
     * own — a note about it would be noise in a dialog that is already listing lost rows.
     */
    private fun readProductTags(rows: List<Map<String, Any?>>): List<ProductTag> =
        rows.mapNotNull { row ->
            val productId = row.long("productId") ?: return@mapNotNull null
            val tagId = row.long("tagId") ?: return@mapNotNull null
            ProductTag(productId = productId, tagId = tagId)
        }.distinct()

    /**
     * Reads the `promos` array. A missing one means "this file predates promos" or "the exporter
     * was told not to include them", and both are simply an empty list.
     *
     * The ids in the file are kept here and remapped by [StockDao.restore], so a product promo is
     * validated against the products that actually survived the read; otherwise a restored rule
     * would point at a row this device never received.
     *
     * `productIds` is accepted both as a JSON array (what this build exports) and as the
     * comma-separated string older builds wrote, including the literal `"[]"`. Accepting both
     * costs nothing and means a backup made by the desktop app round-trips too.
     */
    private fun readPromos(
        rows: List<Map<String, Any?>>,
        categoryIds: Set<Long>,
        productIds: Set<Long>,
        notes: Notes,
    ): List<Promo> {
        val seenIds = HashSet<Long>()
        return rows.mapIndexedNotNull { index, row ->
            val id = row.long("id") ?: run {
                notes.skip("promo #$index", "no id")
                return@mapIndexedNotNull null
            }
            val name = row.string("name")?.trim().orEmpty()
            if (name.isEmpty()) {
                notes.skip("promo #$index", "blank name")
                return@mapIndexedNotNull null
            }
            if (!seenIds.add(id)) {
                notes.skip("promo #$index", "duplicate id $id")
                return@mapIndexedNotNull null
            }

            val kindName = row.string("kind")?.trim().orEmpty()
            val kind = PromoKind.entries.firstOrNull {
                it.name.equals(kindName, ignoreCase = true)
            } ?: PromoKind.CATEGORY

            val categoryId = row.long("categoryId")
            if (kind == PromoKind.CATEGORY && (categoryId == null || categoryId !in categoryIds)) {
                notes.skip("promo \"$name\"", "category not in the file")
                return@mapIndexedNotNull null
            }

            val keptProductIds = if (kind == PromoKind.PRODUCTS) {
                readProductIdList(row["productIds"]).filter { it in productIds }.distinct()
            } else {
                emptyList()
            }
            if (kind == PromoKind.PRODUCTS && keptProductIds.isEmpty()) {
                notes.skip("promo \"$name\"", "none of its products could be read")
                return@mapIndexedNotNull null
            }

            val discountName = row.string("discountType")?.trim().orEmpty()
            val discountType = DiscountType.entries.firstOrNull {
                it.name.equals(discountName, ignoreCase = true)
            } ?: DiscountType.PERCENT

            Promo(
                id = id,
                name = name,
                kind = kind,
                categoryId = if (kind == PromoKind.CATEGORY) categoryId else null,
                productIdsCsv = keptProductIds.joinToString(","),
                thresholdQuantity = (row.int("thresholdQuantity") ?: 1).coerceAtLeast(1),
                discountType = discountType,
                discountPercentBasisPoints = (row.int("discountPercentBasisPoints") ?: 0)
                    .coerceIn(0, 10_000),
                discountFixedCents = (row.long("discountFixedCents") ?: 0L).coerceAtLeast(0L),
                enabled = row.boolean("enabled", default = true),
                startsAt = row.long("startsAt"),
                endsAt = row.long("endsAt"),
            )
        }
    }

    /** Reads a promo's product list from either a JSON array or the legacy CSV string. */
    private fun readProductIdList(raw: Any?): List<Long> = when (raw) {
        null, JsonParser.JsonNull -> emptyList()
        is List<*> -> raw.mapNotNull { item ->
            when (item) {
                is JsonParser.JsonNumber -> item.toLongOrNull()
                is String -> item.trim().toLongOrNull()
                else -> null
            }
        }
        is String -> raw
            .replace("[", "")
            .replace("]", "")
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
        is JsonParser.JsonNumber -> listOfNotNull(raw.toLongOrNull())
        else -> emptyList()
    }.distinct()

    /**
     * Reads the `settings` object, or returns null when the file carries nothing usable.
     *
     * Every read is defensive, and more so than for the data tables, because the consequence of
     * a strict read here is disproportionate: a hand-edited or future-written settings block would
     * abort an otherwise perfectly good restore of the user's products and sales. So a missing
     * block, a wrong type, or an out-of-range number produces a null field — meaning "the file did
     * not say" — rather than an exception.
     *
     * Two groups are deliberately **not** read: `deviceSpecific` (see [ImportedSettings]) and any
     * key this build does not know, which is how a newer file still imports into an older app.
     */
    private fun readSettings(data: Map<String, Any?>): ImportedSettings? {
        val settings = data.obj("settings") ?: return null

        // Sub-blocks are optional: a v1 file from before the grouping has every value at the top
        // level, and still has to be readable.
        val appearance = settings.obj("appearance")
        val startup = settings.obj("startupScreen")
        val scanner = settings.obj("scanner")

        fun bool(key: String) = settings.softBoolean(key)
        fun appearanceBool(key: String) = appearance?.softBoolean(key)
        fun startupBool(key: String) = startup?.softBoolean(key)
        fun scannerBool(key: String) = scanner?.softBoolean(key)
        fun int(key: String) = settings.softInt(key)
        fun startupInt(key: String) = startup?.softInt(key)
        fun scannerInt(key: String) = scanner?.softInt(key)
        fun long(key: String) = settings.softLong(key)
        fun appearanceLong(key: String) = appearance?.softLong(key)

        val imported = ImportedSettings(
            // ---- Shop -------------------------------------------------------
            // Clamped rather than rejected: a rate of 200% is nonsense but losing the whole
            // restore over it is worse, and 100% is the same bound the settings screen enforces.
            taxRateBasisPoints = int("taxRateBasisPoints")?.coerceIn(0, 10_000),
            cardFeeRateBasisPoints = int("cardFeeRateBasisPoints")?.coerceIn(0, 10_000),
            cardFeeFixedCents = long("cardFeeFixedCents")
                ?.coerceIn(0L, MaxCardFeeFixedCents),
            showMoneyDecimals = bool("showMoneyDecimals"),
            // Kept as a raw id; `applyImported` normalises it through `Currency.fromId`.
            currencyId = settings.softString("currencyId"),

            languageTag = settings.softString("languageTag"),

            // ---- Appearance --------------------------------------------------
            // Enum-backed values are kept as raw ids and normalised by the writer, so a file from
            // a build with a font this one has never shipped falls back to the default rather
            // than being dropped on the floor here.
            fontFamilyId = appearance?.softString("fontFamilyId"),
            textSizeId = appearance?.softString("textSizeId"),
            motionSpeedId = appearance?.softString("motionSpeedId"),
            useAppBackgroundColor = appearanceBool("useAppBackgroundColor"),
            backgroundColorArgb = appearanceLong("backgroundColorArgb"),
            useAppButtonColor = appearanceBool("useAppButtonColor"),
            buttonColorArgb = appearanceLong("buttonColorArgb"),
            useAppPanelColor = appearanceBool("useAppPanelColor"),
            panelColorArgb = appearanceLong("panelColorArgb"),
            useAppTextColor = appearanceBool("useAppTextColor"),
            textColorArgb = appearanceLong("textColorArgb"),
            useAppHighlightColor = appearanceBool("useAppHighlightColor"),
            highlightColorArgb = appearanceLong("highlightColorArgb"),
            activeThemeId = appearance?.softString("activeThemeId"),
            savedThemes = readSavedThemes(appearance),

            // ---- Startup screen ----------------------------------------------
            showLoadingScreen = startupBool("showLoadingScreen"),
            loadingIconId = startup?.softString("loadingIconId"),
            loadingSpinId = startup?.softString("loadingSpinId"),
            loadingCircleId = startup?.softString("loadingCircleId"),
            loadingImageSizeDp = startupInt("loadingImageSizeDp")
                ?.coerceIn(MinLoadingImageSizeDp, MaxLoadingImageSizeDp),
            loadingCircleSizeDp = startupInt("loadingCircleSizeDp")
                ?.coerceIn(MinLoadingCircleSizeDp, MaxLoadingCircleSizeDp),

            // ---- Scanner -----------------------------------------------------
            scannerBeepEnabled = scannerBool("scannerBeepEnabled"),
            scannerBeepId = scanner?.softString("scannerBeepId"),
            saleSoundEnabled = scannerBool("saleSoundEnabled"),
            saleSoundId = scanner?.softString("saleSoundId"),
            saleAnimationId = scanner?.softString("saleAnimationId"),
            saleAnimationSizeDp = scannerInt("saleAnimationSizeDp")
                ?.coerceIn(MinSaleAnimationSizeDp, MaxSaleAnimationSizeDp),
            scanDelayMillis = scannerInt("scanDelayMillis")
                ?.coerceIn(MinScanDelayMillis, MaxScanDelayMillis),
        productChangeDelayMillis = scannerInt("productChangeDelayMillis")
                ?.coerceIn(MinProductChangeDelayMillis, MaxProductChangeDelayMillis),
            showScanPopup = scannerBool("showScanPopup"),
            scanVibrationEnabled = scannerBool("scanVibrationEnabled"),
            scanVibrationId = scanner?.softString("scanVibrationId"),
            // 0 is the platform's "hardware default", which is not a strength the UI offers, so
            // the floor here is 1 to match the slider.
            scanVibrationStrength = scannerInt("scanVibrationStrength")
                ?.coerceIn(MinScanVibrationStrength, MaxScanVibrationStrength),
        )

        // A block that parsed to nothing is the same as no block: the dialog must not offer to
        // restore settings that would change nothing.
        return imported.takeUnless { it.isEmpty }
    }

    /**
     * Reads the saved custom themes, or null when the file has none.
     *
     * An absent array and an empty one are treated the same on purpose: this is a merge, not a
     * destructive replace, so "no saved themes in this file" must leave the phone's own list
     * alone. That matters because a desktop that has never imported a phone backup still writes
     * an empty list, and a stock transfer must not wipe the phone's themes as a side effect.
     *
     * A malformed row is dropped rather than failing the whole settings import, for the same
     * reason the other lenient settings readers are lenient: a backup that restores the shop's
     * colours without one broken extra theme is far better than no restore at all.
     */
    private fun readSavedThemes(appearance: Map<String, Any?>?): List<ColorTheme>? {
        if (appearance == null || !appearance.containsKey("savedThemes")) return null
        val seen = HashSet<String>()
        return appearance.objArray("savedThemes").mapNotNull { row ->
            val id = row.string("id")?.trim().orEmpty()
            val name = row.string("name")?.trim().orEmpty()
            val background = row.long("backgroundArgb")
            val button = row.long("buttonArgb")
            val panel = row.long("panelArgb")
            val highlight = row.long("highlightArgb")
            if (
                id.isEmpty() ||
                name.isEmpty() ||
                background == null ||
                button == null ||
                panel == null ||
                highlight == null ||
                !seen.add(id)
            ) {
                return@mapNotNull null
            }
            ColorTheme(
                id = id,
                name = name,
                backgroundArgb = background,
                buttonArgb = button,
                panelArgb = panel,
                highlightArgb = highlight,
                textArgb = row.long("textArgb"),
            )
        }.takeIf { it.isNotEmpty() }
    }

    private fun readCategories(
        rows: List<Map<String, Any?>>,
        notes: Notes,
    ): List<Category> {
        val seenIds = HashSet<Long>()
        val seenNames = HashSet<String>()
        return rows.mapIndexedNotNull { index, row ->
            val id = row.long("id") ?: run {
                notes.skip("category #$index", "no id")
                return@mapIndexedNotNull null
            }
            val name = row.string("name")?.trim().orEmpty()
            when {
                name.isEmpty() -> {
                    notes.skip("category #$index", "blank name")
                    null
                }
                !seenIds.add(id) -> {
                    notes.skip("category #$index", "duplicate id $id")
                    null
                }
                // The table has a unique index on the name, so a second "Drinks" cannot exist.
                !seenNames.add(name.lowercase()) -> {
                    notes.skip("category \"$name\"", "duplicate name")
                    null
                }
                else -> Category(id = id, name = name)
            }
        }
    }

    private fun readProducts(
        rows: List<Map<String, Any?>>,
        categoryIds: Set<Long>,
        notes: Notes,
    ): List<Product> {
        val seenIds = HashSet<Long>()
        val seenBarcodes = HashSet<String>()
        return rows.mapIndexedNotNull { index, row ->
            val id = row.long("id") ?: run {
                notes.skip("product #$index", "no id")
                return@mapIndexedNotNull null
            }
            val barcode = row.string("barcode")?.trim().orEmpty()
            val name = row.string("name")?.trim().orEmpty()
            // A category id that is not in the file is a dangling reference, so the product is
            // imported as uncategorised instead of pointing at a row that will not exist.
            val categoryId = row.long("categoryId")?.takeIf { it in categoryIds }
            when {
                barcode.isEmpty() -> {
                    notes.skip("product #$index", "no barcode")
                    null
                }
                name.isEmpty() -> {
                    notes.skip("product \"$barcode\"", "no name")
                    null
                }
                !seenIds.add(id) -> {
                    notes.skip("product #$index", "duplicate id $id")
                    null
                }
                // Barcodes are unique in the table and are what the scanner looks up.
                !seenBarcodes.add(barcode) -> {
                    notes.skip("product \"$name\"", "duplicate barcode $barcode")
                    null
                }
                else -> Product(
                    id = id,
                    barcode = barcode,
                    name = name,
                    priceCents = (row.long("priceCents") ?: 0L).coerceAtLeast(0L),
                    quantity = (row.int("quantity") ?: 0).coerceAtLeast(0),
                    categoryId = categoryId,
                    imagePath = row.string("imagePath")?.trim()?.takeIf { it.isNotEmpty() },
                    // Absent in every backup written before favourites existed, and in any file
                    // hand-edited to drop it — `boolean` already treats a missing key as false
                    // rather than as an error.
                    favourite = row.boolean("favourite"),
                )
            }
        }
    }

    /**
     * Every sale line in the document, from both the nested and the flat arrays.
     *
     * `orders[].lines` is authoritative: those rows are the ones whose order header is present.
     * `sales[]` repeats them and adds nothing that is not already nested, so it is merged in by
     * (id, orderId) with the nested copy winning. `legacySales[]` is deliberately **not** read —
     * it duplicates `sales[]` and is only split out so an external reader can tell the cart-era
     * rows from the ones that predate carts.
     */
    private fun readSales(data: Map<String, Any?>, notes: Notes): List<Sale> {
        val byKey = LinkedHashMap<Pair<Long, String>, Sale>()

        fun add(row: Map<String, Any?>, parentOrderId: String?, label: String) {
            val id = row.long("id") ?: run {
                notes.skip(label, "no id")
                return
            }
            val productId = row.long("productId") ?: run {
                notes.skip(label, "no productId")
                return
            }
            val quantity = row.int("quantity") ?: 1
            if (quantity <= 0) {
                notes.skip(label, "non-positive quantity")
                return
            }
            val orderId = (parentOrderId ?: row.string("orderId")).orEmpty()
            val sale = Sale(
                id = id,
                productId = productId,
                productName = row.string("productName")?.trim().orEmpty(),
                barcode = row.string("barcode")?.trim().orEmpty(),
                quantity = quantity,
                unitPriceCents = (row.long("unitPriceCents") ?: 0L).coerceAtLeast(0L),
                soldAt = row.long("soldAt") ?: 0L,
                orderId = orderId,
            )
            // getOrPut, not put: the nested copy is read first and must win over the flat array.
            byKey.getOrPut(sale.id to sale.orderId) { sale }
        }

        data.objArray("orders").forEachIndexed { orderIndex, order ->
            val orderId = order.string("orderId").orEmpty()
            val lines = when (val value = order["lines"]) {
                null, JsonParser.JsonNull -> return@forEachIndexed
                is List<*> -> value
                else -> {
                    notes.skip("order #$orderIndex", "\"lines\" is not an array")
                    return@forEachIndexed
                }
            }
            lines.forEachIndexed { lineIndex, item ->
                @Suppress("UNCHECKED_CAST")
                val row = item as? Map<String, Any?>
                if (row == null) {
                    notes.skip("order #$orderIndex line #$lineIndex", "not an object")
                } else {
                    add(row, orderId, "order #$orderIndex line #$lineIndex")
                }
            }
        }

        data.objArray("sales").forEachIndexed { index, row ->
            add(row, parentOrderId = null, label = "sale #$index")
        }

        return byKey.values.toList()
    }

    /**
     * Order headers whose lines survived.
     *
     * [lineOrderIds] is what makes this safe: a header is dropped when none of its lines made it
     * in, so the history screen never shows an order that cannot be opened.
     */
    private fun readOrders(
        rows: List<Map<String, Any?>>,
        lineOrderIds: Set<String>,
        notes: Notes,
    ): List<SaleOrder> {
        val seen = HashSet<String>()
        return rows.mapIndexedNotNull { index, row ->
            val orderId = row.string("orderId")?.trim().orEmpty()
            when {
                orderId.isEmpty() -> {
                    notes.skip("order #$index", "no orderId")
                    null
                }
                !seen.add(orderId) -> {
                    notes.skip("order $orderId", "duplicate orderId")
                    null
                }
                orderId !in lineOrderIds -> {
                    notes.skip("order $orderId", "none of its lines could be read")
                    null
                }
                else -> {
                    val paymentMethod = when (row.string("paymentMethod")?.uppercase()) {
                        "CARD" -> PaymentMethod.CARD
                        // Cash is the safe default: it never adds a card fee to a total that did
                        // not have one.
                        else -> PaymentMethod.CASH
                    }
                    SaleOrder(
                        orderId = orderId,
                        soldAt = row.long("soldAt") ?: 0L,
                        paymentMethod = paymentMethod,
                        itemsSubtotalCents = (row.long("itemsSubtotalCents") ?: 0L).coerceAtLeast(0L),
                        taxRateBasisPoints = (row.int("taxRateBasisPoints") ?: 0).coerceIn(0, 10_000),
                        taxCents = (row.long("taxCents") ?: 0L).coerceAtLeast(0L),
                        cardFeeRateBasisPoints =
                            (row.int("cardFeeRateBasisPoints") ?: 0).coerceIn(0, 10_000),
                        cardFeeFixedCents = (row.long("cardFeeFixedCents") ?: 0L).coerceAtLeast(0L),
                        cardFeeCents = (row.long("cardFeeCents") ?: 0L).coerceAtLeast(0L),
                        totalCents = (row.long("totalCents") ?: 0L).coerceAtLeast(0L),
                        itemCount = (row.int("itemCount") ?: 0).coerceAtLeast(0),
                        note = row.string("note").orEmpty().take(MaxOrderNoteLength),
                    )
                }
            }
        }
    }

    /** Collects "row skipped, because" pairs so the UI can report them in one message. */
    private class Notes {
        private val items = mutableListOf<String>()

        fun skip(what: String, why: String) {
            items += "$what ($why)"
        }

        fun toList(): List<String> = items
    }
}

/**
 * A parsed backup, ready to be written.
 *
 * The ids are the ones from the file and are only meaningful *within* this object: [StockDao.restore]
 * remaps them onto fresh primary keys, because the target database has whatever rows it already
 * had. [notes] lists every row that was skipped and why.
 */
data class BackupContents(
    /** Room schema version of the exporting build, or null when the file predates the field. */
    val schemaVersion: Int?,
    /** When the file was written, in epoch millis, or null when absent. */
    val exportedAt: Long?,
    val categories: List<Category>,
    val products: List<Product>,
    val orders: List<SaleOrder>,
    /** Every line, keyed by [Sale.orderId] to an entry in [orders]. */
    val lines: List<Sale>,
    /** Tags, or empty when the file predates them. */
    val tags: List<Tag> = emptyList(),
    /** Product↔tag assignments, using the ids from the file. */
    val productTags: List<ProductTag> = emptyList(),
    /** Human-readable reasons rows were dropped. Empty when the file was imported in full. */
    val notes: List<String>,
    /**
     * Settings carried by the file, or null when it had none this build can apply.
     *
     * Not part of [isEmpty]: settings are a separate choice at restore time, and a file with
     * settings but no rows is still "empty" for the purposes of the import dialog.
     */
    val settings: ImportedSettings? = null,
    /** Suppliers, or empty when the file predates supplies. */
    val providers: List<Provider> = emptyList(),
    /** Supplies catalogue, or empty when the file predates supplies. */
    val materials: List<Material> = emptyList(),
    /** Purchase headers, or empty when the file predates supplies. */
    val materialEntries: List<MaterialEntry> = emptyList(),
    /** Purchase lines belonging to [materialEntries]. */
    val materialEntryLines: List<MaterialEntryLine> = emptyList(),
    /** Promo rules, or empty when the file predates promos or omitted them. */
    val promos: List<Promo> = emptyList(),
) {
    val isEmpty: Boolean
        get() = categories.isEmpty() &&
            products.isEmpty() &&
            orders.isEmpty() &&
            lines.isEmpty() &&
            providers.isEmpty() &&
            materials.isEmpty() &&
            materialEntries.isEmpty() &&
            materialEntryLines.isEmpty() &&
            promos.isEmpty()

    /** Whether this file contains anything for the supplies section. */
    val hasSupplies: Boolean
        get() = providers.isNotEmpty() ||
            materials.isNotEmpty() ||
            materialEntries.isNotEmpty() ||
            materialEntryLines.isNotEmpty()
}

// ---- Lenient readers for the settings block --------------------------------
//
// The strict readers in JsonParser.kt throw on a wrong type, which is right for the data tables:
// a product with a non-numeric price is a broken row and should be reported. For settings it is
// wrong. A malformed colour, a text size from a future build, a number someone edited by hand —
// none of these are grounds to abandon the user's products and sales. These return null instead,
// which the merge reads as "the file did not say".

/** Reads [key] as a Boolean, or null when absent or not parseable as one. */
internal fun Map<String, Any?>.softBoolean(key: String): Boolean? = when (val value = this[key]) {
    null, JsonParser.JsonNull -> null
    is Boolean -> value
    is JsonParser.JsonNumber -> value.toBooleanOrNull()
    is String -> when (value.trim().lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no", "" -> false
        else -> null
    }
    else -> null
}

/** Reads [key] as an Int, or null when absent or not a whole number. */
internal fun Map<String, Any?>.softInt(key: String): Int? = when (val value = this[key]) {
    null, JsonParser.JsonNull -> null
    is JsonParser.JsonNumber -> value.toIntOrNull()
    is String -> value.trim().toIntOrNull()
    else -> null
}

/** Reads [key] as a Long, or null when absent or not a whole number. */
internal fun Map<String, Any?>.softLong(key: String): Long? = when (val value = this[key]) {
    null, JsonParser.JsonNull -> null
    is JsonParser.JsonNumber -> value.toLongOrNull()
    is String -> value.trim().toLongOrNull()
    else -> null
}

/** Reads [key] as a String, or null when absent. A number is accepted for its literal text. */
internal fun Map<String, Any?>.softString(key: String): String? = when (val value = this[key]) {
    null, JsonParser.JsonNull -> null
    is String -> value
    is JsonParser.JsonNumber -> value.raw
    is Boolean -> value.toString()
    else -> null
}

/** The document is JSON but not a backup this build can import. */
class BackupFormatException(message: String) : Exception(message)
