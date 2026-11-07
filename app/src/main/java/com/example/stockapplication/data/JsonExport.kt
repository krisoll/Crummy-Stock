package com.example.stockapplication.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Full-database backup as JSON: every category, product, order and sale line, plus the settings
 * that explain the money columns.
 *
 * ## Why a hand-written serialiser
 *
 * The project has no serialisation dependency, and `org.json` is stubbed out in JVM unit tests,
 * so neither could be tested the way the rest of `data/` is. A small emitter keeps the escape
 * rules in one visible place and makes the output assertable as a plain string.
 *
 * ## Shape and guarantees
 *
 * The document is **self-describing and versioned** ([JSON_FORMAT], [JSON_VERSION]) so a desktop
 * reader can sniff it, refuse a version it does not understand, and evolve without guessing.
 *
 * **Primary keys are exported verbatim.** A `sales[].productId` still points at
 * `products[].id`, and `lines[]` still point at `orders[].orderId`, so a restore on another
 * machine — or a normalised import into another app — keeps every relationship intact. Ids are
 * only meaningful *within* one export; they are not globally unique.
 *
 * **Values are raw, not formatted.** Money is in integer cents, rates in basis points, timestamps
 * in epoch milliseconds (UTC). A sibling `*Iso` / `*Formatted` field is emitted where a human
 * would want to read the value at a glance, but it is explicitly a presentation copy so no
 * importer ever parses it back. That is the point of this format: CSVs already exist for humans
 * and spreadsheets; this file is for machines.
 *
 * **Everything is snapshotted in one transaction** — see [StockDao.snapshot] — so the counts and
 * cross-table references cannot describe two different moments in time.
 */

/** Identifies a file as a StockApplication backup. Readers should check this before anything else. */
const val JSON_FORMAT = "stockapplication.backup"

/**
 * Layout version of the backup document itself, independent of the Room schema version.
 *
 * Bump this only for a **breaking** shape change (a renamed or removed field). Adding an optional
 * field is backwards compatible and must not bump it.
 */
const val JSON_VERSION = 1

/**
 * Layout version of the `settings` object, independent of [JSON_VERSION].
 *
 * Separate because the two change for different reasons and at different rates: the data tables
 * are stable while the settings block keeps gaining groups (appearance, startup screen, scanner).
 * A reader that finds a *higher* settings version still restores the data and simply ignores the
 * settings it does not recognise, which is why this never needs to reject a whole file.
 */
const val SETTINGS_VERSION = 1

/** Everything needed to rebuild the database, captured at a single instant. */
data class DatabaseSnapshot(
    val schemaVersion: Int,
    val appVersionName: String,
    val categories: List<Category>,
    val products: List<Product>,
    val orders: List<SaleOrder>,
    val saleLines: List<Sale>,
    val legacySales: List<Sale>,
    val settings: AppSettings,
    /**
     * Promo rules, or empty when the user exported without them.
     *
     * Opt-in at export time rather than always-on: restoring a backup that carries promos re-arms
     * those discounts, which is a side effect most people restoring a backup are not expecting.
     */
    val promos: List<Promo> = emptyList(),
    /**
     * Every tag, exported regardless of the `tagsEnabled` view setting.
     *
     * The setting decides whether a device *shows* tags, not whether it has them. Omitting them
     * when the feature happened to be off would make that backup destroy another device's tags on
     * restore, which is the one thing a backup must never do.
     */
    val tags: List<Tag> = emptyList(),
    /**
     * The product↔tag mapping, one row per assignment.
     *
     * A flat array of pairs rather than a `tagIds` list on each product: it keeps [Product]'s
     * export shape unchanged, so an older reader still finds every field it knows, and it mirrors
     * how the join table is actually stored.
     */
    val productTags: List<ProductTag> = emptyList(),
    /**
     * Supply catalogues and purchase history.
     *
     * Exported always, like tags: a shop that has not opened the section simply writes empty
     * arrays, and an older file remains readable because every array is optional to the reader.
     */
    val providers: List<Provider> = emptyList(),
    val materials: List<Material> = emptyList(),
    val materialEntries: List<MaterialEntry> = emptyList(),
    val materialEntryLines: List<MaterialEntryLine> = emptyList(),
    val exportedAt: Long = System.currentTimeMillis(),
)

