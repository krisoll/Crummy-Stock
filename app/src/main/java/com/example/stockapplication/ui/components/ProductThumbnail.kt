package com.example.stockapplication.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.stockapplication.data.ProductImages
import java.io.File

/**
 * Rounds a product photo for list rows.
 *
 * Decoded with [BitmapFactory] rather than an image loader because the files are already local,
 * downscaled on save, and there is no third-party image library in this project. The result is
 * remembered per file so scrolling a long list doesn't re-read the disk.
 *
 * Falls back to the same placeholder the inventory list uses when the product has no photo or the
 * file has gone missing.
 */
@Composable
fun ProductThumbnail(
    fileName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    cornerRadius: Dp = 12.dp,
) {
    val context = LocalContext.current
    // Keyed on the timestamp as well as the name. `remember(fileName)` alone only re-decodes when
    // the *name* changes, so a replacement photo written over the same filename kept showing the
    // old decoded bitmap even though the file on disk was new. The modification time is a cheap
    // way to notice an in-place overwrite.
    val signature = remember(fileName) {
        ProductImages.imageFile(context, fileName)
            ?.takeIf { it.isFile }
            ?.let { "${it.absolutePath}:${it.lastModified()}:${it.length()}" }
    }
    val bitmap: ImageBitmap? = remember(signature) {
        val file: File? = ProductImages.imageFile(context, fileName)
        file?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
    }
    ThumbnailBox(bitmap, modifier, size, cornerRadius)
}

/**
 * Preview for an image that has been picked but not yet copied into app storage.
 *
 * The form needs this because the chosen photo is only written to disk when the product is saved;
 * without it the preview would stay on the placeholder until the next screen visit.
 *
 * Two decode paths, because the two sources hand back very different URIs:
 *  - the system photo picker returns a `content://media/...` row, which streams fine;
 *  - a camera capture is a `content://<app>.fileprovider/cache/...` over a file **we** own, and
 *    that provider is not exported to ourselves in the way a media URI is. Going through the
 *    resolver there is fragile, so a FileProvider URI is mapped back to its [File] and decoded
 *    directly.
 */
@Composable
fun ProductThumbnail(
    uri: Uri?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    cornerRadius: Dp = 12.dp,
) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(uri) {
        uri?.let { picked ->
            runCatching { decodePreview(context, picked)?.asImageBitmap() }.getOrNull()
        }
    }
    ThumbnailBox(bitmap, modifier, size, cornerRadius)
}

/**
 * Decodes a not-yet-saved pick, downsampled to roughly the preview size so a 12 MP capture is not
 * inflated in full just to draw an 88dp square.
 */
private fun decodePreview(
    context: android.content.Context,
    uri: Uri,
): android.graphics.Bitmap? {
    // Our own FileProvider URIs point into the app's cache directory; decode those as files.
    val localFile = if (uri.authority == "${context.packageName}.fileprovider") {
        uri.path?.removePrefix("/cache/")?.let { File(context.cacheDir, it) }
    } else {
        null
    }
    if (localFile != null && localFile.isFile) {
        val decoded = BitmapFactory.decodeFile(localFile.absolutePath, samplingOptions(localFile))
            ?: return null
        // A camera capture carries its rotation in EXIF, which BitmapFactory ignores. Without
        // this the preview shows the photo sideways while the saved copy is upright — the two
        // decodes must agree or the picture appears to jump when the form is saved.
        return ProductImages.bakeOrientation(decoded, ProductImages.readOrientation(localFile))
    }

    // Everything else (the photo picker, other documents) goes through the resolver.
    // Read the tag before consuming the stream: it is in the header, and re-opening the URI a
    // second time only to read it would double the I/O for a full-resolution photo.
    val orientation = ProductImages.readOrientation(context, uri)
    val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, samplingOptions())
    } ?: return null
    return ProductImages.bakeOrientation(decoded, orientation)
}

/** Bounds-first decode options so the preview never loads a full-resolution bitmap. */
private fun samplingOptions(file: File? = null): BitmapFactory.Options =
    BitmapFactory.Options().apply {
        inSampleSize = if (file != null) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            ProductImages.sampleSizeFor(bounds.outWidth, bounds.outHeight, PREVIEW_MAX_EDGE_PX)
        } else {
            1
        }
    }

private const val PREVIEW_MAX_EDGE_PX = 512

@Composable
private fun ThumbnailBox(
    bitmap: ImageBitmap?,
    modifier: Modifier,
    size: Dp,
    cornerRadius: Dp,
) {

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
