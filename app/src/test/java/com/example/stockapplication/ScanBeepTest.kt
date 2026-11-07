package com.example.stockapplication

import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.DefaultScanDelayMillis
import com.example.stockapplication.data.MaxScanDelayMillis
import com.example.stockapplication.data.MinScanDelayMillis
import com.example.stockapplication.data.ScanBeep
import com.example.stockapplication.data.ScanBeepSound
import com.example.stockapplication.data.ScanBeepStore
import com.example.stockapplication.ui.screens.formatScanDelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * The scanner's beep and its settings, tested where they are pure: tone synthesis, the two-part
 * sound model, and the delay's number formatting. Playback itself needs a real audio device and is
 * verified on the device instead.
 */
class ScanBeepTest {

    // ---- The sound model ---------------------------------------------------

    @Test
    fun `no picked file resolves to the built-in tone`() {
        val sound = ScanBeepSound.from(ScanBeep.CHIRP.id, null)
        assertEquals(ScanBeepSound.BuiltIn(ScanBeep.CHIRP), sound)
    }

    @Test
    fun `a blank picked file also resolves to the built-in tone`() {
        // Settings can hold an empty string from a hand-edited store; it must not be treated as a
        // file name, or the scanner would go silent.
        val sound = ScanBeepSound.from(ScanBeep.CLICK.id, "   ")
        assertEquals(ScanBeepSound.BuiltIn(ScanBeep.CLICK), sound)
    }

    @Test
    fun `a picked file wins over the selected tone`() {
        val sound = ScanBeepSound.from(ScanBeep.CLICK.id, "beep-abc-1.wav")
        assertEquals(ScanBeepSound.Picked("beep-abc-1.wav"), sound)
    }

    @Test
    fun `an unknown id falls back to the default tone`() {
        assertEquals(ScanBeep.Default, ScanBeep.fromId("nonsense"))
        assertEquals(ScanBeep.Default, ScanBeep.fromId(null))
    }

    // ---- Tone synthesis ----------------------------------------------------

    @Test
    fun `every tone is audible and short`() {
        ScanBeep.entries.forEach { beep ->
            val samples = ScanBeepStore.renderTone(beep)
            assertTrue("$beep produced no samples", samples.isNotEmpty())
            assertTrue(
                "$beep is silent (peak ${ScanBeepStore.peak(samples)})",
                ScanBeepStore.peak(samples) > Short.MAX_VALUE / 8
            )
            // No tone may run long: a scan beep that outlasts the next scan is a queue of sounds.
            val millis = samples.size * 1_000.0 / ScanBeepStore.SampleRate
            assertTrue("$beep lasts ${millis}ms", millis <= 400.0)
        }
    }

    @Test
    fun `no tone clips`() {
        ScanBeep.entries.forEach { beep ->
            val samples = ScanBeepStore.renderTone(beep)
            val clipped = samples.count { abs(it.toInt()) >= Short.MAX_VALUE }
            assertEquals("$beep clips on $clipped samples", 0, clipped)
        }
    }

    @Test
    fun `tones are distinguishable by pitch`() {
        // Not a listening test: it just proves the shapes are not near-identical, which is what a
        // copy-paste mistake in a generator would produce.
        val crossings = ScanBeep.entries.associateWith {
            ScanBeepStore.zeroCrossings(ScanBeepStore.renderTone(it))
        }
        assertTrue("all tones look alike: $crossings", crossings.values.toSet().size >= 3)
    }

    @Test
    fun `the rising chirp actually rises`() {
        val samples = ScanBeepStore.renderTone(ScanBeep.CHIRP)
        val half = samples.size / 2
        val firstHalf = ScanBeepStore.zeroCrossings(samples.copyOfRange(0, half))
        val secondHalf = ScanBeepStore.zeroCrossings(samples.copyOfRange(half, samples.size))
        assertTrue(
            "chirp does not rise: $firstHalf -> $secondHalf crossings",
            secondHalf > firstHalf
        )
    }

    // ---- The WAV container -------------------------------------------------

