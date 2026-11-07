package com.example.stockapplication.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File

/** Where an export was written, plus a way to share/open it. */
data class ExportResult(val uri: Uri, val fileName: String, val mimeType: String)

/**
 * Writes CSVs and QR label images to either the shared Downloads folder or a folder the user
 * picked from the configuration tab.
 *
 * - **Custom folder (SAF tree URI):** written through [DocumentFile], so no storage permission is
 *   needed on any supported API level and the user controls the location.
 * - **Default:** MediaStore on Android 10+, keeping files out of app-private storage.
 * - **Android 9 and below, default:** app-specific external storage shared via FileProvider.
 */
object ExportFiles {

    private const val TAG = "ExportFiles"

    private const val MIME_CSV = "text/csv"
    private const val MIME_PNG = "image/png"
    private const val MIME_PDF = "application/pdf"
    private const val MIME_JSON = "application/json"

    /**
     * MIME type recorded for a ZIP backup.
     *
     * The canonical `application/zip`, and it has to stay that way: [writeToDocumentTree] passes
     * this to `DocumentFile.createFile`, and a provider will refuse to create a document whose
     * declared type it cannot map to an extension. Reporting `application/octet-stream` here made
     * every ZIP export silently miss the user's chosen folder and land in Downloads instead.
     */
    private const val MIME_ZIP = "application/zip"

    /**
     * Ceiling for a file the user asks to import. A full backup of this app is a few hundred
     * kilobytes; anything past this is not a backup and should not be read into memory.
     */
    private const val MaxImportBytes = 32L * 1024 * 1024

    fun writePdf(
        context: Context,
        fileName: String,
        bytes: ByteArray,
        folderUri: String? = null,
    ): ExportResult = writeBytes(context, fileName, bytes, MIME_PDF, folderUri)

    /**
     * Writes a JSON document (currently only the full database backup).
     *
     * Shares the same destination rules as the other writers: the configured folder when there is
     * one, Downloads otherwise.
     */
    fun writeJson(
        context: Context,
        fileName: String,
        content: String,
        folderUri: String? = null,
    ): ExportResult = writeBytes(
        context = context,
        fileName = fileName,
        bytes = content.toByteArray(Charsets.UTF_8),
        mimeType = MIME_JSON,
        folderUri = folderUri,
    )

    /**
     * Writes a ZIP backup (JSON plus product photos) to the same destinations as every other export.
     *
     * Takes bytes rather than a [java.io.File] so the archive is built by
     * [BackupArchive.write] and this stays a plain "put these bytes somewhere the user can find"
     * helper, with no opinion about the archive's contents.
     */
    fun writeArchive(
        context: Context,
        fileName: String,
        bytes: ByteArray,
        folderUri: String? = null,
    ): ExportResult = writeBytes(
        context = context,
        fileName = fileName,
        bytes = bytes,
        mimeType = MIME_ZIP,
        folderUri = folderUri,
    )

