package com.example.stockapplication

import com.example.stockapplication.data.DotLottie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unpacking a dotLottie bundle.
 *
 * A `.lottie` is a ZIP of `manifest.json` + `animations/<id>/animation.json` + `images/`. The old
 * import path fed it to `fromZipStreamSync`, which expects a gzipped Lottie JSON and would happily
 * parse the manifest as if it were an animation — so these tests pin the layout the reader must
 * actually understand.
 *
 * The archives are built here rather than checked in as fixtures: a real `.lottie` would be opaque
 * binary in the repo, and building them makes each test say exactly which part of the format it is
 * exercising.
 */
@RunWith(RobolectricTestRunner::class)
class DotLottieTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A minimal Lottie document that a player would accept. */
    private val animation = """
        {
          "v": "5.7.4", "fr": 30, "ip": 0, "op": 30, "w": 100, "h": 100,
          "layers": [ { "ind": 1, "ty": 4, "nm": "shape", "ip": 0, "op": 30 } ]
        }
    """.trimIndent()

    /** Writes a ZIP from a name→contents map and returns it. */
    private fun bundle(vararg entries: Pair<String, String>): File {
        val file = temp.newFile("test.lottie")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file
    }

    private val v2Manifest = """
        { "version": "2", "animations": [ { "id": "walk", "loop": true } ] }
    """.trimIndent()

    @Test
    fun `a well-formed bundle extracts its animation and images`() {
        val source = bundle(
            "manifest.json" to v2Manifest,
            "animations/walk/animation.json" to animation,
            "images/arm.png" to "PNGDATA",
        )
        val out = temp.newFolder("out")

        val extracted = DotLottie.extract(source, out)

        assertNotNull("a valid bundle should extract", extracted)
        assertEquals(DotLottie.ANIMATION_FILE, extracted!!.name)
        assertTrue("the animation should be on disk", extracted.isFile)
        assertTrue(extracted.readText().contains("\"layers\""))
        // The image lands beside the JSON, where the rewritten `u` path points.
        assertTrue(File(out, "images/arm.png").isFile)
    }

    @Test
    fun `the image base path is rewritten to the images folder`() {
        // A bundle whose JSON points somewhere other than `images/` — the reader has to normalise
        // it, or the extracted animation would render without its assets.
        val withAssets = """
            {
              "v": "5.7.4", "fr": 30, "ip": 0, "op": 30, "w": 100, "h": 100,
              "assets": [ { "id": "a1", "w": 10, "h": 10, "u": "./", "p": "arm.png" } ],
              "layers": [ { "ind": 1, "ty": 4, "ip": 0, "op": 30 } ]
            }
        """.trimIndent()
        val source = bundle(
            "manifest.json" to v2Manifest,
            "animations/walk/animation.json" to withAssets,
            "images/arm.png" to "PNGDATA",
        )
        val out = temp.newFolder("out")

        val extracted = DotLottie.extract(source, out)

        assertNotNull(extracted)
        assertTrue(
            "the asset base path should point at the extracted images folder",
            extracted!!.readText().contains("\"u\": \"images/\""),
        )
    }

    @Test
    fun `the first animation in the manifest wins`() {
        val twoAnimations = """
            { "version": "2", "animations": [ { "id": "first" }, { "id": "second" } ] }
        """.trimIndent()
        val source = bundle(
            "manifest.json" to twoAnimations,
            "animations/first/animation.json" to animation.replace("shape", "first-shape"),
            "animations/second/animation.json" to animation.replace("shape", "second-shape"),
        )
        val out = temp.newFolder("out")

        val extracted = DotLottie.extract(source, out)

        assertNotNull(extracted)
        assertTrue(
            "the settings hold one animation, so the manifest's first entry is the one to keep",
            extracted!!.readText().contains("first-shape"),
        )
    }

    @Test
    fun `a bundle with no manifest still finds an animation`() {
        // Some exporters emit the directory layout without the manifest. Refusing those would be
        // pedantic when the animation is sitting right there.
        val source = bundle("animations/solo/animation.json" to animation)
        val out = temp.newFolder("out")

        val extracted = DotLottie.extract(source, out)

        assertNotNull("a manifest-less bundle should still extract", extracted)
        assertTrue(extracted!!.readText().contains("\"layers\""))
    }

    @Test
    fun `a manifest naming a missing animation falls back to the entry present`() {
        val source = bundle(
            "manifest.json" to """{ "animations": [ { "id": "gone" } ] }""",
            "animations/actual/animation.json" to animation,
        )
        val out = temp.newFolder("out")

        // The manifest is authoritative when it resolves; when it does not, the file that *is*
        // there beats failing the whole import.
        val extracted = DotLottie.extract(source, out)
        assertNotNull(extracted)
    }

    @Test
    fun `a zip with no animation is rejected`() {
        val source = bundle(
            "manifest.json" to v2Manifest,
            "readme.txt" to "not an animation",
        )
        val out = temp.newFolder("out")

        assertNull("nothing playable means nothing extracted", DotLottie.extract(source, out))
    }

    @Test
    fun `a file that is not a zip is rejected rather than throwing`() {
        // The picker filters by mime type, but a provider can report anything, and a `.lottie`
        // named file may be plain JSON in the wild.
        val notAZip = temp.newFile("fake.lottie").apply { writeText("just some text") }
        val out = temp.newFolder("out")

        assertNull(DotLottie.extract(notAZip, out))
    }

    @Test
    fun `a missing file is rejected`() {
        assertNull(DotLottie.extract(File(temp.root, "nope.lottie"), temp.newFolder("out")))
    }
}
