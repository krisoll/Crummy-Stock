package com.example.stockapplication.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-defined label that can be attached to any number of products.
 *
 * ## How a tag differs from a [Category]
 *
 * A category answers *"what is this?"* and a product has exactly one — it is the shelf the product
 * lives on. A tag answers *"what is this for right now?"* — `fragile`, `clearance`, `to-restock` —
 * and a product can carry any number of them, or none.
 *
 * That difference is why the relationship is a join table ([ProductTag]) rather than a column on
 * [Product]: one product, many tags, and one tag reused across many products. A column would force
 * the user to pick a single label, which is not what a tag is.
 *
 * Tags are behind the `tagsEnabled` setting and hidden by default; the rows exist and persist
 * regardless, so switching the feature off is a view change rather than a delete.
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
)

/**
 * One product-to-tag assignment.
 *
 * Composite primary key rather than an autoincrement id: the pair *is* the identity, so there is no
 * way to attach the same tag to the same product twice — a duplicate would be a no-op row the UI
 * could never show and no query could ever want.
 *
 * Both foreign keys cascade. Deleting a tag must not leave assignments pointing at nothing, and
 * deleting a product must not leave orphaned assignments either; doing it in the database rather
 * than in the DAO means no code path can forget.
 */
@Entity(
    tableName = "product_tags",
    primaryKeys = ["productId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // Both directions are queried: "tags of this product" for the editor, and "products with this
    // tag" for the inventory filter. The primary key only indexes productId first.
    indices = [Index("tagId")]
)
data class ProductTag(
    val productId: Long,
    val tagId: Long,
)
