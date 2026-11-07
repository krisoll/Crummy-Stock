package com.example.stockapplication.data

/** Suffix used when no folder has been chosen. */
const val DefaultExportFolderLabel = "Downloads"

/**
 * Turns a SAF tree URI into the folder's display name.
 *
 * Tree URIs look like `content://com.android.externalstorage.documents/tree/primary%3AStock`
 * (Storage Access Framework) or `.../tree/1A2B-3C4D%3APictures%2FStock` (SD card). Both the `:`
 * and `/` separators are percent-encoded, so the value must be decoded before splitting.
 *
 * Written without `android.net.Uri` so it stays a pure function and is unit-testable on the JVM.
 * Returns null when nothing useful can be extracted, so callers can fall back to a default label.
 */
fun exportFolderDisplayName(treeUri: String?): String? {
    if (treeUri.isNullOrBlank()) return null
    val decoded = percentDecode(treeUri)

    // Everything after the last "/tree/" is "primary:Stock" or "1A2B-3C4D:Pictures/Stock".
    val tailStart = decoded.lastIndexOf("/tree/")
    val path = if (tailStart >= 0) decoded.substring(tailStart + "/tree/".length) else decoded

    val withoutVolume = path.substringAfter(':', path)
    val lastSegment = withoutVolume.trimEnd('/').substringAfterLast('/').trim()
    return lastSegment.ifBlank { null }
}

/** Minimal percent-decoding; returns the input unchanged when a sequence is malformed. */
private fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val out = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val c = value[index]
        if (c == '%' && index + 2 < value.length) {
            val hex = value.substring(index + 1, index + 3)
            val code = hex.toIntOrNull(16)
            if (code != null) {
                out.append(code.toChar())
                index += 3
                continue
            }
        }
        out.append(c)
        index++
    }
    return out.toString()
}
