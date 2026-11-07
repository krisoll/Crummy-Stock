package com.example.stockapplication.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Someone the shop buys supplies from.
 *
 * A provider is a small address book entry, not an accounting relationship: v1 records what each
 * material entry cost but deliberately does not track money still owed. That keeps deleting a
 * provider safe — its purchase history stays behind with the name snapshotted onto each entry.
 *
 * The name is unique, case-insensitively, the same way categories and tags are. Two rows called
 * "Paper Co." would be indistinguishable in the picker and would split the same purchase history
 * across two ids.
 */
@Entity(
    tableName = "providers",
    indices = [Index(value = ["name"], unique = true)]
)
data class Provider(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
)
