package com.example.stockapplication.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Reads and writes the ZIP form of a backup: the same JSON document as before, plus every product
 * photo it references.
 *
 * ## Why a ZIP rather than base64 in the JSON
 *
 * The photos are already-compressed JPEGs. Base64 would inflate them by a third and pull the whole
 * catalogue into memory as one string; a ZIP stores them as-is, lets a reader pull out one entry at
 * a time, and is a format the user can open in any file manager to check what they are restoring.
 *
 * ## Layout
 *
 * ```
 * backup.json              the existing [JSON_FORMAT] document, byte-for-byte unchanged
 * images/img-abc123.jpg    one entry per photo referenced by products[].imagePath
 * ```
 *
 * The `images/` prefix keeps photos namespaced so a future addition (a manifest, settings) cannot
 * collide with them, and so an unzip-everything reader does not scatter JPEGs through the root.
 *
 * ## Compatibility
 *
 * `backup.json` is written exactly as [toJson] produces it, so an existing `.json` importer — the
 * one in an older build of this app, or a desktop script — can still read a `.zip` backup after
 * extracting that one entry. The archive is a *container* change, not a format change, so
 * [JSON_VERSION] deliberately does not move.
 */
object BackupArchive {

    private const val TAG = "BackupArchive"

    /** Name of the JSON entry. Fixed so a reader never has to guess. */
    const val JSON_ENTRY = "backup.json"

    /** Prefix for photo entries, including the trailing slash. */
    const val IMAGE_PREFIX = "images/"

    /** Extension for the archive, used by the file-name helpers below. */
    private const val EXTENSION = ".zip"

    /**
     * Ceiling for an archive the user asks to import.
     *
     * Larger than the JSON limit because photos are the bulk of it: 1,024 px JPEGs at quality 85
     * run to roughly 150 KB each, so this covers thousands of products. The cap exists to stop an
     * arbitrary file being read into memory, not to bound a realistic catalogue.
     */
    const val MaxArchiveBytes = 512L * 1024 * 1024

    /**
     * Ceiling for one inflated entry.
     *
     * A ZIP bomb is a small archive that expands without bound. Refusing an entry above this makes
     * the attack fail on the first oversized member instead of after exhausting the heap.
     */
    private const val MaxEntryBytes = 64L * 1024 * 1024

    /** JSON is a few hundred kilobytes; this only guards against a malformed archive. */
    private const val MaxJsonBytes = 32L * 1024 * 1024

