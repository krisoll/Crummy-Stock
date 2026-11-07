package com.example.stockapplication.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stores the user's picked startup-screen image in the app's private files directory.
 *
 * Deliberately a separate folder from [ProductImages] rather than reusing it: the two have
 * different lifecycles (a product photo belongs to a product row; this one belongs to a setting)
 * and wiping product images should never take the startup screen down with it.
 *
 * The image is **copied in** at pick time. The system photo picker hands back a short-lived read
 * grant, so storing the `content://` URI would leave the startup screen blank a few hours after it
 * was chosen — a bug that would only show up on the *next* launch, which is the worst place to
 * discover it.
 *
 * Everything here swallows failures and reports `null` instead of throwing: a missing or corrupt
 * startup image must never stop the app from launching.
 */
object LoadingImageStore {

    private const val TAG = "LoadingImageStore"
    private const val FOLDER = "loading_image"

    /**
     * Longest edge of the stored image.
     *
     * The startup icon is drawn at most ~150dp; on a 3x display that is under 500px. 512 keeps a
     * sharp result while making the rotation cheap to draw every frame.
     */
    const val MAX_EDGE_PX = 512

    private const val EXTENSION = ".png"

    /**
     * Extension for a copied animated image.
     *
     * `.gif` even when the source was an animated WebP: the loader sniffs the content rather than
     * trusting the name, and keeping one extension for "this file is animated" makes [fileNameFor]'s
     * callers simpler.
     */
    private const val ANIMATED_EXTENSION = ".gif"

    fun imageDir(context: Context): File =
        File(context.applicationContext.filesDir, FOLDER).apply { mkdirs() }

    /** Resolves a stored name to its file, rejecting anything that would escape the folder. */
    fun imageFile(context: Context, fileName: String?): File? {
        val name = fileName?.trim().orEmpty()
        if (name.isEmpty()) return null
        if (name.contains('/') || name.contains('\\') || name == "." || name == "..") return null
        return File(imageDir(context), name)
    }

