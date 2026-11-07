package com.example.stockapplication.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-defined grouping of products, used to browse the inventory picker in the Sales tab.
 *
 * Products reference a category by id rather than by name so renaming one never orphans its
 * products. Deleting a category nulls the references instead of removing the products — see
 * `StockDao.deleteCategory`.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
)