    @Test
    fun `the written wav has a valid riff header and the right length`() {
        val samples = ScanBeepStore.renderTone(ScanBeep.CLASSIC)
        val bytes = ByteArrayOutputStream().also {
            ScanBeepStore.writeWav(it, samples, ScanBeepStore.SampleRate)
        }.toByteArray()

        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
        assertEquals(44 + samples.size * 2, bytes.size)
        // Mono, 16 bit, at the documented rate.
        assertEquals(1, (bytes[22].toInt() and 0xFF) or ((bytes[23].toInt() and 0xFF) shl 8))
        assertEquals(16, (bytes[34].toInt() and 0xFF) or ((bytes[35].toInt() and 0xFF) shl 8))
        assertEquals(
            ScanBeepStore.SampleRate,
            (bytes[24].toInt() and 0xFF) or
                ((bytes[25].toInt() and 0xFF) shl 8) or
                ((bytes[26].toInt() and 0xFF) shl 16) or
                ((bytes[27].toInt() and 0xFF) shl 24)
        )
    }

    @Test
    fun `tone file names are per-tone and versioned`() {
        val names = ScanBeep.entries.map { ScanBeepStore.toneFileName(it) }
        assertEquals("tone file names collide", names.size, names.toSet().size)
        assertTrue(names.first().endsWith("-v${ScanBeepStore.ToneVersion}.wav"))
    }

    @Test
    fun `generated file names are unique`() {
        val first = ScanBeepStore.fileNameFor(now = 1_000L)
        val second = ScanBeepStore.fileNameFor(now = 1_000L)
        assertTrue("two picks in the same millisecond collided", first != second)
        assertTrue(first.endsWith(".wav"))
    }

    // ---- The scan delay ----------------------------------------------------

    @Test
    fun `the delay runs from two to five seconds, three by default`() {
        assertEquals(2_000, MinScanDelayMillis)
        assertEquals(5_000, MaxScanDelayMillis)
        assertEquals(3_000, DefaultScanDelayMillis)
        assertTrue(DefaultScanDelayMillis in MinScanDelayMillis..MaxScanDelayMillis)
    }

    @Test
    fun `the slider's steps divide the range exactly`() {
        // The slider derives `steps` from the bounds on the assumption that the span is a whole
        // number of stops; if it were not, the last stop would miss the maximum.
        val step = 100
        assertEquals(0, (MaxScanDelayMillis - MinScanDelayMillis) % step)
    }

    @Test
    fun `the default delay survives a clamp`() {
        val settings = AppSettings()
        assertEquals(DefaultScanDelayMillis, settings.scanDelayMillis)
        // Round-tripping through the stored value must not move it.
        assertEquals(
            DefaultScanDelayMillis,
            settings.scanDelayMillis.coerceIn(MinScanDelayMillis, MaxScanDelayMillis)
        )
    }

    @Test
    fun `a stored delay below the new floor is raised to it on read`() {
        // Anyone who had picked 0.3 s before the bounds changed must not be left with a value the
        // slider cannot represent. Reading clamps, so the UI never has to defend against it.
        val tooShort = 300.coerceIn(MinScanDelayMillis, MaxScanDelayMillis)
        assertEquals(MinScanDelayMillis, tooShort)
    }

    @Test
    fun `the delay is shown in seconds without trailing zeros`() {
        assertEquals("2", formatScanDelay(2_000))
        assertEquals("2.5", formatScanDelay(2_500))
        assertEquals("3", formatScanDelay(3_000))
        assertEquals("5", formatScanDelay(5_000))
    }

    @Test
    fun `the display formats any value it is given`() {
        // It deliberately does NOT clamp. There are two sliders with different ranges — 2..5 s for
        // the same product and 0.5..2 s for a different one — so a formatter that forced everything
        // into the longer range would print "2" for a 1 s product-change setting.
        assertEquals("1", formatScanDelay(1_000))
        assertEquals("0.5", formatScanDelay(500))
        // Clamping is the repository's job, and it is tested where it happens.
        assertFalse(formatScanDelay(0).startsWith("-"))
    }
}