    /** Default archive file name, e.g. `stock-backup-2025-03-14-1830.zip`. */
    fun archiveFileName(now: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(now))
        return "stock-backup-$stamp$EXTENSION"
    }

    /**
     * True when the picked file should be treated as an archive.
     *
     * Decided by extension rather than by MIME type: providers disagree about ZIP (reporting
     * `application/zip`, `application/x-zip-compressed` or `application/octet-stream`), while the
     * name is stable. A `.json` file is never mistaken for an archive, so old backups keep working.
     */
    fun isArchive(displayName: String): Boolean =
        displayName.trim().endsWith(EXTENSION, ignoreCase = true)

    /**
     * Builds the archive in memory.
     *
     * In memory rather than streaming straight to the destination because the existing export
     * plumbing ([ExportFiles.writeBytes]) already takes a `ByteArray` and handles every destination
     * case — SAF tree, MediaStore, legacy external storage. Streaming would mean reimplementing all
     * three for one file type. Photos are bounded by [ProductImages.MAX_EDGE_PX] and a shop's
     * catalogue is small, so the peak is a few megabytes.
     *
     * @param json the document from [toJson].
     * @param images the photo files to embed, keyed by the exact `imagePath` stored on the product.
     *   Missing files are skipped and logged rather than failing the export: a backup without one
     *   photo is still worth having.
     */
    fun write(json: String, images: Map<String, File>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // The JSON goes first so a reader can stream the archive and reach the index before
            // any photo bytes.
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            images.forEach { (name, file) ->
                // Only the basename is embedded. The stored imagePath is already a bare name, but
                // taking the basename here means a path from a hand-edited database cannot write
                // an entry like "images/../../evil".
                val safeName = File(name).name
                if (safeName.isEmpty()) return@forEach
                val bytes = runCatching {
                    if (file.isFile) file.readBytes() else null
                }.onFailure { Log.w(TAG, "Could not read photo $name for the backup", it) }
                    .getOrNull() ?: return@forEach
                zip.putNextEntry(ZipEntry(IMAGE_PREFIX + safeName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /**
     * A parsed archive: the JSON document plus every photo it carried.
     *
     * @param images photo bytes keyed by bare file name, matching `products[].imagePath`.
     */
    data class Contents(
        val json: String,
        val images: Map<String, ByteArray>,
    )

    /**
     * Reads [bytes] as an archive.
     *
     * Fails only when the ZIP itself is unreadable or carries no [JSON_ENTRY] — both mean the file
     * is not a backup at all. Individual photo entries that are oversized or unreadable are
     * skipped: a restore that brings back the catalogue but not one photo is far better than one
     * that refuses to start.
     *
     * @throws java.io.IOException when the archive cannot be read or has no JSON entry.
     */
    fun read(bytes: ByteArray): Contents {
        require(bytes.size <= MaxArchiveBytes) {
            "File is too large to be a backup (${bytes.size / 1024 / 1024} MB)"
        }
        var json: String? = null
        val images = LinkedHashMap<String, ByteArray>()

        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                when {
                    entry.isDirectory -> Unit
                    entry.name == JSON_ENTRY -> json =
                        readEntry(zip, entry, MaxJsonBytes)?.toString(Charsets.UTF_8)
                    entry.name.startsWith(IMAGE_PREFIX) -> {
                        val name = entry.name.removePrefix(IMAGE_PREFIX)
                        // Flatten to a basename for the same reason the writer does: the importer
                        // later writes these into app-private storage, and an entry named
                        // "a/b.jpg" would either fail or escape the folder.
                        val safeName = File(name).name
                        if (safeName.isNotEmpty()) {
                            readEntry(zip, entry, MaxEntryBytes)?.let { images[safeName] = it }
                        }
                    }
                    // Unknown entries are ignored: a future version may add a manifest, and an
                    // older build must still be able to restore what it does understand.
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val document = json
            ?: error("The archive has no $JSON_ENTRY in it, so it is not a backup.")
        return Contents(document, images)
    }

    /**
     * Reads one entry, refusing anything past [limit].
     *
     * The length is checked before *and* during the read: `entry.size` is attacker-controlled and
     * may be -1 (unknown) or simply wrong, so the running total is what actually bounds the
     * allocation.
     *
     * @return the bytes, or null when the entry is too large and was skipped.
     */
    private fun readEntry(zip: ZipInputStream, entry: ZipEntry, limit: Long): ByteArray? {
        if (entry.size > limit) {
            Log.w(TAG, "Skipping oversized entry ${entry.name} (${entry.size} bytes)")
            return null
        }
        val initial = entry.size.takeIf { it in 1..(1 shl 20) }?.toInt() ?: (16 * 1024)
        val buffer = ByteArrayOutputStream(initial)
        val chunk = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = zip.read(chunk)
            if (read <= 0) break
            total += read
            if (total > limit) {
                Log.w(TAG, "Skipping oversized entry ${entry.name} (over $limit bytes)")
                return null
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }
}

/**
 * Writes restored photos into private storage and hands back the names to store on the products.
 *
 * Restoring an archive cannot simply keep the `imagePath` from the file: the *bytes* live in the
 * archive, and the file they name has to exist on this device.
 */
object RestoredImages {

    private const val TAG = "RestoredImages"

    /**
     * Writes [images] and returns a map from the backup's file name to the name now on disk.
     *
     * @return a mapping for the photos that were written. Names absent from the result had no
     *   bytes in the archive and should be dropped from the product rather than pointed at a file
     *   that will never exist.
     */
    suspend fun writeAll(context: Context, images: Map<String, ByteArray>): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (images.isEmpty()) return@withContext emptyMap()
            val dir = ProductImages.imageDir(context)
            val written = HashMap<String, String>(images.size)
            images.forEach { (backupName, bytes) ->
                runCatching {
                    val safeName = File(backupName).name
                    if (safeName.isEmpty()) return@runCatching null
                    // Overwriting is safe and deliberate: image names are generated per pick, so a
                    // collision means these *are* the same photo — re-restoring the same backup
                    // must not fill storage with duplicate copies of it.
                    File(dir, safeName).writeBytes(bytes)
                    safeName
                }.onFailure { Log.w(TAG, "Could not restore photo $backupName", it) }
                    .getOrNull()?.let { written[backupName] = it }
            }
            written
        }

    /**
     * Rewrites [contents] so every product points at a photo that now exists on this device.
     *
     * Products whose photo was absent or unreadable lose their image rather than keeping a name
     * that resolves to nothing — that would leave the inventory list showing a placeholder while
     * the database claims a photo exists.
     */
    fun apply(contents: BackupContents, names: Map<String, String>): BackupContents {
        if (names.isEmpty()) {
            // Nothing restored: drop every dangling reference from the file.
            return contents.copy(
                products = contents.products.map { it.copy(imagePath = null) }
            )
        }
        return contents.copy(
            products = contents.products.map { product ->
                val resolved = product.imagePath?.let { names[it] }
                product.copy(imagePath = resolved)
            }
        )
    }
}
