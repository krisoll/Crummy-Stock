package com.example.stockapplication

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.stockapplication.data.StockDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Opens a real schema-8 database with the production 8→9 migration.
 *
 * This is the upgrade every existing install will perform. It builds the old tables exactly as
 * Room did in 1.1.1, seeds a product and an order, then opens version 9 through Room's own
 * validation. A mismatch between the migration SQL and the entity schema fails here rather than
 * on a user's phone.
 */
@RunWith(RobolectricTestRunner::class)
class SuppliesMigrationTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun removeOldFile() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `upgrading from schema 8 preserves inventory and adds empty supply tables`() = runTest {
        createVersion8Database()

        val upgraded = Room.databaseBuilder(
            context,
            StockDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(StockDatabase.MIGRATION_8_9, StockDatabase.MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = upgraded.stockDao()
            val product = dao.snapshotProducts().single()
            assertEquals("Coffee", product.name)
            assertEquals("ABC", product.barcode)
            assertEquals(12, product.quantity)

            val order = dao.snapshotOrders().single()
            assertEquals("ORD-OLD", order.orderId)
            assertEquals(1, dao.snapshotSales().size)

            assertTrue(dao.snapshotProviders().isEmpty())
            assertTrue(dao.snapshotMaterials().isEmpty())
            assertTrue(dao.snapshotMaterialEntries().isEmpty())
            assertTrue(dao.snapshotMaterialEntryLines().isEmpty())
        } finally {
            upgraded.close()
        }
    }

    private fun createVersion8Database() {
        val file = context.getDatabasePath(DATABASE_NAME)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            // The complete v8 schema, copied from the schema Room generated for 1.1.1.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `categories` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` " +
                    "ON `categories` (`name`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `products` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`barcode` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`priceCents` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, " +
                    "`categoryId` INTEGER, `imagePath` TEXT)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_products_barcode` " +
                    "ON `products` (`barcode`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sale_orders` (" +
                    "`orderId` TEXT NOT NULL, `soldAt` INTEGER NOT NULL, " +
                    "`paymentMethod` TEXT NOT NULL, `itemsSubtotalCents` INTEGER NOT NULL, " +
                    "`promoDiscountCents` INTEGER NOT NULL, `promoName` TEXT NOT NULL, " +
                    "`taxRateBasisPoints` INTEGER NOT NULL, `taxCents` INTEGER NOT NULL, " +
                    "`cardFeeRateBasisPoints` INTEGER NOT NULL, " +
                    "`cardFeeFixedCents` INTEGER NOT NULL, " +
                    "`cardFeeCents` INTEGER NOT NULL, `totalCents` INTEGER NOT NULL, " +
                    "`itemCount` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                    "PRIMARY KEY(`orderId`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sales` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`productId` INTEGER NOT NULL, `productName` TEXT NOT NULL, " +
                    "`barcode` TEXT NOT NULL, `quantity` INTEGER NOT NULL, " +
                    "`unitPriceCents` INTEGER NOT NULL, `soldAt` INTEGER NOT NULL, " +
                    "`orderId` TEXT NOT NULL, " +
                    "FOREIGN KEY(`productId`) REFERENCES `products`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_sales_productId` ON `sales` (`productId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_sales_soldAt` ON `sales` (`soldAt`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_sales_orderId` ON `sales` (`orderId`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `promos` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `kind` TEXT NOT NULL, `categoryId` INTEGER, " +
                    "`productIdsCsv` TEXT NOT NULL, `thresholdQuantity` INTEGER NOT NULL, " +
                    "`discountType` TEXT NOT NULL, " +
                    "`discountPercentBasisPoints` INTEGER NOT NULL, " +
                    "`discountFixedCents` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, " +
                    "`startsAt` INTEGER, `endsAt` INTEGER)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tags` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `product_tags` (" +
                    "`productId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`productId`, `tagId`), " +
                    "FOREIGN KEY(`productId`) REFERENCES `products`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_product_tags_tagId` " +
                    "ON `product_tags` (`tagId`)"
            )

            db.execSQL("INSERT INTO categories (id, name) VALUES (1, 'Drinks')")
            db.execSQL(
                "INSERT INTO products " +
                    "(id, barcode, name, priceCents, quantity, categoryId, imagePath) " +
                    "VALUES (1, 'ABC', 'Coffee', 350, 12, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO sale_orders " +
                    "(orderId, soldAt, paymentMethod, itemsSubtotalCents, promoDiscountCents, " +
                    "promoName, taxRateBasisPoints, taxCents, cardFeeRateBasisPoints, " +
                    "cardFeeFixedCents, cardFeeCents, totalCents, itemCount, note) " +
                    "VALUES ('ORD-OLD', 1700000000000, 'CASH', 350, 0, '', 0, 0, 0, 0, 0, " +
                    "350, 1, '')"
            )
            db.execSQL(
                "INSERT INTO sales " +
                    "(id, productId, productName, barcode, quantity, unitPriceCents, soldAt, " +
                    "orderId) VALUES (1, 1, 'Coffee', 'ABC', 1, 350, 1700000000000, 'ORD-OLD')"
            )

            db.version = 8
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DATABASE_NAME = "supplies-migration-test.db"
    }
}
