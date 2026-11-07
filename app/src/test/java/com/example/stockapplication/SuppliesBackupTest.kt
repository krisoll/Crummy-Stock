package com.example.stockapplication

import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.BackupContents
import com.example.stockapplication.data.DatabaseSnapshot
import com.example.stockapplication.data.JSON_FORMAT
import com.example.stockapplication.data.JSON_VERSION
import com.example.stockapplication.data.JsonImport
import com.example.stockapplication.data.Material
import com.example.stockapplication.data.MaterialEntry
import com.example.stockapplication.data.MaterialEntryLine
import com.example.stockapplication.data.MaterialUnit
import com.example.stockapplication.data.Provider
import com.example.stockapplication.data.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Supplies in the JSON backup.
 *
 * The rule these tests protect is that the four new tables round-trip without changing the file's
 * layout version, and that an old file without them still imports as an empty supplies section.
 * Totals are recomputed from lines so a damaged or hand-edited header cannot disagree with the
 * detail screen.
 */
class SuppliesBackupTest {

    private fun snapshot(
        providers: List<Provider> = listOf(Provider(1, "Paper Co.", "555", "Main St.", "")),
        materials: List<Material> = listOf(
            Material(
                id = 9,
                name = "Boxes",
                unit = MaterialUnit.BOX,
                stock = 40,
                lowStockThreshold = 10,
            ),
            Material(
                id = 10,
                name = "Labels",
                unit = MaterialUnit.OTHER,
                customUnit = "sheet",
                stock = 100,
            ),
        ),
        entries: List<MaterialEntry> = listOf(
            MaterialEntry(
                entryId = "MAT-1",
                providerId = 1,
                providerName = "Paper Co.",
                purchasedAt = 1_700_000_000_000,
                reference = "INV-9",
                note = "weekly order",
                totalCents = 800,
                lineCount = 2,
            )
        ),
        lines: List<MaterialEntryLine> = listOf(
            MaterialEntryLine(
                id = 4,
                entryId = "MAT-1",
                materialId = 9,
                materialName = "Boxes",
                unit = MaterialUnit.BOX,
                quantity = 2,
                unitCostCents = 250,
            ),
            MaterialEntryLine(
                id = 5,
                entryId = "MAT-1",
                materialId = 10,
                materialName = "Labels",
                unit = MaterialUnit.OTHER,
                customUnit = "sheet",
                quantity = 3,
                unitCostCents = 100,
            ),
        ),
    ) = DatabaseSnapshot(
        schemaVersion = 9,
        appVersionName = "1.2.0",
        categories = emptyList(),
        products = emptyList(),
        orders = emptyList(),
        saleLines = emptyList(),
        legacySales = emptyList(),
        settings = AppSettings(),
        providers = providers,
        materials = materials,
        materialEntries = entries,
        materialEntryLines = lines,
        exportedAt = 1_700_000_100_000,
    )

    @Test
    fun `all four supply tables survive the round trip`() {
        val text = snapshot().toJson()

        val contents = JsonImport.readBackup(text)

        assertEquals(listOf(Provider(1, "Paper Co.", "555", "Main St.", "")), contents.providers)
        assertEquals(2, contents.materials.size)
        assertEquals("sheet", contents.materials.single { it.name == "Labels" }.customUnit)

        val entry = contents.materialEntries.single()
        assertEquals("MAT-1", entry.entryId)
        assertEquals("Paper Co.", entry.providerName)
        assertEquals("INV-9", entry.reference)
        assertEquals(800L, entry.totalCents)
        assertEquals(2, entry.lineCount)

        val lines = contents.materialEntryLines.sortedBy { it.id }
        assertEquals(listOf(4L, 5L), lines.map { it.id })
        assertEquals(MaterialUnit.BOX, lines.first().unit)
        assertEquals("sheet", lines.last().customUnit)
    }

    @Test
    fun `the layout version stays one because the arrays are additive`() {
        val text = snapshot().toJson()
        assertTrue(text.contains("\"version\": $JSON_VERSION"))
        assertTrue(text.contains("\"materialEntries\""))
        assertTrue(text.contains("\"materialEntryLines\""))
        assertTrue(text.contains("\"providerName\": \"Paper Co.\""))
    }

    @Test
    fun `a supplies-only file is not empty and opens the import dialog`() {
        val contents = JsonImport.readBackup(snapshot().toJson())
        assertFalse(contents.isEmpty)
        assertTrue(contents.hasSupplies)
    }

