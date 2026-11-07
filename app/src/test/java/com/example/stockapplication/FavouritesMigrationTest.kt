package com.example.stockapplication

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.stockapplication.data.StockDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Opens a real schema-9 database with the production 9→10 migration.
 *
 * This is the upgrade every existing install performs when favourites ship. It builds the old
 * tables exactly as Room did in 1.2.0, seeds inventory, then opens version 10 through Room's own
 * schema validation — which is what catches a migration whose SQL does not match the entity.
 *
 * A fresh install cannot catch that: it creates the new schema from scratch and never runs the
 * `ALTER TABLE`. The only way to exercise the upgrade path is to hand-build the old database, which
 * is why this exists.
 */
@RunWith(RobolectricTestRunner::class)
class FavouritesMigrationTest {

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
    fun `upgrading from schema 9 preserves inventory and adds the favourite column`() = runTest {
        createVersion9Database()

        val upgraded = Room.databaseBuilder(
            context,
            StockDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(StockDatabase.MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = upgraded.stockDao()

            // The row that existed before the upgrade must still be there, unchanged.
            val product = dao.snapshotProducts().single()
            assertEquals("Coffee", product.name)
            assertEquals("ABC", product.barcode)
            assertEquals(12, product.quantity)
            assertEquals(350L, product.priceCents)
            assertEquals(1L, product.categoryId)

            // The new column defaults to "not a favourite" rather than null or true: an existing
            // user must not have their whole catalogue starred on upgrade.
            assertFalse(product.favourite)

            // History is untouched by a product-column change.
            assertEquals(1, dao.snapshotOrders().size)
            assertEquals(1, dao.snapshotSales().size)
        } finally {
            upgraded.close()
        }
    }

    @Test
    fun `the new column is writable after the migration`() = runTest {
        createVersion9Database()

        val upgraded = Room.databaseBuilder(
            context,
            StockDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(StockDatabase.MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = upgraded.stockDao()
            val id = dao.snapshotProducts().single().id

            dao.setFavourite(id, true)
            assertTrue(dao.snapshotProducts().single().favourite)

            dao.setFavourite(id, false)
            assertFalse(dao.snapshotProducts().single().favourite)
        } finally {
            upgraded.close()
        }
    }

    /**
     * The complete v9 schema, copied from what Room generated for 1.2.0.
     *
     * Deliberately hand-written rather than generated: the point is to be independent of the
     * current entities, so this test fails if the production migration and the entity drift apart.
     */
    private fun createVersion9Database() {
        val file = context.getDatabasePath(DATABASE_NAME)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `categories` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` " +
                    "ON `categories` (`name`)"
            )
            // Note the absence of `favourite` — this is the whole point of the fixture.
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
            // Supplies, added in 8→9.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `providers` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`phone` TEXT NOT NULL, " +
                    "`address` TEXT NOT NULL, " +
                    "`notes` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_providers_name` " +
                    "ON `providers` (`name`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `materials` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`unit` TEXT NOT NULL, " +
                    "`customUnit` TEXT NOT NULL, " +
                    "`stock` INTEGER NOT NULL, " +
                    "`lowStockThreshold` INTEGER NOT NULL, " +
                    "`notes` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_materials_name` " +
                    "ON `materials` (`name`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `material_entries` (" +
                    "`entryId` TEXT NOT NULL, " +
                    "`providerId` INTEGER, " +
                    "`providerName` TEXT NOT NULL, " +
                    "`purchasedAt` INTEGER NOT NULL, " +
                    "`reference` TEXT NOT NULL, " +
                    "`note` TEXT NOT NULL, " +
                    "`totalCents` INTEGER NOT NULL, " +
                    "`lineCount` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`entryId`), " +
                    "FOREIGN KEY(`providerId`) REFERENCES `providers`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_material_entries_providerId` " +
                    "ON `material_entries` (`providerId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_material_entries_purchasedAt` " +
                    "ON `material_entries` (`purchasedAt`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `material_entry_lines` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`entryId` TEXT NOT NULL, " +
                    "`materialId` INTEGER, " +
                    "`materialName` TEXT NOT NULL, " +
                    "`unit` TEXT NOT NULL, " +
                    "`customUnit` TEXT NOT NULL, " +
                    "`quantity` INTEGER NOT NULL, " +
                    "`unitCostCents` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`entryId`) REFERENCES `material_entries`(`entryId`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`materialId`) REFERENCES `materials`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_material_entry_lines_entryId` " +
                    "ON `material_entry_lines` (`entryId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_material_entry_lines_materialId` " +
                    "ON `material_entry_lines` (`materialId`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_material_entry_lines_entryId_materialId` " +
                    "ON `material_entry_lines` (`entryId`, `materialId`)"
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

            db.version = 9
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DATABASE_NAME = "favourites-migration-test.db"
    }
}
