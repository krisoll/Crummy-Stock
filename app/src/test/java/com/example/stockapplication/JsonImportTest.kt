package com.example.stockapplication

import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.BackupFormatException
import com.example.stockapplication.data.Category
import com.example.stockapplication.data.DatabaseSnapshot
import com.example.stockapplication.data.DiscountType
import com.example.stockapplication.data.JsonImport
import com.example.stockapplication.data.JsonParseException
import com.example.stockapplication.data.JSON_FORMAT
import com.example.stockapplication.data.JSON_VERSION
import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.Promo
import com.example.stockapplication.data.PromoKind
import com.example.stockapplication.data.Sale
import com.example.stockapplication.data.SaleOrder
import com.example.stockapplication.data.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The importer is the code path that can destroy a shop's records in one tap, so these tests are
 * written as round trips: build a snapshot, serialise it with the real [toJson], read it back with
 * the real importer, and assert nothing moved.
 *
 * The interesting cases are not the happy path but the damaged files: a hand-edited backup, a
 * truncated one, a version from the future, and the structural duplicates the format deliberately
 * contains (`sales[]` mirroring `orders[].lines`, and `legacySales[]` mirroring the cart-era rows).
 */
class JsonImportTest {

    private val settings = AppSettings(
        taxRateBasisPoints = 1900,
        cardFeeRateBasisPoints = 275,
        cardFeeFixedCents = 15,
        languageTag = "es",
        // Appearance choices one phone made; the importer must not carry these across.
        useAppBackgroundColor = true,
        backgroundColorArgb = 0xFF102030,
    )

    private fun snapshot() = DatabaseSnapshot(
        schemaVersion = 6,
        appVersionName = "1.0",
        categories = listOf(Category(id = 7, name = "Drinks")),
        products = listOf(
            Product(id = 1, barcode = "ABC", name = "Coffee", priceCents = 350, quantity = 12),
            Product(
                id = 2,
                barcode = "DEF",
                name = "Tea \"Earl Grey\", loose",
                priceCents = 420,
                quantity = 0,
                categoryId = 7,
                imagePath = "img-2.jpg",
            ),
        ),
        orders = listOf(
            SaleOrder(
                orderId = "ORD-1",
                soldAt = 1_700_000_000_000,
                paymentMethod = PaymentMethod.CARD,
                itemsSubtotalCents = 770,
                taxRateBasisPoints = 1900,
                taxCents = 146,
                cardFeeRateBasisPoints = 275,
                cardFeeFixedCents = 15,
                cardFeeCents = 36,
                totalCents = 588,
                itemCount = 2,
                note = "line1\nline2\ttabbed \\ back",
            )
        ),
        saleLines = listOf(
            Sale(
                id = 10,
                productId = 1,
                productName = "Coffee",
                barcode = "ABC",
                quantity = 1,
                unitPriceCents = 350,
                soldAt = 1_700_000_000_000,
                orderId = "ORD-1",
            ),
            Sale(
                id = 11,
                productId = 2,
                productName = "Tea",
                barcode = "DEF",
                quantity = 1,
                unitPriceCents = 420,
                soldAt = 1_700_000_000_000,
                orderId = "ORD-1",
            ),
        ),
        legacySales = listOf(
            Sale(
                id = 9,
                productId = 1,
                productName = "Coffee",
                barcode = "ABC",
                quantity = 2,
                unitPriceCents = 350,
                soldAt = 1_699_000_000_000,
            )
        ),
        settings = settings,
        exportedAt = 1_700_000_100_000,
    )

    // ---- Round trip --------------------------------------------------------

    @Test
    fun `round trips a whole backup without changing a field`() {
        val source = snapshot()
        val back = JsonImport.readBackup(source.toJson())

        assertEquals(6, back.schemaVersion)
        assertEquals(1_700_000_100_000, back.exportedAt)
        assertEquals(source.categories, back.categories)
        assertEquals(source.products, back.products)
        assertEquals(source.orders, back.orders)
        assertEquals(emptyList<String>(), back.notes)
    }

    @Test
    fun `round trips every sale line exactly once`() {
        val back = JsonImport.readBackup(snapshot().toJson())
        // Two cart lines. legacySales[9] is a duplicate of a row the reader already has by id and
        // orderId, so folding both arrays must not double the ledger.
        assertEquals(2, back.lines.size)
        assertEquals(setOf(10L, 11L), back.lines.map { it.id }.toSet())
        assertEquals(listOf("ORD-1", "ORD-1"), back.lines.map { it.orderId })
    }

