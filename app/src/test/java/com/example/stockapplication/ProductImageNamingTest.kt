package com.example.stockapplication

import com.example.stockapplication.data.ProductImages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure parts of the product-photo pipeline. Nothing here needs a real Android
 * `Context`, so these run as plain JVM tests.
 */
class ProductImageNamingTest {

    @Test
    fun generatedNamesAreUniqueEvenWithinTheSameMillisecond() {
        val now = 1_700_000_000_000L
        val first = ProductImages.generateFileName(now = now)
        val second = ProductImages.generateFileName(now = now)

        assertNotEquals(first, second)
    }

    @Test
    fun generatedNamesUseOnlyStorageSafeCharacters() {
        val name = ProductImages.generateFileName(sourceId = 42L, now = 1_700_000_000_000L)

        // File names must not contain path separators or characters Android's filesystem rejects.
        val illegal = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', ' ')
        illegal.forEach { assertTrue("contains $it", name.indexOf(it) < 0) }
        assertTrue(name.endsWith(".jpg"))
    }

    @Test
    fun generatedNamesAreShortEnoughForAnyFilesystem() {
        val name = ProductImages.generateFileName(sourceId = Long.MAX_VALUE, now = Long.MAX_VALUE)

        assertTrue("name was ${name.length} chars", name.length <= 64)
    }

    @Test
    fun sampleSizeKeepsTheLongEdgeAtOrAboveTheTarget() {
        // Sample sizes are powers of two. 4000/2 = 2000 >= 1024 but 4000/4 = 1000 < 1024,
        // so decoding happens at 1/2 scale.
        assertEquals(2, ProductImages.sampleSizeFor(4000, 3000, maxEdge = 1024))
        // 8000/4 = 2000 >= 1024 and 8000/8 = 1000 < 1024, so quarter scale is the largest safe one.
        assertEquals(4, ProductImages.sampleSizeFor(8000, 6000, maxEdge = 1024))
        // Already small enough: no subsampling at all.
        assertEquals(1, ProductImages.sampleSizeFor(800, 600, maxEdge = 1024))
        // Portrait orientation must be judged on the long edge too.
        assertEquals(4, ProductImages.sampleSizeFor(600, 8000, maxEdge = 1024))
    }

    @Test
    fun sampleSizeNeverReturnsZeroOrNegative() {
        assertEquals(1, ProductImages.sampleSizeFor(0, 0))
        assertEquals(1, ProductImages.sampleSizeFor(-5, -5))
    }
}
