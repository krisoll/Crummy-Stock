package com.example.stockapplication

import com.example.stockapplication.data.Product
import com.example.stockapplication.data.filterProducts
import com.example.stockapplication.data.matchesAnyTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tag filter's contract.
 *
 * The interesting decisions, all pinned here: that an empty selection filters nothing (so the
 * feature costs nothing when unused), that several selected tags are **OR**'d rather than AND'ed,
 * and that tags compose with the category chip and the search box the way the other filters do.
 */
class TagFilteringTest {

    private val coffee = Product(id = 1, barcode = "A", name = "Coffee", quantity = 5)
    private val tea = Product(id = 2, barcode = "B", name = "Tea", quantity = 5)
    private val mug = Product(id = 3, barcode = "C", name = "Mug", quantity = 5)
    private val products = listOf(coffee, tea, mug)

    // coffee: fragile + clearance; tea: fragile; mug: no tags at all
    private val assignments = mapOf(
        coffee.id to setOf(10L, 20L),
        tea.id to setOf(10L),
    )

    @Test
    fun `no tag selected shows everything`() {
        val visible = filterProducts(products, null, "", emptySet(), assignments)
        assertEquals(products, visible)
    }

    @Test
    fun `a single tag shows only the products carrying it`() {
        val visible = filterProducts(products, null, "", setOf(10L), assignments)
        assertEquals(setOf(coffee, tea), visible.toSet())
    }

    @Test
    fun `several tags are OR'd, not AND'ed`() {
        // "fragile OR clearance" — the useful question. An AND would return only coffee, and
        // for two unrelated tags would return nothing at all, which reads as a broken filter.
        val visible = filterProducts(products, null, "", setOf(10L, 20L), assignments)
        assertEquals(setOf(coffee, tea), visible.toSet())
    }

    @Test
    fun `a tag nobody carries matches nothing`() {
        val visible = filterProducts(products, null, "", setOf(999L), assignments)
        assertTrue(visible.isEmpty())
    }

    @Test
    fun `untagged products are excluded once any tag is selected`() {
        // The mug has no tags, so it cannot match either id — the alternative (treating "no
        // tags" as a wildcard) would make the filter useless.
        assertFalse(matchesAnyTag(mug, setOf(10L), assignments))
        assertTrue(matchesAnyTag(mug, emptySet(), assignments))
    }

    @Test
    fun `tags combine with the search box using AND`() {
        // Tag says "fragile", search says "tea" — only tea is both.
        val visible = filterProducts(products, null, "tea", setOf(10L), assignments)
        assertEquals(listOf(tea), visible)
    }

    @Test
    fun `tags combine with the category chip using AND`() {
        val categorised = listOf(
            coffee.copy(categoryId = 7),
            tea.copy(categoryId = 7),
            mug.copy(categoryId = 8),
        )
        // Category 8 (mugs) AND tag 10 (fragile): the mug is not fragile, so nothing matches.
        val empty = filterProducts(categorised, 8, "", setOf(10L), assignments)
        assertTrue(empty.isEmpty())

        // Category 7 AND tag 20 (clearance): only coffee is both.
        val coffeeOnly = filterProducts(categorised, 7, "", setOf(20L), assignments)
        assertEquals(listOf(categorised[0]), coffeeOnly)
        assertEquals(coffee.id, coffeeOnly.single().id)
    }

    @Test
    fun `omitting the tag arguments matches the old two-argument behaviour`() {
        // Guards the default parameters: existing call sites that know nothing about tags must
        // keep filtering exactly as they did.
        val withoutTags = filterProducts(products, null, "cof")
        val withExplicitEmpty = filterProducts(products, null, "cof", emptySet(), emptyMap())
        assertEquals(withoutTags, withExplicitEmpty)
        assertEquals(listOf(coffee), withoutTags)
    }
}
