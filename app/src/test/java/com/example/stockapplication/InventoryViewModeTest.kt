package com.example.stockapplication

import com.example.stockapplication.data.DefaultInventoryViewModeId
import com.example.stockapplication.data.InventoryViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins down the inventory layout preference.
 *
 * The important property is the **default**: an existing install must keep the flat list after
 * upgrading, so a missing or unreadable preference has to resolve to [InventoryViewMode.LIST]
 * rather than to the newer folder view.
 */
class InventoryViewModeTest {

    @Test
    fun theDefaultIsTheLayoutTheAppAlreadyHad() {
        assertEquals(InventoryViewMode.LIST, InventoryViewMode.fromId(DefaultInventoryViewModeId))
        assertEquals(DefaultInventoryViewModeId, InventoryViewMode.LIST.id)
    }

    @Test
    fun aMissingPreferenceFallsBackToTheList() {
        // A fresh install, or a store written before the preference existed.
        assertEquals(InventoryViewMode.LIST, InventoryViewMode.fromId(null))
        assertEquals(InventoryViewMode.LIST, InventoryViewMode.fromId(""))
    }

    @Test
    fun anUnknownPreferenceFallsBackToTheList() {
        // A value written by a future build, or a corrupted store. Must not crash the first
        // screen the user sees after upgrading.
        assertEquals(InventoryViewMode.LIST, InventoryViewMode.fromId("grid"))
        assertEquals(InventoryViewMode.LIST, InventoryViewMode.fromId("FOLDERS"))
    }

    @Test
    fun everyModeRoundTripsThroughItsId() {
        InventoryViewMode.entries.forEach { mode ->
            assertEquals(mode, InventoryViewMode.fromId(mode.id))
        }
    }

    @Test
    fun theIdsAreDistinct() {
        // Two modes sharing an id would make the stored preference ambiguous.
        val ids = InventoryViewMode.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertNotEquals(InventoryViewMode.LIST.id, InventoryViewMode.FOLDERS.id)
    }
}
