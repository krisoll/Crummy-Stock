package com.example.stockapplication

import com.example.stockapplication.data.Product
import com.example.stockapplication.data.UNCATEGORISED_CATEGORY_ID
import com.example.stockapplication.data.filterProductsByCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Add from inventory" side sheet filters by category chip. That logic is pure and lives in
 * the data layer so it can be pinned down here, independently of Compose.
 */
class ProductCategoryFilterTest {

    private fun product(id: Long, name: String, categoryId: Long?) = Product(
        id = id,
        barcode = "BC-$id",
        name = name,
        categoryId = categoryId,
    )

    private val drinks = product(1, "Coffee", categoryId = 10L)
    private val tea = product(2, "Tea", categoryId = 10L)
    private val bread = product(3, "Bread", categoryId = 20L)
    private val loose = product(4, "Loose item", categoryId = null)

    private val all = listOf(drinks, tea, bread, loose)

    @Test
    fun nullSelectionShowsEverything() {
        assertEquals(all, filterProductsByCategory(all, null))
    }

    @Test
    fun realCategoryIdShowsOnlyThatCategory() {
        assertEquals(
            listOf(drinks, tea),
            filterProductsByCategory(all, 10L)
        )
        assertEquals(listOf(bread), filterProductsByCategory(all, 20L))
    }

    @Test
    fun uncategorisedSentinelShowsOnlyProductsWithoutACategory() {
        assertEquals(
            listOf(loose),
            filterProductsByCategory(all, UNCATEGORISED_CATEGORY_ID)
        )
    }

    @Test
    fun unknownCategoryIdProducesAnEmptyListRatherThanEverything() {
        // Guards against the old "when(selectedCategoryId) else ->" pattern silently falling back
        // to the full list when a category is deleted while its chip is selected.
        assertTrue(filterProductsByCategory(all, 999L).isEmpty())
    }

    @Test
    fun filteringPreservesTheIncomingOrder() {
        // The DAO already sorts by name; the filter must not reshuffle the list.
        assertEquals(
            listOf("Bread"),
            filterProductsByCategory(listOf(bread, drinks, tea), 20L).map { it.name }
        )
    }

    @Test
    fun emptyInventoryIsHandledForEverySelection() {
        assertEquals(emptyList<Product>(), filterProductsByCategory(emptyList(), null))
        assertEquals(emptyList<Product>(), filterProductsByCategory(emptyList(), 10L))
        assertEquals(
            emptyList<Product>(),
            filterProductsByCategory(emptyList(), UNCATEGORISED_CATEGORY_ID)
        )
    }
}
