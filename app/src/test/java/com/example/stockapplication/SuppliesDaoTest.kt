package com.example.stockapplication

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.stockapplication.data.BackupContents
import com.example.stockapplication.data.DeleteEntryResult
import com.example.stockapplication.data.Material
import com.example.stockapplication.data.MaterialEntry
import com.example.stockapplication.data.MaterialEntryLine
import com.example.stockapplication.data.MaterialUnit
import com.example.stockapplication.data.NewMaterialLine
import com.example.stockapplication.data.Provider
import com.example.stockapplication.data.RecordEntryResult
import com.example.stockapplication.data.StockDatabase
import com.example.stockapplication.data.StockDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The supplies transactions against a real in-memory Room database.
 *
 * These tests cover the two things a plain JVM test cannot: SQL (the grouped provider summary and
 * the derived last cost) and the all-or-nothing stock changes around recording and deleting an
 * entry.
 */
@RunWith(RobolectricTestRunner::class)
class SuppliesDaoTest {

    private lateinit var db: StockDatabase
    private lateinit var dao: StockDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StockDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.stockDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `recording an entry snapshots names units and totals and increases stock`() = runTest {
        val providerId = dao.insertProvider(Provider(name = "Paper Co.", phone = "555"))
        val boxes = dao.insertMaterial(
            Material(name = "Boxes", unit = MaterialUnit.BOX, stock = 10, lowStockThreshold = 4)
        )
        val labels = dao.insertMaterial(
            Material(
                name = "Labels",
                unit = MaterialUnit.OTHER,
                customUnit = "sheet",
                stock = 2,
            )
        )

        val result = dao.recordMaterialEntry(
            entryId = "MAT-1",
            providerId = providerId,
            purchasedAt = 1_700_000_000_000,
            reference = "INV-9",
            note = "weekly order",
            lines = listOf(
                NewMaterialLine(materialId = boxes, quantity = 2, unitCostCents = 250),
                NewMaterialLine(materialId = labels, quantity = 3, unitCostCents = 100),
            ),
        )

        val entry = (result as RecordEntryResult.Success).entry
        assertEquals("Paper Co.", entry.providerName)
        assertEquals(800L, entry.totalCents)
        assertEquals(2, entry.lineCount)
        assertEquals("INV-9", entry.reference)
        assertEquals(12, dao.findMaterial(boxes)?.stock)
        assertEquals(5, dao.findMaterial(labels)?.stock)

        val lines = dao.findMaterialEntryLines("MAT-1")
        assertEquals(listOf("Boxes", "Labels"), lines.map { it.materialName })
        assertEquals(listOf(MaterialUnit.BOX, MaterialUnit.OTHER), lines.map { it.unit })
        assertEquals("sheet", lines.single { it.materialName == "Labels" }.customUnit)
        assertEquals(500L, lines.first().totalCents)
    }

    @Test
    fun `deleting an entry reverses exactly the stock it added`() = runTest {
        val providerId = dao.insertProvider(Provider(name = "Paper Co."))
        val boxes = dao.insertMaterial(Material(name = "Boxes", stock = 10))

        dao.recordMaterialEntry(
            entryId = "MAT-1",
            providerId = providerId,
            purchasedAt = 1_000,
            lines = listOf(NewMaterialLine(boxes, 5, 200)),
        )

        val result = dao.deleteMaterialEntry("MAT-1")

        assertTrue(result is DeleteEntryResult.Success)
        assertEquals(10, dao.findMaterial(boxes)?.stock)
        assertNull(dao.findMaterialEntry("MAT-1"))
        assertTrue(dao.findMaterialEntryLines("MAT-1").isEmpty())
    }