/**
 * Serialises this snapshot to a pretty-printed JSON document.
 *
 * Pretty-printing costs bytes but is worth it: the file is meant to be opened, diffed and
 * hand-inspected when a restore goes wrong.
 *
 * @param includeSettings when false the `settings` object carries only the accounting rates and
 *   the money format — the things that describe the *shop*. When true (the default) it also
 *   carries the appearance, language and scanner choices, which describe one phone.
 *
 *   Exporting them is not the same as applying them: the file always records the full set so a
 *   backup can rebuild a device, and the importer decides whether to restore them. That split is
 *   why this defaults to true — a backup that quietly omitted half the settings would be a backup
 *   the user cannot rely on.
 */
fun DatabaseSnapshot.toJson(includeSettings: Boolean = true): String {
    val out = StringBuilder(64 * 1024)
    val writer = JsonWriter(out)
    writer.beginObject()

    writer.field("format", JSON_FORMAT)
    writer.field("version", JSON_VERSION)
    writer.field("schemaVersion", schemaVersion)
    writer.field("appVersion", appVersionName)
    writer.field("exportedAt", exportedAt)
    writer.field("exportedAtIso", isoUtc(exportedAt))
    writer.field("timezone", TimeZone.getDefault().id)

    // Explains every money field in the document without the reader needing this source file.
    writer.name("money").beginObject()
    writer.field("unit", "cents")
    writer.field(
        "note",
        "All money values are integer minor units (cents). Rates are basis points: " +
            "275 = 2.75%. Timestamps are epoch milliseconds."
    )
    writer.endObject()

    // Lets an importer allocate ids above the existing maximum without scanning every array.
    writer.name("counts").beginObject()
    writer.field("categories", categories.size)
    writer.field("products", products.size)
    writer.field("orders", orders.size)
    writer.field("saleLines", saleLines.size)
    writer.field("legacySales", legacySales.size)
    writer.field("promos", promos.size)
    writer.field("tags", tags.size)
    writer.field("productTags", productTags.size)
    writer.field("providers", providers.size)
    writer.field("materials", materials.size)
    writer.field("materialEntries", materialEntries.size)
    writer.field("materialEntryLines", materialEntryLines.size)
    writer.endObject()

    writer.name("categories").beginArray()
    categories.sortedBy { it.id }.forEach { category ->
        writer.beginObject()
        writer.field("id", category.id)
        writer.field("name", category.name)
        writer.endObject()
    }
    writer.endArray()

    writer.name("products").beginArray()
    products.sortedBy { it.id }.forEach { product ->
        writer.beginObject()
        writer.field("id", product.id)
        writer.field("barcode", product.barcode)
        writer.field("name", product.name)
        writer.field("priceCents", product.priceCents)
        writer.field("priceFormatted", formatCents(product.priceCents))
        writer.field("quantity", product.quantity)
        // Explicit null rather than an omitted key: "uncategorised" is a real state and has to
        // survive a round trip distinctly from "field absent".
        writer.field("categoryId", product.categoryId)
        writer.field("imagePath", product.imagePath)
        writer.field("isOutOfStock", product.isOutOfStock)
        writer.field("isLowStock", product.isLowStock)
        // Exported even though it is a display preference: a user who has starred twenty products
        // and restores a backup should get them back. `JSON_VERSION` is deliberately not bumped —
        // an added optional field imports cleanly into older builds and vice versa.
        writer.field("favourite", product.favourite)
        writer.endObject()
    }
    writer.endArray()

    val linesByOrder = saleLines.groupBy { it.orderId }
    writer.name("orders").beginArray()
    orders.sortedBy { it.soldAt }.forEach { order ->
        writer.beginObject()
        writer.field("orderId", order.orderId)
        writer.field("soldAt", order.soldAt)
        writer.field("soldAtIso", isoUtc(order.soldAt))
        writer.field("paymentMethod", order.paymentMethod.name)
        writer.field("itemCount", order.itemCount)
        writer.field("itemsSubtotalCents", order.itemsSubtotalCents)
        writer.field("promoDiscountCents", order.promoDiscountCents)
        writer.field("promoName", order.promoName)
        writer.field("taxRateBasisPoints", order.taxRateBasisPoints)
        writer.field("taxCents", order.taxCents)
        writer.field("cardFeeRateBasisPoints", order.cardFeeRateBasisPoints)
        writer.field("cardFeeFixedCents", order.cardFeeFixedCents)
        writer.field("cardFeeCents", order.cardFeeCents)
        writer.field("totalCents", order.totalCents)
        writer.field("totalFormatted", formatCents(order.totalCents))
        writer.field("note", order.note)

        // Lines are nested as well as flat, so a reader can work either way: iterate orders to
        // redraw a receipt, or iterate sales[] for a flat ledger. Both are the same objects.
        writer.name("lines").beginArray()
        linesByOrder[order.orderId].orEmpty().sortedBy { it.id }.forEach { line ->
            writeSale(writer, line)
        }
        writer.endArray()
        writer.endObject()
    }
    writer.endArray()

    // Every sale row, including the nested ones. Redundant by design: this is the flat table.
    writer.name("sales").beginArray()
    saleLines.sortedBy { it.id }.forEach { line -> writeSale(writer, line) }
    writer.endArray()

    // Sales written before carts existed carry an empty orderId, so they belong to no order and
    // would be invisible to a reader that only walks `orders[].lines`. Kept in their own array so
    // an importer chooses what to do with them instead of silently losing history.
    writer.name("legacySales").beginArray()
    legacySales.sortedBy { it.id }.forEach { sale -> writeSale(writer, sale) }
    writer.endArray()

    // Omitted entirely when the export was made without promos. An empty array would be
    // indistinguishable from "the shop has no promos", and an importer must be able to tell the
    // difference between "none here" and "not included".
    if (promos.isNotEmpty()) {
        writer.name("promos").beginArray()
        promos.sortedBy { it.id }.forEach { promo ->
            writer.beginObject()
            writer.field("id", promo.id)
            writer.field("name", promo.name)
            writer.field("kind", promo.kind.name)
            writer.field("categoryId", promo.categoryId)
            writer.field("productIds", promo.productIdList)
            writer.field("thresholdQuantity", promo.thresholdQuantity)
            writer.field("discountType", promo.discountType.name)
            writer.field("discountPercentBasisPoints", promo.discountPercentBasisPoints)
            writer.field("discountFixedCents", promo.discountFixedCents)
            writer.field("enabled", promo.enabled)
            writer.field("startsAt", promo.startsAt)
            writer.field("endsAt", promo.endsAt)
            writer.endObject()
        }
        writer.endArray()
    }

    writer.name("tags").beginArray()
    tags.sortedBy { it.id }.forEach { tag ->
        writer.beginObject()
        writer.field("id", tag.id)
        writer.field("name", tag.name)
        writer.endObject()
    }
    writer.endArray()

    writer.name("productTags").beginArray()
    // Sorted so two exports of the same database produce identical bytes: the join table has no
    // natural order, and an unstable file breaks diffing a backup against the one before it.
    productTags.sortedWith(compareBy({ it.productId }, { it.tagId })).forEach { assignment ->
        writer.beginObject()
        writer.field("productId", assignment.productId)
        writer.field("tagId", assignment.tagId)
        writer.endObject()
    }
    writer.endArray()

    // ---- Supplies ---------------------------------------------------------
    writer.name("providers").beginArray()
    providers.sortedBy { it.id }.forEach { provider ->
        writer.beginObject()
        writer.field("id", provider.id)
        writer.field("name", provider.name)
        writer.field("phone", provider.phone)
        writer.field("address", provider.address)
        writer.field("notes", provider.notes)
        writer.endObject()
    }
    writer.endArray()

    writer.name("materials").beginArray()
    materials.sortedBy { it.id }.forEach { material ->
        writer.beginObject()
        writer.field("id", material.id)
        writer.field("name", material.name)
        writer.field("unit", material.unit.name)
        writer.field("customUnit", material.customUnit)
        writer.field("stock", material.stock)
        writer.field("lowStockThreshold", material.lowStockThreshold)
        writer.field("notes", material.notes)
        writer.endObject()
    }
    writer.endArray()

    val entryLinesByEntry = materialEntryLines.groupBy { it.entryId }
    writer.name("materialEntries").beginArray()
    materialEntries.sortedBy { it.entryId }.forEach { entry ->
        writer.beginObject()
        writer.field("entryId", entry.entryId)
        writer.field("providerId", entry.providerId)
        writer.field("providerName", entry.providerName)
        writer.field("purchasedAt", entry.purchasedAt)
        writer.field("purchasedAtIso", isoUtc(entry.purchasedAt))
        writer.field("reference", entry.reference)
        writer.field("note", entry.note)
        writer.field("totalCents", entry.totalCents)
        writer.field("lineCount", entry.lineCount)

        // Nested like the sale lines, so a reader can either walk this copy or the flat table.
        writer.name("lines").beginArray()
        entryLinesByEntry[entry.entryId].orEmpty().sortedBy { it.id }.forEach { line ->
            writeMaterialEntryLine(writer, line)
        }
        writer.endArray()
        writer.endObject()
    }
    writer.endArray()

    // The flat line table. It duplicates the nested copy by design, just like `sales[]`.
    writer.name("materialEntryLines").beginArray()
    materialEntryLines.sortedBy { it.id }.forEach { line ->
        writeMaterialEntryLine(writer, line)
    }
    writer.endArray()

    // ---- Settings ---------------------------------------------------------
    // The whole preference set, grouped so a human opening the file can find a value without
    // knowing this source file. The three money fields stay at the top level under their original
    // keys: files written before this grouping existed are still readable by the same importer,
    // and an older reader of a *new* file still finds the rates where it expects them.
    writer.name("settings").beginObject()
    writer.field("settingsVersion", SETTINGS_VERSION)

    writer.field("taxRateBasisPoints", settings.taxRateBasisPoints)
    writer.field("cardFeeRateBasisPoints", settings.cardFeeRateBasisPoints)
    writer.field("cardFeeFixedCents", settings.cardFeeFixedCents)
    // Presentation-only, but it belongs with the rates rather than with the theme: it changes how
    // every price on screen reads, and nobody would look for it under "appearance".
    writer.field("showMoneyDecimals", settings.showMoneyDecimals)
    writer.field("currencyId", settings.currency.id)

    if (includeSettings) {
        // Names the device the file came from, so the two blocks below are never confused.
        writer.field("languageTag", settings.languageTag)

        writer.name("appearance").beginObject()
        writer.field("fontFamilyId", settings.fontFamilyId)
        writer.field("textSizeId", settings.textSizeId)
        writer.field("motionSpeedId", settings.motionSpeedId)
        writer.field("useAppBackgroundColor", settings.useAppBackgroundColor)
        writer.field("backgroundColorArgb", settings.backgroundColorArgb)
        writer.field("useAppButtonColor", settings.useAppButtonColor)
        writer.field("buttonColorArgb", settings.buttonColorArgb)
        writer.field("useAppPanelColor", settings.useAppPanelColor)
        writer.field("panelColorArgb", settings.panelColorArgb)
        writer.field("useAppTextColor", settings.useAppTextColor)
        writer.field("textColorArgb", settings.textColorArgb)
        writer.field("useAppHighlightColor", settings.useAppHighlightColor)
        writer.field("highlightColorArgb", settings.highlightColorArgb)

        // ---- Theme selection -------------------------------------------------
        // The colours above are what the app draws; these two make the *choice* explicit. Without
        // them a custom theme was indistinguishable from a hand-mixed palette, and a backup of a
        // saved theme could not bring the name back. `activeThemeId` is null for a hand-mixed
        // palette or a file written before this field existed.
        writer.field("activeThemeId", settings.activeThemeId())
        writer.name("savedThemes").beginArray()
        settings.savedThemes.forEach { theme ->
            writer.beginObject()
            writer.field("id", theme.id)
            writer.field("name", theme.name)
            writer.field("backgroundArgb", theme.backgroundArgb)
            writer.field("buttonArgb", theme.buttonArgb)
            writer.field("panelArgb", theme.panelArgb)
            writer.field("highlightArgb", theme.highlightArgb)
            writer.field("textArgb", theme.textArgb)
            writer.endObject()
        }
        writer.endArray()
        writer.endObject()

        writer.name("startupScreen").beginObject()
        writer.field("showLoadingScreen", settings.showLoadingScreen)
        writer.field("loadingIconId", settings.loadingIconId)
        writer.field("loadingSpinId", settings.loadingSpinId)
        writer.field("loadingCircleId", settings.loadingCircleId)
        writer.field("loadingImageSizeDp", settings.loadingImageSizeDp)
        writer.field("loadingCircleSizeDp", settings.loadingCircleSizeDp)
        writer.endObject()

        writer.name("scanner").beginObject()
        writer.field("scannerBeepEnabled", settings.scannerBeepEnabled)
        writer.field("scannerBeepId", settings.scannerBeep.id)
        writer.field("saleSoundEnabled", settings.saleSoundEnabled)
        writer.field("saleSoundId", settings.saleSoundId)
        // The raw id, not `settings.saleAnimation.id`: that accessor normalises through
        // `SaleAnimation.fromId`, which has no entry for the Lottie sentinel and would silently
        // write the default glyph instead — losing the choice of a custom animation on restore.
        writer.field("saleAnimationId", settings.saleAnimationId)
        writer.field("saleAnimationSizeDp", settings.saleAnimationSizeDp)
        writer.field("scanDelayMillis", settings.scanDelayMillis)
        writer.field("productChangeDelayMillis", settings.productChangeDelayMillis)
        writer.field("showScanPopup", settings.showScanPopup)
        writer.field("scanVibrationEnabled", settings.scanVibrationEnabled)
        writer.field("scanVibrationId", settings.scanVibration.id)
        writer.field("scanVibrationStrength", settings.scanVibrationStrength)
        writer.endObject()

        // ---- Device-specific, deliberately not restorable ------------------
        // All four of these name something that exists only on the exporting phone: the export
        // folder is a persisted SAF grant, and the three file names are copies in app-private
        // storage that another device has never seen. They are written so the backup is a faithful
        // record of the machine it came from, and the importer skips this block on purpose —
        // restoring them would leave the app pointing at files and folders that are not there.
        writer.name("deviceSpecific").beginObject()
        writer.field(
            "note",
            "Not restored: these refer to files and folders on the exporting device."
        )
        writer.field("exportFolderUri", settings.exportFolderUri)
        writer.field("loadingImageFileName", settings.loadingImageFileName)
        // Same rule as the picked image above: the *choice* of a Lottie startup screen travels in
        // `loadingIconId`, but the file itself is a copy on this device and is never restored.
        writer.field("loadingLottieFileName", settings.loadingLottieFileName)
        writer.field("scannerBeepFileName", settings.scannerBeepFileName)
        writer.field("saleSoundFileName", settings.saleSoundFileName)
        // Recorded but not restored, like the others: it names a copy in this device's private
        // storage. The *choice* of using a custom animation does round-trip, via `saleAnimationId`
        // in the scanner block — so a restore on another device selects "lottie" with no file and
        // the receipt falls back to the default glyph rather than to nothing.
        writer.field("saleLottieFileName", settings.saleLottieFileName)
        writer.endObject()
    }
    writer.endObject()

    writer.endObject()
    return out.toString()
}

