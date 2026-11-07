package com.example.stockapplication

import androidx.compose.ui.graphics.Color
import com.example.stockapplication.ui.components.parseHex
import com.example.stockapplication.ui.components.toHexString
import com.example.stockapplication.ui.components.toHsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HsvConversionTest {

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.5f) {
        assertEquals(expected, actual, tolerance)
    }

    @Test
    fun convertsPrimaryColours() {
        Color(0xFFFF0000).toHsv().let {
            assertClose(0f, it.hue)
            assertClose(1f, it.saturation)
            assertClose(1f, it.brightness)
        }
        Color(0xFF00FF00).toHsv().let {
            assertClose(120f, it.hue)
            assertClose(1f, it.saturation)
        }
        Color(0xFF0000FF).toHsv().let {
            assertClose(240f, it.hue)
            assertClose(1f, it.saturation)
        }
    }

    @Test
    fun greysHaveZeroSaturation() {
        val grey = Color(0xFF808080).toHsv()
        assertClose(0f, grey.saturation)
        assertClose(0.502f, grey.brightness, tolerance = 0.01f)
        // Hue is meaningless for greys; the picker keeps whatever hue the user had.
        assertClose(0f, grey.hue)
    }

    @Test
    fun blackAndWhiteAreUncoloured() {
        assertEquals(0f, Color.Black.toHsv().brightness, 0.001f)
        assertEquals(0f, Color.White.toHsv().saturation, 0.001f)
        assertEquals(1f, Color.White.toHsv().brightness, 0.001f)
    }

    @Test
    fun hueIsAlwaysInRange() {
        // Exercises the negative-hue branch: max == red but green < blue.
        val magentaish = Color(0xFFFF00FF).toHsv()
        assertClose(300f, magentaish.hue)
        (0..360).forEach { step ->
            val hsv = Color(
                red = (step % 256) / 255f,
                green = ((step * 3) % 256) / 255f,
                blue = ((step * 7) % 256) / 255f,
            ).toHsv()
            assertTrue(hsv.hue >= 0f && hsv.hue <= 360f)
        }
    }

    @Test
    fun hexRoundTrip() {
        listOf(0xFF6750A4, 0xFF000000, 0xFFFFFFFF, 0xFF2E7D32).forEach { argb ->
            val color = Color((argb.toLong() and 0xFFFFFFFFL).toULong() shl 32)
            val hex = color.toHexString()
            assertEquals(6, hex.length)
            val parsed = parseHex(hex)
            assertTrue(parsed != null)
            assertEquals(hex, parsed!!.toHexString())
        }
    }

    @Test
    fun hexParsingRejectsIncompleteInput() {
        assertNull(parseHex(""))
        assertNull(parseHex("12345"))
        assertNull(parseHex("1234567"))
        assertNull(parseHex("GGGGGG"))
        assertTrue(parseHex("6750A4") != null)
        assertTrue(parseHex("#6750A4") != null)
    }

    @Test
    fun hexParsingIsOpaque() {
        val parsed = parseHex("000000")!!
        assertEquals(1f, parsed.alpha, 0.001f)
    }

    @Test
    fun hsvRoundTripsThroughRgb() {
        // The picker re-derives HSV from the stored colour, so this must be lossless enough not
        // to visibly drift each time the settings screen is opened.
        val original = Color(0xFF7D5260)
        val hsv = original.toHsv()
        val rebuilt = Color.hsv(hsv.hue, hsv.saturation, hsv.brightness)
        assertEquals(original.toHexString(), rebuilt.toHexString())
    }
}
