package com.example.stockapplication

import com.example.stockapplication.data.LoadingSpin
import com.example.stockapplication.ui.components.SlidePauseTravelExtent
import com.example.stockapplication.ui.components.SlidePauseTravelFraction
import com.example.stockapplication.ui.components.easeInOut
import com.example.stockapplication.ui.components.slidePauseOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks down the geometry of the pause-at-centre slide.
 *
 * This is three-segment piecewise arithmetic, which is exactly the shape of code that looks
 * obviously correct and is off by one segment. The tests are written against the *behaviour* the
 * animation is meant to have — enters from the left, stops dead in the middle, leaves to the
 * right — rather than against the implementation's own constants.
 */
class SlidePauseTest {

    @Test
    fun startsOffScreenToTheLeft() {
        assertEquals(-SlidePauseTravelExtent, slidePauseOffset(0f), 0.0001f)
    }

    @Test
    fun endsOffScreenToTheRight() {
        assertEquals(SlidePauseTravelExtent, slidePauseOffset(1f), 0.0001f)
    }

    @Test
    fun isDeadCentreHalfwayThroughTheCycle() {
        // The hold is symmetric about 0.5, so this is the one point guaranteed to be mid-pause.
        assertEquals(0f, slidePauseOffset(0.5f), 0.0001f)
    }

    @Test
    fun theWholeHoldIsPinnedAtCentre() {
        // The entire middle stretch must be exactly 0, not drifting. A hold that creeps sideways
        // reads as a stutter rather than a pause.
        val halfTravel = SlidePauseTravelFraction / 2f
        val holdStart = halfTravel
        val holdEnd = halfTravel + (1f - SlidePauseTravelFraction)

        var progress = holdStart
        while (progress < holdEnd) {
            assertEquals(
                "expected a dead-centre hold at progress=$progress",
                0f,
                slidePauseOffset(progress),
                0.0001f,
            )
            progress += 0.005f
        }
    }

    @Test
    fun entersFromTheLeftAndLeavesToTheRight() {
        // Mid-entry the image is still left of centre; mid-exit it has passed to the right.
        assertTrue("entry sample should be left of centre", slidePauseOffset(0.1f) < 0f)
        assertTrue("exit sample should be right of centre", slidePauseOffset(0.9f) > 0f)
    }

    @Test
    fun theHoldOccupiesTheRightShareOfTheCycle() {
        // Guards the most likely real regression: tuning the travel fraction but leaving the
        // hold boundaries computed from a stale value.
        val halfTravel = SlidePauseTravelFraction / 2f
        val hold = 1f - SlidePauseTravelFraction

        // Just inside the hold, on both sides.
        assertEquals(0f, slidePauseOffset(halfTravel + 0.001f), 0.0001f)
        assertEquals(0f, slidePauseOffset(halfTravel + hold - 0.001f), 0.0001f)

        // Just outside it, on both sides, the image has already begun to move.
        assertTrue(slidePauseOffset(halfTravel - 0.001f) < 0f)
        assertTrue(slidePauseOffset(halfTravel + hold + 0.001f) > 0f)
    }

    @Test
    fun travelIsMonotonicBeforeTheHoldAndAfterIt() {
        // Walking the entry, each sample must be further right than the last. A dip here would
        // mean the image visibly reverses direction mid-flight.
        var previous = slidePauseOffset(0f)
        var progress = 0.005f
        val halfTravel = SlidePauseTravelFraction / 2f
        while (progress < halfTravel) {
            val current = slidePauseOffset(progress)
            assertTrue(
                "entry reversed at progress=$progress ($previous -> $current)",
                current >= previous,
            )
            previous = current
            progress += 0.005f
        }

        // And the same for the exit, sampled from just past the hold.
        var exitProgress = halfTravel + (1f - SlidePauseTravelFraction) + 0.001f
        previous = slidePauseOffset(exitProgress)
        exitProgress += 0.005f
        while (exitProgress <= 1f) {
            val current = slidePauseOffset(exitProgress)
            assertTrue(
                "exit reversed at progress=$exitProgress ($previous -> $current)",
                current >= previous,
            )
            previous = current
            exitProgress += 0.005f
        }
    }

    @Test
    fun easingIsPinnedAtBothEnds() {
        // The segment boundaries depend on this: if easeInOut(0) or easeInOut(1) drifted, the
        // image would jump slightly the moment the hold starts or ends.
        assertEquals(0f, easeInOut(0f), 0.0001f)
        assertEquals(1f, easeInOut(1f), 0.0001f)
    }

    @Test
    fun easingStartsAndEndsAtRest() {
        // Zero velocity at the ends is the whole point: the image must settle into the hold rather
        // than arriving at full speed and stopping dead.
        // Sampled just inside each end; a linear curve would move ~0.01 in that distance.
        val settleIn = (1f - easeInOut(0.99f)).toDouble()
        val settleOut = easeInOut(0.01f).toDouble()
        assertTrue("settle-in was $settleIn, too abrupt", settleIn < 0.002)
        assertTrue("settle-out was $settleOut, too abrupt", settleOut < 0.002)
    }