/** One sale line, shared by `orders[].lines`, `sales[]` and `legacySales[]`. */
private fun writeSale(writer: JsonWriter, sale: Sale) {
    writer.beginObject()
    writer.field("id", sale.id)
    writer.field("productId", sale.productId)
    // Denormalised on purpose: the product may be deleted later, and a receipt must still show
    // the name and price that were actually charged.
    writer.field("productName", sale.productName)
    writer.field("barcode", sale.barcode)
    writer.field("quantity", sale.quantity)
    writer.field("unitPriceCents", sale.unitPriceCents)
    writer.field("totalCents", sale.totalCents)
    writer.field("soldAt", sale.soldAt)
    writer.field("soldAtIso", isoUtc(sale.soldAt))
    writer.field("orderId", sale.orderId)
    writer.endObject()
}

/** One material entry line, shared by `materialEntries[].lines` and `materialEntryLines[]`. */
private fun writeMaterialEntryLine(writer: JsonWriter, line: MaterialEntryLine) {
    writer.beginObject()
    writer.field("id", line.id)
    writer.field("entryId", line.entryId)
    writer.field("materialId", line.materialId)
    // Snapshotted at purchase time, so deleting or renaming the material cannot rewrite history.
    writer.field("materialName", line.materialName)
    writer.field("unit", line.unit.name)
    writer.field("customUnit", line.customUnit)
    writer.field("quantity", line.quantity)
    writer.field("unitCostCents", line.unitCostCents)
    writer.endObject()
}

