package com.example.stockapplication.ui.screens

import android.net.Uri

/**
 * Everything the add/edit product form collects, in one value.
 *
 * Introduced so the screen's save callbacks stay readable as the form grew (photo, category, ...)
 * instead of turning into a six-positional-argument lambda that is easy to get wrong at the call
 * site in `StockApp`.
 *
 * @param imagePath file name of a photo that is already stored, kept by the form.
 * @param pickedImage a freshly picked image that has **not** been copied yet. The ViewModel does
 *                    that copy, because only it can outlive a configuration change mid-save.
 */
data class ProductDraft(
    val barcode: String,
    val name: String,
    val priceCents: Long,
    val quantity: Int,
    val categoryId: Long?,
    val imagePath: String?,
    val pickedImage: Uri? = null,
    /** True when the user removed the existing photo, so the saved product should have none. */
    val imageCleared: Boolean = false,
    /**
     * The product's tag ids, as selected in the form.
     *
     * Empty whenever tagging is switched off, which is also what a product with no tags looks
     * like — the save path only writes tags when the feature is on, so an empty set chosen on a
     * device that does not show tags can never wipe the ones a backup restored.
     */
    val tagIds: Set<Long> = emptySet(),
) {
    /** The photo value to persist, before any newly picked image has been copied in. */
    val resolvedImagePath: String? get() = if (imageCleared) null else imagePath
}
