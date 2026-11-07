package com.example.stockapplication.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.airbnb.lottie.LottieCompositionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Stores the user's imported Lottie animation in the app's private files directory.
 *
 * ## Why the file is copied in
 *
 * The document picker hands back a short-lived read grant, so storing the `content://` URI would
 * leave the receipt animation broken a few hours after it was chosen — and the failure would only
 * appear on the *next sale*, which is the worst place to discover it. The bytes are copied into
 * `files/sale_animations/` at pick time and only the file **name** is persisted, for the same
 * reason as every other picked file: the app's data directory moves between installs.
 *
 * ## Why the file is parsed before it is kept
 *
 * [save] hands the copied bytes to [LottieCompositionFactory] and throws the file away if it will
 * not parse. Accepting anything with a `.json` extension and finding out later means a receipt
 * with a blank hole where the flourish should be — a much worse failure than a refused import,
 * because it appears mid-transaction rather than at the moment of the mistake.
 *
 * ## Two folders, one store
 *
 * The same copy-and-validate logic serves the **sale flourish** and the **startup screen**, kept
 * in separate directories (`forLoadingScreen`) because the two are reset by different controls —
 * one shared folder would let "reset the sale animation" take the startup screen's file with it.
 *
 * Everything here reports `null` rather than throwing: a missing or corrupt animation must never
 * stop a sale from being recorded.
 */
object SaleAnimationStore {

    private const val TAG = "SaleAnimationStore"
    private const val FOLDER = "sale_animations"

    /**
     * Startup-screen animations get their own folder.
     *
     * Same store, same validation, different directory: the two are shown in different places and
     * cleared by different resets, so mixing them would make "reset the sale animation" either
     * miss its own files or take the startup screen's with it.
     */
    private const val LOADING_FOLDER = "loading_animations"

    /** Largest file accepted, in bytes. */
    const val MAX_BYTES = 8L * 1024 * 1024

    /** A fresh name per pick, so the previous file is never overwritten while still displayed. */
    private val counter = AtomicLong(0L)

    fun animationDir(context: Context): File =
        File(context.applicationContext.filesDir, FOLDER).apply { mkdirs() }

    fun loadingAnimationDir(context: Context): File =
        File(context.applicationContext.filesDir, LOADING_FOLDER).apply { mkdirs() }

    /**
     * Resolves a stored name to its file, rejecting anything that would escape the folder.
     *
     * The name comes from DataStore, which a hand-edited backup could have written, so `../` has
     * to be refused rather than trusted.
     */
    fun animationFile(context: Context, fileName: String?): File? =
        safeFileIn(animationDir(context), fileName)

    /** The startup-screen equivalent of [animationFile]. */
    fun loadingAnimationFile(context: Context, fileName: String?): File? =
        safeFileIn(loadingAnimationDir(context), fileName)

    /**
     * The file a player should actually open, for either format.
     *
     * A `.json` import *is* the playable file. A `.lottie` import is a folder holding
     * `animation.json` plus its `images/` — the player needs the document inside it, not the
     * container, so the path is resolved one level down. Null means "nothing playable here", which
     * is what every caller already treats as a reason to fall back.
     */
    fun playableFile(context: Context, fileName: String?, forLoadingScreen: Boolean = false): File? {
        // A `.lottie` names its container, which no longer exists; the document to play lives in
        // the extracted folder beside it.
        extractedFolder(context, fileName, forLoadingScreen)?.let { folder ->
            return File(folder, DotLottie.ANIMATION_FILE).takeIf { it.isFile }
        }

        val stored = if (forLoadingScreen) {
            loadingAnimationFile(context, fileName)
        } else {
            animationFile(context, fileName)
        } ?: return null

        return if (stored.isDirectory) {
            File(stored, DotLottie.ANIMATION_FILE).takeIf { it.isFile }
        } else {
            stored.takeIf { it.isFile }
        }
    }

    /**
     * The folder a `.lottie` import was unpacked into.
     *
     * The stored name is the *container's* (`anim-x.lottie`), which is deleted after unpacking, so
     * the extracted folder beside it is derived rather than stored. Keeping the name stable this
     * way means the settings never have to distinguish "a .json" from "a .lottie" — every caller
     * persists one string either way.
     */
    fun extractedFolder(context: Context, fileName: String?, forLoadingScreen: Boolean = false): File? {
        val container = if (forLoadingScreen) {
            loadingAnimationFile(context, fileName)
        } else {
            animationFile(context, fileName)
        } ?: return null
        if (!fileName.orEmpty().endsWith(".lottie")) return null
        val dir = container.parentFile ?: return null
        return File(dir, "${container.name.removeSuffix(".lottie")}.dir")
    }