/** Default file name, e.g. `stock-backup-2025-03-14-1830.json`. */
fun jsonBackupFileName(now: Long = System.currentTimeMillis()): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(now))
    return "stock-backup-$stamp.json"
}

/** ISO-8601 UTC timestamp, so a reader never has to guess the exporting machine's timezone. */
internal fun isoUtc(millis: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(millis))
}

/**
 * A minimal pretty-printing JSON emitter.
 *
 * Handles the two things that actually bite in practice: escaping (product names and order notes
 * are free text, and can contain quotes, backslashes, newlines and control characters) and
 * trailing commas (a comma is written *before* each element except the first).
 *
 * Not thread-safe; use one writer per document.
 */
internal class JsonWriter(private val out: StringBuilder) {

    /** One entry per open container: whether it has already had an element written. */
    private val hasElement = ArrayDeque<Boolean>()

    /** Set by [name] so the value that follows does not emit a comma or a newline. */
    private var afterName = false

    fun beginObject() {
        open('{')
    }

    fun endObject() = close('}')

    fun beginArray() {
        open('[')
    }

    fun endArray() = close(']')

    private fun open(openChar: Char) {
        beforeValue()
        out.append(openChar)
        hasElement.addLast(false)
    }

    private fun close(closeChar: Char) {
        val hadElement = hasElement.removeLastOrNull() ?: false
        if (hadElement) newline()
        out.append(closeChar)
        // A container just finished, so it counts as this parent's element — including when it
        // was the value of a named field, where `beforeValue` deliberately left the flag alone.
        afterName = false
    }

