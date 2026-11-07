package com.example.stockapplication

import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.Category
import com.example.stockapplication.data.DatabaseSnapshot
import com.example.stockapplication.data.JSON_FORMAT
import com.example.stockapplication.data.JSON_VERSION
import com.example.stockapplication.data.JsonWriter
import com.example.stockapplication.data.PaymentMethod
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.Sale
import com.example.stockapplication.data.SaleOrder
import com.example.stockapplication.data.isoUtc
import com.example.stockapplication.data.jsonBackupFileName
import com.example.stockapplication.data.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup file is the only way data leaves this app, so these tests treat the JSON as a
 * contract with whatever reads it on the other side: it has to be *valid* JSON, it has to carry
 * the raw values, and it has to preserve the relationships between rows.
 *
 * There is no JSON parser on the plain JVM test classpath (`org.json` is stubbed, and the project
 * deliberately has no serialisation dependency), so [assertValidJson] walks the document once
 * with a small structural scanner. That is enough to catch an unescaped quote, a stray trailing
 * comma or an unbalanced container — the ways a hand-written emitter actually breaks.
 */
class JsonExportTest {

    private val settings = AppSettings(
        taxRateBasisPoints = 1900,
        cardFeeRateBasisPoints = 275,
        cardFeeFixedCents = 15,
        languageTag = "es",
    )