    /**
     * Reads a JSON document the user picked — the counterpart to [writeJson].
     *
     * Goes through the [android.content.ContentResolver] rather than [java.io.File] because the
     * system file picker hands back a `content://` URI, which may point at cloud storage the app
     * has no path for. The read grant from the picker is enough; nothing is persisted, since the
     * picked file is consumed immediately.
     *
     * Refuses implausibly large files up front, because reading an arbitrary file into a [String]
     * is how an "import" turns into an OOM.
     *
     * @throws java.io.IOException when the URI cannot be opened, or the file is too large.
     */
    /**
     * The human-readable file name behind [uri], for showing in a confirmation dialog.
     *
     * Falls back to the last path segment and then to the URI itself, because a provider is
     * allowed to return null from `DISPLAY_NAME` and an empty dialog line would be worse than a
     * technical one.
     */
    fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: uri.toString()
    }

    fun readJson(context: Context, uri: Uri): String {
        val bytes = readBytes(context, uri, MaxImportBytes)
        // Decoding as UTF-8 explicitly, not through the platform default: the writer emits UTF-8
        // and a differently-encoded read would mangle every non-ASCII product name.
        return bytes.toString(Charsets.UTF_8)
    }

    /**
     * Reads a ZIP backup the user picked, as raw bytes.
     *
     * Separate from [readJson] only in its ceiling: an archive carries photos, so it is allowed to
     * be much larger. The size is checked against the descriptor *and* the actual read, because a
     * provider is free to report an unknown or dishonest length.
     */
    fun readArchive(context: Context, uri: Uri): ByteArray =
        readBytes(context, uri, BackupArchive.MaxArchiveBytes)

    /**
     * Reads the whole content behind [uri], refusing anything past [limit].
     *
     * Reading an arbitrary file into memory is how an "import" turns into an OOM, so the guard is
     * shared by every importer rather than repeated in each one.
     */
    private fun readBytes(context: Context, uri: Uri, limit: Long): ByteArray {
        val resolver = context.contentResolver
        val declared = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (declared > limit) {
            error("File is too large to be a backup (${declared / 1024 / 1024} MB)")
        }
        val bytes = resolver.openInputStream(uri)?.use { stream ->
            stream.readBytes()
        } ?: error("Could not open the selected file")
        if (bytes.size > limit) {
            error("File is too large to be a backup (${bytes.size / 1024 / 1024} MB)")
        }
        return bytes
    }

    fun writeCsv(
        context: Context,
        fileName: String,
        content: String,
        folderUri: String? = null,
    ): ExportResult = writeBytes(
        context = context,
        fileName = fileName,
        bytes = content.toByteArray(Charsets.UTF_8),
        mimeType = MIME_CSV,
        folderUri = folderUri,
    )

    fun writePng(
        context: Context,
        fileName: String,
        bitmap: Bitmap,
        folderUri: String? = null,
    ): ExportResult {
        val bytes = java.io.ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        return writeBytes(context, fileName, bytes, MIME_PNG, folderUri)
    }

    private fun writeBytes(
        context: Context,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        folderUri: String?,
    ): ExportResult {
        if (folderUri != null) {
            writeToDocumentTree(context, Uri.parse(folderUri), fileName, bytes, mimeType)
                ?.let { return ExportResult(it, fileName, mimeType) }
            // Folder was deleted or permission was revoked: fall through to Downloads rather than
            // failing the export outright.
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create $fileName")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Could not open $fileName for writing")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
            return ExportResult(uri, fileName, mimeType)
        }

        // Legacy path: app-private external storage, shared via FileProvider.
        val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return ExportResult(uri, fileName, mimeType)
    }

    /** Returns the new document's URI, or null if the tree could not be written to. */
    private fun writeToDocumentTree(
        context: Context,
        treeUri: Uri,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
    ): Uri? = runCatching {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
        if (tree == null) {
            Log.w(TAG, "Selected folder is no longer reachable; using Downloads")
            return@runCatching null
        }
        if (!tree.canWrite()) {
            Log.w(TAG, "Selected folder is not writable; using Downloads")
            return@runCatching null
        }
        // Replace a previous export of the same day rather than creating "file (1)" copies.
        tree.findFile(fileName)?.delete()
        // DocumentFile derives the extension from the MIME type, and several providers do not
        // know "application/json", creating the document without a suffix. The explicit name is
        // preferred; the fallback only runs when the provider returned a name lacking the suffix.
        //
        // A provider refuses to create a document whose MIME type it cannot map, and returns null
        // rather than throwing — which silently sent every such export to Downloads instead of the
        // folder the user chose. That is logged, not swallowed, so the next occurrence is visible.
        val document = tree.createFile(mimeType, fileName)
        if (document == null) {
            Log.w(
                TAG,
                "Folder would not create \"$fileName\" as $mimeType; using Downloads instead"
            )
            return@runCatching null
        }
        if (!document.name.orEmpty().endsWith(fileName.substringAfterLast('.'), ignoreCase = true)) {
            runCatching { document.renameTo(fileName) }
        }
        val stream = context.contentResolver.openOutputStream(document.uri)
        if (stream == null) {
            Log.w(TAG, "Could not open \"$fileName\" for writing; using Downloads instead")
            // A document with no bytes written would show up in the user's folder as an empty
            // file, so it is removed before falling back.
            runCatching { document.delete() }
            return@runCatching null
        }
        stream.use { it.write(bytes) }
        document.uri
    }.onFailure {
        Log.w(TAG, "Could not write \"$fileName\" to the selected folder; using Downloads", it)
    }.getOrNull()

    /** True when [treeUri] still points at a writable folder. */
    fun canWriteTo(context: Context, treeUri: String): Boolean = runCatching {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return false
        tree.exists() && tree.isDirectory && tree.canWrite()
    }.getOrDefault(false)

    /**
     * Persists read/write permission for a folder the user picked. Without this the grant only
     * lasts until the process dies.
     */
    fun persistFolderPermission(context: Context, treeUri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        true
    }.getOrDefault(false)

    /** Fires the system share sheet for a previously written export. */
    fun share(context: Context, result: ExportResult) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = result.mimeType
            putExtra(Intent.EXTRA_STREAM, result.uri)
            putExtra(Intent.EXTRA_TITLE, result.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Share ${result.fileName}")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    /** Opens the export in whatever app can handle it (spreadsheet, gallery, print...). */
    fun open(context: Context, result: ExportResult) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(result.uri, result.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { share(context, result) }
    }
}
