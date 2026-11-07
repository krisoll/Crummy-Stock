package com.example.stockapplication

import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.LoadingIcon
import com.example.stockapplication.data.LoadingImage
import com.example.stockapplication.data.LoadingIconLottieId
import com.example.stockapplication.data.LoadingImageStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the rule that decides which startup image is drawn.
 *
 * The interesting case is the interaction between the two persisted fields: a stored file name and
 * an icon id. Getting the precedence wrong either ignores the user's picture or makes it
 * impossible to ever go back to an icon.
 */
class LoadingImageTest {

    @Test
    fun noStoredFileMeansTheChosenIcon() {
        val image = LoadingImage.from(iconId = LoadingIcon.STORE.id, imageFileName = null)
        assertEquals(LoadingImage.BuiltIn(LoadingIcon.STORE), image)
    }

    @Test
    fun aStoredFileWinsOverTheIcon() {
        val image = LoadingImage.from(
            iconId = LoadingIcon.STORE.id,
            imageFileName = "startup-abc-1.png",
        )
        assertEquals(LoadingImage.Picked("startup-abc-1.png"), image)
    }

    @Test
    fun blankAndNullFileNamesFallBackToTheBundledAnimation() {
        // DataStore can hand back an empty string after a hand-edited or partially cleared store.
        // With no icon chosen that is the untouched-install case, which now means the bundled
        // animation rather than the old cart icon.
        listOf(null, "", "   ").forEach { name ->
            assertEquals(
                LoadingImage.Lottie(null),
                LoadingImage.from(iconId = null, imageFileName = name),
            )
        }
    }

    @Test
    fun removingThePictureRestoresTheIconRatherThanTheDefault() {
        val settings = AppSettings(
            loadingIconId = LoadingIcon.RECEIPT.id,
            loadingImageFileName = null,
        )
        // The icon must still be remembered, so clearing a picture is not a one-way door.
        assertEquals(LoadingImage.BuiltIn(LoadingIcon.RECEIPT), settings.loadingImage)
    }

    @Test
    fun storedFileNameIsTrimmed() {
        val image = LoadingImage.from(iconId = null, imageFileName = "  startup-a-1.png  ")
        assertEquals(image, LoadingImage.Picked("startup-a-1.png"))
    }

    @Test
    fun defaultSettingsDrawTheBundledAnimation() {
        // The upgrade rule: an install that never touched the startup screen stores "cart", and
        // must follow the new default rather than being left on the old icon forever.
        assertEquals(LoadingImage.Lottie(null), AppSettings().loadingImage)
        assertNull(AppSettings().loadingImageFileName)
    }

    @Test
    fun aDeliberateIconChoiceIsNotUpgraded() {
        // Anything other than the old default was picked on purpose and stays put.
        val settings = AppSettings(loadingIconId = LoadingIcon.STORE.id)
        assertEquals(LoadingImage.BuiltIn(LoadingIcon.STORE), settings.loadingImage)
    }

    @Test
    fun aPickedPictureStillBeatsEverything() {
        // Even with the Lottie id stored, a picture the user chose is what they expect to see.
        val settings = AppSettings(
            loadingIconId = LoadingIconLottieId,
            loadingImageFileName = "startup-abc-1.png",
            loadingLottieFileName = "anim-xyz-1.json",
        )
        assertEquals(LoadingImage.Picked("startup-abc-1.png"), settings.loadingImage)
    }

    @Test
    fun anImportedAnimationBeatsTheBundledOne() {
        val settings = AppSettings(
            loadingIconId = LoadingIconLottieId,
            loadingLottieFileName = "anim-xyz-1.json",
        )
        assertEquals(LoadingImage.Lottie("anim-xyz-1.json"), settings.loadingImage)
    }

    @Test
    fun selectingLottieWithNoFileMeansTheBundledAnimation() {
        // What "Use an animation" writes when the user has not imported their own: the id alone,
        // and `null` resolves to the resource shipped in the APK.
        val settings = AppSettings(loadingIconId = LoadingIconLottieId)
        assertEquals(LoadingImage.Lottie(null), settings.loadingImage)
    }

    @Test
    fun fileNamesAreUniquePerPick() {
        // Two picks in the same millisecond must not collide, or the second would overwrite the
        // file the first is still being displayed from.
        val a = LoadingImageStore.fileNameFor(now = 1_000L)
        val b = LoadingImageStore.fileNameFor(now = 1_000L)
        assertNotEquals(a, b)
        assertTrue(a.endsWith(".png"))
    }

    @Test
    fun fileNamesNeverEscapeTheirFolder() {
        // A stored name is untrusted by the time it is read back; these must resolve to nothing.
        // Same guarded-name logic as ProductImages, which rejects separators and dot-dot so a
        // hand-edited preference cannot point the app at an arbitrary file.
        listOf("../secret.png", "..\\secret.png", "a/b.png", "..", ".", "").forEach { name ->
            assertNull("expected null for \"$name\"", rejectedName(name.trim()))
        }
    }

    /** Mirrors the guard in LoadingImageStore.imageFile, which needs a Context to call directly. */
    private fun rejectedName(name: String): String? =
        if (name.isEmpty() ||
            name.contains('/') ||
            name.contains('\\') ||
            name == "." ||
            name == ".."
        ) {
            null
        } else {
            name
        }
}