    @Test
    fun easingTrailsLinearBeforeTheMidpointAndLeadsAfterIt() {
        // Smoothstep is symmetric about (0.5, 0.5), so the midpoint is exactly on the linear line —
        // it is the inflection, not a lead. The tell is either side of it: slow at first, then
        // catching up and overshooting linear, before settling again.
        assertEquals(0.5f, easeInOut(0.5f), 0.0001f)
        assertTrue("ease(0.25) should trail linear", easeInOut(0.25f) < 0.25f)
        assertTrue("ease(0.75) should lead linear", easeInOut(0.75f) > 0.75f)
    }

    @Test
    fun easingIsFasterMidSegmentThanAtTheEdges() {
        // The measurable meaning of "eases in and out": speed peaks in the middle and falls to
        // zero at both ends, rather than being constant as it was before.
        fun speedAt(t: Float) = easeInOut(t + 0.001f) - easeInOut(t)

        val edgeSpeed = speedAt(0.02f).toDouble()
        val midSpeed = speedAt(0.5f).toDouble()
        assertTrue(
            "mid speed $midSpeed should exceed edge speed $edgeSpeed",
            midSpeed > edgeSpeed * 2.0,
        )
    }

    @Test
    fun easingIsMonotonicAndStaysInRange() {
        // A dip would mean the image briefly reverses; leaving 0..1 would overshoot the screen.
        var previous = easeInOut(0f)
        var t = 0f
        while (t <= 1f) {
            val current = easeInOut(t)
            assertTrue("eased value reversed at t=$t", current >= previous)
            assertTrue("eased value $current out of range at t=$t", current in 0f..1f)
            previous = current
            t += 0.01f
        }
    }

    @Test
    fun easingClampsOutOfRangeInput() {
        // Defensive: progress is derived from a float animation and can land a hair outside 0..1.
        assertEquals(0f, easeInOut(-0.5f), 0.0001f)
        assertEquals(1f, easeInOut(1.5f), 0.0001f)
    }

    @Test
    fun easingDoesNotDisturbThePinnedHold() {
        // The whole reason easing is applied per-segment rather than to the cycle as a whole.
        val halfTravel = SlidePauseTravelFraction / 2f
        val hold = 1f - SlidePauseTravelFraction
        assertEquals(0f, slidePauseOffset(halfTravel), 0.0001f)
        assertEquals(0f, slidePauseOffset(halfTravel + hold / 2f), 0.0001f)
    }

    @Test
    fun travelClearsTheScreenOnATypicalPhone() {
        // Resolves the travel units into real pixels exactly the way `SpinningImage` does, for a
        // 1080px screen and a 96dp image at 3x (288px). This is the arithmetic that decides
        // whether the image actually reaches the edge or merely shuffles near the centre.
        //
        // The image's rest position is the *centre* of the container (540px), so all edges are
        // measured from there — getting that origin wrong is what made an earlier version of this
        // check pass while the animation visibly stopped short.
        val containerWidthPx = 1080f
        val imageWidthPx = 288f
        val restCentrePx = containerWidthPx / 2f
        val scale = (containerWidthPx + imageWidthPx) / 2f

        val startCentrePx = restCentrePx + slidePauseOffset(0f) * scale
        val endCentrePx = restCentrePx + slidePauseOffset(1f) * scale

        // Fully off screen to the left: the image's right edge is left of x=0.
        assertTrue(
            "start centre ${startCentrePx}px does not clear the left edge",
            startCentrePx + imageWidthPx / 2f <= 0f,
        )
        // Fully off screen to the right: the image's left edge is past the container.
        assertTrue(
            "end centre ${endCentrePx}px does not clear the right edge",
            endCentrePx - imageWidthPx / 2f >= containerWidthPx,
        )
    }

    @Test
    fun bothSlidesAreMarkedAsTravelling() {
        // Regression guard. `SpinningImage` picks its container from this flag; if a slide is not
        // marked, it still animates but only across a 168dp disc instead of the screen, which
        // looks like "the slide is too small" rather than like a missing case.
        assertTrue(LoadingSpin.SLIDE.travelsAcrossScreen)
        assertTrue(LoadingSpin.SLIDE_PAUSE.travelsAcrossScreen)
    }

    @Test
    fun noOtherMotionIsMarkedAsTravelling() {
        // The disc-framed motions must keep the fixed circle container; letting one travel would
        // break its framing.
        LoadingSpin.entries
            .filter { it != LoadingSpin.SLIDE && it != LoadingSpin.SLIDE_PAUSE }
            .forEach { spin ->
                assertTrue(
                    "\"${spin.id}\" should not travel across the screen",
                    !spin.travelsAcrossScreen,
                )
            }
    }

    @Test
    fun neverOvershootsTheScreenBounds() {
        // Outside [-extent, +extent] the image would fail to clear the edge, leaving a sliver
        // visible at the moment the cycle restarts.
        var progress = 0f
        while (progress <= 1f) {
            val offset = slidePauseOffset(progress)
            assertTrue(
                "offset $offset out of bounds at progress=$progress",
                offset >= -SlidePauseTravelExtent - 0.0001f &&
                    offset <= SlidePauseTravelExtent + 0.0001f,
            )
            progress += 0.005f
        }
    }
}
