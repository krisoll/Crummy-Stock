package com.example.stockapplication

import com.example.stockapplication.data.Product
import com.example.stockapplication.ui.viewmodel.InventoryFilter
import com.example.stockapplication.ui.viewmodel.InventoryUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryUiStateTest {

    private val products = listOf(
        Product(id = 1, barcode = "111", name = "Coffee", priceCents = 500, quantity = 10),
        Product(id = 2, barcode = "222", name = "Tea", priceCents = 300, quantity = 3),
        Product(id = 3, barcode = "333", name = "Sugar", priceCents = 200, quantity = 0),
    )

    @Test
    fun filtersByStockLevel() {
        val state = InventoryUiState(products = products, isLoading = false)
        assertEquals(3, state.visibleProducts.size)
        assertEquals(
            listOf("Tea"),
            state.copy(filter = InventoryFilter.LOW).visibleProducts.map { it.name }
        )
        assertEquals(
            listOf("Sugar"),
            state.copy(filter = InventoryFilter.OUT).visibleProducts.map { it.name }
        )
    }

    @Test
    fun searchMatchesNameAndBarcode() {
        val state = InventoryUiState(products = products, isLoading = false)
        assertEquals(1, state.copy(searchQuery = "coff").visibleProducts.size)
        assertEquals(1, state.copy(searchQuery = "333").visibleProducts.size)
    }

    @Test
    fun totalsAreComputedFromAllProducts() {
        val state = InventoryUiState(products = products, isLoading = false)
        assertEquals(13, state.totalUnits)
        assertEquals(10 * 500L + 3 * 300L, state.totalValueCents)
        assertEquals(2, state.lowStockCount)
    }
}