    private fun fixture(
        products: List<Product> = listOf(
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
        categories: List<Category> = listOf(Category(id = 7, name = "Drinks")),
        orders: List<SaleOrder> = listOf(
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
        saleLines: List<Sale> = listOf(
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
        legacySales: List<Sale> = listOf(
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
    ) = DatabaseSnapshot(
        schemaVersion = 6,
        appVersionName = "1.0",
        categories = categories,
        products = products,
        orders = orders,
        saleLines = saleLines,
        legacySales = legacySales,
        settings = settings,
        exportedAt = 1_700_000_100_000,
    )

    @Test
    fun `produces structurally valid json`() {
        assertValidJson(fixture().toJson())
    }

    @Test
    fun `emits the format marker and versions`() {
        val json = fixture().toJson()
        assertTrue(json.contains("\"format\": \"$JSON_FORMAT\""))
        assertTrue(json.contains("\"version\": $JSON_VERSION"))
        assertTrue(json.contains("\"schemaVersion\": 6"))
        assertTrue(json.contains("\"appVersion\": \"1.0\""))
    }

    @Test
    fun `writes raw money values alongside the readable ones`() {
        val json = fixture().toJson()
        assertTrue(json.contains("\"priceCents\": 350"))
        // The presentation copy follows the chosen currency; the raw integer is the value.
        assertTrue(json.contains("\"priceFormatted\": \"$3.50\""))
        assertTrue(json.contains("\"taxRateBasisPoints\": 1900"))
        assertTrue(json.contains("\"totalCents\": 588"))
    }

    @Test
    fun `preserves primary keys so relationships survive`() {
        val json = fixture().toJson()
        // Category id on the product, and product ids on the sale lines.
        assertTrue(json.contains("\"categoryId\": 7"))
        assertTrue(json.contains("\"productId\": 1"))
        assertTrue(json.contains("\"orderId\": \"ORD-1\""))
    }

    @Test
    fun `nests order lines and also emits the flat sales table`() {
        val json = fixture().toJson()
        assertTrue(json.contains("\"lines\": ["))
        // Two nested lines + the same two rows in the flat table + one legacy row = 5.
        assertEquals(5, Regex("\"unitPriceCents\"").findAll(json).count())
        // The legacy row appears exactly once, in legacySales only.
        assertEquals(1, Regex("\"orderId\": \"\"").findAll(json).count())
    }

    @Test
    fun `separates legacy sales that have no order header`() {
        val json = fixture().toJson()
        val legacyBlock = json.substringAfter("\"legacySales\": [").substringBefore("\n  ]")
        assertTrue(legacyBlock.contains("\"productName\": \"Coffee\""))
        // The legacy row is the only one with an empty orderId.
        assertTrue(legacyBlock.contains("\"orderId\": \"\""))
    }

    @Test
    fun `escapes quotes backslashes and control characters in free text`() {
        val json = fixture().toJson()
        assertTrue(json.contains("Tea \\\"Earl Grey\\\", loose"))
        assertTrue(json.contains("line1\\nline2\\ttabbed \\\\ back"))
        assertValidJson(json)
    }

    @Test
    fun `null optional fields are emitted as json null rather than dropped`() {
        val json = fixture().toJson()
        // The first product has no category and no image.
        val firstProduct = json.substringAfter("\"products\": [").substringBefore("\n  },")
        assertTrue(firstProduct, firstProduct.contains("\"categoryId\": null"))
        assertTrue(firstProduct, firstProduct.contains("\"imagePath\": null"))
    }

    @Test
    fun `emits empty arrays for an empty database`() {
        val json = DatabaseSnapshot(
            schemaVersion = 6,
            appVersionName = "1.0",
            categories = emptyList(),
            products = emptyList(),
            orders = emptyList(),
            saleLines = emptyList(),
            legacySales = emptyList(),
            settings = AppSettings(),
            exportedAt = 1_700_000_000_000,
        ).toJson()
        assertTrue(json.contains("\"products\": []"))
        assertValidJson(json)
    }

    @Test
    fun `appearance settings are only written when asked for`() {
        assertFalse(fixture().toJson(includeSettings = false).contains("languageTag"))
        assertTrue(fixture().toJson(includeSettings = true).contains("\"languageTag\": \"es\""))
        // The accounting rates are always present: they explain the order columns.
        assertTrue(fixture().toJson(includeSettings = false).contains("\"taxRateBasisPoints\""))
    }

    @Test
    fun `iso timestamp is utc`() {
        assertEquals("2023-11-14T22:13:20Z", isoUtc(1_700_000_000_000))
    }

    @Test
    fun `file name is timestamped and sorts lexicographically`() {
        val early = jsonBackupFileName(1_700_000_000_000)
        val late = jsonBackupFileName(1_700_000_060_000)
        assertTrue(early.startsWith("stock-backup-"))
        assertTrue(early.endsWith(".json"))
        assertTrue("$early should sort before $late", early < late)
    }

    @Test
    fun `writer escapes every character json forbids`() {
        val out = StringBuilder()
        val writer = JsonWriter(out)
        writer.beginObject()
        writer.field("k", "a\"b\\c\nd\re\tf\u0001")
        writer.endObject()
        assertEquals("{\n  \"k\": \"a\\\"b\\\\c\\nd\\re\\tf\\u0001\"\n}", out.toString())
    }

    /**
     * Walks the document checking it is structurally sound: balanced containers, well-formed
     * strings, no trailing or doubled commas, and no value written immediately before a `]`.
     */
    private fun assertValidJson(json: String) {
        val stack = ArrayDeque<Char>()
        var inString = false
        var escaped = false
        // True when the parser has just consumed a separator and is therefore mid-value.
        var expectValue = false
        // True when the last thing consumed was an opening bracket (an empty container is legal).
        var justOpened = false

        json.forEachIndexed { index, char ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                return@forEachIndexed
            }
            when (char) {
                '"' -> {
                    inString = true
                    expectValue = false
                    justOpened = false
                }
                '{', '[' -> {
                    // Legal as a value (after `:` or `,`), or as the start of the document.
                    stack.addLast(char)
                    expectValue = true
                    justOpened = true
                }
                '}', ']' -> {
                    // A value is still "expected" right after `{`/`[`, which is allowed here.
                    if (expectValue && !justOpened && char == ']') {
                        fail(json, index, "value expected before $char")
                    }
                    val open = stack.removeLastOrNull() ?: fail(json, index, "unbalanced $char")
                    if ((open == '{' && char != '}') || (open == '[' && char != ']')) {
                        fail(json, index, "mismatched $open / $char")
                    }
                    expectValue = false
                    justOpened = false
                }
                ',' -> {
                    if (expectValue) fail(json, index, "trailing or doubled comma")
                    expectValue = true
                    justOpened = false
                }
                ':' -> {
                    expectValue = true
                    justOpened = false
                }
                ' ', '\n', '\r', '\t' -> Unit
                else -> {
                    // Bare token characters: numbers, true, false, null.
                    expectValue = false
                    justOpened = false
                }
            }
        }
        assertFalse("unterminated string", inString)
        assertEquals("unbalanced containers", 0, stack.size)
    }

    private fun fail(json: String, index: Int, reason: String): Nothing {
        val from = (index - 40).coerceAtLeast(0)
        val to = (index + 40).coerceAtMost(json.length)
        throw AssertionError("Invalid JSON ($reason) at $index near: …${json.substring(from, to)}…")
    }
}
