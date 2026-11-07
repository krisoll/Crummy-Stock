package com.example.stockapplication

import com.example.stockapplication.data.Material
import com.example.stockapplication.data.MaterialEntry
import com.example.stockapplication.data.MaterialEntryWithLines
import com.example.stockapplication.data.MaterialRow
import com.example.stockapplication.data.ProviderSummary
import com.example.stockapplication.ui.screens.supplies.lineTotalCents
import com.example.stockapplication.ui.viewmodel.SuppliesUiState
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuppliesUiStateTest {

    private val materials = listOf(
        MaterialRow(
            material = Material(id = 1, name = "Bags", stock = 10, lowStockThreshold = 5),
            lastUnitCostCents = 250L,
        ),
        MaterialRow(
            material = Material(id = 2, name = "Labels", stock = 3, lowStockThreshold = 5),
            lastUnitCostCents = 100L,
        ),
        MaterialRow(
            material = Material(id = 3, name = "Boxes", stock = 0, lowStockThreshold = 0),
            lastUnitCostCents = null,
        ),
        MaterialRow(
            material = Material(id = 4, name = "Tape", stock = 5, lowStockThreshold = 5),
            lastUnitCostCents = 50L,
        ),
    )

    @Test
    fun lowStockCountMatchesTheLowFilter() {
        val state = SuppliesUiState(materials = materials, isLoading = false)
        assertEquals(2, state.lowStockCount)
    }

    @Test
    fun stockValueUsesLastPaidCostAndIgnoresUncostedStock() {
        val state = SuppliesUiState(materials = materials, isLoading = false)
        // 10 × 250 + 3 × 100 + 0 (no purchase) + 5 × 50.
        assertEquals(3_050L, state.inventoryValueCents)
    }

    @Test
    fun providerSummaryRebuildsTheEditableProvider() {
        val provider = ProviderSummary(
            id = 7,
            name = "Paper Co.",
            phone = "555-0100",
            entryCount = 4,
            totalSpentCents = 12_500L,
            address = "1 Mill Street",
            notes = "Call before noon",
        ).asProvider()

        assertEquals(7L, provider.id)
        assertEquals("Paper Co.", provider.name)
        assertEquals("1 Mill Street", provider.address)
        assertEquals("Call before noon", provider.notes)
        assertTrue(provider.phone.isNotBlank())
    }

    @Test
    fun currentMonthSpendIgnoresEarlierPurchases() {
        val calendar = Calendar.getInstance()
        val thisMonth = calendar.timeInMillis
        calendar.add(Calendar.MONTH, -1)
        val lastMonth = calendar.timeInMillis

        val state = SuppliesUiState(
            entries = listOf(
                entry("MAT-a", thisMonth, 1_500L),
                entry("MAT-b", thisMonth, 250L),
                entry("MAT-c", lastMonth, 9_000L),
            ),
            isLoading = false,
        )

        assertEquals(1_750L, state.currentMonthSpendCents)
    }

    @Test
    fun lineTotalRejectsZeroQuantityAndOverflow() {
        assertEquals(450L, lineTotalCents(3, 150L))
        assertNull(lineTotalCents(0, 150L))
        assertNull(lineTotalCents(-1, 150L))
        assertNull(lineTotalCents(Int.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(0L, lineTotalCents(3, 0L))
    }

    private fun entry(entryId: String, purchasedAt: Long, totalCents: Long) =
        MaterialEntryWithLines(
            entry = MaterialEntry(
                entryId = entryId,
                providerId = 1L,
                providerName = "Paper Co.",
                purchasedAt = purchasedAt,
                totalCents = totalCents,
                lineCount = 1,
            ),
            lines = emptyList(),
        )
}
