package com.example.stockapplication

import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.DatabaseSnapshot
import com.example.stockapplication.data.JsonImport
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.ProductTag
import com.example.stockapplication.data.Tag
import com.example.stockapplication.data.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tags in the backup file.
 *
 * The rule these tests protect is that tags are **data, not a view setting**: they go into every
 * export regardless of the `tagsEnabled` switch, and an older file without them still imports. Get
 * that wrong and a backup taken with the feature off would erase another device's tags on restore.
 */
class TagBackupTest {

    private fun snapshot(
        tags: List<Tag> = emptyList(),
        productTags: List<ProductTag> = emptyList(),
    ) = DatabaseSnapshot(
        schemaVersion = 8,
        appVersionName = "1.0",
        categories = emptyList(),
        products = listOf(Product(id = 1, barcode = "A", name = "Coffee", quantity = 3)),
        orders = emptyList(),
        saleLines = emptyList(),
        legacySales = emptyList(),
        settings = AppSettings(),
        tags = tags,
        productTags = productTags,
        exportedAt = 1_700_000_000_000,
    )

    @Test
    fun `tags and their assignments survive the round trip`() {
        val text = snapshot(
            tags = listOf(Tag(id = 10, name = "fragile"), Tag(id = 20, name = "clearance")),
            productTags = listOf(ProductTag(productId = 1, tagId = 10)),
        ).toJson()

        val contents = JsonImport.readBackup(text)

        assertEquals(listOf(Tag(10, "fragile"), Tag(20, "clearance")), contents.tags)
        assertEquals(listOf(ProductTag(1, 10)), contents.productTags)
    }

    @Test
    fun `tags are exported even with tagging switched off`() {
        // The snapshot above carries `AppSettings()` — i.e. tagsEnabled = false — and still writes
        // the tags. That is the point: the setting hides the UI, it does not make the data go away.
        val text = snapshot(tags = listOf(Tag(id = 10, name = "fragile"))).toJson()
        assertTrue(text.contains("fragile"))
        assertTrue(text.contains("\"tags\""))
    }

    @Test
    fun `a file without tags imports with empty lists rather than failing`() {
        val text = """
            {
              "format": "stockapplication.backup",
              "version": 1,
              "schemaVersion": 7,
              "categories": [],
              "products": [],
              "orders": []
            }
        """.trimIndent()

        val contents = JsonImport.readBackup(text)
        assertTrue(contents.tags.isEmpty())
        assertTrue(contents.productTags.isEmpty())
        // And no settings block either, so the import dialog offers no settings switch.
        assertNull(contents.settings)
    }

    @Test
    fun `duplicate tag names are dropped and reported`() {
        // The table has a UNIQUE index on the name, so letting a second "Fragile" through would
        // abort the whole restore on the insert.
        val text = """
            {
              "format": "stockapplication.backup",
              "version": 1,
              "schemaVersion": 8,
              "categories": [],
              "products": [],
              "orders": [],
              "tags": [
                { "id": 1, "name": "fragile" },
                { "id": 2, "name": "Fragile" }
              ]
            }
        """.trimIndent()

        val contents = JsonImport.readBackup(text)
        assertEquals(1, contents.tags.size)
        assertTrue(
            "the dropped duplicate should be explained to the user",
            contents.notes.any { it.contains("duplicate") }
        )
    }

    @Test
    fun `a blank tag name is dropped`() {
        val text = """
            {
              "format": "stockapplication.backup",
              "version": 1,
              "schemaVersion": 8,
              "categories": [],
              "products": [],
              "orders": [],
              "tags": [ { "id": 1, "name": "   " } ]
            }
        """.trimIndent()

        assertTrue(JsonImport.readBackup(text).tags.isEmpty())
    }

    @Test
    fun `an assignment missing either end is discarded`() {
        val text = """
            {
              "format": "stockapplication.backup",
              "version": 1,
              "schemaVersion": 8,
              "categories": [],
              "products": [],
              "orders": [],
              "tags": [ { "id": 5, "name": "fragile" } ],
              "productTags": [
                { "productId": 1, "tagId": 5 },
                { "productId": 2 },
                { "tagId": 5 },
                { "productId": 1, "tagId": 5 }
              ]
            }
        """.trimIndent()

        val contents = JsonImport.readBackup(text)
        // Only the complete, distinct pair survives.
        assertEquals(listOf(ProductTag(1, 5)), contents.productTags)
    }
}
