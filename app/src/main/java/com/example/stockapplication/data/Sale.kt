package com.example.stockapplication.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** An immutable record of a single unit sold, used for the sales history screen. */
@Entity(
    tableName = "sales",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId"), Index("soldAt"), Index("orderId")]
)
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val productId: Long,
    val productName: String,
    val barcode: String,
    val quantity: Int,
    val unitPriceCents: Long,
    val soldAt: Long,
    /** Groups the line items belonging to one checkout. Empty for legacy single-item rows. */
    val orderId: String = "",
) {
    val totalCents: Long get() = unitPriceCents * quantity
}
