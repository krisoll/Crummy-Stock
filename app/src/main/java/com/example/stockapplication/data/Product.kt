package com.example.stockapplication.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A sellable product tracked by the app. [barcode] is the value encoded in the product's
 * QR / barcode label and is used as the lookup key when scanning.
 */
@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"], unique = true)]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val barcode: String,
    val name: String,
    val priceCents: Long = 0L,
    val quantity: Int = 0,
    /** Id of the owning [Category], or null when the product is uncategorised. */
    val categoryId: Long? = null,
    /**
     * File **name** (not a full path) of the product photo inside the app's private
     * `files/product_images` folder — see [ProductImages]. Null when the product has no photo.
     *
     * Only the name is stored so the row survives the app's data directory moving between
     * installs/restores; the folder is resolved at load time.
     */
    val imagePath: String? = null,
    /**
     * Whether the shopkeeper starred this product.
     *
     * A favourite is surfaced at the top of the sales picker, where the time saved by not hunting
     * through an alphabetical list is the whole point. It is a **per-product** flag rather than a
     * tag because it is a single yes/no with no grouping semantics, and it has to be readable
     * without a join on every list.
     *
     * Defaults to `false` so every existing construction site keeps compiling, and so the schema
     * migration can add the column with a safe default (see `MIGRATION_9_10`).
     */
    val favourite: Boolean = false,
) {
    val isOutOfStock: Boolean get() = quantity <= 0

    val isLowStock: Boolean get() = quantity in 1..LOW_STOCK_THRESHOLD

    companion object {
        const val LOW_STOCK_THRESHOLD = 5
    }
}
