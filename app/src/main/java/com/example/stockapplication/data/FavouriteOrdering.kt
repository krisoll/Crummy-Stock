package com.example.stockapplication.data

/**
 * Puts favourites first, keeping everything alphabetical within each group.
 *
 * ## Why this is a sort and not a filter
 *
 * The picker already has category chips and a search box; adding a third way to *exclude* products
 * would make it possible to have a favourite that is invisible with no indication why. Pinning
 * instead means the list always contains everything the filters allow — the favourites are simply
 * at the top, where a cashier reaches them without scrolling.
 *
 * ## Why the sort is stable and total
 *
 * `sortedWith` on a comparison that ignores nothing: favourites before non-favourites, then name.
 * A comparison that returned "equal" for two different products would let the database's row order
 * leak through, so the same list could come back in a different order between queries — which
 * reads as flicker when the list recomposes.
 *
 * Kept here rather than inline in the picker so it can be unit-tested without Compose, matching how
 * [ProductFiltering] is organised.
 */
fun favouritesFirst(products: List<Product>): List<Product> = products.sortedWith(
    compareByDescending<Product> { it.favourite }
        // Same collation the DAO orders by, so the two agree on "alphabetical".
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
)