    @Test
    fun `deleting an entry is blocked when its stock was already consumed`() = runTest {
        val providerId = dao.insertProvider(Provider(name = "Paper Co."))
        val boxes = dao.insertMaterial(Material(name = "Boxes", stock = 0))

        dao.recordMaterialEntry(
            entryId = "MAT-1",
            providerId = providerId,
            purchasedAt = 1_000,
            lines = listOf(NewMaterialLine(boxes, 5, 200)),
        )
        // Simulate using three boxes after the purchase.
        dao.setMaterialStock(boxes, 2)

        val result = dao.deleteMaterialEntry("MAT-1")

        assertTrue(result is DeleteEntryResult.InsufficientStock)
        assertEquals("Boxes", (result as DeleteEntryResult.InsufficientStock).materialName)
        assertEquals(2, result.available)
        assertEquals(5, result.requested)
        // Nothing was removed or restored.
        assertEquals(2, dao.findMaterial(boxes)?.stock)
        assertEquals(1, dao.findMaterialEntryLines("MAT-1").size)
    }

    @Test
    fun `deleting a provider or material leaves the invoice readable`() = runTest {
        val providerId = dao.insertProvider(Provider(name = "Paper Co."))
        val boxes = dao.insertMaterial(Material(name = "Boxes", unit = MaterialUnit.BOX))
        dao.recordMaterialEntry(
            entryId = "MAT-1",
            providerId = providerId,
            purchasedAt = 1_000,
            lines = listOf(NewMaterialLine(boxes, 2, 200)),
        )

        dao.deleteProvider(providerId)
        val afterProvider = dao.findMaterialEntry("MAT-1")
        assertNull(afterProvider?.providerId)
        assertEquals("Paper Co.", afterProvider?.providerName)

        dao.deleteMaterial(boxes)
        val line = dao.findMaterialEntryLines("MAT-1").single()
        assertNull(line.materialId)
        assertEquals("Boxes", line.materialName)
        assertEquals(MaterialUnit.BOX, line.unit)
    }

    @Test
    fun `last cost comes from the newest purchase date not insertion order`() = runTest {
        val providerId = dao.insertProvider(Provider(name = "Paper Co."))
        val boxes = dao.insertMaterial(Material(name = "Boxes"))

        // Newest purchase first, so a query ordered by line id would get this one wrong.
        dao.recordMaterialEntry(
            entryId = "MAT-NEW",
            providerId = providerId,
            purchasedAt = 2_000,
            lines = listOf(NewMaterialLine(boxes, 1, 300)),
        )
        dao.recordMaterialEntry(
            entryId = "MAT-OLD",
            providerId = providerId,
            purchasedAt = 1_000,
            lines = listOf(NewMaterialLine(boxes, 1, 500)),
        )

        assertEquals(300L, dao.lastUnitCost(boxes))
        assertEquals(300L, dao.observeMaterials().first().single().lastUnitCostCents)
    }

    @Test
    fun `a duplicate material in one entry is rejected before any write`() = runTest {
        val providerId = dao.insertProvider(Provider(name = "Paper Co."))
        val boxes = dao.insertMaterial(Material(name = "Boxes", stock = 0))

        val result = dao.recordMaterialEntry(
            entryId = "MAT-1",
            providerId = providerId,
            purchasedAt = 1_000,
            lines = listOf(
                NewMaterialLine(boxes, 1, 200),
                NewMaterialLine(boxes, 2, 200),
            ),
        )

        assertTrue(result is RecordEntryResult.DuplicateMaterial)
        assertEquals(0, dao.findMaterial(boxes)?.stock)
        assertNull(dao.findMaterialEntry("MAT-1"))
    }

    @Test
    fun `provider summary counts purchases and total spend`() = runTest {
        val providerId = dao.insertProvider(Provider(name = "Paper Co.", phone = "555"))
        val boxes = dao.insertMaterial(Material(name = "Boxes"))
        dao.recordMaterialEntry(
            entryId = "MAT-1",
            providerId = providerId,
            purchasedAt = 1_000,
            lines = listOf(NewMaterialLine(boxes, 1, 200)),
        )
        dao.recordMaterialEntry(
            entryId = "MAT-2",
            providerId = providerId,
            purchasedAt = 2_000,
            lines = listOf(NewMaterialLine(boxes, 1, 350)),
        )

        val summary = dao.observeProviders().first().single()
        assertEquals("Paper Co.", summary.name)
        assertEquals("555", summary.phone)
        assertEquals(2, summary.entryCount)
        assertEquals(550L, summary.totalSpentCents)
    }

