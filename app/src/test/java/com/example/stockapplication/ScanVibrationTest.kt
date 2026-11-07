package com.example.stockapplication

import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.DefaultScanVibrationStrength
import com.example.stockapplication.data.MaxScanVibrationStrength
import com.example.stockapplication.data.MinScanDelayMillis
import com.example.stockapplication.data.MinScanVibrationStrength
import com.example.stockapplication.data.ScanVibration
import com.example.stockapplication.data.ScanVibrationStore
import com.example.stockapplication.ui.screens.personalize.formatVibrationDuration
import com.example.stockapplication.ui.screens.personalize.vibrationPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scanner's vibration, tested where it is pure: pattern shape, the amplitude arrays handed to
 * the platform, and the settings clamping. Actually buzzing a device is verified by hand.
 */
class ScanVibrationTest {

    // ---- Patterns ----------------------------------------------------------

    @Test
    fun `every pattern starts and ends with silence`() {
        // Android's haptics guidance: begin and end at zero amplitude. Starting suddenly can ring
        // the actuator, and many drivers only brake actively when the waveform ends at 0.
        ScanVibration.entries.forEach { pattern ->
            assertEquals("$pattern does not start with silence", 0L, pattern.timings.first())
            assertEquals("$pattern does not end with silence", 0L, pattern.timings.last())
        }
    }

    @Test
    fun `every pattern has an odd number of segments`() {
        // silence, pulse, silence, pulse, ... silence — an odd length is the only shape that both
        // starts and ends on a silence while alternating throughout.
        ScanVibration.entries.forEach { pattern ->
            assertEquals(
                "$pattern has ${pattern.timings.size} timings",
                1,
                pattern.timings.size % 2
            )
            assertTrue("$pattern has no pulses", pattern.timings.size > 1)
        }
    }

    @Test
    fun `every pattern has at least one pulse and a sensible on-time`() {
        ScanVibration.entries.forEach { pattern ->
            val onTime = ScanVibrationStore.onTimeMillis(pattern)
            assertTrue("$pattern has no pulses", onTime > 0)
            assertTrue("$pattern is too short to feel ($onTime ms)", onTime >= 20)
            // A buzz still running when the next scan lands feels like one broken vibration.
            assertTrue(
                "$pattern ($onTime ms) outlasts the shortest scan delay ($MinScanDelayMillis ms)",
                onTime <= MinScanDelayMillis
            )
        }
    }

    @Test
    fun `no pattern outlasts the shortest scan delay`() {
        // A buzz still running when the next accepted scan lands would feel like one continuous,
        // broken vibration rather than two separate confirmations.
        ScanVibration.entries.forEach { pattern ->
            assertTrue(
                "$pattern (${pattern.durationMillis} ms) outlasts the shortest delay " +
                    "($MinScanDelayMillis ms)",
                pattern.durationMillis <= MinScanDelayMillis
            )
        }
    }

    @Test
    fun `patterns are distinguishable by total on-time`() {
        val onTimes = ScanVibration.entries.map { ScanVibrationStore.onTimeMillis(it) }
        assertTrue("all patterns feel the same: $onTimes", onTimes.toSet().size >= 3)
    }

    @Test
    fun `an unknown pattern id falls back to the default`() {
        assertEquals(ScanVibration.Default, ScanVibration.fromId("nonsense"))
        assertEquals(ScanVibration.Default, ScanVibration.fromId(null))
    }

    // ---- Amplitudes --------------------------------------------------------

    @Test
    fun `amplitudes align with the timings and silence the gaps`() {
        ScanVibration.entries.forEach { pattern ->
            val amplitudes = ScanVibrationStore.amplitudesFor(pattern, amplitude = 200)
            assertEquals("$pattern length mismatch", pattern.timings.size, amplitudes.size)
            amplitudes.forEachIndexed { index, value ->
                assertEquals(
                    "$pattern segment $index has the wrong amplitude",
                    if (ScanVibrationStore.isPulse(index)) 200 else 0,
                    value
                )
            }
            // The first and last segments are silences, so no pulse is left unbraked at the end.
            assertEquals("$pattern starts with a pulse", 0, amplitudes.first())
            assertEquals("$pattern ends with a pulse", 0, amplitudes.last())
        }
    }

    @Test
    fun `every pattern asks for at least one pulse`() {
        // A pattern whose amplitudes are all zero is silently nothing at all, which is the
        // easiest way to ship a control that appears to work and does not.
        ScanVibration.entries.forEach { pattern ->
            val amplitudes = ScanVibrationStore.amplitudesFor(pattern, amplitude = 200)
            assertTrue(
                "$pattern vibrates on no segment",
                amplitudes.any { it > 0 }
            )
        }
    }

    @Test
    fun `amplitude zero is preserved so the device can pick its own intensity`() {
        // 0 is not "off" in the platform API — it means "hardware default", which is what an
        // untouched setting should ask for.
        val amplitudes = ScanVibrationStore.amplitudesFor(ScanVibration.TAP, amplitude = 0)
        assertEquals(0, amplitudes[0])
        assertTrue("no pulses at amplitude 0", amplitudes.all { it == 0 })
    }

    // ---- Settings ----------------------------------------------------------

    @Test
    fun `the stored strength is clamped on read`() {
        val tooHigh = AppSettings(scanVibrationStrength = 9_999)
        assertEquals(MaxScanVibrationStrength, tooHigh.effectiveScanVibrationStrength)

        val tooLow = AppSettings(scanVibrationStrength = -50)
        assertEquals(MinScanVibrationStrength, tooLow.effectiveScanVibrationStrength)
    }

    @Test
    fun `the default strength is inside the range`() {
        val settings = AppSettings()
        assertEquals(
            DefaultScanVibrationStrength,
            settings.effectiveScanVibrationStrength.coerceIn(
                MinScanVibrationStrength,
                MaxScanVibrationStrength
            )
        )
    }

    @Test
    fun `vibration and the beep are both on by default`() {
        // Both feedback channels default on: a cashier holding the phone in one hand is not
        // necessarily watching the screen, so the confirmation should be felt as well as heard.
        val settings = AppSettings()
        assertTrue(settings.scanVibrationEnabled)
        assertTrue(settings.playsScanBeep)
    }

    @Test
    fun `the vibration settings resolve through the model`() {
        val settings = AppSettings(
            scanVibrationId = ScanVibration.HEARTBEAT.id,
            scanVibrationEnabled = true,
        )
        assertEquals(ScanVibration.HEARTBEAT, settings.scanVibration)
        assertTrue(settings.scanVibrationEnabled)
    }

    // ---- Display -----------------------------------------------------------

    @Test
    fun `the strength readout is a percentage of the maximum`() {
        assertEquals(0, vibrationPercent(0))
        assertEquals(50, vibrationPercent(128))
        assertEquals(100, vibrationPercent(MaxScanVibrationStrength))
    }

    @Test
    fun `the strength readout clamps out-of-range values`() {
        assertEquals(100, vibrationPercent(10_000))
        assertEquals(0, vibrationPercent(-10))
    }

    @Test
    fun `the duration readout is in milliseconds`() {
        assertEquals("35 ms", formatVibrationDuration(ScanVibration.TAP))
        assertEquals("180 ms", formatVibrationDuration(ScanVibration.BUZZ))
    }}
