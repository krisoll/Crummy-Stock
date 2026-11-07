package com.example.stockapplication

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.example.stockapplication.data.ProductImages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Camera photos are stored sideways with an EXIF tag saying how to turn them upright, and
 * [BitmapFactory] ignores that tag. These tests pin the matrix maths that corrects it — the reason
 * "sometimes" a photo arrived rotated was that the stored orientation depends on the sensor and on
 * how the phone was held, so only some captures look wrong.
 */
@RunWith(RobolectricTestRunner::class)
class ImageOrientationTest {

    /** A 4x2 bitmap whose top-left pixel is red, so a rotation can be told apart from a flip. */
    private fun asymmetric(): Bitmap {
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.RED)
        return bitmap
    }

    @Test
    fun `orientation normal returns the same instance`() {
        val source = asymmetric()
        // Identity matters: callers recycle the input only when a new bitmap came back, so
        // allocating here would recycle a bitmap that is still on screen.
        assertSame(source, ProductImages.applyOrientation(source, ExifInterface.ORIENTATION_NORMAL))
    }

    @Test
    fun `orientation undefined is treated as upright`() {
        val source = asymmetric()
        assertSame(
            source,
            ProductImages.applyOrientation(source, ExifInterface.ORIENTATION_UNDEFINED)
        )
    }

    @Test
    fun `rotate 90 swaps the axes`() {
        val rotated = ProductImages.applyOrientation(
            asymmetric(),
            ExifInterface.ORIENTATION_ROTATE_90
        )
        assertEquals(2, rotated.width)
        assertEquals(4, rotated.height)
    }

    @Test
    fun `rotate 270 swaps the axes`() {
        val rotated = ProductImages.applyOrientation(
            asymmetric(),
            ExifInterface.ORIENTATION_ROTATE_270
        )
        assertEquals(2, rotated.width)
        assertEquals(4, rotated.height)
    }

    @Test
    fun `rotate 180 keeps the dimensions`() {
        val rotated = ProductImages.applyOrientation(
            asymmetric(),
            ExifInterface.ORIENTATION_ROTATE_180
        )
        assertEquals(4, rotated.width)
        assertEquals(2, rotated.height)
    }

    @Test
    fun `flips keep the dimensions`() {
        listOf(
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
        ).forEach { orientation ->
            val flipped = ProductImages.applyOrientation(asymmetric(), orientation)
            assertEquals(4, flipped.width)
            assertEquals(2, flipped.height)
        }
    }

    @Test
    fun `bake recycles the input once a new bitmap is allocated`() {
        val source = asymmetric()
        val baked = ProductImages.bakeOrientation(source, ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(true, source.isRecycled)
        assertNotNull(baked)
    }

    @Test
    fun `bake keeps the input alive when nothing was rotated`() {
        val source = asymmetric()
        val baked = ProductImages.bakeOrientation(source, ExifInterface.ORIENTATION_NORMAL)
        assertSame(source, baked)
        assertEquals(false, source.isRecycled)
    }

    @Test
    fun `unknown tag falls back to upright`() {
        val source = asymmetric()
        // 99 is not a defined ORIENTATION_* value; guessing at it would turn photos at random.
        assertSame(source, ProductImages.applyOrientation(source, 99))
    }

    @Test
    fun `readOrientation on a file without exif is normal`() {
        val file = File.createTempFile("plain", ".png")
        try {
            // A PNG has no EXIF block at all, which is the common case for logos.
            file.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            assertEquals(
                ExifInterface.ORIENTATION_NORMAL,
                ProductImages.readOrientation(file)
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `readOrientation tolerates a missing file`() {
        val missing = File(
            ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "does-not-exist.jpg"
        )
        // A broken cache file must not be able to block saving a product.
        assertEquals(
            ExifInterface.ORIENTATION_NORMAL,
            ProductImages.readOrientation(missing)
        )
    }
}