    private fun safeFileIn(dir: File, fileName: String?): File? {
        val name = fileName?.trim().orEmpty()
        if (name.isEmpty()) return null
        if (name.contains('/') || name.contains('\\') || name == "." || name == "..") return null
        return File(dir, name)
    }

    /**
     * Copies and validates the animation behind [uri].
     *
     * @return the stored file name, or null when the document could not be read, was too large, or
     *   is not a Lottie animation this build can play.
     */
    suspend fun save(
        context: Context,
        uri: Uri,
        /** Which animation this is: the sale flourish, or the startup screen. */
        forLoadingScreen: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = fileNameFor(uri, now)
            val dir = if (forLoadingScreen) {
                loadingAnimationDir(context)
            } else {
                animationDir(context)
            }
            val target = File(dir, fileName)

            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    // Bounded copy: a picked file is arbitrary user data, and an 80 MB "animation"
                    // would be read into memory by the parser below.
                    val buffer = ByteArray(8 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_BYTES) return@use false
                        output.write(buffer, 0, read)
                    }
                    true
                }
            } ?: false

            if (!copied) {
                target.delete()
                return@runCatching null
            }

            // A `.lottie` is a ZIP *bundle*, not a compressed JSON: it has to be unpacked into a
            // folder before any parser can read it. The container itself is then deleted, so what
            // is kept is exactly the extracted animation and its images.
            val isBundle = fileName.endsWith(".lottie")
            val playable: File = if (isBundle) {
                val folder = File(dir, "${fileName.removeSuffix(".lottie")}.dir")
                val extracted = DotLottie.extract(source = target, targetDir = folder)
                // The container has served its purpose either way; only the extracted folder is
                // kept when the unpack succeeded.
                target.delete()
                if (extracted == null) {
                    folder.deleteRecursively()
                    return@runCatching null
                }
                extracted
            } else {
                target
            }

            // Parsed from the *stored* file rather than the stream: this validates the exact bytes
            // that will be played later, so a partial copy cannot pass and then fail on the receipt.
            //
            // `fromJsonInputStreamSync` rather than the task-based form: this already runs on
            // `Dispatchers.IO` inside a suspend function, so handing the work to another thread
            // would only add a hop and make the failure path async for no gain.
            val parsed = runCatching {
                LottieCompositionFactory.fromJsonInputStreamSync(
                    playable.inputStream(),
                    // Cache key: the file name, unique per pick, so a replaced animation is never
                    // served from the previous one's cache entry.
                    fileName,
                )
            }.getOrNull()

            // Two checks, because Lottie alone is not strict enough: it parses `{}` and even
            // `{"not":"an animation"}` into an empty composition with no layers rather than
            // failing. Without the content check a random .json would be accepted and then draw an
            // empty box on the receipt — exactly the failure this validation exists to prevent.
            val composition = parsed?.value
            val hasContent = composition != null &&
                composition.layers.isNotEmpty() &&
                composition.duration > 0f
            if (!hasContent) {
                target.delete()
                // Delete the *folder* for a bundle, not just the document inside it: the images
                // beside it would otherwise accumulate in private storage on every bad import.
                if (isBundle) {
                    playable.parentFile?.deleteRecursively()
                } else {
                    playable.delete()
                }
                return@runCatching null
            }
            fileName
        }.onFailure { Log.w(TAG, "Could not import sale animation", it) }
            .getOrNull()
    }

    /**
     * Deletes a stored animation. Missing files and blank names are ignored.
     *
     * Recursive: a `.lottie` import is a folder holding the animation and its images, and leaving
     * that behind would leak a directory on every replace.
     */
    fun delete(context: Context, fileName: String?, forLoadingScreen: Boolean = false) {
        val file = if (forLoadingScreen) {
            loadingAnimationFile(context, fileName)
        } else {
            animationFile(context, fileName)
        }
        // The extracted folder for a `.lottie` import, resolved before the name stops meaning
        // anything. Recursive, because it holds the images too.
        val folder = extractedFolder(context, fileName, forLoadingScreen)
        runCatching {
            file?.deleteRecursively()
            folder?.deleteRecursively()
        }.onFailure { Log.w(TAG, "Could not delete animation", it) }
    }

    /**
     * A unique name preserving the picked file's extension.
     *
     * The extension is kept because `.lottie` is a ZIP container while `.json` is a document, and
     * the two are read differently even when the content is otherwise the same animation.
     */
    fun fileNameFor(uri: Uri, now: Long = System.currentTimeMillis()): String {
        val extension = (uri.lastPathSegment.orEmpty().substringAfterLast('.', ""))
            .lowercase()
            .takeIf { it == "json" || it == "lottie" }
            ?: "json"
        return "anim-${now.toString(36)}-${counter.incrementAndGet().toString(36)}.$extension"
    }
}
