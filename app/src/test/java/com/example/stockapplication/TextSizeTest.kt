package com.example.stockapplication

import com.example.stockapplication.data.DefaultTextSizeId
import com.example.stockapplication.data.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The text-size setting's contract.
 *
 * The important guarantee is the first test: [TextSize.NORMAL] is the default, so it has to be a
 * multiplier of exactly 1. A "almost 1" normal would silently restyle every screen of every
 * existing install the moment this setting shipped.
 */
class TextSizeTest {

    @Test
    fun `the default size is exactly unscaled`() {
        assertEquals(1f, TextSize.NORMAL.scale, 0f)
        assertEquals(TextSize.NORMAL, TextSize.fromId(DefaultTextSizeId))
    }

    @Test
    fun `small and large actually differ from normal in the expected direction`() {
        assertTrue(TextSize.SMALL.scale < TextSize.NORMAL.scale)
        assertTrue(TextSize.LARGE.scale > TextSize.NORMAL.scale)
    }

    @Test
    fun `an unknown or missing id falls back to normal`() {
        // Covers a fresh install (null), a restored backup from a future version, and a renamed
        // id — all of which must land on the default rather than on whichever entry is first.
        assertEquals(TextSize.NORMAL, TextSize.fromId(null))
        assertEquals(TextSize.NORMAL, TextSize.fromId(""))
        assertEquals(TextSize.NORMAL, TextSize.fromId("gigantic"))
    }

    @Test
    fun `every size has a distinct id and round-trips through it`() {
        val ids = TextSize.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        TextSize.entries.forEach { size ->
            assertEquals(size, TextSize.fromId(size.id))
        }
    }
}
