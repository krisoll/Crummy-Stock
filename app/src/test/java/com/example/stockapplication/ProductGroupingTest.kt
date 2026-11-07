package com.example.stockapplication

import com.example.stockapplication.data.Category
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.groupProductsByCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The inventory list is sectioned by category, so these tests pin down the ordering rules and the
 * "never lose a product" guarantee.
 */
class ProductGroupingTest {

    private fun product(id: Long, name: String, categoryId: Long? = null) = Product(
        id = id,
        barcode = "BC$id",
        name = name,
        priceCents = 100,
        quantity = 1,
        categoryId = categoryId,
    )

    private fun category(id: Long, name: String) = Category(id = id, name = name)

    @Test
    fun groupsProductsUnderTheirCategories() {
        val categories = listOf(category(1, "Drinks"), category(2, "Food"))
        val products = listOf(
            product(1, "Cola", categoryId = 1),
            product(2, "Bread", categoryId = 2),
            product(3, "Water", categoryId = 1),
        )

        val groups = groupProductsByCategory(products, categories)

        assertEquals(2, groups.size)
        assertEquals("Drinks", groups[0].categoryName)
        assertEquals(listOf("Cola", "Water"), groups[0].products.map { it.name })
        assertEquals("Food", groups[1].categoryName)
    }

    @Test
    fun categoriesAreAlphabetical() {
        val categories = listOf(category(1, "Zebra"), category(2, "Apple"))
        val products = listOf(
            product(1, "Z", categoryId = 1),
            product(2, "A", categoryId = 2),
        )

        val groups = groupProductsByCategory(products, categories)
        assertEquals(listOf("Apple", "Zebra"), groups.map { it.categoryName })
    }

    @Test
    fun uncategorisedProductsGoLast() {
        val categories = listOf(category(1, "Drinks"))
        val products = listOf(
            product(1, "Loose item", categoryId = null),
            product(2, "Cola", categoryId = 1),
        )

        val groups = groupProductsByCategory(products, categories)

        assertEquals(2, groups.size)
        assertEquals("Drinks", groups[0].categoryName)
        assertNull(groups[1].categoryName)
        assertEquals(listOf("Loose item"), groups[1].products.map { it.name })
    }

    @Test
    fun emptyCategoriesAreNotRendered() {
        val categories = listOf(category(1, "Drinks"), category(2, "Empty"))
        val products = listOf(product(1, "Cola", categoryId = 1))

        val groups = groupProductsByCategory(products, categories)

        assertEquals(1, groups.size)
        assertEquals("Drinks", groups[0].categoryName)
    }

    @Test
    fun productsWithADeletedCategoryAreStillShown() {
        // A product pointing at a category that no longer exists must not vanish from the list.
        val products = listOf(product(1, "Orphan", categoryId = 99))

        val groups = groupProductsByCategory(products, emptyList())

        val all = groups.flatMap { it.products }
        assertEquals(listOf("Orphan"), all.map { it.name })
        assertNull(groups.single().categoryId)
    }

    @Test
    fun orphanedProductsMergeIntoTheUncategorisedBucket() {
        val products = listOf(
            product(1, "No category", categoryId = null),
            product(2, "Orphan", categoryId = 99),
        )

        val groups = groupProductsByCategory(products, emptyList())

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().products.size)
    }

    @Test
    fun noProductsMeansNoGroups() {
        assertTrue(groupProductsByCategory(emptyList(), listOf(category(1, "Drinks"))).isEmpty())
    }
}
