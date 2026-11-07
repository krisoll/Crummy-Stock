package com.example.stockapplication.data

/**
 * Sentinel category id used by the sales picker's "Uncategorised" filter chip. No real category
 * can have it because Room assigns auto-increment ids starting at 1.
 */
const val UNCATEGORISED_CATEGORY_ID: Long = -1L

/**
 * Filters the inventory picker list by the selected category chip.
 *
 * Pure so the sheet's behaviour can be unit-tested without a database or a Compose runtime.
 *
 * @param selectedId null for "All", [UNCATEGORISED_CATEGORY_ID] for products with no category, or
 *                   a real [Category] id.
 */
fun filterProductsByCategory(products: List<Product>, selectedId: Long?): List<Product> = when (selectedId) {
    null -> products
    UNCATEGORISED_CATEGORY_ID -> products.filter { it.categoryId == null }
    else -> products.filter { it.categoryId == selectedId }
}

/**
 * Matches a product against a free-text search query.
 *
 * Searches the name and the barcode, case-insensitively, so both "cola" and a scanned/typed code
 * find the product. A blank query matches everything.
 */
fun matchesSearchQuery(product: Product, query: String): Boolean {
    val clean = query.trim()
    if (clean.isEmpty()) return true
    return product.name.contains(clean, ignoreCase = true) ||
        product.barcode.contains(clean, ignoreCase = true)
}

/**
 * Applies the picker's category chip and search box together.
 *
 * The two are combined with AND: picking "Drinks" and typing "cola" shows only drinks whose name
 * or barcode contains "cola". Pure, so the picker's filtering is unit-tested without Compose.
 */
fun filterProducts(
    products: List<Product>,
    selectedCategoryId: Long?,
    query: String,
    /** Tag filter; empty means "any tag". See [matchesAnyTag]. */
    selectedTagIds: Set<Long> = emptySet(),
    /** productId -> its tag ids, only meaningful when [selectedTagIds] is not empty. */
    tagIdsByProduct: Map<Long, Set<Long>> = emptyMap(),
): List<Product> =
    filterProductsByCategory(products, selectedCategoryId)
        .filter { matchesSearchQuery(it, query) }
        .filter { matchesAnyTag(it, selectedTagIds, tagIdsByProduct) }

/**
 * Matches a product against the selected tag chips.
 *
 * **OR, not AND.** Selecting "fragile" and "clearance" means "show me anything either labelled" —
 * the useful question in a shop. An AND would answer "products that are both", which is almost
 * always empty and reads as a broken filter.
 *
 * This deliberately differs from the category chip, which is single-select and therefore has no
 * combining rule: a product has one category but many tags, so the same operator cannot apply.
 *
 * An empty [selectedTagIds] matches everything, so the filter costs nothing when unused.
 */
fun matchesAnyTag(
    product: Product,
    selectedTagIds: Set<Long>,
    tagIdsByProduct: Map<Long, Set<Long>>,
): Boolean {
    if (selectedTagIds.isEmpty()) return true
    val productTags = tagIdsByProduct[product.id] ?: return false
    return productTags.any { it in selectedTagIds }
}

/**
 * A heading plus the products that fall under it, for the sectioned inventory list.
 *
 * [categoryId] is null for the "uncategorised" bucket.
 */
data class ProductGroup(
    val categoryId: Long?,
    val categoryName: String?,
    val products: List<Product>,
)

/**
 * Groups products under their categories for display.
 *
 * Ordering is deliberate: real categories come first, alphabetically by name (matching the order
 * the picker uses), and the uncategorised bucket goes **last** so it doesn't push the organised
 * stock down the screen. Products with no category are never dropped — a product whose category was
 * deleted must still be visible.
 *
 * Pure, so the sectioning can be unit-tested without a database.
 */
fun groupProductsByCategory(
    products: List<Product>,
    categories: List<Category>,
): List<ProductGroup> {
    if (products.isEmpty()) return emptyList()

    val byCategoryId = products.groupBy { it.categoryId }
    val groups = mutableListOf<ProductGroup>()

    categories
        .sortedBy { it.name.lowercase() }
        .forEach { category ->
            val members = byCategoryId[category.id].orEmpty()
            if (members.isNotEmpty()) {
                groups += ProductGroup(
                    categoryId = category.id,
                    categoryName = category.name,
                    products = members
                )
            }
        }

    val uncategorised = byCategoryId[null].orEmpty()
    if (uncategorised.isNotEmpty()) {
        groups += ProductGroup(
            categoryId = null,
            categoryName = null,
            products = uncategorised
        )
    }

    // Anything pointing at a category that no longer exists is shown as uncategorised rather than
    // vanishing silently.
    val knownIds = categories.map { it.id }.toSet()
    val orphans = byCategoryId
        .filterKeys { it != null && it !in knownIds }
        .values
        .flatten()
    if (orphans.isNotEmpty()) {
        val existing = groups.lastOrNull { it.categoryId == null }
        if (existing == null) {
            groups += ProductGroup(categoryId = null, categoryName = null, products = orphans)
        } else {
            groups[groups.lastIndexOf(existing)] =
                existing.copy(products = existing.products + orphans)
        }
    }

    return groups
}
