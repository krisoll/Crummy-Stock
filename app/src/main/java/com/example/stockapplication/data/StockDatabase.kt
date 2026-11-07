package com.example.stockapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Product::class,
        Sale::class,
        SaleOrder::class,
        Category::class,
        Promo::class,
        Tag::class,
        ProductTag::class,
        Provider::class,
        Material::class,
        MaterialEntry::class,
        MaterialEntryLine::class,
    ],
    version = StockDatabase.SCHEMA_VERSION,
    exportSchema = false
)
abstract class StockDatabase : RoomDatabase() {

    abstract fun stockDao(): StockDao

    companion object {
        /**
         * Room schema version, recorded in JSON backups so an importer can tell which columns to
         * expect. Must match the `version` above; kept as a constant so the exporter and the
         * `@Database` annotation cannot drift apart.
         */
        const val SCHEMA_VERSION = 10

        /**
         * The app's version name, read from the manifest so `build.gradle.kts` stays the single
         * source of truth.
         *
         * Falls back to "unknown" rather than throwing: a backup missing one informational field
         * is still worth writing, and this runs inside the export path.
         */
        fun appVersionName(context: Context): String = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"

        @Volatile
        private var instance: StockDatabase? = null

        /**
         * v5 added [SaleOrder.cardFeeFixedCents] for the flat card fee.
         *
         * `NOT NULL DEFAULT 0` keeps every historical order valid: before this version there was
         * no flat fee, and 0 is exactly right for orders that never paid one.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `sale_orders` ADD COLUMN `cardFeeFixedCents` INTEGER NOT NULL " +
                        "DEFAULT 0"
                )
            }
        }

        /**
         * v6 added the `note` column on `sale_orders` for an optional per-order memo.
         *
         * `NOT NULL DEFAULT ''` keeps every historical order valid: they simply have no note.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `sale_orders` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * v7 added the `promos` table and the two promo columns on `sale_orders`.
         *
         * Additive throughout, with defaults that describe history correctly: a sale that
         * predates promos genuinely had no promo discount and no promo name.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `promos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`categoryId` INTEGER, " +
                        "`productIdsCsv` TEXT NOT NULL, " +
                        "`thresholdQuantity` INTEGER NOT NULL, " +
                        "`discountType` TEXT NOT NULL, " +
                        "`discountPercentBasisPoints` INTEGER NOT NULL, " +
                        "`discountFixedCents` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`startsAt` INTEGER, " +
                        "`endsAt` INTEGER)"
                )
                db.execSQL(
                    "ALTER TABLE `sale_orders` ADD COLUMN `promoDiscountCents` INTEGER NOT NULL " +
                        "DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `sale_orders` ADD COLUMN `promoName` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * v8 added the `tags` and `product_tags` tables for the optional tagging feature.
         *
         * Purely additive: no existing row is touched, and a user who never turns the feature on
         * ends up with two empty tables and an app that behaves exactly as before. There is
         * deliberately no data migration into them — pre-existing products simply have no tags,
         * which is the correct description of them.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL)"
                )
                // Matches the UNIQUE index Room derives from Tag's `indices` declaration; without
                // it the schema check on open fails with a mismatch rather than a missing column.
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `product_tags` (" +
                        "`productId` INTEGER NOT NULL, " +
                        "`tagId` INTEGER NOT NULL, " +
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
            }
        }

        /**
         * v9 added the supplies inventory: providers, materials, and the purchase entries whose
         * lines increase material stock.
         *
         * Purely additive. Existing products, sales and settings are untouched, and a user who
         * never opens the new section simply gets four empty tables. The foreign keys are
         * `SET NULL` so deleting a provider or material keeps old invoices readable through the
         * names snapshotted on each row.
         */
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
            }
        }

        /**
         * Adds [Product.favourite].
         *
         * A single `ALTER TABLE` with a non-null default, which is the safest shape SQLite offers:
         * every existing product becomes "not a favourite", so an upgrade never has a catalogue
         * that is suddenly all-starred, and no row is rewritten.
         *
         * SQLite stores a boolean as an INTEGER, hence the 0 rather than `false`.
         */
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `products` ADD COLUMN `favourite` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun get(context: Context): StockDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StockDatabase::class.java,
                    "stock.db"
                )
                    // v2 added the sale_orders table and Sale.orderId; v4 added Product.imagePath;
                    // v5 added SaleOrder.cardFeeFixedCents; v6 added SaleOrder.note; v7 added the
                    // promos table and the promo columns on sale_orders; v8 added the tags and
                    // product_tags tables; v9 added providers, materials and purchase entries;
                    // v10 added Product.favourite.
                    // All changes are additive with defaults, so dropping and rebuilding would
                    // lose history; fall back to destructive migration only if an older,
                    // incompatible schema is found.
                    .addMigrations(
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                    )
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build().also { instance = it }
            }
    }
}
