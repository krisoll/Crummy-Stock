package com.example.stockapplication

import com.example.stockapplication.data.Material
import com.example.stockapplication.data.MaterialEntryLine
import com.example.stockapplication.data.MaterialUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure rules around a material line and the low-stock badge. */
class MaterialEntryTest {

    @Test
    fun `line total multiplies quantity by unit cost`() {
        val line = MaterialEntryLine(
            entryId = "MAT-1",
            materialId = 1,
            materialName = "Boxes",
            unit = MaterialUnit.BOX,
            quantity = 7,
            unitCostCents = 125,
        )
        assertEquals(875L, line.totalCents)
    }

    @Test
    fun `a free line still has a total of zero`() {
        val line = MaterialEntryLine(
            entryId = "MAT-1",
            materialId = 1,
            materialName = "Sample",
            unit = MaterialUnit.PIECE,
            quantity = 3,
            unitCostCents = 0,
        )
        assertEquals(0L, line.totalCents)
    }

    @Test
    fun `out of stock and low stock are distinct bands`() {
        val material = Material(name = "Boxes", stock = 5, lowStockThreshold = 5)
        assertTrue(material.isLowStock)
        assertFalse(material.isOutOfStock)

        assertFalse(material.copy(stock = 6).isLowStock)
        assertTrue(material.copy(stock = 0).isOutOfStock)
        assertFalse(material.copy(stock = 0).isLowStock)
    }

    @Test
    fun `a zero threshold means nothing is low while stock remains`() {
        val material = Material(name = "Boxes", stock = 1, lowStockThreshold = 0)
        assertFalse(material.isLowStock)
    }

    @Test
    fun `unit ids round-trip and unknown values fall back to other`() {
        MaterialUnit.entries.forEach { unit ->
            assertEquals(unit, MaterialUnit.fromId(unit.name))
        }
        assertEquals(MaterialUnit.OTHER, MaterialUnit.fromId("sack"))
        assertEquals(MaterialUnit.KG, MaterialUnit.fromId("kg"))
    }
}
