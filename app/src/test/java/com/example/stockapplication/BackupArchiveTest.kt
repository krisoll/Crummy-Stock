package com.example.stockapplication

import com.example.stockapplication.data.BackupArchive
import com.example.stockapplication.data.BackupContents
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.RestoredImages
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Covers the ZIP container only — [JsonImport] and [toJson] already have their own tests, and the
 * archive deliberately does not touch the document format.
 */
class BackupArchiveTest {

    private val json = """{"format":"stockapplication.backup","version":1}"""

    private fun photo(name: String, bytes: ByteArray = byteArrayOf(1, 2, 3)): Pair<String, File> {
        val file = File.createTempFile("img", ".jpg")
        file.deleteOnExit()
        file.writeBytes(bytes)
        return name to file
    }

    @Test
    fun `round trip keeps the json and every photo`() {
        val images = mapOf(photo("img-a.jpg"), photo("img-b.jpg"))
        val bytes = BackupArchive.write(json, images)

        val read = BackupArchive.read(bytes)

        assertEquals(json, read.json)
        assertEquals(setOf("img-a.jpg", "img-b.jpg"), read.images.keys)
        assertArrayEquals(byteArrayOf(1, 2, 3), read.images["img-a.jpg"])
    }

    @Test
    fun `archive with no photos still carries the json`() {
        val read = BackupArchive.read(BackupArchive.write(json, emptyMap()))
        assertEquals(json, read.json)
        assertTrue(read.images.isEmpty())
    }

    @Test
    fun `missing photo file is skipped rather than failing the export`() {
        val missing = File("/does/not/exist/img.jpg")
        val bytes = BackupArchive.write(json, mapOf("img-gone.jpg" to missing))
        // A backup without one photo is still worth having.
        val read = BackupArchive.read(bytes)
        assertEquals(json, read.json)
        assertTrue(read.images.isEmpty())
    }

    @Test
    fun `unknown entries are ignored`() {
        // Simulates a future version adding a manifest: an older build must not choke on it.
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { stream ->
            stream.putNextEntry(ZipEntry(BackupArchive.JSON_ENTRY))
            stream.write(json.toByteArray())
            stream.closeEntry()
            stream.putNextEntry(ZipEntry("manifest.txt"))
            stream.write("future".toByteArray())
            stream.closeEntry()
        }

        val read = BackupArchive.read(out.toByteArray())
        assertEquals(json, read.json)
        assertTrue(read.images.isEmpty())
    }

    @Test
    fun `archive without a json entry is rejected`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { stream ->
            stream.putNextEntry(ZipEntry("${BackupArchive.IMAGE_PREFIX}img.jpg"))
            stream.write(byteArrayOf(1, 2, 3))
            stream.closeEntry()
        }

        val error = runCatching { BackupArchive.read(out.toByteArray()) }.exceptionOrNull()
        assertTrue(
            "expected a message naming the missing entry, got: $error",
            error?.message?.contains(BackupArchive.JSON_ENTRY) == true
        )
    }

    @Test
    fun `entry names cannot escape the images folder`() {
        // A hand-edited archive may try to path-traverse. The writer and reader both flatten to a
        // basename, so what lands on disk is always inside product_images.
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { stream ->
            stream.putNextEntry(ZipEntry(BackupArchive.JSON_ENTRY))
            stream.write(json.toByteArray())
            stream.closeEntry()
            stream.putNextEntry(ZipEntry("${BackupArchive.IMAGE_PREFIX}../../evil.jpg"))
            stream.write(byteArrayOf(9))
            stream.closeEntry()
        }

        val read = BackupArchive.read(out.toByteArray())
        assertEquals(setOf("evil.jpg"), read.images.keys)
    }

    @Test
    fun `isArchive matches only zip names`() {
        assertTrue(BackupArchive.isArchive("stock-backup-2025-01-01-1200.zip"))
        assertTrue(BackupArchive.isArchive("BACKUP.ZIP"))
        assertTrue(BackupArchive.isArchive("  spaced.zip  "))
        // An old plain backup must keep taking the JSON path.
        assertEquals(false, BackupArchive.isArchive("stock-backup-2025-01-01-1200.json"))
        assertEquals(false, BackupArchive.isArchive("notes.txt"))
        assertEquals(false, BackupArchive.isArchive(""))
    }

    @Test
    fun `file name carries the timestamp and the zip extension`() {
        // compared against the same formatter the writer uses rather than a literal date: the
        // stamp is formatted in the default timezone, so hard-coding "1970-01-01" for epoch 0
        // would fail anywhere west of UTC.
        val expected = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US)
            .format(java.util.Date(1_700_000_000_000L))
        assertEquals(
            "stock-backup-$expected.zip",
            BackupArchive.archiveFileName(1_700_000_000_000L)
        ) // actual second, expected first
    }

    @Test
    fun `products point at restored photos and drop the ones that were not`() {
        val contents = BackupContents(
            schemaVersion = 1,
            exportedAt = 0L,
            categories = emptyList(),
            products = listOf(
                Product(1, "a", "has photo", 0, 1, imagePath = "img-a.jpg"),
                Product(2, "b", "photo missing", 0, 1, imagePath = "img-gone.jpg"),
                Product(3, "c", "never had one", 0, 1, imagePath = null),
            ),
            orders = emptyList(),
            lines = emptyList(),
            notes = emptyList(),
        )

        val applied = RestoredImages.apply(contents, mapOf("img-a.jpg" to "img-a.jpg"))

        assertEquals("img-a.jpg", applied.products[0].imagePath)
        // Pointing at a file that was never written would claim a photo exists while the list
        // showed the placeholder.
        assertNull(applied.products[1].imagePath)
        assertNull(applied.products[2].imagePath)
    }

    @Test
    fun `no restored photos clears every reference`() {
        val contents = BackupContents(
            schemaVersion = 1,
            exportedAt = 0L,
            categories = emptyList(),
            products = listOf(
                Product(1, "a", "p", 0, 1, imagePath = "img-a.jpg"),
            ),
            orders = emptyList(),
            lines = emptyList(),
            notes = emptyList(),
        )

        assertNull(RestoredImages.apply(contents, emptyMap()).products[0].imagePath)
    }

    @Test
    fun `archive is a valid zip readable by the platform`() {
        // Guards against writing something only our own reader happens to parse.
        val bytes = BackupArchive.write(json, mapOf(photo("img-a.jpg")))
        val entries = ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertEquals(
            listOf(BackupArchive.JSON_ENTRY, "${BackupArchive.IMAGE_PREFIX}img-a.jpg"),
            entries
        )
    }
}