    @Test
    fun `restore treats stock as authoritative and does not replay entries`() = runTest {
        val contents = BackupContents(
            schemaVersion = 9,
            exportedAt = 1_000,
            categories = emptyList(),
            products = emptyList(),
            orders = emptyList(),
            lines = emptyList(),
            notes = emptyList(),
            providers = listOf(Provider(id = 7, name = "Paper Co.")),
            materials = listOf(Material(id = 9, name = "Boxes", stock = 10)),
            materialEntries = listOf(
                MaterialEntry(
                    entryId = "MAT-1",
                    providerId = 7,
                    providerName = "Paper Co.",
                    purchasedAt = 1_000,
                    totalCents = 800,
                    lineCount = 1,
                )
            ),
            materialEntryLines = listOf(
                MaterialEntryLine(
                    id = 4,
                    entryId = "MAT-1",
                    materialId = 9,
                    materialName = "Boxes",
                    unit = MaterialUnit.PIECE,
                    quantity = 4,
                    unitCostCents = 200,
                )
            ),
        )

        val first = dao.restore(contents, replace = true)
        assertEquals(1, first.providersAdded)
        assertEquals(1, first.materialsAdded)
        assertEquals(1, first.entriesAdded)
        assertEquals(1, first.entryLinesAdded)
        assertEquals(10, dao.snapshotMaterials().single().stock)

        // Importing the same file again is a no-op and does not add the four boxes twice.
        val second = dao.restore(contents, replace = false)
        assertEquals(0, second.entriesAdded)
        assertEquals(0, second.entryLinesAdded)
        assertEquals(10, dao.snapshotMaterials().single().stock)
        assertEquals(1, dao.snapshotMaterialEntries().size)
        assertEquals(1, dao.snapshotMaterialEntryLines().size)
    }

    @Test
    fun `merge preserves stock already on this device`() = runTest {
        val existingProvider = dao.insertProvider(Provider(name = "Paper Co."))
        val existingMaterial = dao.insertMaterial(Material(name = "Boxes", stock = 7))
        dao.recordMaterialEntry(
            entryId = "MAT-LOCAL",
            providerId = existingProvider,
            purchasedAt = 2_000,
            lines = listOf(NewMaterialLine(existingMaterial, 2, 200)),
        )
        // Nine boxes are on hand by the time the older backup is merged in.
        dao.setMaterialStock(existingMaterial, 9)

        val contents = BackupContents(
            schemaVersion = 9,
            exportedAt = 1_000,
            categories = emptyList(),
            products = emptyList(),
            orders = emptyList(),
            lines = emptyList(),
            notes = emptyList(),
            providers = listOf(Provider(id = 1, name = "Paper Co.")),
            materials = listOf(Material(id = 2, name = "Boxes", stock = 30)),
            materialEntries = listOf(
                MaterialEntry(
                    entryId = "MAT-OLD",
                    providerId = 1,
                    providerName = "Paper Co.",
                    purchasedAt = 1_000,
                    totalCents = 200,
                    lineCount = 1,
                )
            ),
            materialEntryLines = listOf(
                MaterialEntryLine(
                    entryId = "MAT-OLD",
                    materialId = 2,
                    materialName = "Boxes",
                    unit = MaterialUnit.PIECE,
                    quantity = 1,
                    unitCostCents = 200,
                )
            ),
        )

        dao.restore(contents, replace = false)

        assertEquals(9, dao.findMaterial(existingMaterial)?.stock)
        assertEquals(2, dao.snapshotMaterialEntries().size)
        assertEquals(2, dao.snapshotMaterialEntryLines().size)
    }
}
