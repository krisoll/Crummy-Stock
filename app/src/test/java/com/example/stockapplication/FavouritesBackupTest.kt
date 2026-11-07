package com.example.stockapplication

import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.DatabaseSnapshot
import com.example.stockapplication.data.JsonImport
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Favourites in the backup file.
 *
 * Two rules matter and they pull in opposite directions:
 *
 *  - a favourite the user set must **survive** export → import, or a restore silently loses it;
 *  - a backup written **before** favourites existed must still import, with nothing starred.
 *
 * The format version is deliberately not bumped for this: adding an optional field is compatible
 * in both directions, and bumping would make new files unreadable by older builds for no reason.
 */
class FavouritesBackupTest {

    private fun snapshot(products: List<Product>) = DatabaseSnapshot(
        schemaVersion = 10,
        appVersionName = "1.3.0",
        categories = emptyList(),
        products = products,
        orders = emptyList(),
        saleLines = emptyList(),
        legacySales = emptyList(),
        settings = AppSettings(),
        tags = emptyList(),
        productTags = emptyList(),
        exportedAt = 1_700_000_000_000,
    )

    @Test
    fun `a favourite survives the round trip`() {
        val text = snapshot(
            listOf(
                Product(id = 1, barcode = "A", name = "Coffee", favourite = true),
                Product(id = 2, barcode = "B", name = "Tea", favourite = false),
            )
        ).toJson()

        val products = JsonImport.readBackup(text).products

        assertTrue(products.single { it.id == 1L }.favourite)
        assertFalse(products.single { it.id == 2L }.favourite)
    }

    @Test
    fun `the field is written into the file`() {
        val text = snapshot(listOf(Product(id = 1, barcode = "A", name = "Coffee", favourite = true)))
            .toJson()

        assertTrue(text.contains("\"favourite\""))
    }

    @Test
    fun `a backup from before favourites imports with nothing starred`() {
        // Hand-written on purpose: this is the shape of a file an older build produced, and the
        // importer must not treat the missing key as a reason to skip the product.
        val text = """
            {
              "format": "stockapplication.backup",
              "version": 1,
              "schemaVersion": 9,
              "categories": [],
              "products": [
                {
                  "id": 1,
                  "barcode": "A",
                  "name": "Coffee",
                  "priceCents": 350,
                  "quantity": 4,
                  "categoryId": null,
                  "imagePath": null
                }
              ],
              "orders": [],
              "sales": [],
              "legacySales": [],
              "settings": {}
            }
        """.trimIndent()

        val products = JsonImport.readBackup(text).products

        assertEquals(1, products.size)
        assertEquals("Coffee", products.single().name)
        assertFalse(products.single().favourite)
    }
}
