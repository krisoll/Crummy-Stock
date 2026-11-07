package com.example.stockapplication.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * One purchase of supplies from a provider, modelled as an invoice header.
 *
 * The header stores the provider name and totals as they were at the time of purchase. The
 * [providerId] is only a link for filtering and may become null when the provider is deleted;
 * history is never rewritten.
 *
 * @param entryId stable business key, like [SaleOrder.orderId], so restoring the same backup twice
 *   does not duplicate the purchase.
 */
@Entity(
    tableName = "material_entries",
    foreignKeys = [
        ForeignKey(
            entity = Provider::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("providerId"), Index("purchasedAt")]
)
data class MaterialEntry(
    @PrimaryKey val entryId: String,
    val providerId: Long?,
    /** Provider name at purchase time, kept after the provider row is deleted. */
    val providerName: String,
    val purchasedAt: Long,
    /** Optional invoice or folio number. */
    val reference: String = "",
    val note: String = "",
    /** Sum of the line totals, in cents. Stored so history is a fixed fact. */
    val totalCents: Long,
    val lineCount: Int,
)

/**
 * One material in a purchase, with the name and unit snapshotted at the time.
 *
 * Deleting or renaming a material must not rewrite a past invoice, which is why [materialId] is
 * nullable and the display fields live here rather than being joined from `materials`.
 */
@Entity(
    tableName = "material_entry_lines",
    foreignKeys = [
        ForeignKey(
            entity = MaterialEntry::class,
            parentColumns = ["entryId"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Material::class,
            parentColumns = ["id"],
            childColumns = ["materialId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("entryId"),
        Index("materialId"),
        Index(value = ["entryId", "materialId"], unique = true),
    ]
)
data class MaterialEntryLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val entryId: String,
    val materialId: Long?,
    /** Material name at purchase time. */
    val materialName: String,
    val unit: MaterialUnit,
    /** Snapshot of [Material.customUnit], only meaningful when [unit] is [MaterialUnit.OTHER]. */
    val customUnit: String = "",
    val quantity: Int,
    val unitCostCents: Long,
) {
    val totalCents: Long get() = quantity.toLong() * unitCostCents
}

/** A purchase and its lines, used by the detail screen and the report. */
data class MaterialEntryWithLines(
    @Embedded val entry: MaterialEntry,
    @Relation(parentColumn = "entryId", entityColumn = "entryId")
    val lines: List<MaterialEntryLine>,
)

/**
 * What one provider row shows in the providers list.
 *
 * The address and notes are carried along so tapping a row can open the editor without a second
 * lookup: the list already has everything the form needs, and a suspend fetch just to fill two
 * text fields would make the editor open a frame or two late.
 */
data class ProviderSummary(
    val id: Long,
    val name: String,
    val phone: String,
    val entryCount: Int,
    val totalSpentCents: Long,
    val address: String = "",
    val notes: String = "",
) {
    /** Rebuilds the editable entity for the provider form. */
    fun asProvider(): Provider = Provider(
        id = id,
        name = name,
        phone = phone,
        address = address,
        notes = notes,
    )
}

/** One line submitted by the entry editor, before the DAO snapshots the material. */
data class NewMaterialLine(
    val materialId: Long,
    val quantity: Int,
    val unitCostCents: Long,
)

/** A validated purchase submitted by the entry editor. */
data class NewMaterialEntry(
    val providerId: Long,
    val purchasedAt: Long,
    val reference: String = "",
    val note: String = "",
    val lines: List<NewMaterialLine>,
)

/** A validated line plus the material row it will snapshot, used inside the DAO transaction. */
internal data class PreparedMaterialLine(
    val material: Material,
    val quantity: Int,
    val unitCostCents: Long,
)

/** Outcome of recording a material entry and increasing stock. */
sealed interface RecordEntryResult {
    data class Success(val entry: MaterialEntry) : RecordEntryResult
    data object Empty : RecordEntryResult
    data object ProviderNotFound : RecordEntryResult
    data class MaterialNotFound(val materialId: Long) : RecordEntryResult
    data class DuplicateMaterial(val name: String) : RecordEntryResult
    data class InvalidLine(
        val materialName: String,
        val quantity: Int,
        val unitCostCents: Long,
    ) : RecordEntryResult
    data object TotalTooLarge : RecordEntryResult
    data class StockTooLarge(val materialName: String) : RecordEntryResult
}

/** Outcome of deleting a purchase and reversing the stock it added. */
sealed interface DeleteEntryResult {
    data class Success(val entry: MaterialEntry) : DeleteEntryResult
    data object NotFound : DeleteEntryResult
    data class InsufficientStock(
        val materialName: String,
        val available: Int,
        val requested: Int,
    ) : DeleteEntryResult
}
