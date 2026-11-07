package com.example.stockapplication

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.stockapplication.data.BackupContents
import com.example.stockapplication.data.Category
import com.example.stockapplication.data.DiscountType
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.Promo
import com.example.stockapplication.data.PromoKind
import com.example.stockapplication.data.StockDao
import com.example.stockapplication.data.StockDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Promos are part of a backup and must survive [StockDao.restore] like every other table.
 *
 * The ids inside the file are meaningless on this device, so the point of these tests is the
 * remapping: a category promo must point at the category row that was actually written, and a
 * product promo's CSV must list the new product ids.
 */
@RunWith(RobolectricTestRunner::class)
class PromoBackupTest {

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
    fun `restore writes promos and remaps their category and product ids`() = runTest {
        val result = dao.restore(backupWithPromos(), replace = true)

        assertEquals(2, result.promosAdded)

        val categories = dao.snapshotCategories()
        val products = dao.snapshotProducts()
        val promos = dao.snapshotPromos()

        assertEquals(2, promos.size)

        val categoryPromo = promos.single { it.name == "Tea time" }
        assertEquals(PromoKind.CATEGORY, categoryPromo.kind)
        assertEquals(categories.single().id, categoryPromo.categoryId)
        assertEquals("", categoryPromo.productIdsCsv)

        val productPromo = promos.single { it.name == "Coffee pair" }
        assertEquals(PromoKind.PRODUCTS, productPromo.kind)
        assertEquals(null, productPromo.categoryId)
        assertEquals(products.map { it.id }.toSet(), productPromo.productIdSet)
        assertEquals(DiscountType.PERCENT, productPromo.discountType)
        assertEquals(500, productPromo.discountPercentBasisPoints)
        assertEquals(false, productPromo.enabled)
    }

    @Test
    fun `importing the same file twice does not duplicate promos`() = runTest {
        dao.restore(backupWithPromos(), replace = true)
        val second = dao.restore(backupWithPromos(), replace = false)

        assertEquals(0, second.promosAdded)
        assertEquals(2, dao.snapshotPromos().size)
    }

    private fun backupWithPromos() = BackupContents(
        schemaVersion = 8,
        exportedAt = 1_700_000_000_000,
        categories = listOf(Category(id = 7, name = "Drinks")),
        products = listOf(
            Product(id = 1, barcode = "ABC", name = "Coffee", priceCents = 350, quantity = 10),
            Product(id = 2, barcode = "DEF", name = "Tea", priceCents = 420, quantity = 10),
        ),
        orders = emptyList(),
        lines = emptyList(),
        notes = emptyList(),
        promos = listOf(
            Promo(
                id = 10,
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
                id = 11,
                name = "Coffee pair",
                kind = PromoKind.PRODUCTS,
                productIdsCsv = "1,2",
                thresholdQuantity = 2,
                discountType = DiscountType.PERCENT,
                discountPercentBasisPoints = 500,
                enabled = false,
            ),
        ),
    )
}
