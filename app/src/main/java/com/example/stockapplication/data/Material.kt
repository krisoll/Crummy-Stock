package com.example.stockapplication.data

import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.stockapplication.R

/**
 * The unit a material is counted in.
 *
 * Stored by enum constant, never by its translated label: a backup taken in Spanish must still
 * read back as "kg" on an English phone. [OTHER] carries its own label in
 * [Material.customUnit], validated by the editor and the repository.
 */
enum class MaterialUnit(@param:StringRes val labelRes: Int) {
    PIECE(R.string.unit_piece),
    PACK(R.string.unit_pack),
    BOX(R.string.unit_box),
    BAG(R.string.unit_bag),
    DOZEN(R.string.unit_dozen),
    KG(R.string.unit_kg),
    GRAM(R.string.unit_gram),
    LITRE(R.string.unit_litre),
    MILLILITRE(R.string.unit_millilitre),
    METRE(R.string.unit_metre),
    OTHER(R.string.unit_other),
    ;

    companion object {
        /**
         * Resolves a value from a backup.
         *
         * A unit this build does not know falls back to [OTHER] rather than dropping the material:
         * the raw value is then preserved as the custom label, so a file from a newer build keeps
         * its meaning instead of silently becoming "piece".
         */
        fun fromId(id: String?): MaterialUnit =
            entries.firstOrNull { it.name.equals(id?.trim(), ignoreCase = true) } ?: OTHER
    }
}

/**
 * A non-sellable supply the shop buys and consumes — bags, labels, ingredients.
 *
 * Materials are deliberately separate from [Product]: they are not sold at the till, have no
 * barcode or price, and their stock is increased by material entries rather than being replayed
 * from them. [stock] is authoritative; the entry history explains how it got there.
 */
@Entity(
    tableName = "materials",
    indices = [Index(value = ["name"], unique = true)]
)
data class Material(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val unit: MaterialUnit = MaterialUnit.PIECE,
    /** Only meaningful when [unit] is [MaterialUnit.OTHER]; ignored otherwise. */
    val customUnit: String = "",
    /** Current quantity on hand. Always >= 0. */
    val stock: Int = 0,
    /** At or below this quantity the material is shown as low stock. */
    val lowStockThreshold: Int = 5,
    val notes: String = "",
) {
    val isOutOfStock: Boolean get() = stock <= 0

    /** Out of stock is reported separately, so this only covers the positive-but-low band. */
    val isLowStock: Boolean get() = stock in 1..lowStockThreshold
}

/** A material with its derived last paid unit cost. */
data class MaterialRow(
    @androidx.room.Embedded val material: Material,
    /**
     * Unit cost from the most recent entry that included this material, or null before the first
     * purchase. Derived rather than stored so backdating or deleting an entry cannot leave a stale
     * "last cost" behind.
     */
    val lastUnitCostCents: Long?,
)