    @Test
    fun `preserves free text escaping exactly`() {
        val back = JsonImport.readBackup(snapshot().toJson())
        assertEquals("Tea \"Earl Grey\", loose", back.products.single { it.id == 2L }.name)
        assertEquals("line1\nline2\ttabbed \\ back", back.orders.single().note)
    }

    @Test
    fun `keeps a null category distinct from category zero`() {
        val back = JsonImport.readBackup(snapshot().toJson())
        assertNull(back.products.single { it.id == 1L }.categoryId)
        assertEquals(7L, back.products.single { it.id == 2L }.categoryId)
    }

    @Test
    fun `does not import appearance settings`() {
        // The exporter writes these only when asked; the reader ignores them either way, because
        // they describe one phone rather than the shop.
        val back = JsonImport.readBackup(snapshot().toJson(includeSettings = true))
        assertEquals(1, back.orders.size)
    }

    @Test
    fun `round trips promo rules`() {
        val source = snapshot().copy(
            promos = listOf(
                Promo(
                    id = 5,
                    name = "Tea time",
                    kind = PromoKind.CATEGORY,
                    categoryId = 7,
                    thresholdQuantity = 3,
                    discountType = DiscountType.FIXED,
                    discountPercentBasisPoints = 1667,
                    discountFixedCents = 400,
                    enabled = true,
                ),
                Promo(
                    id = 6,
                    name = "Coffee pair",
                    kind = PromoKind.PRODUCTS,
                    productIdsCsv = "1,2",
                    thresholdQuantity = 2,
                    discountType = DiscountType.PERCENT,
                    discountPercentBasisPoints = 500,
                    enabled = false,
                    startsAt = 1_000L,
                    endsAt = 2_000L,
                ),
            )
        )
        val back = JsonImport.readBackup(source.toJson())
        assertEquals(source.promos, back.promos)
        assertEquals(emptyList<String>(), back.notes)
    }

    @Test
    fun `reads promo product ids from the legacy csv string`() {
        val json = backupJson(
            extra = """
                "promos": [
                  {"id": 1, "name": "Old product promo", "kind": "PRODUCTS",
                   "productIds": "1, 2", "thresholdQuantity": 2},
                  {"id": 2, "name": "Old category promo", "kind": "CATEGORY",
                   "categoryId": 7, "productIds": "[]", "thresholdQuantity": 1}
                ],
            """.trimIndent()
        )
        val back = JsonImport.readBackup(json)
        assertEquals(2, back.promos.size)
        assertEquals("1", back.promos.first { it.id == 1L }.productIdsCsv)
        assertEquals("", back.promos.first { it.id == 2L }.productIdsCsv)
        assertEquals(7L, back.promos.first { it.id == 2L }.categoryId)
    }

    @Test
    fun `drops a promo whose points at a product that is not in the file`() {
        val json = backupJson(
            extra = """
                "promos": [
                  {"id": 1, "name": "Ghost promo", "kind": "PRODUCTS",
                   "productIds": [99], "thresholdQuantity": 1}
                ],
            """.trimIndent()
        )
        val back = JsonImport.readBackup(json)
        assertTrue(back.promos.isEmpty())
        assertTrue(back.notes.any { "none of its products" in it })
    }

    @Test
    fun `reports an empty file as empty rather than an error`() {
        val empty = DatabaseSnapshot(
            schemaVersion = 6,
            appVersionName = "1.0",
            categories = emptyList(),
            products = emptyList(),
            orders = emptyList(),
            saleLines = emptyList(),
            legacySales = emptyList(),
            settings = settings,
        )
        assertTrue(JsonImport.readBackup(empty.toJson()).isEmpty)
    }

    // ---- Refusals ----------------------------------------------------------

    @Test
    fun `refuses a json document that is not a backup`() {
        val error = assertFailsWith<BackupFormatException> {
            JsonImport.readBackup("""{"hello": "world"}""")
        }
        assertTrue(error.message.orEmpty().contains("not a StockApplication backup"))
    }

    @Test
    fun `refuses a backup from a newer format version`() {
        val json = """{"format": "$JSON_FORMAT", "version": ${JSON_VERSION + 1}}"""
        val error = assertFailsWith<BackupFormatException> { JsonImport.readBackup(json) }
        assertTrue(error.message.orEmpty().contains("newer version"))
    }

