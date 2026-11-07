package com.example.stockapplication.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stores product photos in the app's private files directory.
 *
 * Photos are copied in rather than referenced by their original `content://` URI: a picker grant
 * is short-lived, so referencing it would leave broken thumbnails a few hours after the product
 * was saved.
 *
 * Only the resulting **file name** is stored on [Product.imagePath] (see that property for why).
 * Every entry point swallows failures and returns `null`/no-ops instead of throwing, so a broken
 * or unreadable image can never block saving a product.
 */
object ProductImages {

    private const val TAG = "ProductImages"
    private const val FOLDER = "product_images"

    /** Longest edge of a stored photo. Receipt thumbnails are far smaller, so this is plenty. */
    const val MAX_EDGE_PX = 1024

    /** JPEG quality used when re-encoding the picked image. */
    const val JPEG_QUALITY = 85

    private const val EXTENSION = ".jpg"

    /** Folder holding every product photo, created on demand. */
    fun imageDir(context: Context): File =
        File(context.applicationContext.filesDir, FOLDER).apply { mkdirs() }

    /** Resolves a stored name to its file. Returns null for a null/blank name. */
    fun imageFile(context: Context, fileName: String?): File? {
        val name = fileName?.trim().orEmpty()
        if (name.isEmpty()) return null
        // Guard against a name that would escape the folder (e.g. from a hand-edited database).
        if (name.contains('/') || name.contains('\\') || name == "." || name == "..") return null
        return File(imageDir(context), name)
    }

    /**
     * Copies the image behind [uri] into private storage, downscaling it and re-encoding as JPEG.
     *
     * @return the stored file name, or null when the image could not be read or written.
     */
    suspend fun saveImage(context: Context, uri: Uri, sourceId: Long = 0L): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodeDownscaled(context, uri) ?: return@runCatching null
                val fileName = generateFileName(sourceId)
                val target = File(imageDir(context), fileName)
                val written = target.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                bitmap.recycle()
                if (!written) {
                    target.delete()
                    return@runCatching null
                }
                fileName
            }.onFailure { Log.w(TAG, "Could not save product image", it) }
                .getOrNull()
        }

    /** Deletes a stored photo. Missing files and blank names are ignored. */
    fun deleteImage(context: Context, fileName: String?) {
        runCatching { imageFile(context, fileName)?.delete() }
            .onFailure { Log.w(TAG, "Could not delete product image", it) }
    }

    /**
     * Builds the file name used for a stored photo.
     *
     * A timestamp plus a per-instance counter keeps two picks made in the same millisecond from
     * overwriting each other, and the extension is fixed by us rather than taken from the source.
     */
    fun generateFileName(sourceId: Long = 0L, now: Long = System.currentTimeMillis()): String {
        val stamp = now.toString(36)
        val sequence = counter.incrementAndGet().toString(36)
        val owner = if (sourceId > 0) "p$sourceId-" else ""
        return "img-$owner$stamp-$sequence$EXTENSION"
    }

    private val counter = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * Reads the image bounds first so a 12-megapixel camera photo is never fully inflated, then
     * decodes with the smallest power-of-two sample size that keeps the long edge >= [MAX_EDGE_PX].
     */
    private fun decodeDownscaled(context: Context, uri: Uri): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        // Orientation is read from the stream *before* decoding: the tag lives in the file
        // header, and re-opening the stream afterwards would be a second full read.
        val orientation = readOrientation(context, uri)

        // Sampling is computed from the *stored* dimensions, which stay valid after rotation:
        // a 4000x3000 landscape frame rotated upright is still 12 MP.
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@runCatching null

        // Rotate first, then scale: scaling a rotated bitmap costs the same, but rotating the
        // smaller one is cheaper, and the long edge is the same either way.
        val upright = bakeOrientation(decoded, orientation)
        val scaled = scaleToMaxEdge(upright)
        if (scaled !== upright) upright.recycle()
        scaled
    }.onFailure { Log.w(TAG, "Could not decode picked image", it) }.getOrNull()

    /**
     * Reads the EXIF orientation of the image behind [uri].
     *
     * Why this is needed: `BitmapFactory` returns the **stored** pixels and ignores the orientation
     * tag entirely. A phone in portrait typically stores its sensor's landscape frame and records
     * `ORIENTATION_ROTATE_90` alongside it; the gallery and every image loader you have ever used
     * applies that tag, which is why the photo looks upright everywhere except here.
     *
     * Falls back to [ExifInterface.ORIENTATION_NORMAL] for anything that is not a readable image
     * with that tag: a PNG or a stripped JPEG has no orientation, and decoding it normally is the
     * right answer for both.
     */
    fun readOrientation(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL).orUpright()

    /** The same read, for a file already on disk (the camera's cache capture). */
    fun readOrientation(file: File): Int = runCatching {
        ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL).orUpright()

    /**
     * Collapses "no usable tag" onto [ExifInterface.ORIENTATION_NORMAL].
     *
     * A format with no EXIF block at all (PNG, or our own re-encoded JPEGs) reports
     * `ORIENTATION_UNDEFINED` — a *present* value, so `getOrDefault` never replaces it. Callers
     * only care about "is there a rotation to apply?", and treating undefined as a distinct case
     * they must each remember is how one of them eventually forgets.
     */
    private fun Int.orUpright(): Int =
        if (this == ExifInterface.ORIENTATION_UNDEFINED) ExifInterface.ORIENTATION_NORMAL else this

    /**
     * Bakes [orientation] into the pixels so the bitmap displays upright.
     *
     * Returns the *same* instance when the image is already normal, so callers must compare
     * identity before recycling — see [bakeOrientation] for the recycling-aware wrapper.
     *
     * The transition matrix is the one Android documents for EXIF: the last column flips or swaps
     * axes, and the first two position the result so no row ends up negative.
     */
    fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return bitmap
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            // Anything else (including a corrupt tag) is treated as upright rather than guessed at.
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    /**
     * [applyOrientation] that also recycles the input once it has been superseded.
     *
     * Only recycles when a new bitmap was actually allocated: when the orientation is normal the
     * input is returned as-is and must stay alive.
     */
    fun bakeOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val rotated = applyOrientation(bitmap, orientation)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** Largest power-of-two sample size that still leaves the long edge at or above [maxEdge]. */
    fun sampleSizeFor(width: Int, height: Int, maxEdge: Int = MAX_EDGE_PX): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= maxEdge || height / (sample * 2) >= maxEdge) {
            sample *= 2
        }
        return sample
    }

    /** Scales a bitmap down so its long edge is at most [maxEdge]. Never enlarges. */
    fun scaleToMaxEdge(
        bitmap: Bitmap,
        maxEdge: Int = MAX_EDGE_PX,
    ): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (maxEdge <= 0 || longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
