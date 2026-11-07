package com.example.stockapplication.data

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Unpacks a dotLottie bundle into something the Lottie player already in the project can draw.
 *
 * ## What a `.lottie` file actually is
 *
 * A plain ZIP holding a documented bundle:
 *
 * ```
 * manifest.json                    required; lists the animations by id
 * animations/<id>/animation.json   the ordinary Lottie document, one per animation
 * images/                          shared image assets, referenced by animation.json
 * ```
 *
 * ## Why this reader exists rather than another dependency
 *
 * `LottieCompositionFactory.fromZipStreamSync` — which the importer used to call for `.lottie` —
 * reads a **gzipped Lottie JSON**, not this container. Handing it a real bundle meant parsing the
 * manifest as if it were an animation. The alternatives were a second, parallel rendering stack
 * from LottieFiles, or ~150 lines here. This keeps one renderer, one cache and one set of
 * behaviours.
 *
 * ## One animation per bundle
 *
 * A bundle can carry several; the app's settings have room for one, so the **first** entry in the
 * manifest wins. That is stated in the picker's help text rather than left as a surprise.
 *
 * ## Limits
 *
 * A picked file is untrusted input, and a ZIP bomb is otherwise a trivial way to fill the device:
 * every entry is capped, and so is the running total.
 */
object DotLottie {

    private const val TAG = "DotLottie"

    /** Largest single entry accepted. Embedded images are the big ones. */
    const val MaxEntryBytes = 12L * 1024 * 1024

    /** Largest total extracted across all entries. */
    const val MaxTotalBytes = 32L * 1024 * 1024

    /** Where the extracted animation's own images are written, relative to the animation folder. */
    private const val IMAGE_PREFIX = "images/"

    /** File name the animation is always written as, inside [targetDir]. */
    const val ANIMATION_FILE = "animation.json"

    /**
     * Extracts the bundle at [source] into [targetDir].
     *
     * The result is a folder holding [ANIMATION_FILE] plus an `images/` folder, which is exactly
     * the layout a Lottie `assets[].u` path expects — the JSON's asset base paths are rewritten to
     * match so the images resolve.
     *
     * @return the extracted animation file, or null when the archive is not a usable bundle.
     */
    fun extract(source: File, targetDir: File): File? {
        if (!source.isFile) return null
        return runCatching {
            targetDir.mkdirs()

            var animationJson: String? = null
            var animationPath: String? = null
            val images = LinkedHashMap<String, ByteArray>()
            // Collected first, written afterwards: the JSON's asset paths cannot be rewritten
            // until the manifest has told us which animation is "the" one.
            var manifest: String? = null
            var firstAnimationEntry: String? = null
            var budget = MaxTotalBytes

            ZipInputStream(source.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        when {
                            name == MANIFEST_ENTRY -> manifest = readEntry(zip, entry)?.toString(Charsets.UTF_8)

                            // "animations/<id>/animation.json" — remember the first one seen as a
                            // fallback for bundles with no usable manifest.
                            name.startsWith(ANIMATION_PREFIX) && name.endsWith("/$ANIMATION_FILE") ->
                                if (firstAnimationEntry == null) firstAnimationEntry = name

                            name.startsWith(IMAGE_PREFIX) -> {
                                val base = File(name.removePrefix(IMAGE_PREFIX)).name
                                if (base.isNotEmpty()) {
                                    readEntry(zip, entry)?.let {
                                        images[base] = it
                                        budget -= it.size
                                    }
                                }
                            }
                        }
                        if (budget <= 0) {
                            Log.w(TAG, "Bundle exceeds the extraction limit; giving up")
                            return@use
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // The manifest names the animation, so re-open the archive for just that entry. Trades
            // one extra pass over the ZIP for not holding every animation in memory at once.
            //
            // Falls back to the entry actually present when the manifest names one that is not in
            // the archive: a mismatched id is a broken export, not a reason to refuse a file whose
            // animation is sitting right there.
            val wanted = manifest
                ?.let { firstAnimationId(it) }
                ?.let { "$ANIMATION_PREFIX$it/$ANIMATION_FILE" }
                ?.takeIf { it == firstAnimationEntry }
                ?: firstAnimationEntry

            if (wanted == null) {
                Log.w(TAG, "No animation found in the bundle")
                return@runCatching null
            }

            ZipInputStream(source.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name == wanted) {
                        animationJson = readEntry(zip, entry)?.toString(Charsets.UTF_8)
                        animationPath = entry.name
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val raw = animationJson ?: return@runCatching null

            // Images sit beside the JSON in an "images/" folder, so the base path inside the
            // document has to point there. Bundles normally already write `"u": "images/"`, but a
            // hand-built one may use "./" or omit it entirely.
            val patched = rewriteImageBasePath(raw)

            targetDir.listFiles()?.forEach { it.deleteRecursively() }
            targetDir.mkdirs()

            images.forEach { (name, bytes) ->
                val imageDir = File(targetDir, IMAGE_PREFIX).apply { mkdirs() }
                File(imageDir, name).writeBytes(bytes)
            }

            val out = File(targetDir, ANIMATION_FILE)
            out.writeText(patched, Charsets.UTF_8)
            Log.i(TAG, "Extracted $animationPath (${images.size} images)")
            out
        }.onFailure { Log.w(TAG, "Could not unpack dotLottie bundle", it) }
            .getOrNull()
    }

    /**
     * The id of the first animation the manifest lists.
     *
     * Handles both shapes found in the wild: v2's `"animations": [ { "id": "..." } ]` and v1's
     * `"animations": [ "..." ]`. Read with a regex rather than a JSON parser because the manifest
     * is a flat, well-known document and this avoids adding a dependency for four fields.
     */
    private fun firstAnimationId(manifest: String): String? {
        val objectId = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(manifest)?.groupValues?.get(1)
        if (objectId != null) return objectId
        // v1: a bare array of id strings at the top level.
        return Regex("\"animations\"\\s*:\\s*\\[\\s*\"([^\"]+)\"").find(manifest)?.groupValues?.get(1)
    }

    /**
     * Points every image asset at the `images/` folder next to the JSON.
     *
     * Only touches the `"u"` field of assets that name a file — a vector-only animation has none,
     * and an asset whose `p` is already a data URI is left alone.
     */
    private fun rewriteImageBasePath(json: String): String =
        Regex("(\"u\"\\s*:\\s*)\"[^\"]*\"")
            .replace(json) { match -> "${match.groupValues[1]}\"$IMAGE_PREFIX\"" }

    /**
     * Reads one entry, refusing anything over [MaxEntryBytes].
     *
     * @return the bytes, or null when the entry is oversized or unreadable.
     */
    private fun readEntry(zip: ZipInputStream, entry: java.util.zip.ZipEntry): ByteArray? {
        if (entry.size > MaxEntryBytes) {
            Log.w(TAG, "Skipping oversized entry ${entry.name} (${entry.size} bytes)")
            return null
        }
        val buffer = ByteArrayOutputStream(
            entry.size.takeIf { it in 1..(1 shl 20) }?.toInt() ?: (16 * 1024)
        )
        val chunk = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = zip.read(chunk)
            if (read <= 0) break
            total += read
            // The declared size is advisory: a malicious archive can lie, so the real byte count
            // is the one that has to stay under the cap.
            if (total > MaxEntryBytes) {
                Log.w(TAG, "Entry ${entry.name} exceeded the size limit while reading")
                return null
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private const val MANIFEST_ENTRY = "manifest.json"
    private const val ANIMATION_PREFIX = "animations/"
}
