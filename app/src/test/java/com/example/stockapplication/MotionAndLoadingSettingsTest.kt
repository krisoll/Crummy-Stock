package com.example.stockapplication

import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.DefaultLoadingCircleId
import com.example.stockapplication.data.DefaultLoadingCircleSizeDp
import com.example.stockapplication.data.DefaultLoadingImageSizeDp
import com.example.stockapplication.data.MaxLoadingCircleSizeDp
import com.example.stockapplication.data.MaxLoadingImageSizeDp
import com.example.stockapplication.data.MinLoadingCircleSizeDp
import com.example.stockapplication.data.MinLoadingImageSizeDp
import com.example.stockapplication.data.DefaultLoadingIconId
import com.example.stockapplication.data.DefaultLoadingSpinId
import com.example.stockapplication.data.LoadingCircle
import com.example.stockapplication.data.LoadingImage
import com.example.stockapplication.data.DefaultMotionSpeedId
import com.example.stockapplication.data.LoadingIcon
import com.example.stockapplication.data.LoadingSpin
import com.example.stockapplication.data.MotionSpeed
import com.example.stockapplication.ui.components.SlidePauseTravelFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the contract of the animation-speed and startup-screen settings.
 *
 * These are pure lookups and arithmetic, so they are tested without Compose: what matters is that
 * stored ids round-trip, that unknown values degrade to the documented defaults, and that the
 * duration scaling behaves at both extremes.
 */
class MotionAndLoadingSettingsTest {

    @Test
    fun speedDefaultsToNormal() {
        assertEquals(DefaultMotionSpeedId, AppSettings().motionSpeedId)
        assertEquals(MotionSpeed.NORMAL, AppSettings().motionSpeed)
        assertEquals(MotionSpeed.NORMAL, MotionSpeed.fromId(null))
        assertEquals(MotionSpeed.NORMAL, MotionSpeed.fromId("nonsense"))
    }

    @Test
    fun everySpeedIdRoundTrips() {
        MotionSpeed.entries.forEach { speed ->
            assertEquals(speed, MotionSpeed.fromId(speed.id))
        }
    }

    @Test
    fun offCollapsesEveryDurationToZero() {
        // Motion must be *disabled*, not merely fast. Any non-zero duration would still be visible
        // and would also mean the AnimatedContent wrappers stay mid-transition.
        listOf(0, 45, 180, 460, 5000).forEach { base ->
            assertEquals(0, MotionSpeed.OFF.scale(base))
        }
    }

    @Test
    fun normalLeavesDurationsUntouched() {
        listOf(0, 45, 180, 460).forEach { base ->
            assertEquals(base, MotionSpeed.NORMAL.scale(base))
        }
    }

    @Test
    fun fastIsQuickerAndSlowIsSlowerThanNormal() {
        val base = 300
        assertTrue(MotionSpeed.FAST.scale(base) < MotionSpeed.NORMAL.scale(base))
        assertTrue(MotionSpeed.SLOW.scale(base) > MotionSpeed.NORMAL.scale(base))
        // Both stay positive so the transitions still happen.
        assertTrue(MotionSpeed.FAST.scale(base) > 0)
        assertTrue(MotionSpeed.SLOW.scale(base) > 0)
    }

    @Test
    fun staggerDelayCollapsesAlongWithTheSpeeds() {
        assertEquals(0, MotionSpeed.OFF.scale(45))
        assertNotEquals(0, MotionSpeed.NORMAL.scale(45))
    }

    @Test
    fun loadingIconDefaultsToCart() {
        val settings = AppSettings()
        assertEquals(DefaultLoadingIconId, settings.loadingIconId)
        assertEquals(LoadingIcon.CART, settings.loadingIcon)
        assertEquals(LoadingIcon.CART, LoadingIcon.fromId(null))
        assertEquals(LoadingIcon.CART, LoadingIcon.fromId("no-such-icon"))
    }

    @Test
    fun everyLoadingIconIdRoundTripsAndHasDistinctIcons() {
        LoadingIcon.entries.forEach { icon ->
            assertEquals(icon, LoadingIcon.fromId(icon.id))
        }
        // Two entries sharing an icon would make the picker look broken.
        val distinct = LoadingIcon.entries.map { it.icon }.distinct()
        assertEquals(LoadingIcon.entries.size, distinct.size)
    }

    @Test
    fun loadingSpinDefaultsToClockwise() {
        val settings = AppSettings()
        assertEquals(DefaultLoadingSpinId, settings.loadingSpinId)
        assertEquals(LoadingSpin.CLOCKWISE, settings.loadingSpin)
        assertEquals(LoadingSpin.CLOCKWISE, LoadingSpin.fromId(null))
        assertEquals(LoadingSpin.CLOCKWISE, LoadingSpin.fromId(""))
    }

