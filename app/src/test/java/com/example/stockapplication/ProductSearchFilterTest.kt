package com.example.stockapplication

import com.example.stockapplication.data.Product
import com.example.stockapplication.data.filterProducts
import com.example.stockapplication.data.matchesSearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Add from inventory" sheet filters by a category chip *and* a free-text search box.
 *
 * These tests pin down the combination rule (AND) and that both name and barcode are searched,
 * since a shopkeeper may type either.
 */
class ProductSearchFilterTest {

    private fun product(id: Long, name: String, barcode: String = "BC-$id", categoryId: Long? = null) =
        Product(
            id = id,
            barcode = barcode,
            name = name,
            categoryId = categoryId,
        )

    private val cola = product(1, "Cola", barcode = "111", categoryId = 10L)
    private val dietCola = product(2, "Diet Cola", barcode = "222", categoryId = 10L)
    private val bread = product(3, "Bread", barcode = "333", categoryId = 20L)
    private val all = listOf(cola, dietCola, bread)

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(matchesSearchQuery(cola, ""))
        assertTrue(matchesSearchQuery(cola, "   "))
    }

    @Test
    fun matchesNameCaseInsensitively() {
        assertTrue(matchesSearchQuery(cola, "col"))
        assertTrue(matchesSearchQuery(cola, "COLA"))
        assertFalse(matchesSearchQuery(cola, "bread"))
    }

    @Test
    fun matchesBarcode() {
        assertTrue(matchesSearchQuery(cola, "111"))
        assertTrue(matchesSearchQuery(cola, "11"))
    }

    @Test
    fun queryIsTrimmed() {
        assertTrue(matchesSearchQuery(cola, "  cola  "))
    }

    @Test
    fun searchNarrowsTheWholeList() {
        val result = filterProducts(all, selectedCategoryId = null, query = "cola")
        assertEquals(listOf("Cola", "Diet Cola"), result.map { it.name })
    }

    @Test
    fun searchAndCategoryCombineWithAnd() {
        // "cola" matches both drinks, but the Food chip excludes them.
        assertEquals(
            emptyList<Product>(),
            filterProducts(all, selectedCategoryId = 20L, query = "cola")
        )
        // Bread matches neither the Drinks chip nor the query.
        assertEquals(
            listOf("Cola", "Diet Cola"),
            filterProducts(all, selectedCategoryId = 10L, query = "cola").map { it.name }
        )
    }

    @Test
    fun noResultsWhenNothingMatches() {
        assertTrue(filterProducts(all, selectedCategoryId = null, query = "zzz").isEmpty())
    }

    @Test
    fun searchByBarcodeFindsTheProduct() {
        assertEquals(
            listOf("Bread"),
            filterProducts(all, selectedCategoryId = null, query = "333").map { it.name }
        )
    }
}
