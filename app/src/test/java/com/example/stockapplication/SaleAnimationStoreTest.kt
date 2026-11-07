package com.example.stockapplication

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.stockapplication.data.DotLottie
import com.example.stockapplication.data.SaleAnimationStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Importing a Lottie animation.
 *
 * Two things matter here and both are about what happens when a pick is *wrong*:
 *
 *  - a file that is not a Lottie animation must be rejected at import time and leave nothing
 *    behind, rather than being accepted and then failing on the next receipt;
 *  - a stored name is untrusted by the time it is read back (a hand-edited backup can write it),
 *    so it must not be able to point outside the app's own folder.
 */
@RunWith(RobolectricTestRunner::class)
class SaleAnimationStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * The smallest document that is a *usable* Lottie animation.
     *
     * It needs one real layer, not just a valid envelope: Lottie happily parses an empty document
     * (or even `{}`) into a composition with no layers, which draws nothing. [SaleAnimationStore]
     * rejects exactly that, so a fixture without layers would test the rejection path while
     * pretending to test the happy one.
     */
    private val minimalAnimation = """
        {
          "v": "5.7.4",
          "fr": 30,
          "ip": 0,
          "op": 30,
          "w": 100,
          "h": 100,
          "nm": "test",
          "ddd": 0,
          "assets": [],
          "layers": [
            {
              "ddd": 0,
              "ind": 1,
              "ty": 4,
              "nm": "shape",
              "sr": 1,
              "ks": {
                "o": { "a": 0, "k": 100 },
                "r": { "a": 0, "k": 0 },
                "p": { "a": 0, "k": [50, 50, 0] },
                "a": { "a": 0, "k": [0, 0, 0] },
                "s": { "a": 0, "k": [100, 100, 100] }
              },
              "ao": 0,
              "shapes": [
                {
                  "ty": "gr",
                  "it": [
                    { "ty": "el", "p": { "a": 0, "k": [0, 0] }, "s": { "a": 0, "k": [60, 60] } },
                    { "ty": "fl", "c": { "a": 0, "k": [1, 0, 0, 1] }, "o": { "a": 0, "k": 100 } }
                  ]
                }
              ],
              "ip": 0,
              "op": 30,
              "st": 0,
              "bm": 0
            }
          ]
        }
    """.trimIndent()

    /** Writes [content] into the app's cache and returns a `file://` uri for it. */
    private fun uriFor(content: String, name: String): Uri {
        val file = File(context.cacheDir, name).apply { writeText(content) }
        return Uri.fromFile(file)
    }

    @Test
    fun `a valid animation is stored and its name returned`() = runTest {
        val uri = uriFor(minimalAnimation, "valid.json")

        val stored = SaleAnimationStore.save(context, uri)

        assertNotNull("a parsable animation should be accepted", stored)
        assertTrue("the extension should be preserved", stored!!.endsWith(".json"))
        val file = SaleAnimationStore.animationFile(context, stored)
        assertNotNull(file)
        assertTrue("the file should exist on disk", file!!.isFile)
    }

    @Test
    fun `a file that is not an animation is rejected and leaves nothing behind`() = runTest {
        val before = SaleAnimationStore.animationDir(context).listFiles()?.size ?: 0
        val uri = uriFor("{ \"not\": \"an animation\" }", "wrong.json")

        val stored = SaleAnimationStore.save(context, uri)

        assertNull("a non-animation must be refused", stored)
        // No residue: a rejected pick must not accumulate dead files in private storage. The
        // store copies first and deletes on parse failure, so the count must be unchanged.
        val after = SaleAnimationStore.animationDir(context).listFiles()?.size ?: 0
        assertEquals("the rejected copy should have been deleted", before, after)
    }

    @Test
    fun `an unreadable uri is rejected rather than throwing`() = runTest {
        val missing = Uri.fromFile(File(context.cacheDir, "does-not-exist.json"))
        assertNull(SaleAnimationStore.save(context, missing))
    }

    @Test
    fun `file names are unique per pick and keep the extension`() {
        val json = SaleAnimationStore.fileNameFor(Uri.parse("content://a/anim.json"), now = 1_000L)
        val sameMs = SaleAnimationStore.fileNameFor(Uri.parse("content://a/anim.json"), now = 1_000L)
        val zipped = SaleAnimationStore.fileNameFor(Uri.parse("content://a/anim.lottie"), now = 1_000L)

        // Two picks in the same millisecond must not collide, or the second would overwrite the
        // file the first is still being displayed from.
        assertNotEquals(json, sameMs)
        assertTrue(json.endsWith(".json"))
        // `.lottie` is a ZIP container and is read by a different parser, so the extension has to
        // survive.
        assertTrue(zipped.endsWith(".lottie"))
    }

    @Test
    fun `an unknown extension falls back to json`() {
        // A provider that reports no path still yields a usable name rather than a bare `sale-…`.
        val name = SaleAnimationStore.fileNameFor(Uri.parse("content://a/noextension"))
        assertTrue(name.endsWith(".json"))
    }

    @Test
    fun `stored names cannot escape the animation folder`() {
        listOf("../secret.json", "..\\secret.json", "a/b.json", "..", ".", "", "   ").forEach { name ->
            assertNull("expected null for \"$name\"", SaleAnimationStore.animationFile(context, name))
        }
    }

    @Test
    fun `deleting removes the file`() = runTest {
        val stored = SaleAnimationStore.save(context, uriFor(minimalAnimation, "todelete.json"))
        assertNotNull(stored)
        val file = SaleAnimationStore.animationFile(context, stored)
        assertTrue(file!!.isFile)

        SaleAnimationStore.delete(context, stored)

        assertFalse("the file should be gone", file.exists())
    }

    @Test
    fun `deleting a blank or missing name is a no-op`() {
        // Must not throw: the caller does this on the "use a built-in animation" path, where there
        // may never have been a file.
        SaleAnimationStore.delete(context, null)
        SaleAnimationStore.delete(context, "")
        SaleAnimationStore.delete(context, "not-there.json")
    }

    @Test
    fun `the loading screen keeps its animation in its own folder`() = runTest {
        // Both kinds share the copy-and-validate logic but not the directory: "reset the sale
        // animation" must not be able to take the startup screen's file with it.
        val stored = SaleAnimationStore.save(
            context,
            uriFor(minimalAnimation, "loading.json"),
            forLoadingScreen = true,
        )
        assertNotNull(stored)

        val loadingFile = SaleAnimationStore.loadingAnimationFile(context, stored)
        assertNotNull("the loading animation should resolve", loadingFile)
        assertTrue("the file should exist", loadingFile!!.isFile)

        // And it is not in the sale folder, nor resolvable through the sale accessor.
        assertNull(
            "the sale folder must not contain it",
            SaleAnimationStore.animationFile(context, stored)?.takeIf { it.isFile },
        )

        SaleAnimationStore.delete(context, stored, forLoadingScreen = true)
        assertFalse("deleting through the loading accessor should remove it", loadingFile.exists())
    }

    @Test
    fun `a non-animation is refused for the loading screen too`() = runTest {
        val before = SaleAnimationStore.loadingAnimationDir(context).listFiles()?.size ?: 0
        val stored = SaleAnimationStore.save(
            context,
            uriFor("{ \"nope\": true }", "bad-loading.json"),
            forLoadingScreen = true,
        )
        assertNull(stored)
        assertEquals(
            before,
            SaleAnimationStore.loadingAnimationDir(context).listFiles()?.size ?: 0,
        )
    }

    /** Builds a `.lottie` bundle (a ZIP) and returns a uri for it. */
    private fun bundleUri(name: String, animationJson: String): Uri {
        val file = File(context.cacheDir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{ "version": "2", "animations": [ { "id": "walk" } ] }""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("animations/walk/animation.json"))
            zip.write(animationJson.toByteArray())
            zip.closeEntry()
        }
        return Uri.fromFile(file)
    }

    @Test
    fun `a dotLottie bundle is unpacked and its animation kept`() = runTest {
        // The container itself is not playable: what has to survive is the extracted
        // animation.json, which is what `playableFile` hands to the player.
        val stored = SaleAnimationStore.save(
            context,
            bundleUri("valid.lottie", minimalAnimation),
        )

        assertNotNull("a well-formed bundle should import", stored)
        assertTrue(stored!!.endsWith(".lottie"))

        val playable = SaleAnimationStore.playableFile(context, stored)
        assertNotNull("the extracted animation should resolve", playable)
        assertTrue("the animation should be on disk", playable!!.isFile)
        assertEquals(DotLottie.ANIMATION_FILE, playable.name)
    }

    @Test
    fun `a bundle whose animation is not a Lottie is rejected and leaves nothing behind`() = runTest {
        val before = SaleAnimationStore.animationDir(context).listFiles()?.size ?: 0

        val stored = SaleAnimationStore.save(
            context,
            bundleUri("broken.lottie", "{ \"not\": \"an animation\" }"),
        )

        assertNull("an unplayable bundle must be refused", stored)
        // Neither the container nor a half-extracted folder may be left behind.
        assertEquals(
            "no residue should remain in the animation folder",
            before,
            SaleAnimationStore.animationDir(context).listFiles()?.size ?: 0,
        )
    }

    @Test
    fun `deleting a bundle import removes the extracted folder`() = runTest {
        val stored = SaleAnimationStore.save(
            context,
            bundleUri("todelete.lottie", minimalAnimation),
        )
        assertNotNull(stored)
        val playable = SaleAnimationStore.playableFile(context, stored)
        assertTrue(playable!!.isFile)

        SaleAnimationStore.delete(context, stored)

        assertFalse(
            "the whole extracted folder should go, not just the JSON",
            SaleAnimationStore.animationFile(context, stored)?.exists() ?: false,
        )
    }

    @Test
    fun `a stored animation resolves to a real path`() = runTest {
        val stored = SaleAnimationStore.save(context, uriFor(minimalAnimation, "path.json"))
        val path = SaleAnimationStore.animationFile(context, stored)?.absolutePath
        assertNotNull(path)
        assertEquals(true, File(path!!).isFile)
    }
}