    @Test
    fun everyLoadingSpinIdRoundTrips() {
        LoadingSpin.entries.forEach { spin ->
            assertEquals(spin, LoadingSpin.fromId(spin.id))
        }
    }

    @Test
    fun everyLoadingSpinIdIsDistinct() {
        // A duplicated id would make two radio options unselectable interchangeably.
        val ids = LoadingSpin.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun everyMotionHasAUsableCycleLength() {
        // A zero or negative period would divide by zero inside the repeatable spec.
        LoadingSpin.entries.forEach { spin ->
            assertTrue("\"${spin.id}\" has a bad period", spin.periodMillis > 0)
        }
    }

    @Test
    fun travellingMotionsGetTheirOwnPace() {
        // These deviate from the neutral 1400ms on purpose; if one silently fell back to the
        // default it would read as sluggish rather than broken, which is easy to miss.
        assertEquals(1100, LoadingSpin.PULSE.periodMillis)
        assertEquals(900, LoadingSpin.BOUNCE.periodMillis)
        assertEquals(2000, LoadingSpin.SLIDE.periodMillis)
        assertEquals(1400, LoadingSpin.CLOCKWISE.periodMillis)
        assertEquals(1400, LoadingSpin.NONE.periodMillis)
    }

    @Test
    fun thePausingSlideGetsLongerThanThePlainOne() {
        // It spends a chunk of its cycle standing still, so it needs proportionally more wall
        // clock to cover the same distance at the same apparent speed.
        assertTrue(LoadingSpin.SLIDE_PAUSE.periodMillis > LoadingSpin.SLIDE.periodMillis)
    }

    @Test
    fun theHoldReadsAsABeatNotAStall() {
        // Bounded on both sides. Below ~250ms it registers as a dropped frame; above ~900ms it
        // looks like the animation has hung. The brief is explicitly a "fraction of a second",
        // so both bounds matter and an oversized hold is as wrong as a tiny one.
        val cycle = LoadingSpin.SLIDE_PAUSE.periodMillis
        val holdMillis = cycle * (1f - SlidePauseTravelFraction)
        assertTrue(
            "hold was ${holdMillis}ms, too short to read as deliberate",
            holdMillis >= 250.0,
        )
        assertTrue(
            "hold was ${holdMillis}ms, too long to read as a beat",
            holdMillis <= 900.0,
        )
    }

    @Test
    fun slidePauseIsSelectableAndRoundTrips() {
        assertEquals(LoadingSpin.SLIDE_PAUSE, LoadingSpin.fromId(LoadingSpin.SLIDE_PAUSE.id))
        assertEquals("slide_pause", LoadingSpin.SLIDE_PAUSE.id)
    }

    @Test
    fun bounceAndSlideAreSelectableAndRoundTrip() {
        // Both new motions must survive a trip through DataStore, or the user's choice is lost
        // on the next launch.
        listOf(LoadingSpin.BOUNCE, LoadingSpin.SLIDE).forEach { spin ->
            assertEquals(spin, LoadingSpin.fromId(spin.id))
        }
        assertEquals("bounce", LoadingSpin.BOUNCE.id)
        assertEquals("slide", LoadingSpin.SLIDE.id)
    }

    @Test
    fun noMotionIsTheFirstOptionAndIsNotTheDefault() {
        // "Still" is listed first because it is the least surprising choice to reach for, but the
        // default stays a spin so a fresh install still looks alive.
        assertEquals(LoadingSpin.NONE, LoadingSpin.entries.first())
        assertEquals("none", LoadingSpin.NONE.id)
        assertNotEquals(DefaultLoadingSpinId, LoadingSpin.NONE.id)
    }

    @Test
    fun loadingCircleDefaultsToSoft() {
        val settings = AppSettings()
        assertEquals(DefaultLoadingCircleId, settings.loadingCircleId)
        assertEquals(LoadingCircle.SOFT, settings.loadingCircle)
        assertEquals(LoadingCircle.SOFT, LoadingCircle.fromId(null))
        assertEquals(LoadingCircle.SOFT, LoadingCircle.fromId("nonsense"))
    }

    @Test
    fun everyLoadingCircleIdRoundTrips() {
        LoadingCircle.entries.forEach { circle ->
            assertEquals(circle, LoadingCircle.fromId(circle.id))
        }
        val ids = LoadingCircle.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun noneIsAnAvailableCircle() {
        // "None" must be a real, persisted choice rather than a null that silently falls back.
        assertEquals(LoadingCircle.NONE, LoadingCircle.fromId("none"))
        assertEquals(
            LoadingCircle.NONE,
            AppSettings(loadingCircleId = "none").loadingCircle,
        )
    }

    @Test
    fun circleChoiceIsIndependentOfTheOtherLoadingSettings() {
        val settings = AppSettings(
            loadingCircleId = LoadingCircle.OUTLINE.id,
            loadingSpinId = LoadingSpin.NONE.id,
            loadingImageFileName = "startup-a-1.gif",
        )
        assertEquals(LoadingCircle.OUTLINE, settings.loadingCircle)
        assertEquals(LoadingSpin.NONE, settings.loadingSpin)
        assertEquals(LoadingImage.Picked("startup-a-1.gif"), settings.loadingImage)
    }

    @Test
    fun startupScreenIsOnByDefault() {
        // A user who has never opened Config should still see the branded startup screen.
        assertTrue(AppSettings().showLoadingScreen)
    }

    @Test
    fun startupImageAndCircleSizesDefaultToTheSharedConstants() {
        val settings = AppSettings()
        assertEquals(DefaultLoadingImageSizeDp, settings.loadingImageSizeDp)
        assertEquals(DefaultLoadingCircleSizeDp, settings.loadingCircleSizeDp)
        // The image default now sits at the top of its range (200), which is *larger* than the
        // circle default (168) — so the floor kicks in and the effective circle matches the image
        // rather than clipping it. This is the case the floor exists for, and it is now the
        // default state rather than an edge case.
        assertEquals(
            DefaultLoadingImageSizeDp,
            settings.effectiveLoadingCircleSizeDp,
        )
    }

    @Test
    fun configuredSizesAreIndependentUntilTheCircleWouldClipTheImage() {
        // A deliberately oversized image and a small circle: the circle must be raised to the
        // image's size rather than left smaller, which would clip the logo.
        val settings = AppSettings(loadingImageSizeDp = 160, loadingCircleSizeDp = 80)
        assertEquals(160, settings.loadingImageSizeDp)
        assertEquals(80, settings.loadingCircleSizeDp)
        assertEquals(160, settings.effectiveLoadingCircleSizeDp)
    }

    @Test
    fun aLargeEnoughCircleIsLeftAlone() {
        val settings = AppSettings(loadingImageSizeDp = 72, loadingCircleSizeDp = 240)
        assertEquals(240, settings.effectiveLoadingCircleSizeDp)
    }

    @Test
    fun sizeRangesAreOrderedAndContainTheirDefaults() {
        // A range whose default sat outside it would make the slider's first frame jump.
        assertTrue(MinLoadingImageSizeDp < MaxLoadingImageSizeDp)
        assertTrue(DefaultLoadingImageSizeDp in MinLoadingImageSizeDp..MaxLoadingImageSizeDp)
        assertTrue(MinLoadingCircleSizeDp < MaxLoadingCircleSizeDp)
        assertTrue(DefaultLoadingCircleSizeDp in MinLoadingCircleSizeDp..MaxLoadingCircleSizeDp)
    }

    @Test
    fun normalSpeedLeavesTheCycleLengthUntouched() {
        LoadingSpin.entries.forEach { spin ->
            assertEquals(spin.periodMillis, spin.periodMillisAt(MotionSpeed.NORMAL))
        }
    }

    @Test
    fun speedsStretchAndCompressTheCycle() {
        val base = LoadingSpin.CLOCKWISE.periodMillis
        assertTrue(LoadingSpin.CLOCKWISE.periodMillisAt(MotionSpeed.FAST) < base)
        assertTrue(LoadingSpin.CLOCKWISE.periodMillisAt(MotionSpeed.SLOW) > base)
    }

    @Test
    fun everySpeedKeepsAUsableCycleLength() {
        // Including OFF: a zero-length infinite repeatable is an invalid spec, and the startup
        // animation is not driven by AppMotion's zero-duration handouts.
        LoadingSpin.entries.forEach { spin ->
            MotionSpeed.entries.forEach { speed ->
                assertTrue(
                    "${spin.id} at ${speed.id} produced an unusable period",
                    spin.periodMillisAt(speed) > 0,
                )
            }
        }
    }

    @Test
    fun offFallsBackToTheMotionsOwnPace() {
        assertEquals(0, MotionSpeed.OFF.scale(LoadingSpin.CLOCKWISE.periodMillis))
        assertEquals(
            LoadingSpin.CLOCKWISE.periodMillis,
            LoadingSpin.CLOCKWISE.periodMillisAt(MotionSpeed.OFF),
        )
    }
}