    @Test
    fun `a file without supplies imports with empty arrays`() {
        val text = """
            {
              "format": "$JSON_FORMAT",
              "version": $JSON_VERSION,
              "schemaVersion": 8,
              "categories": [],
              "products": [],
              "orders": []
            }
        """.trimIndent()

        val contents = JsonImport.readBackup(text)
        assertTrue(contents.providers.isEmpty())
        assertTrue(contents.materials.isEmpty())
        assertTrue(contents.materialEntries.isEmpty())
        assertTrue(contents.materialEntryLines.isEmpty())
        assertFalse(contents.hasSupplies)
        assertTrue(contents.isEmpty)
    }

    @Test
    fun `header total is recomputed from the surviving lines`() {
        val text = suppliesJson(
            entries = """
                {
                  "entryId": "MAT-1",
                  "providerId": 1,
                  "providerName": "Paper Co.",
                  "purchasedAt": 1000,
                  "totalCents": 999999,
                  "lineCount": 99,
                  "lines": [
                    {"id": 1, "materialId": 9, "materialName": "Boxes",
                     "unit": "BOX", "quantity": 2, "unitCostCents": 250}
                  ]
                }
            """.trimIndent()
        )

        val entry = JsonImport.readBackup(text).materialEntries.single()
        assertEquals(500L, entry.totalCents)
        assertEquals(1, entry.lineCount)
    }

    @Test
    fun `a unit from a newer build is preserved as a custom label`() {
        val text = suppliesJson(
            materials = """
                {"id": 9, "name": "Tape", "unit": "ROLL", "stock": 3}
            """.trimIndent(),
            entries = entryWithLine(),
        )

        val material = JsonImport.readBackup(text).materials.single()
        assertEquals(MaterialUnit.OTHER, material.unit)
        assertEquals("ROLL", material.customUnit)
    }

    @Test
    fun `a second line for the same material is dropped and reported`() {
        val text = suppliesJson(
            entries = """
                {
                  "entryId": "MAT-1",
                  "providerId": 1,
                  "providerName": "Paper Co.",
                  "purchasedAt": 1000,
                  "lines": [
                    {"id": 1, "materialId": 9, "materialName": "Boxes",
                     "quantity": 1, "unitCostCents": 200},
                    {"id": 2, "materialId": 9, "materialName": "Boxes",
                     "quantity": 2, "unitCostCents": 200}
                  ]
                }
            """.trimIndent()
        )

        val contents = JsonImport.readBackup(text)
        assertEquals(1, contents.materialEntryLines.size)
        assertEquals(200L, contents.materialEntries.single().totalCents)
        assertTrue(contents.notes.any { it.contains("duplicate material") })
    }

    @Test
    fun `an entry with no readable lines is dropped`() {
        val text = suppliesJson(
            entries = """
                {
                  "entryId": "MAT-1",
                  "providerId": 1,
                  "providerName": "Paper Co.",
                  "purchasedAt": 1000,
                  "lines": []
                }
            """.trimIndent()
        )

        val contents = JsonImport.readBackup(text)
        assertTrue(contents.materialEntries.isEmpty())
        assertTrue(contents.materialEntryLines.isEmpty())
        assertTrue(contents.notes.any { it.contains("none of its lines") })
    }

    @Test
    fun `a missing provider row does not lose the snapshotted name`() {
        val text = suppliesJson(
            providers = "",
            entries = entryWithLine(),
        )

        val entry = JsonImport.readBackup(text).materialEntries.single()
        assertEquals("Paper Co.", entry.providerName)
        assertEquals(null, entry.providerId)
    }

    private fun entryWithLine(): String = """
        {
          "entryId": "MAT-1",
          "providerId": 99,
          "providerName": "Paper Co.",
          "purchasedAt": 1000,
          "lines": [
            {"id": 1, "materialId": 9, "materialName": "Tape",
             "quantity": 1, "unitCostCents": 200}
          ]
        }
    """.trimIndent()

    private fun suppliesJson(
        providers: String = """{"id": 1, "name": "Paper Co."}""",
        materials: String = """{"id": 9, "name": "Boxes", "unit": "BOX", "stock": 10}""",
        entries: String = "",
    ): String = """
        {
          "format": "$JSON_FORMAT",
          "version": $JSON_VERSION,
          "schemaVersion": 9,
          "providers": [$providers],
          "materials": [$materials],
          "materialEntries": [$entries],
          "materialEntryLines": []
        }
    """.trimIndent()
}