    /**
     * Writes a key and its colon; the matching value must be written immediately after.
     *
     * Returns the writer so a container can be opened in the same expression:
     * `writer.name("orders").beginArray()`.
     */
    fun name(key: String): JsonWriter {
        beforeValue()
        appendString(key)
        out.append(": ")
        afterName = true
        return this
    }

    fun field(key: String, value: String?) {
        name(key)
        if (value == null) out.append("null") else appendString(value)
        afterName = false
    }

    fun field(key: String, value: Long?) {
        name(key)
        out.append(value?.toString() ?: "null")
        afterName = false
    }

    fun field(key: String, value: Int?) {
        name(key)
        out.append(value?.toString() ?: "null")
        afterName = false
    }

    fun field(key: String, value: Boolean) {
        name(key)
        out.append(if (value) "true" else "false")
        afterName = false
    }

    /** Emits any other value via [Any.toString]; null becomes JSON `null`. */
    fun field(key: String, value: Any?) = field(key, value?.toString())

    /**
     * Called before an element is written: emits the separating comma and newline if this
     * container already has one, and marks the container as populated.
     *
     * Right after [name] there must be no comma — the colon already separated the key and value —
     * and marking the container is deferred to [close] so that a *container* value
     * (`name(..).beginArray()`) still registers as one element of its parent. Marking it here
     * would make the parent believe the element exists while the nested container is still being
     * written, which silently drops the comma before the next key.
     */
    private fun beforeValue() {
        if (afterName) {
            afterName = false
            return
        }
        if (hasElement.isEmpty()) return
        val already = hasElement.removeLast()
        if (already) out.append(',')
        hasElement.addLast(true)
        newline()
    }

    private fun newline() {
        out.append('\n')
        repeat(hasElement.size) { out.append("  ") }
    }

    private fun appendString(value: String) {
        out.append('"')
        for (char in value) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (char < ' ') {
                    // The remaining control characters are not legal unescaped inside JSON.
                    out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(char)
                }
            }
        }
        out.append('"')
    }
}
