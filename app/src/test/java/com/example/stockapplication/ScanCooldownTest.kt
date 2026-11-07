package com.example.stockapplication

import com.example.stockapplication.data.DefaultProductChangeDelayMillis
import com.example.stockapplication.data.DefaultScanDelayMillis
import com.example.stockapplication.data.MinProductChangeDelayMillis
import com.example.stockapplication.data.MinScanDelayMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scanner's two cooldowns, extracted so they can be pinned down without Compose or a camera.
 *
 * ## Why there are two
 *
 * Seeing the *same* code again is the camera re-reading a label still in frame — a misfire, which
 * the long delay suppresses hard. Seeing a *different* code is the cashier moving on, which only
 * needs to be slower than the camera's own repeat rate. A single number cannot serve both: short
 * enough for a basket means a stationary label floods the cart, long enough to stop that means
 * every item takes seconds.
 *
 * ## What this replaced
 *
 * The rule was briefly a flat cooldown on every scan, and before that `code == lastCode && elapsed
 * < delay` — which meant a different product was accepted with no wait at all.
 */
class ScanCooldownTest {

    /** Mirrors the check in `ScannerDialog`. */
    private fun accepts(now: Long, lastScanAt: Long, delay: Long): Boolean =
        (now - lastScanAt) >= delay

    /** The delay that applies for [code], given what was accepted last. */
    private fun delayFor(code: String, lastCode: String?, repeatDelay: Int, changeDelay: Int): Long =
        if (code == lastCode) {
            repeatDelay.toLong().coerceAtLeast(MinScanDelayMillis.toLong())
        } else {
            changeDelay.toLong().coerceAtLeast(MinProductChangeDelayMillis.toLong())
        }

    @Test
    fun theSameCodeWaitsTheLongDelay() {
        val delay = delayFor("A", lastCode = "A", repeatDelay = 3_000, changeDelay = 1_000)
        assertEquals(3_000L, delay)

        assertFalse(accepts(now = 1_000, lastScanAt = 0, delay = delay))
        assertFalse(accepts(now = 2_500, lastScanAt = 0, delay = delay))
        assertTrue(accepts(now = 3_000, lastScanAt = 0, delay = delay))
    }

    @Test
    fun aDifferentCodeWaitsTheShortDelay() {
        val delay = delayFor("B", lastCode = "A", repeatDelay = 3_000, changeDelay = 1_000)
        assertEquals(1_000L, delay)

        // Still inside the *long* delay, but the short one already allows it — which is the whole
        // point of splitting them.
        assertFalse(accepts(now = 500, lastScanAt = 0, delay = delay))
        assertTrue(accepts(now = 1_000, lastScanAt = 0, delay = delay))
    }

    @Test
    fun theFirstScanOfASessionIsAccepted() {
        // `lastScanAt` starts at 0 and `lastCode` at null, so the first code takes the short path
        // and passes as soon as the clock is past the delay.
        val delay = delayFor("A", lastCode = null, repeatDelay = 3_000, changeDelay = 1_000)
        assertTrue(accepts(now = System.currentTimeMillis(), lastScanAt = 0, delay = delay))
    }

    @Test
    fun neitherDelayCanCollapseToZero() {
        // The floors are what keep a camera that re-reads continuously from flooding the cart, even
        // if a stored value is nonsense.
        assertEquals(
            MinScanDelayMillis.toLong(),
            delayFor("A", lastCode = "A", repeatDelay = 0, changeDelay = 0),
        )
        assertEquals(
            MinProductChangeDelayMillis.toLong(),
            delayFor("B", lastCode = "A", repeatDelay = 0, changeDelay = 0),
        )
    }

    @Test
    fun theDefaultsAreThreeSecondsAndOneSecond() {
        assertEquals(3_000, DefaultScanDelayMillis)
        assertEquals(1_000, DefaultProductChangeDelayMillis)
        // The short one must stay comfortably below the long one, or the distinction is pointless.
        assertTrue(DefaultProductChangeDelayMillis < DefaultScanDelayMillis)
    }
}