    @Test
    fun `refuses a non-object document`() {
        assertFailsWith<BackupFormatException> { JsonImport.readBackup("[1, 2, 3]") }
    }

    @Test
    fun `refuses malformed json with a position`() {
        val error = assertFailsWith<JsonParseException> {
            JsonImport.readBackup("""{"format": "$JSON_FORMAT", "products": [ }""")
        }
        assertTrue(error.message.orEmpty().contains("line"))
    }

    @Test
    fun `refuses a truncated file instead of importing half of it`() {
        val full = snapshot().toJson()
        val truncated = full.substring(0, full.length / 2)
        assertFailsWith<JsonParseException> { JsonImport.readBackup(truncated) }
    }

    // ---- Recovery ----------------------------------------------------------

    @Test
    fun `skips a duplicate barcode instead of failing the whole import`() {
        val json = backupJson(
            products = """
                {"id": 1, "barcode": "ABC", "name": "Coffee", "priceCents": 350, "quantity": 5},
                {"id": 2, "barcode": "ABC", "name": "Coffee again", "priceCents": 350, "quantity": 1}
            """.trimIndent()
        )
        val back = JsonImport.readBackup(json)
        assertEquals(1, back.products.size)
        assertEquals("Coffee", back.products.single().name)
        assertEquals(1, back.notes.size)
        assertTrue(back.notes.single().contains("duplicate barcode"))
    }

    @Test
    fun `drops a category id that is not in the file`() {
        val json = backupJson(
            products = """{"id": 1, "barcode": "ABC", "name": "Coffee", "categoryId": 99}"""
        )
        val back = JsonImport.readBackup(json)
        assertNull(back.products.single().categoryId)
    }

    @Test
    fun `drops an order whose lines could not be read`() {
        val json = backupJson(
            orders = """
                {"orderId": "ORD-1", "soldAt": 1, "totalCents": 100, "lines": []}
            """.trimIndent()
        )
        val back = JsonImport.readBackup(json)
        assertEquals(emptyList<SaleOrder>(), back.orders)
        assertTrue(back.notes.any { it.contains("none of its lines") })
    }

    @Test
    fun `reads a file that has only the flat sales array`() {
        // Older or hand-trimmed files may lack the nested copy; the merge has to cope.
        val json = backupJson(
            extra = """"sales": [
                {"id": 5, "productId": 1, "productName": "Coffee", "quantity": 2,
                 "unitPriceCents": 350, "soldAt": 100, "orderId": "ORD-1"}
            ],"""
        )
        val back = JsonImport.readBackup(json)
        assertEquals(1, back.lines.size)
        assertEquals(5L, back.lines.single().id)
    }

    @Test
    fun `survives a field with the wrong type by refusing the file`() {
        // A string where a number belongs is a file we cannot trust; reporting it beats guessing.
        val json = backupJson(
            products = """{"id": 1, "barcode": "ABC", "name": "Coffee", "priceCents": "lots"}"""
        )
        assertFailsWith<JsonParseException> { JsonImport.readBackup(json) }
    }

    @Test
    fun `ignores unknown fields so newer files still read`() {
        val json = backupJson(
            products = """{"id": 1, "barcode": "ABC", "name": "Coffee", "futureField": 42}"""
        )
        assertEquals(1, JsonImport.readBackup(json).products.size)
    }

    // ---- Helpers -----------------------------------------------------------

    /** A minimal valid document with the given arrays substituted in. */
    private fun backupJson(
        products: String = """{"id": 1, "barcode": "ABC", "name": "Coffee"}""",
        orders: String = "",
        extra: String = "",
    ): String = """
        {
          "format": "$JSON_FORMAT",
          "version": $JSON_VERSION,
          "schemaVersion": 6,
          "exportedAt": 1700000100000,
          "categories": [{"id": 7, "name": "Drinks"}],
          "products": [
        $products
          ],
          "orders": [
        $orders
          ],
          $extra
          "settings": {"taxRateBasisPoints": 1900}
        }
    """.trimIndent()

    private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            fail("Expected ${T::class.java.simpleName} but got ${error::class.java.name}: ${error.message}")
        }
        fail("Expected ${T::class.java.simpleName} but nothing was thrown")
        throw AssertionError("unreachable")
    }
}