    /**
     * Copies the image behind [uri] into private storage.
     *
     * Two paths, because they have opposite requirements:
     *
     *  - **Still images** are decoded, downscaled and re-encoded as PNG. PNG rather than JPEG
     *    because a logo usually has flat colour or transparency, and both look visibly worse after
     *    JPEG re-encoding.
     *  - **Animated images (GIF / animated WebP)** are copied **byte for byte**. Decoding them with
     *    [BitmapFactory] would silently collapse them to a single frame — the animation would be
     *    lost with no error, which is the hardest kind of bug to notice. The decoder that reads
     *    them back understands the format natively and downsamples while decoding, so a large GIF
     *    still costs little to display.
     *
     * @return the stored file name, or null when the image could not be read or written.
     */
    suspend fun save(context: Context, uri: Uri, now: Long = System.currentTimeMillis()): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val animated = isAnimated(context, uri)
                val fileName = fileNameFor(now, animated)
                val target = File(imageDir(context), fileName)
                val written = if (animated) {
                    copyVerbatim(context, uri, target)
                } else {
                    val bitmap = decodeDownscaled(context, uri) ?: return@runCatching null
                    val ok = target.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    bitmap.recycle()
                    ok
                }
                if (!written) {
                    target.delete()
                    return@runCatching null
                }
                fileName
            }.onFailure { Log.w(TAG, "Could not save startup image", it) }.getOrNull()
        }

    /**
     * True when the content behind [uri] should be copied verbatim rather than re-encoded.
     *
     * Two checks, cheapest first:
     *
     *  - **GIF container signature.** A header sniff, six bytes, and it catches every GIF —
     *    including single-frame ones, which is harmless: copying those verbatim still displays
     *    correctly.
     *  - **Animated WebP**, on API 28+, via [ImageDecoder]'s reporter. The reporter runs before
     *    the bitmap is allocated, so asking "how many frames?" costs essentially nothing.
     *
     * Deliberately *not* decoding the image here: a 20 MP animated file would be inflated twice,
     * once to inspect it and again to save it.
     */
    private fun isAnimated(context: Context, uri: Uri): Boolean =
        hasGifHeader(context, uri) || isAnimatedWebP(context, uri)

    /** Cheap magic-number check for the GIF87a / GIF89a signatures. */
    private fun hasGifHeader(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val header = ByteArray(6)
            if (stream.read(header) != header.size) return@use false
            val magic = String(header, Charsets.US_ASCII)
            magic == "GIF87a" || magic == "GIF89a"
        } ?: false
    }.getOrDefault(false)

    /** Animated WebP is detectable without a full decode, but only from API 28. */
    private fun isAnimatedWebP(context: Context, uri: Uri): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@runCatching false
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        // The reporter runs before any pixels are allocated, so this stays cheap even for a file
        // far larger than the startup screen will ever draw.
        ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
            decoder.setTargetSize(1, 1)
        }.let { drawable -> drawable is AnimatedImageDrawable }
    }.getOrDefault(false)

    /** Streams the original bytes through, preserving every frame. */
    private fun copyVerbatim(context: Context, uri: Uri, target: File): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } != null
    }.getOrDefault(false)

    /**
     * Decodes a stored still startup image, or null if it is missing or unreadable.
     *
     * Animated files return their **first frame** here; this is only used by callers that need a
     * plain bitmap (the unit-testable file-name checks). The startup screen itself renders through
     * Coil so that animation plays.
     */
    suspend fun load(context: Context, fileName: String?): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val file = imageFile(context, fileName)?.takeIf { it.isFile } ?: return@withContext null
            runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
                .onFailure { Log.w(TAG, "Could not decode startup image", it) }
                .getOrNull()
        }

    /** Absolute path of a stored image, for handing to an image loader. */
    fun imagePath(context: Context, fileName: String?): String? =
        imageFile(context, fileName)?.takeIf { it.isFile }?.absolutePath

    /** Deletes a stored startup image. Missing files and blank names are ignored. */
    fun delete(context: Context, fileName: String?) {
        runCatching { imageFile(context, fileName)?.delete() }
            .onFailure { Log.w(TAG, "Could not delete startup image", it) }
    }

    /**
     * A fresh file name per pick.
     *
     * A timestamp plus a counter keeps two picks in the same millisecond apart, and keeps the name
     * unique so the previous file is never overwritten while it is still being displayed.
     */
    fun fileNameFor(now: Long = System.currentTimeMillis(), animated: Boolean = false): String {
        val extension = if (animated) ANIMATED_EXTENSION else EXTENSION
        return "startup-${now.toString(36)}-${counter.incrementAndGet().toString(36)}$extension"
    }

    private val counter = java.util.concurrent.atomic.AtomicLong(0L)

    /** Bounds-first decode, so a 12 MP gallery photo is never inflated in full. */
    private suspend fun decodeDownscaled(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

                // Same reasoning as ProductImages: a gallery photo of a logo arrives sideways
                // unless the EXIF tag is baked in. Read it before the decoding stream is opened.
                val orientation = ProductImages.readOrientation(context, uri)

                val options = BitmapFactory.Options().apply {
                    inSampleSize = ProductImages.sampleSizeFor(
                        bounds.outWidth,
                        bounds.outHeight,
                        MAX_EDGE_PX,
                    )
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    // Scaling happens below, so the decoder must not pre-scale as well.
                    inScaled = false
                }
                val decoded = resolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: return@runCatching null

                val upright = ProductImages.bakeOrientation(decoded, orientation)
                val scaled = ProductImages.scaleToMaxEdge(upright, MAX_EDGE_PX)
                if (scaled !== upright) upright.recycle()
                scaled
            }.onFailure { Log.w(TAG, "Could not decode picked startup image", it) }.getOrNull()
        }
}
