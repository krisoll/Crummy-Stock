package com.example.stockapplication

import com.example.stockapplication.data.Product
import com.example.stockapplication.data.favouritesFirst
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins down the picker's ordering rule.
 *
 * The load-bearing property is that this is a **total** order: every product compares unequal to
 * every other one (names are unique in practice, and the tie-break falls through to name). A
 * comparison that could return "equal" would let Room's row order leak through, and the same list
 * would arrive in a different order between queries — which reads as flicker when Compose
 * recomposes.
 */
class FavouriteOrderingTest {

    private fun product(id: Long, name: String, favourite: Boolean = false) = Product(
        id = id,
        barcode = "B$id",
        name = name,
        favourite = favourite,
    )

    @Test
    fun favouritesComeFirst() {
        val ordered = favouritesFirst(
            listOf(
                product(1, "Apple"),
                product(2, "Banana", favourite = true),
                product(3, "Cherry"),
                product(4, "Date", favourite = true),
            )
        )

        assertEquals(listOf("Banana", "Date", "Apple", "Cherry"), ordered.map { it.name })
    }

    @Test
    fun eachGroupStaysAlphabeticalRegardlessOfInputOrder() {
        val ordered = favouritesFirst(
            listOf(
                product(1, "Zucchini", favourite = true),
                product(2, "Apricot", favourite = true),
                product(3, "Yam"),
                product(4, "Avocado"),
            )
        )

        assertEquals(listOf("Apricot", "Zucchini", "Avocado", "Yam"), ordered.map { it.name })
    }

    @Test
    fun orderingIsCaseInsensitiveLikeTheDatabase() {
        // The DAO orders by `name COLLATE NOCASE`, so a case-sensitive sort here would make the
        // favourites block disagree with the rest of the list.
        val ordered = favouritesFirst(
            listOf(product(1, "banana"), product(2, "Apple"), product(3, "cherry"))
        )

        assertEquals(listOf("Apple", "banana", "cherry"), ordered.map { it.name })
    }

    @Test
    fun noFavouritesLeavesTheListAlphabetical() {
        val ordered = favouritesFirst(
            listOf(product(1, "C"), product(2, "A"), product(3, "B"))
        )

        assertEquals(listOf("A", "B", "C"), ordered.map { it.name })
    }

    @Test
    fun allFavouritesStillSortsAlphabetically() {
        val ordered = favouritesFirst(
            listOf(
                product(1, "B", favourite = true),
                product(2, "A", favourite = true),
            )
        )

        assertEquals(listOf("A", "B"), ordered.map { it.name })
    }

    @Test
    fun anEmptyListIsFine() {
        assertEquals(emptyList<Product>(), favouritesFirst(emptyList()))
    }

    @Test
    fun nothingIsDroppedOrDuplicated() {
        val input = listOf(
            product(1, "A", favourite = true),
            product(2, "B"),
            product(3, "C", favourite = true),
        )

        val ordered = favouritesFirst(input)

        assertEquals(input.size, ordered.size)
        assertEquals(input.map { it.id }.toSet(), ordered.map { it.id }.toSet())
    }
}
