package com.example.stockapplication.data

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.stockapplication.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A colour the user picked for the app theme, stored as an ARGB int. */
typealias ArgbColor = Long

const val DefaultFontFamilyId = "default"

/**
 * The ink the app falls back to when no theme supplies one.
 *
 * Written as a real value rather than left as "no override" (a `null` colour / a removed key).
 * Leaving the text channel unset does **not** mean "use the default ink" — [resolveColorScheme]
 * reads it as "keep whatever the base palette's `onSurface` happens to be", and once a theme has
 * installed its own panel that is no longer the palette the user is looking at. It also leaves the
 * stored ARGB behind as a latent trap: switching the override back on later re-applies the *old*
 * theme's colour. Both are how Midnight's near-white ink ended up on a pale page, with every label
 * on it invisible.
 */
const val DefaultTextArgb: ArgbColor = 0xFF000000

/**
 * The five colour channels the user can override, named so "is this customised?" can be asked of
 * one of them at a time without five near-identical functions.
 */
enum class ColorChannel { BACKGROUND, BUTTON, PANEL, TEXT, HIGHLIGHT }

/** Upper bound for the flat card fee: 1000.00 in the shop's currency. */
const val MaxCardFeeFixedCents: Long = 100_000L

/**
 * Upper bound for a promo's fixed-amount discount: 10,000.00.
 *
 * Deliberately **not** [MaxCardFeeFixedCents]. The promo editor reused the card-fee parser, whose
 * 1000.00 cap silently rewrote any larger discount — a promo meant to take 5,000 off a basket was
 * stored and applied as 1,000, and with a per-item threshold that zeroed every price in the cart.
 * A discount and a card fee are unrelated quantities and should not share a ceiling.
 *
 * Still bounded, because an unbounded field can overflow the arithmetic that applies it and would
 * let a mistyped amount take an entire basket to zero.
 */
const val MaxPromoDiscountCents: Long = 1_000_000L

/**
 * Longest note that can be attached to a sale. Generous for a name or tab reference, but bounded
 * so the field can never be used as bulk storage and stays readable on the history row.
 */
const val MaxOrderNoteLength: Int = 120

/** App language choices. An empty [tag] means "follow the system language". */
enum class AppLanguage(val tag: String, @param:StringRes val labelRes: Int) {
    SYSTEM("", R.string.language_system),
    ENGLISH("en", R.string.language_english),
    SPANISH("es", R.string.language_spanish),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

const val DefaultMotionSpeedId = "normal"

/**
 * How quickly the app's transitions run. [OFF] hands out zero-duration specs instead of
 * removing the transitions, so the UI tree keeps the same shape either way.
 */
enum class MotionSpeed(val id: String, val multiplier: Float, @param:StringRes val labelRes: Int) {
    OFF("off", 0f, R.string.settings_motion_off),
    FAST("fast", 0.6f, R.string.settings_motion_fast),
    NORMAL("normal", 1f, R.string.settings_motion_normal),
    SLOW("slow", 1.6f, R.string.settings_motion_slow),
    ;

    /** Stretches or compresses a base duration (in millis) to match this speed. */
    fun scale(baseMillis: Int): Int = (baseMillis * multiplier).toInt()

    companion object {
        fun fromId(id: String?): MotionSpeed = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

const val DefaultTextSizeId = "normal"

const val DefaultInventoryViewModeId = "list"

/**
 * How the inventory tab draws its products.
 *
 * Two views of the *same* data, not two datasets: switching changes only the layout, and every
 * filter, search and tag selection carries across unchanged. That matters because the switcher sits
 * in the header beside those controls, so a user expects it to be a display choice rather than a
 * navigation move.
 *
 * [LIST] is the original flat list with category headers inline — kept as the default so the app
 * does not rearrange itself under anyone who upgrades. [FOLDERS] groups products behind a category
 * card you open, which is the better shape once a shop has more than a screenful of products.
 */
enum class InventoryViewMode(val id: String) {
    LIST(DefaultInventoryViewModeId),
    FOLDERS("folders"),
    ;

    companion object {
        fun fromId(id: String?): InventoryViewMode =
            entries.firstOrNull { it.id == id } ?: LIST
    }
}

/**
 * How large every piece of text in the app is drawn.
 *
 * A multiplier on the Material typography rather than a set of alternative type scales: the user is
 * choosing "a bit bigger", not a different hierarchy, so scaling preserves the design's relative
 * sizes and therefore every layout's proportions.
 *
 * [NORMAL] is exactly 1f on purpose. It is the default, so it has to reproduce the unscaled
 * typography precisely — a 1.05f "normal" would change the appearance of every existing install.
 */
enum class TextSize(
    val id: String,
    val scale: Float,
    @param:StringRes val labelRes: Int,
) {
    SMALL("small", 0.9f, R.string.settings_text_size_small),
    NORMAL(DefaultTextSizeId, 1f, R.string.settings_text_size_normal),
    LARGE("large", 1.15f, R.string.settings_text_size_large),
    ;

    companion object {
        fun fromId(id: String?): TextSize = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

const val DefaultLoadingIconId = "cart"
const val DefaultLoadingSpinId = "clockwise"

/**
 * Id meaning "the startup screen plays a Lottie animation rather than an icon or picture".
 *
 * Stored in the existing `loadingIconId` preference so the choice needs no new key. It is
 * deliberately **not** a member of [LoadingIcon]: that enum is the list of vectors the app ships
 * and the picker enumerates it, so a member with no icon would put a blank tile in the grid.
 */
const val LoadingIconLottieId = "lottie"

/**
 * The glyph the receipt opens with.
 *
 * Declared here as well as on [SaleAnimation.Default] because the settings class and the enum both
 * need a default before the other is guaranteed to be initialised: a `const` used as an `Int`-style
 * initialiser cannot reach into an enum's companion without risking a circular class-init.
 */
const val DefaultSaleAnimationId = "thumb_up"

/**
 * Id meaning "play the user's imported Lottie file instead of a built-in glyph".
 *
 * Deliberately **not** a member of [SaleAnimation]: the enum is a list of pictures the app ships,
 * and this selects a file that may not exist. Keeping it out means [SaleAnimation.fromId] still
 * resolves every id it is given to something drawable, so a stored `lottie` with no file behind it
 * degrades to the default glyph rather than to a missing entry.
 */
const val SaleAnimationLottieId = "lottie"

/**
 * Inclusive bounds of the wait before the **same** code may be accepted again.
 *
 * This is the long one. A label left in front of the camera is re-read many times a second, and
 * this window is what stops it being added over and over. The floor is deliberately high (2 s)
 * because a short one made that misfire trivial on a camera that keeps a code locked in view.
 */
const val MinScanDelayMillis = 2_000
const val MaxScanDelayMillis = 5_000
const val DefaultScanDelayMillis = 3_000

/**
 * Inclusive bounds of the wait before a **different** code may be accepted, in milliseconds.
 *
 * Separate from [MinScanDelayMillis] because the two jobs are not the same: re-reading the label
 * already in frame is a misfire to be suppressed hard, while moving to the next product is the
 * cashier actually working and should be as quick as the camera allows.
 *
 * The floor is 500 ms rather than 0. Even a fast cashier cannot physically swap items faster than
 * that, so anything shorter only lets the camera's own repeat reads through as if they were new
 * products.
 */
const val MinProductChangeDelayMillis = 500
const val MaxProductChangeDelayMillis = 2_000
const val DefaultProductChangeDelayMillis = 1_000

/**
 * Vibration strength, expressed as an amplitude against [android.os.VibrationEffect.MAX_AMPLITUDE].
 *
 * 0 means "let the hardware decide", which is what an amplitude-controlled effect does when it is
 * given 0. That matters because `DEFAULT_AMPLITUDE` is not available below API 26, and the platform
 * documents 0 as the portable way to ask for the device's own intensity.
 */
const val MinScanVibrationStrength = 0
const val MaxScanVibrationStrength = 255
const val DefaultScanVibrationStrength = 160

/**
 * How the scanner buzzes when it accepts a scan.
 *
 * Named patterns rather than a free length slider: the useful distinction between one vibration and
 * another is its *rhythm*, not its duration, and a slider can only express "longer".
 *
 * Each entry owns its timings so the settings screen, the preview button and the scanner itself all
 * read the same numbers — see [ScanVibrationStore].
 */
/**
 * How the scanner buzzes when it accepts a scan.
 *
 * Named patterns rather than a free length slider: the useful distinction between one vibration
 * and another is its *rhythm*, not its duration, and a slider can only express "longer".
 *
 * **The timings are a raw waveform, not an on/off pattern.** `VibrationEffect.createWaveform` reads
 * `timings[i]` as the length of segment `i`, with the *amplitude* array deciding whether that
 * segment is a pulse or a silence — [ScanVibrationStore] derives the amplitudes by alternating.
 * That is why every pattern here starts and ends with a silence: Android's own haptics guidance is
 * to begin and end a waveform at zero amplitude, both to avoid audible ringing from a sudden
 * voltage change and because many drivers apply active braking only when the waveform ends at 0.
 *
 * Each entry owns its timings so the settings screen, the preview button and the scanner itself
 * all read the same numbers.
 */
enum class ScanVibration(
    val id: String,
    @param:StringRes val labelRes: Int,
    /** Alternate silence/pulse durations in milliseconds, starting and ending with a silence. */
    val timings: LongArray,
) {
    /**
     * A single short tap. The tactile equivalent of the classic till beep: present, not insistent,
     * and short enough that a burst of scans does not turn into one long rumble.
     */
    TAP("tap", R.string.settings_scan_vibration_tap, longArrayOf(0L, 35L, 0L)),

    /** Two quick pulses — reads as "done, and confirmed" rather than just "done". */
    DOUBLE("double", R.string.settings_scan_vibration_double, longArrayOf(0L, 25L, 60L, 25L, 0L)),

    /** A firm single thump for users who scan through a case or with gloves on. */
    THUMP("thump", R.string.settings_scan_vibration_thump, longArrayOf(0L, 90L, 0L)),

    /** A long swell: the buzziest option, for noisy environments where a tap goes unnoticed. */
    BUZZ("buzz", R.string.settings_scan_vibration_buzz, longArrayOf(0L, 180L, 0L)),

    /** A rising heartbeat: three accelerating taps, unmistakable in a sequence of scans. */
    HEARTBEAT(
        "heartbeat",
        R.string.settings_scan_vibration_heartbeat,
        longArrayOf(0L, 30L, 90L, 40L, 70L, 55L, 0L),
    ),
    ;

    /** Total wall-clock length of the pattern, silences included. */
    val durationMillis: Long get() = timings.sum()

    companion object {
        val Default = TAP

        fun fromId(id: String?): ScanVibration =
            entries.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * The beep played when a scan is accepted.
 *
 * Built-in tones rather than a pitch slider: two knobs (a frequency and a duration) can produce a
 * sound the user cannot describe and cannot get back once they have left the page, while a named
 * list can be replayed and compared. Each entry is a short sine sweep — see [ScanBeepStore] — and
 * every one of them is under a third of a second so it never overlaps the next scan.
 *
 * The enum holds only [id] and [labelRes] so it stays free of any Android audio type; the sound
 * itself is generated lazily and cached by the store.
 */
enum class ScanBeep(val id: String, @param:StringRes val labelRes: Int) {
    CLASSIC("classic", R.string.settings_scan_beep_classic),
    CHIRP("chirp", R.string.settings_scan_beep_chirp),
    CLICK("click", R.string.settings_scan_beep_click),
    BLIP("blip", R.string.settings_scan_beep_blip),
    DIGITAL("digital", R.string.settings_scan_beep_digital),
    ;

    companion object {
        val Default = CLASSIC

        fun fromId(id: String?): ScanBeep = entries.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * The chime played when a sale is completed and the receipt appears.
 *
 * A separate enum from [ScanBeep] on purpose: the two sounds are chosen for opposite reasons. The
 * scan beep is tuned to be short and unobtrusive because it fires dozens of times per basket, while
 * the sale sound is the one moment in a transaction a cashier may actually want to hear — so the
 * list leans towards celebratory two- and three-note chimes rather than blips.
 *
 * Like [ScanBeep], it carries only [id] and [labelRes]; the audio itself is synthesised lazily and
 * cached by [ScanBeepStore].
 */
enum class SaleSound(val id: String, @param:StringRes val labelRes: Int) {
    CASH_REGISTER("register", R.string.settings_sale_sound_cash_register),
    CHIME("chime", R.string.settings_sale_sound_chime),
    ASCENDING("ascending", R.string.settings_sale_sound_ascending),
    BELL("bell", R.string.settings_sale_sound_bell),
    COINS("coins", R.string.settings_sale_sound_coins),
    SUCCESS("success", R.string.settings_sale_sound_success),
    ;

    companion object {
        val Default = CHIME

        fun fromId(id: String?): SaleSound = entries.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * The image shown, with a burst around it, when a sale is completed.
 *
 * The same picker shape as [LoadingIcon] and for the same reason: a named list of glyphs the user
 * can see is easier to choose from than any numeric setting, and it keeps this file free of the
 * animation itself — the enum knows the *picture*, and `SaleThumbsUp` decides how it moves.
 *
 * The list leans towards "the sale went through" rather than towards decoration: every entry is
 * something a cashier would be happy to have flashed at them a few hundred times a day.
 */
enum class SaleAnimation(
    val id: String,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    THUMBS_UP(DefaultSaleAnimationId, Icons.Default.ThumbUp, R.string.settings_sale_animation_thumbs_up),
    CHECK("check", Icons.Default.CheckCircle, R.string.settings_sale_animation_check),
    STAR("star", Icons.Default.Star, R.string.settings_sale_animation_star),
    MONEY("money", Icons.Default.Savings, R.string.settings_sale_animation_money),
    CELEBRATION("celebration", Icons.Default.Celebration, R.string.settings_sale_animation_celebration),
    RECEIPT("receipt", Icons.AutoMirrored.Filled.ReceiptLong, R.string.settings_sale_animation_receipt),
    MEDAL("medal", Icons.Default.WorkspacePremium, R.string.settings_sale_animation_medal),
    ;

    companion object {
        val Default = THUMBS_UP

        fun fromId(id: String?): SaleAnimation = entries.firstOrNull { it.id == id } ?: Default
    }
}

/** Order matters: this is the order the icon picker lists them in. */
enum class LoadingIcon(
    val id: String,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    CART(DefaultLoadingIconId, Icons.Default.ShoppingCart, R.string.loading_icon_cart),
    INVENTORY("inventory", Icons.Default.Inventory2, R.string.loading_icon_inventory),
    RECEIPT("receipt", Icons.AutoMirrored.Filled.ReceiptLong, R.string.loading_icon_receipt),
    BOX("box", Icons.Default.Inventory, R.string.loading_icon_box),
    STORE("store", Icons.Default.Storefront, R.string.loading_icon_store),
    BARCODE("barcode", Icons.Default.QrCodeScanner, R.string.loading_icon_barcode),
    TAG("tag", Icons.Default.LocalOffer, R.string.loading_icon_tag),
    MONEY("money", Icons.Default.Payments, R.string.loading_icon_money),
    TRENDING("trending", Icons.AutoMirrored.Filled.TrendingUp, R.string.loading_icon_trending),
    SETTINGS("settings", Icons.Default.Settings, R.string.loading_icon_settings),
    ;

    companion object {
        fun fromId(id: String?): LoadingIcon = entries.firstOrNull { it.id == id } ?: CART
    }
}

/**
 * What the startup screen draws.
 *
 * Four sources, modelled as one sealed type rather than "icon id + optional file paths" because
 * exactly one of them is ever in play: a half-set pair would invite code that has to guess which
 * wins.
 *
 * A picked image is **copied into app storage** on pick (see [LoadingImageStore]) for the same
 * reason product photos are: the system photo picker's read grant is short-lived, so a startup
 * screen pointing at it would break within hours of being chosen. Lottie files are copied the same
 * way, by [SaleAnimationStore].
 */
sealed interface LoadingImage {

    /** One of the [LoadingIcon] entries. */
    data class BuiltIn(val icon: LoadingIcon) : LoadingImage

    /** A PNG/JPG picked from the device and stored under [fileName]. */
    data class Picked(val fileName: String) : LoadingImage

    /**
     * A Lottie animation.
     *
     * [fileName] null means **the animation bundled with the app**, which is the default a fresh
     * install starts on; a name means a file the user imported.
     */
    data class Lottie(val fileName: String?) : LoadingImage

    companion object {
        /**
         * Resolves the persisted settings into the one image to draw.
         *
         * Precedence, highest first: a picked picture, an imported Lottie file, the bundled Lottie
         * default, then the chosen icon. Each step exists so that the more specific choice wins —
         * picking a picture takes effect without having to clear anything else, and removing it
         * falls back through the chain rather than to a blank screen.
         *
         * ## The upgrade rule
         *
         * `loadingIconId` of [DefaultLoadingIconId] with no file of either kind means "this
         * install never touched the startup screen" — which used to draw the cart icon and now
         * draws the bundled animation. Without this, existing installs would silently stay on the
         * old default while new ones got the animation, leaving two different apps in the wild.
         * Any *other* icon id, or any picked file, is a deliberate choice and is respected.
         */
        fun from(
            iconId: String?,
            imageFileName: String?,
            lottieFileName: String? = null,
        ): LoadingImage {
            val image = imageFileName?.trim().orEmpty()
            if (image.isNotEmpty()) return Picked(image)

            val lottie = lottieFileName?.trim().orEmpty()
            if (iconId == LoadingIconLottieId) return Lottie(lottie.ifEmpty { null })

            val id = iconId?.trim().orEmpty()
            // Untouched install: no id stored at all, or the old default cart icon.
            if (id.isEmpty() || id == DefaultLoadingIconId) return Lottie(null)

            return BuiltIn(LoadingIcon.fromId(id))
        }
    }
}

/** How the loading icon moves while the app starts. */
enum class LoadingSpin(val id: String, @param:StringRes val labelRes: Int) {
    /** No transform at all: the image sits still. An animated GIF still plays. */
    NONE("none", R.string.loading_spin_none),

    /** Turns continuously in one direction. */
    CLOCKWISE(DefaultLoadingSpinId, R.string.loading_spin_clockwise),

    /** Turns continuously the other way. */
    COUNTER_CLOCKWISE("counter_clockwise", R.string.loading_spin_counter_clockwise),

    /** Rocking quarter-turns: calmer than a full spin, easier to read at small sizes. */
    PENDULUM("pendulum", R.string.loading_spin_pendulum),

    /** Grows and shrinks like a heartbeat. */
    PULSE("pulse", R.string.loading_spin_pulse),

    /**
     * Hops up and down, with a small squash at the bottom of each arc.
     *
     * The squash on contact is what makes a bounce read as weight rather than as a shape sliding
     * on a spring.
     */
    BOUNCE("bounce", R.string.loading_spin_bounce),

    /**
     * Travels across the screen: enters from the left, leaves on the right.
     *
     * Unlike the other motions this one is **not** contained by the disc — it needs the full width
     * to cross, so the circular framing is suppressed while it runs (see `SpinningImage`).
     */
    SLIDE("slide", R.string.loading_spin_slide),

    /**
     * Like [SLIDE], but pauses in the centre before carrying on.
     *
     * The hold is what makes it read as deliberate rather than as a loop stuttering. It also
     * guarantees the image is legible for a moment on a launch that ends mid-flight, which the
     * plain slide cannot promise — half the time it is caught off-screen.
     */
    SLIDE_PAUSE("slide_pause", R.string.loading_spin_slide_pause),
    ;

    /**
     * Whether this motion travels the full width of its container.
     *
     * Lives on the enum so `SpinningImage` cannot forget one: a travelling motion left on the
     * fixed 168dp disc container still animates, it just never reaches the screen edges — a bug
     * that looks like "the slide is too small" rather than like a missing case.
     */
    val travelsAcrossScreen: Boolean
        get() = this == SLIDE || this == SLIDE_PAUSE

    /**
     * How long one cycle of this motion should take.
     *
     * Lives here rather than at each call site so the startup screen and the Config preview cannot
     * drift, and so tuning one motion is a single edit. The values are not uniform because the
     * motions do not read the same at a shared speed:
     *
     *  - `PULSE` beats faster than a rotation or it looks sluggish rather than alive.
     *  - `BOUNCE` needs to feel light; a slow hop reads as heavy.
     *  - `SLIDE` crosses a full screen, so it needs time not to look like a teleport.
     *  - `SLIDE_PAUSE` adds a stationary hold on top of that travel, so it needs the most.
     *  - Everything else sits at the neutral 1400ms.
     */
    val periodMillis: Int
        get() = when (this) {
            PULSE -> 1100
            BOUNCE -> 900
            SLIDE -> 2000
            SLIDE_PAUSE -> 3200
            NONE, CLOCKWISE, COUNTER_CLOCKWISE, PENDULUM -> 1400
        }

    /**
     * One full cycle at the user's chosen [speed].
     *
     * The startup motion is the one animation *not* driven by `AppMotion`'s specs — it is an
     * infinite transition with its own period — so the speed preference has to be applied here
     * explicitly. Without this the "Animations" setting did nothing to the one animation the
     * user actually tunes in Config, and the preview could not honestly represent it.
     *
     * `MotionSpeed.OFF` deliberately does **not** collapse this to zero: a zero-length infinite
     * repeatable is an invalid spec (it would divide the frame budget by zero). "Still" is the
     * separate [NONE] motion, and `SpinningImage` skips the frame clock for it entirely.
     */
    fun periodMillisAt(speed: MotionSpeed): Int {
        val scaled = speed.scale(periodMillis)
        return if (scaled <= 0) periodMillis else scaled
    }

    companion object {
        fun fromId(id: String?): LoadingSpin = entries.firstOrNull { it.id == id } ?: CLOCKWISE
    }
}

const val DefaultLoadingCircleId = "soft"

/**
 * Bounds are inclusive, and the slider derives its step count from the span between them.
 *
 * The default sits at the **top** of the range rather than the middle: the startup screen is the
 * first thing a shop sees on every launch, and a Lottie animation or a logo is meant to be looked
 * at, not to be a small icon. The slider still goes down to 48 for anyone who wants it smaller.
 */
const val MinLoadingImageSizeDp = 48
const val MaxLoadingImageSizeDp = 200
const val DefaultLoadingImageSizeDp = 200

const val MinLoadingCircleSizeDp = 72
const val MaxLoadingCircleSizeDp = 320
const val DefaultLoadingCircleSizeDp = 168

/**
 * Bounds of the receipt flourish's glyph, in dp.
 *
 * The range is wider than the startup image's upper end because this is the one full-screen
 * moment in the app: the default sits well below the maximum so the rings always have room to
 * expand inside the screen, but a shop that wants the amount to be readable across a counter can
 * push it up. The floor keeps the glyph legible next to its own rings, which scale with it.
 */
const val MinSaleAnimationSizeDp = 40
const val MaxSaleAnimationSizeDp = 160
const val DefaultSaleAnimationSizeDp = 76

/**
 * The disc drawn behind the startup image.
 *
 * A ring of discrete choices rather than a colour picker: this is a first-run flourish, and the
 * point of it is to frame the logo well — which is a question of contrast and weight, not of
 * picking an arbitrary hex value.
 */
enum class LoadingCircle(val id: String, @param:StringRes val labelRes: Int) {
    /** No disc at all: the image sits directly on the background. */
    NONE("none", R.string.loading_circle_none),

    /** The default: a translucent tint of the app's primary colour. */
    SOFT(DefaultLoadingCircleId, R.string.loading_circle_soft),

    /** Solid primary container, for logos with transparent backgrounds. */
    SOLID("solid", R.string.loading_circle_solid),

    /** A thin outline only: the lightest way to frame an image. */
    OUTLINE("outline", R.string.loading_circle_outline),

    /** The panel colour, which follows the user's panel customisation. */
    PANEL("panel", R.string.loading_circle_panel),
    ;

    companion object {
        fun fromId(id: String?): LoadingCircle = entries.firstOrNull { it.id == id } ?: SOFT
    }
}

/** Font families offered in the configuration tab. */
enum class AppFontFamily(val id: String, @param:StringRes val labelRes: Int) {
    DEFAULT(DefaultFontFamilyId, R.string.settings_font_default),
    SANS("sans", R.string.settings_font_sans),
    SERIF("serif", R.string.settings_font_serif),
    MONOSPACE("monospace", R.string.settings_font_monospace),
    CURSIVE("cursive", R.string.settings_font_cursive),
    DARUMADROP("darumadrop", R.string.settings_font_darumadrop),
    CHARISILBOLD("charaisilbold",R.string.settings_font_charaisilbold),
    CHARISILREGULAR("charaisilregular",R.string.settings_font_charaisilregular),
    COINY("coiny",R.string.settings_font_coiny),
    ENRIQUETA("enriqueta",R.string.settings_font_enriqueta),
    HENNYPENNY("hennypenny",R.string.settings_font_hennypenny),
    LACQUER("lacquer",R.string.settings_font_lacquer),
    LIMELIGHT("limelight",R.string.settings_font_limelight),
    ;

    companion object {
        fun fromId(id: String?): AppFontFamily =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * User-configurable settings: where exports are written and what the app looks like.
 *
 * Colours are nullable because "not customised" must be distinguishable from "customised to the
 * same value as the default" — otherwise switching the default palette would not affect a user
 * who had merely opened the colour picker.
 */
data class AppSettings(
    val exportFolderUri: String? = null,
    val useAppBackgroundColor: Boolean = false,
    val backgroundColorArgb: ArgbColor? = null,
    val useAppButtonColor: Boolean = false,
    val buttonColorArgb: ArgbColor? = null,
    /** Colour of the cards/sections that sit on top of the background. */
    val useAppPanelColor: Boolean = false,
    val panelColorArgb: ArgbColor? = null,
    /** Colour used for text drawn on the background and panels. */
    val useAppTextColor: Boolean = false,
    val textColorArgb: ArgbColor? = null,
    /**
     * Themes the user has saved, newest first.
     *
     * Stored as a JSON list in one preference rather than as several keys: a theme is four or
     * five values that only mean anything together, and splitting them across keys would let a
     * half-written theme appear in the picker. See [ColorTheme].
     */
    val savedThemes: List<ColorTheme> = emptyList(),
    /**
     * Colour used for the **highlighted** things: selected filter chips, the selected
     * navigation-bar indicator, and emphasised text such as a category heading.
     */
    val useAppHighlightColor: Boolean = false,
    val highlightColorArgb: ArgbColor? = null,
    /** Font family id, e.g. "default" / "serif" / "monospace". */
    val fontFamilyId: String = DefaultFontFamilyId,
    /** BCP-47 language tag, or empty to follow the system. */
    val languageTag: String = "",
    /** General tax rate applied to every sale, in basis points (1/100 %). */
    val taxRateBasisPoints: Int = 0,
    /** Extra rate charged on card payments, in basis points (1/100 %). */
    val cardFeeRateBasisPoints: Int = 0,
    /**
     * Flat fee charged on every card payment, in cents. Added to the percentage card fee, so the
     * card deduction is `subtotal × cardFeeRateBasisPoints % + cardFeeFixedCents`.
     */
    val cardFeeFixedCents: Long = 0,
    /**
     * Whether money is shown with its cents, e.g. "12.99" instead of "13".
     *
     * Display-only: prices are always *stored* in exact cents, so switching this never rewrites
     * data and can be reversed at any time. See [formatCents] for the rounding rule.
     */
    val showMoneyDecimals: Boolean = true,
    /**
     * Which currency symbol every amount is shown in; see [Currency].
     *
     * Display-only in the same sense as [showMoneyDecimals]: the stored amounts are plain minor
     * units and are never rescaled, so this relabels the numbers rather than converting them.
     */
    val currencyId: String = DefaultCurrencyId,
    /** How quickly screen transitions and expansion animations run. */
    val motionSpeedId: String = DefaultMotionSpeedId,
    /** How large every piece of text is drawn; see [TextSize]. */
    val textSizeId: String = DefaultTextSizeId,
    /**
     * Whether the product tagging feature is shown at all.
     *
     * **Off by default**, and that is the point: most shops never need a second classification
     * axis, and a tag row in the product editor would be clutter for them. Off hides the whole
     * feature — the editor, the filter chips, the manager.
     *
     * It hides the UI, **not the data**. Any tags and assignments already stored stay in the
     * database and keep being exported in backups; they simply stop being drawn. Switching the
     * toggle back on brings them all back. Deleting them would make the switch a data-loss trap,
     * which a display preference has no right to be.
     */
    val tagsEnabled: Boolean = false,
    /**
     * How the inventory tab draws its products.
     *
     * Defaults to [InventoryViewMode.LIST], which is the layout the app has always had — so an
     * existing install sees no change on upgrade and a user who never opens the switcher is never
     * surprised by their products rearranging themselves.
     */
    val inventoryViewModeId: String = DefaultInventoryViewModeId,
    /**
     * Which source the startup screen draws.
     *
     * A [LoadingIcon] id, or [LoadingIconLottieId] for a Lottie animation. Ignored while
     * [loadingImageFileName] is set — a picked picture always wins.
     */
    val loadingIconId: String = DefaultLoadingIconId,
    /**
     * File **name** of an imported loading-screen Lottie animation, or null for the bundled one.
     *
     * Only the name, like every other picked file: the app's data directory moves between installs
     * and restores, which would leave an absolute path dangling.
     */
    val loadingLottieFileName: String? = null,
    /**
     * Name of a picked image in app storage, or null to use [loadingIconId].
     *
     * Only the file name is stored, not an absolute path: the app's data directory can move
     * between installs and restores, which would leave a stored path dangling.
     */
    val loadingImageFileName: String? = null,
    /**
     * Width/height of the startup image, in dp.
     *
     * Stored as an Int rather than a [androidx.compose.ui.unit.Dp] so it stays DataStore-friendly
     * and the clamping can be unit-tested without Compose.
     */
    val loadingImageSizeDp: Int = DefaultLoadingImageSizeDp,
    /** Width/height of the disc drawn behind the startup image, in dp. */
    val loadingCircleSizeDp: Int = DefaultLoadingCircleSizeDp,
    /** How that icon moves while the app starts. */
    val loadingSpinId: String = DefaultLoadingSpinId,
    /** Which disc, if any, is drawn behind the startup image. */
    val loadingCircleId: String = DefaultLoadingCircleId,
    /** Whether the startup screen appears at all. */
    val showLoadingScreen: Boolean = true,
    /** Whether an accepted scan plays a beep. */
    val scannerBeepEnabled: Boolean = true,
    /** Name of a beep the user picked from the device, or null to use [scannerBeepId]. */
    val scannerBeepFileName: String? = null,
    /** Which built-in tone the scanner beeps with. Ignored while [scannerBeepFileName] is set. */
    val scannerBeepId: String = ScanBeep.Default.id,
    /**
     * Whether a sale plays a sound when it is paid for.
     *
     * Off by default, unlike the scan beep: it is a flourish rather than a confirmation, and a
     * shop that is already full of till noise should not gain another sound until the user asks
     * for one.
     */
    val saleSoundEnabled: Boolean = false,
    /** Name of a sound the user picked from the device, or null to use [saleSoundId]. */
    val saleSoundFileName: String? = null,
    /** Which built-in chime plays on a sale. Ignored while [saleSoundFileName] is set. */
    val saleSoundId: String = SaleSound.Default.id,
    /**
     * Which flourish the receipt plays when a sale completes: a [SaleAnimation] id, or
     * [SaleAnimationLottieId] for the user's imported file.
     */
    val saleAnimationId: String = DefaultSaleAnimationId,
    /**
     * File **name** (not a path) of the imported Lottie animation, inside the app's private
     * `files/sale_animations` folder. Null when none has been imported.
     *
     * Only the name is stored for the same reason as every other picked file: the app's data
     * directory moves between installs, so an absolute path would dangle.
     */
    val saleLottieFileName: String? = null,
    /**
     * How big that glyph is drawn, in dp.
     *
     * Stored as an Int rather than a [androidx.compose.ui.unit.Dp] so it stays DataStore-friendly
     * and the clamping can be unit-tested without Compose — the same reasoning as the loading sizes.
     */
    val saleAnimationSizeDp: Int = DefaultSaleAnimationSizeDp,
    /**
     * How long the scanner waits before accepting the **same** code again, in milliseconds.
     *
     * A shop scanner runs while the label is still in frame, so this is what stops one product
     * from being added dozens of times. Stored as an Int for the same reason the loading sizes are:
     * DataStore-friendly, and clampable in a unit test without Compose.
     */
    val scanDelayMillis: Int = DefaultScanDelayMillis,
    /**
     * How long the scanner waits before accepting a **different** code, in milliseconds.
     *
     * The short one. Moving to the next product is the cashier working, not a misfire, so this is
     * deliberately much lower than [scanDelayMillis] — see [MinProductChangeDelayMillis].
     */
    val productChangeDelayMillis: Int = DefaultProductChangeDelayMillis,
    /** Whether the product that was just scanned is shown as a banner over the camera. */
    val showScanPopup: Boolean = true,
    /**
     * Whether an accepted scan vibrates.
     *
     * On by default, matching the beep: a cashier scanning with the phone held in one hand is not
     * necessarily looking at the screen, so the confirmation should be felt as well as heard. Users
     * who find it intrusive can switch it off in Personalize, and devices without a vibration motor
     * simply ignore it — see `ScanVibrationStore.isSupported`.
     */
    val scanVibrationEnabled: Boolean = true,
    /** Which pattern the scanner vibrates with. */
    val scanVibrationId: String = ScanVibration.Default.id,
    /** Vibration amplitude, 0-255, where 0 lets the device choose. */
    val scanVibrationStrength: Int = DefaultScanVibrationStrength,
) {
    val hasExportFolder: Boolean get() = exportFolderUri != null

    /** Human-readable folder name, e.g. `.../tree/primary%3AStock` -> "Stock". */
    val exportFolderLabel: String
        get() = exportFolderDisplayName(exportFolderUri) ?: DefaultExportFolderLabel

    val motionSpeed: MotionSpeed get() = MotionSpeed.fromId(motionSpeedId)
    val textSize: TextSize get() = TextSize.fromId(textSizeId)

    /**
     * True when the channel holds exactly the app's own default colour.
     *
     * Used by the colour page so a palette applied from a theme does not report itself as a
     * "custom" colour. Applying **Default** writes the stock colours into the store — it has to,
     * because a theme is one atomic write and "Default" is a real palette — leaving the override
     * switched on for every channel, which the toggles read as "this was customised". It was not:
     * the value is the default, so the toggle belongs off.
     *
     * Compared by *value*, not by whether an override exists, because "never customised" and
     * "customised to exactly the default" are the same pixels and should look the same. The
     * stored override is deliberately left in place so the colour pickers still open on the right
     * swatch; this is a presentation question only.
     */
    fun isDefaultColor(channel: ColorChannel): Boolean {
        // A channel that was never set is, trivially, at its default. Checking the stored value
        // only would call a cleared channel "customised", which is the state "restore the
        // predetermined colours" leaves every channel in.
        val stored = storedColor(channel) ?: return true
        return stored == defaultColor(channel)
    }

    /**
     * What the colour toggles should read as "on".
     *
     * Off in two distinct cases, both of which the raw `use*` flag reports as on:
     *
     *  - the override was never switched on, and
     *  - the override is on but holds the app's own default.
     *
     * The second is exactly what applying any theme produces — a theme is one atomic write that
     * switches all five channels on — so reading the flag directly made a freshly applied preset
     * report itself as a custom colour on every channel.
     *
     * **Text is judged by value alone.** Its default is a stated colour rather than an absence, so
     * a theme also writing "black" is not visibly different from never having touched it, and
     * there is nothing for the user to have customised. The other four are judged by override *or*
     * value: an override pinned to the stock colour is still a deliberate act ("I chose this"),
     * but it is indistinguishable on screen from the theme's own palette, so it too reads as off.
     */
    fun isCustomColorEnabled(channel: ColorChannel): Boolean = when (channel) {
        ColorChannel.BACKGROUND -> useAppBackgroundColor && !isDefaultColor(channel)
        ColorChannel.BUTTON -> useAppButtonColor && !isDefaultColor(channel)
        ColorChannel.PANEL -> useAppPanelColor && !isDefaultColor(channel)
        ColorChannel.HIGHLIGHT -> useAppHighlightColor && !isDefaultColor(channel)
        ColorChannel.TEXT -> !isDefaultColor(channel)
    }

    /** The raw ARGB stored for [channel], or null when it has never been written. */
    private fun storedColor(channel: ColorChannel): ArgbColor? = when (channel) {
        ColorChannel.BACKGROUND -> backgroundColorArgb
        ColorChannel.BUTTON -> buttonColorArgb
        ColorChannel.PANEL -> panelColorArgb
        ColorChannel.TEXT -> textColorArgb
        ColorChannel.HIGHLIGHT -> highlightColorArgb
    }

    /** The app's own colour for [channel]; see [DefaultColors]. */
    private fun defaultColor(channel: ColorChannel): ArgbColor = when (channel) {
        ColorChannel.BACKGROUND -> DefaultColors.background
        ColorChannel.BUTTON -> DefaultColors.button
        ColorChannel.PANEL -> DefaultColors.panel
        ColorChannel.HIGHLIGHT -> DefaultColors.highlight
        ColorChannel.TEXT -> DefaultTextArgb
    }
    val currency: Currency get() = Currency.fromId(currencyId)
    val loadingIcon: LoadingIcon get() = LoadingIcon.fromId(loadingIconId)
    val loadingCircle: LoadingCircle get() = LoadingCircle.fromId(loadingCircleId)

    /** The one image the startup screen draws. See [LoadingImage.from] for the precedence. */
    val loadingImage: LoadingImage
        get() = LoadingImage.from(loadingIconId, loadingImageFileName, loadingLottieFileName)

    /** Whether the startup screen is currently drawing a Lottie animation. */
    val usesLottieLoadingScreen: Boolean get() = loadingImage is LoadingImage.Lottie
    val loadingSpin: LoadingSpin get() = LoadingSpin.fromId(loadingSpinId)

    /**
     * The circle only ever needs to be as big as the image plus a uniform frame; a circle smaller
     * than the image would clip it, so the stored value is floored at the image size on read.
     * Applying the floor here rather than only in the UI means a hand-edited store cannot produce
     * a clipped image either.
     */
    val effectiveLoadingCircleSizeDp: Int
        get() = loadingCircleSizeDp.coerceAtLeast(loadingImageSizeDp)

    /** Which built-in tone to beep with, ignoring any picked file. */
    val scannerBeep: ScanBeep get() = ScanBeep.fromId(scannerBeepId)

    /** The one sound the scanner plays: a picked file if there is one, else the built-in tone. */
    val scannerBeepSound: ScanBeepSound
        get() = ScanBeepSound.from(scannerBeepId, scannerBeepFileName)

    /** Whether anything at all should be played for a scan. */
    val playsScanBeep: Boolean get() = scannerBeepEnabled

    /**
     * The one sound a completed sale plays: a picked file if there is one, else the built-in chime.
     *
     * The file wins over the tone for the same reason as the scanner: picking a sound has to take
     * effect immediately, and clearing it has to fall back to whatever chime is still selected.
     */
    val saleSound: ScanBeepSound get() = ScanBeepSound.fromSale(saleSoundId, saleSoundFileName)

    /** Whether a completed sale should play [saleSound]. */
    val playsSaleSound: Boolean get() = saleSoundEnabled

    /** Which built-in glyph the sale flourish draws. See [usesLottieSaleAnimation]. */
    val saleAnimation: SaleAnimation get() = SaleAnimation.fromId(saleAnimationId)

    /**
     * Whether the receipt should play the imported Lottie file.
     *
     * Requires **both** the id and a file: an id left over from an uninstalled or relocated file
     * must fall back to the built-in glyph instead of rendering nothing.
     */
    val usesLottieSaleAnimation: Boolean
        get() = saleAnimationId == SaleAnimationLottieId && !saleLottieFileName.isNullOrBlank()

    /** Which vibration pattern the scanner uses. */
    val scanVibration: ScanVibration get() = ScanVibration.fromId(scanVibrationId)

    /**
     * Strength clamped to the range the platform accepts.
     *
     * Applied on read as well as on write so a hand-edited store cannot hand an out-of-range
     * amplitude to [android.os.VibrationEffect.createWaveform], which throws.
     */
    val effectiveScanVibrationStrength: Int
        get() = scanVibrationStrength.coerceIn(
            MinScanVibrationStrength,
            MaxScanVibrationStrength,
        )
}

/** Like [AppSettings.exportFolderLabel], but localised for the "no folder chosen" case. */
fun AppSettings.localizedExportFolderLabel(context: Context): String =
    exportFolderDisplayName(exportFolderUri)
        ?: context.getString(R.string.settings_default_folder)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings"
)

/**
 * Reads and writes the user's persisted preferences.
 *
 * [dataStore] is exposed so anything that needs the same store can share one handle rather than
 * opening a second DataStore on the same file, which DataStore refuses to allow.
 */
class SettingsRepository(val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    private object Keys {
        val exportFolder = stringPreferencesKey("export_folder_uri")
        val useBackground = stringPreferencesKey("use_background_colour")
        val background = stringPreferencesKey("background_argb")
        val useButton = stringPreferencesKey("use_button_colour")
        val button = stringPreferencesKey("button_argb")
        val usePanel = stringPreferencesKey("use_panel_colour")
        val panel = stringPreferencesKey("panel_argb")
        val useText = stringPreferencesKey("use_text_colour")
        val text = stringPreferencesKey("text_argb")
        val useHighlight = stringPreferencesKey("use_highlight_colour")
        val highlight = stringPreferencesKey("highlight_argb")
        val savedThemes = stringPreferencesKey("saved_colour_themes")
        val fontFamily = stringPreferencesKey("font_family_id")
        val languageTag = stringPreferencesKey("language_tag")
        val taxRate = stringPreferencesKey("tax_rate_basis_points")
        val cardFeeRate = stringPreferencesKey("card_fee_rate_basis_points")
        val cardFeeFixed = stringPreferencesKey("card_fee_fixed_cents")
        val showMoneyDecimals = stringPreferencesKey("show_money_decimals")
        val currency = stringPreferencesKey("currency_id")
        val motionSpeed = stringPreferencesKey("motion_speed_id")
        val tagsEnabled = stringPreferencesKey("tags_enabled")
        val inventoryViewMode = stringPreferencesKey("inventory_view_mode_id")
        val onboardingComplete = stringPreferencesKey("onboarding_complete")
        val onboardingStep = stringPreferencesKey("onboarding_step")
    val textSize = stringPreferencesKey("text_size_id")
        val loadingIcon = stringPreferencesKey("loading_icon_id")
        val loadingImage = stringPreferencesKey("loading_image_file")
        val loadingLottieFile = stringPreferencesKey("loading_lottie_file")
        val loadingImageSize = stringPreferencesKey("loading_image_size_dp")
        val loadingCircleSize = stringPreferencesKey("loading_circle_size_dp")
        val loadingSpin = stringPreferencesKey("loading_spin_id")
        val loadingCircle = stringPreferencesKey("loading_circle_id")
        val showLoadingScreen = stringPreferencesKey("show_loading_screen")
        val scannerBeepEnabled = stringPreferencesKey("scanner_beep_enabled")
        val scannerBeep = stringPreferencesKey("scanner_beep_id")
        val scannerBeepFile = stringPreferencesKey("scanner_beep_file")
        val saleSoundEnabled = stringPreferencesKey("sale_sound_enabled")
        val saleSound = stringPreferencesKey("sale_sound_id")
        val saleSoundFile = stringPreferencesKey("sale_sound_file")
        val saleAnimation = stringPreferencesKey("sale_animation_id")
        val saleLottieFile = stringPreferencesKey("sale_lottie_file")
        val saleAnimationSize = stringPreferencesKey("sale_animation_size_dp")
        val scanDelay = stringPreferencesKey("scan_delay_millis")
        val productChangeDelay = stringPreferencesKey("product_change_delay_millis")
        val showScanPopup = stringPreferencesKey("show_scan_popup")
        val scanVibrationEnabled = stringPreferencesKey("scan_vibration_enabled")
        val scanVibration = stringPreferencesKey("scan_vibration_id")
        val scanVibrationStrength = stringPreferencesKey("scan_vibration_strength")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            exportFolderUri = prefs[Keys.exportFolder],
            useAppBackgroundColor = prefs[Keys.useBackground]?.toBoolean() ?: false,
            backgroundColorArgb = prefs[Keys.background]?.toLongOrNull(),
            useAppButtonColor = prefs[Keys.useButton]?.toBoolean() ?: false,
            buttonColorArgb = prefs[Keys.button]?.toLongOrNull(),
            useAppPanelColor = prefs[Keys.usePanel]?.toBoolean() ?: false,
            panelColorArgb = prefs[Keys.panel]?.toLongOrNull(),
            useAppTextColor = prefs[Keys.useText]?.toBoolean() ?: false,
            textColorArgb = prefs[Keys.text]?.toLongOrNull(),
            useAppHighlightColor = prefs[Keys.useHighlight]?.toBoolean() ?: false,
            highlightColorArgb = prefs[Keys.highlight]?.toLongOrNull(),
            savedThemes = decodeThemes(prefs[Keys.savedThemes]),
            fontFamilyId = prefs[Keys.fontFamily] ?: DefaultFontFamilyId,
            languageTag = prefs[Keys.languageTag] ?: "",
            taxRateBasisPoints = prefs[Keys.taxRate]?.toIntOrNull() ?: 0,
            cardFeeRateBasisPoints = prefs[Keys.cardFeeRate]?.toIntOrNull() ?: 0,
            cardFeeFixedCents = prefs[Keys.cardFeeFixed]?.toLongOrNull() ?: 0L,
            showMoneyDecimals = prefs[Keys.showMoneyDecimals]?.toBoolean() ?: true,
            currencyId = Currency.fromId(prefs[Keys.currency]).id,
            motionSpeedId = prefs[Keys.motionSpeed] ?: DefaultMotionSpeedId,
            tagsEnabled = prefs[Keys.tagsEnabled]?.toBoolean() ?: false,
            inventoryViewModeId = InventoryViewMode.fromId(prefs[Keys.inventoryViewMode]).id,
    textSizeId = TextSize.fromId(prefs[Keys.textSize]).id,
            loadingIconId = prefs[Keys.loadingIcon] ?: DefaultLoadingIconId,
            loadingImageFileName = prefs[Keys.loadingImage],
            loadingLottieFileName = prefs[Keys.loadingLottieFile],
            loadingImageSizeDp = prefs[Keys.loadingImageSize]?.toIntOrNull()
                ?.coerceIn(MinLoadingImageSizeDp, MaxLoadingImageSizeDp)
                ?: DefaultLoadingImageSizeDp,
            loadingCircleSizeDp = prefs[Keys.loadingCircleSize]?.toIntOrNull()
                ?.coerceIn(MinLoadingCircleSizeDp, MaxLoadingCircleSizeDp)
                ?: DefaultLoadingCircleSizeDp,
            loadingSpinId = prefs[Keys.loadingSpin] ?: DefaultLoadingSpinId,
            loadingCircleId = prefs[Keys.loadingCircle] ?: DefaultLoadingCircleId,
            showLoadingScreen = prefs[Keys.showLoadingScreen]?.toBoolean() ?: true,
            scannerBeepEnabled = prefs[Keys.scannerBeepEnabled]?.toBoolean() ?: true,
            scannerBeepFileName = prefs[Keys.scannerBeepFile],
            scannerBeepId = prefs[Keys.scannerBeep] ?: ScanBeep.Default.id,
            saleSoundEnabled = prefs[Keys.saleSoundEnabled]?.toBoolean() ?: false,
            saleSoundFileName = prefs[Keys.saleSoundFile],
            saleSoundId = prefs[Keys.saleSound] ?: SaleSound.Default.id,
            saleAnimationId = prefs[Keys.saleAnimation] ?: DefaultSaleAnimationId,
        saleLottieFileName = prefs[Keys.saleLottieFile],
            saleAnimationSizeDp = prefs[Keys.saleAnimationSize]?.toIntOrNull()
                ?.coerceIn(MinSaleAnimationSizeDp, MaxSaleAnimationSizeDp)
                ?: DefaultSaleAnimationSizeDp,
            scanDelayMillis = prefs[Keys.scanDelay]?.toIntOrNull()
                ?.coerceIn(MinScanDelayMillis, MaxScanDelayMillis)
                ?: DefaultScanDelayMillis,
            // A store written before this setting existed has no value, so it must default rather
            // than inherit anything from scanDelayMillis — the two are independent by design.
            productChangeDelayMillis = prefs[Keys.productChangeDelay]?.toIntOrNull()
                ?.coerceIn(MinProductChangeDelayMillis, MaxProductChangeDelayMillis)
                ?: DefaultProductChangeDelayMillis,
            showScanPopup = prefs[Keys.showScanPopup]?.toBoolean() ?: true,
            scanVibrationEnabled = prefs[Keys.scanVibrationEnabled]?.toBoolean() ?: true,
            scanVibrationId = prefs[Keys.scanVibration] ?: ScanVibration.Default.id,
            scanVibrationStrength = prefs[Keys.scanVibrationStrength]?.toIntOrNull()
                ?.coerceIn(MinScanVibrationStrength, MaxScanVibrationStrength)
                ?: DefaultScanVibrationStrength,
        )
    }

    suspend fun setExportFolder(uri: Uri?) {
        dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.exportFolder)
            else prefs[Keys.exportFolder] = uri.toString()
        }
    }

    suspend fun setBackgroundColor(enabled: Boolean, argb: ArgbColor?) {
        dataStore.edit { prefs ->
            if (enabled) prefs[Keys.useBackground] = enabled.toString()
            else prefs.remove(Keys.useBackground)
            if (argb != null) prefs[Keys.background] = argb.toString()
        }
    }

    suspend fun setButtonColor(enabled: Boolean, argb: ArgbColor?) {
        dataStore.edit { prefs ->
            if (enabled) prefs[Keys.useButton] = enabled.toString()
            else prefs.remove(Keys.useButton)
            if (argb != null) prefs[Keys.button] = argb.toString()
        }
    }

    suspend fun setPanelColor(enabled: Boolean, argb: ArgbColor?) {
        dataStore.edit { prefs ->
            if (enabled) prefs[Keys.usePanel] = enabled.toString()
            else prefs.remove(Keys.usePanel)
            if (argb != null) prefs[Keys.panel] = argb.toString()
        }
    }

    /**
     * Turns the text colour override on or off.
     *
     * Enabling with no colour stores the default ink rather than nothing at all. The caller passes
     * `null` whenever the picker holds no draft (see `StockApp`'s `onToggleText`), and storing
     * nothing there left whatever ARGB a previous theme had written still in the key — so the
     * toggle turned "on" and silently reinstated the last theme's white ink on a pale page.
     */
    suspend fun setTextColor(enabled: Boolean, argb: ArgbColor?) {
        dataStore.edit { prefs ->
            if (enabled) {
                prefs[Keys.useText] = true.toString()
                prefs[Keys.text] = (argb ?: prefs[Keys.text]?.toLongOrNull() ?: DefaultTextArgb)
                    .toString()
            } else {
                prefs.remove(Keys.useText)
                prefs.remove(Keys.text)
            }
        }
    }

    suspend fun setHighlightColor(enabled: Boolean, argb: ArgbColor?) {
        dataStore.edit { prefs ->
            if (enabled) prefs[Keys.useHighlight] = enabled.toString()
            else prefs.remove(Keys.useHighlight)
            if (argb != null) prefs[Keys.highlight] = argb.toString()
        }
    }

    suspend fun setFontFamily(fontFamilyId: String) {
        dataStore.edit { prefs -> prefs[Keys.fontFamily] = fontFamilyId }
    }

    suspend fun setLanguageTag(tag: String) {
        dataStore.edit { prefs -> prefs[Keys.languageTag] = tag }
    }

    /**
     * The first-run flow's progress.
     *
     * Read as a flow rather than a one-shot so the activity gate re-renders the instant onboarding
     * finishes, without an explicit callback. Not part of [AppSettings] on purpose: that type is
     * backed up, exported and imported as the user's *preferences*, and onboarding completion is
     * neither a preference nor something a restore should replay.
     */
    val onboarding: Flow<OnboardingProgress> = dataStore.data.map { prefs ->
        OnboardingProgress.stored(
            complete = prefs[Keys.onboardingComplete]?.toBoolean() ?: false,
            stepName = prefs[Keys.onboardingStep],
        )
    }

    /**
     * Records which step the user is on.
     *
     * Written *before* the step is shown, so a crash or a process death mid-flow resumes on the
     * step the user was actually looking at rather than replaying the previous one.
     */
    suspend fun setOnboardingStep(step: OnboardingStep) {
        dataStore.edit { prefs -> prefs[Keys.onboardingStep] = step.name }
    }

    /** Marks the first run finished. The app opens normally from the next launch on. */
    suspend fun completeOnboarding() {
        dataStore.edit { prefs -> prefs[Keys.onboardingComplete] = true.toString() }
    }

    suspend fun setTaxRate(basisPoints: Int) {
        dataStore.edit { prefs -> prefs[Keys.taxRate] = basisPoints.coerceIn(0, 10_000).toString() }
    }

    suspend fun setCardFeeRate(basisPoints: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.cardFeeRate] = basisPoints.coerceIn(0, 10_000).toString()
        }
    }

    /** Stores the flat card fee in cents, clamped to a sane range. */
    suspend fun setCardFeeFixed(cents: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.cardFeeFixed] = cents.coerceIn(0, MaxCardFeeFixedCents).toString()
        }
    }

    /** Toggles cents in money labels. Display-only; no stored amount is touched. */
    suspend fun setShowMoneyDecimals(show: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.showMoneyDecimals] = show.toString() }
    }

    /** Stores the chosen currency symbol. Display-only; no stored amount is rescale. */
    suspend fun setCurrency(id: String) {
        dataStore.edit { prefs -> prefs[Keys.currency] = Currency.fromId(id).id }
    }

    suspend fun setMotionSpeed(id: String) {
        dataStore.edit { prefs -> prefs[Keys.motionSpeed] = MotionSpeed.fromId(id).id }
    }

    suspend fun setTextSize(id: String) {
        dataStore.edit { prefs -> prefs[Keys.textSize] = TextSize.fromId(id).id }
    }

    suspend fun setTagsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.tagsEnabled] = enabled.toString() }
    }

    /** Stores the inventory layout. Display-only; no product or category is touched. */
    suspend fun setInventoryViewMode(id: String) {
        dataStore.edit { prefs -> prefs[Keys.inventoryViewMode] = InventoryViewMode.fromId(id).id }
    }

    suspend fun setLoadingIcon(id: String) {
        dataStore.edit { prefs -> prefs[Keys.loadingIcon] = LoadingIcon.fromId(id).id }
    }

    suspend fun setLoadingSpin(id: String) {
        dataStore.edit { prefs -> prefs[Keys.loadingSpin] = LoadingSpin.fromId(id).id }
    }

    suspend fun setLoadingCircle(id: String) {
        dataStore.edit { prefs -> prefs[Keys.loadingCircle] = LoadingCircle.fromId(id).id }
    }

    /**
     * Stores the picked startup image's file name, replacing whatever was there.
     *
     * @return the previous file name, so the caller can delete the file it just orphaned.
     */
    suspend fun setLoadingImage(fileName: String?): String? {
        var previous: String? = null
        dataStore.edit { prefs ->
            previous = prefs[Keys.loadingImage]
            val clean = fileName?.trim().orEmpty()
            if (clean.isEmpty()) prefs.remove(Keys.loadingImage)
            else prefs[Keys.loadingImage] = clean
        }
        return previous
    }

    /**
     * Stores an imported loading-screen animation's file name, replacing whatever was there.
     *
     * @return the previous file name, so the caller can delete the file it just orphaned.
     */
    suspend fun setLoadingLottieFile(fileName: String?): String? {
        var previous: String? = null
        dataStore.edit { prefs ->
            previous = prefs[Keys.loadingLottieFile]
            val clean = fileName?.trim().orEmpty()
            if (clean.isEmpty()) prefs.remove(Keys.loadingLottieFile)
            else prefs[Keys.loadingLottieFile] = clean
        }
        return previous
    }

    suspend fun setLoadingImageSize(dp: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.loadingImageSize] =
                dp.coerceIn(MinLoadingImageSizeDp, MaxLoadingImageSizeDp).toString()
        }
    }

    suspend fun setLoadingCircleSize(dp: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.loadingCircleSize] =
                dp.coerceIn(MinLoadingCircleSizeDp, MaxLoadingCircleSizeDp).toString()
        }
    }

    suspend fun setShowLoadingScreen(show: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.showLoadingScreen] = show.toString() }
    }

    suspend fun setScannerBeepEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.scannerBeepEnabled] = enabled.toString() }
    }

    suspend fun setScannerBeep(id: String) {
        dataStore.edit { prefs -> prefs[Keys.scannerBeep] = ScanBeep.fromId(id).id }
    }

    suspend fun setSaleSoundEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.saleSoundEnabled] = enabled.toString() }
    }

    suspend fun setSaleSound(id: String) {
        dataStore.edit { prefs -> prefs[Keys.saleSound] = SaleSound.fromId(id).id }
    }

    /**
     * Chooses the flourish: a [SaleAnimation] id, or [SaleAnimationLottieId] for the imported file.
     *
     * The Lottie sentinel is written verbatim rather than normalised through `SaleAnimation.fromId`,
     * which would reject it; anything else is normalised so an unknown id still lands on a glyph
     * this build can draw.
     */
    suspend fun setSaleAnimation(id: String) {
        dataStore.edit { prefs ->
            prefs[Keys.saleAnimation] =
                if (id == SaleAnimationLottieId) id else SaleAnimation.fromId(id).id
        }
    }

    /**
     * Stores the imported animation's file name, replacing whatever was there.
     *
     * @return the previous file name, so the caller can delete the file it just orphaned.
     */
    suspend fun setSaleLottieFile(fileName: String?): String? {
        var previous: String? = null
        dataStore.edit { prefs ->
            previous = prefs[Keys.saleLottieFile]
            val clean = fileName?.trim().orEmpty()
            if (clean.isEmpty()) prefs.remove(Keys.saleLottieFile)
            else prefs[Keys.saleLottieFile] = clean
        }
        return previous
    }

    /** Size of the sale flourish, clamped to [MinSaleAnimationSizeDp]..[MaxSaleAnimationSizeDp]. */
    suspend fun setSaleAnimationSize(dp: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.saleAnimationSize] =
                dp.coerceIn(MinSaleAnimationSizeDp, MaxSaleAnimationSizeDp).toString()
        }
    }

    /**
     * Stores the picked sale sound's file name, replacing whatever was there.
     *
     * @return the previous file name, so the caller can delete the file it just orphaned.
     */
    suspend fun setSaleSoundFile(fileName: String?): String? {
        var previous: String? = null
        dataStore.edit { prefs ->
            previous = prefs[Keys.saleSoundFile]
            val clean = fileName?.trim().orEmpty()
            if (clean.isEmpty()) prefs.remove(Keys.saleSoundFile)
            else prefs[Keys.saleSoundFile] = clean
        }
        return previous
    }

    /**
     * Brings the sale sound back to its defaults: off, built-in chime, no picked file.
     *
     * @return the file names that are no longer referenced, for the caller to delete. Both sounds'
     *   files are reported so `resetScanner`, which is the section's reset, can share the cleanup.
     */
    suspend fun resetSaleSound(): List<String> {
        val previous = mutableListOf<String>()
        dataStore.edit { prefs ->
            prefs[Keys.saleSoundFile]?.let { previous += it }
            prefs.remove(Keys.saleSoundEnabled)
            prefs.remove(Keys.saleSound)
            prefs.remove(Keys.saleSoundFile)
            prefs.remove(Keys.saleAnimation)
            prefs.remove(Keys.saleAnimationSize)
        }
        return previous
    }

    /** Waits before the **same** code is accepted again, clamped to its own bounds. */
    suspend fun setScanDelay(millis: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.scanDelay] =
                millis.coerceIn(MinScanDelayMillis, MaxScanDelayMillis).toString()
        }
    }

    /** Waits before a **different** code is accepted, clamped to its own — lower — bounds. */
    suspend fun setProductChangeDelay(millis: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.productChangeDelay] =
                millis.coerceIn(MinProductChangeDelayMillis, MaxProductChangeDelayMillis).toString()
        }
    }

    suspend fun setShowScanPopup(show: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.showScanPopup] = show.toString() }
    }

    suspend fun setScanVibrationEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.scanVibrationEnabled] = enabled.toString() }
    }

    suspend fun setScanVibration(id: String) {
        dataStore.edit { prefs -> prefs[Keys.scanVibration] = ScanVibration.fromId(id).id }
    }

    /** Vibration amplitude, clamped to [MinScanVibrationStrength]..[MaxScanVibrationStrength]. */
    suspend fun setScanVibrationStrength(amplitude: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.scanVibrationStrength] = amplitude
                .coerceIn(MinScanVibrationStrength, MaxScanVibrationStrength)
                .toString()
        }
    }

    /**
     * Stores the picked beep's file name, replacing whatever was there.
     *
     * @return the previous file name, so the caller can delete the file it just orphaned.
     */
    suspend fun setScannerBeepFile(fileName: String?): String? {
        var previous: String? = null
        dataStore.edit { prefs ->
            previous = prefs[Keys.scannerBeepFile]
            val clean = fileName?.trim().orEmpty()
            if (clean.isEmpty()) prefs.remove(Keys.scannerBeepFile)
            else prefs[Keys.scannerBeepFile] = clean
        }
        return previous
    }

    /**
     * Brings the scanner back to its defaults: beep on, built-in tone, one second between scans,
     * popup on.
     *
     * @return the picked-beep file name that is no longer referenced, for the caller to delete.
     */
    suspend fun resetScanner(): String? {
        var previous: String? = null
        dataStore.edit { prefs ->
            previous = prefs[Keys.scannerBeepFile]
            prefs.remove(Keys.scannerBeepEnabled)
            prefs.remove(Keys.scannerBeep)
            prefs.remove(Keys.scannerBeepFile)
            prefs.remove(Keys.saleSoundEnabled)
            prefs.remove(Keys.saleSound)
            prefs.remove(Keys.saleSoundFile)
            prefs.remove(Keys.saleAnimation)
            prefs.remove(Keys.saleAnimationSize)
            prefs.remove(Keys.scanDelay)
            prefs.remove(Keys.showScanPopup)
            prefs.remove(Keys.scanVibrationEnabled)
            prefs.remove(Keys.scanVibration)
            prefs.remove(Keys.scanVibrationStrength)
        }
        return previous
    }

    /**
     * Brings the startup screen back to its default icon and motion.
     *
     * @return the picked-image file name that is no longer referenced, for the caller to delete.
     */
    suspend fun resetLoadingScreen(): String? {
        var previous: String? = null
        dataStore.edit { prefs ->
            previous = prefs[Keys.loadingImage]
            prefs.remove(Keys.loadingIcon)
            prefs.remove(Keys.loadingImage)
            prefs.remove(Keys.loadingImageSize)
            prefs.remove(Keys.loadingSpin)
            prefs.remove(Keys.loadingCircle)
            prefs.remove(Keys.loadingCircleSize)
            prefs.remove(Keys.showLoadingScreen)
        }
        return previous
    }

    /**
     * Clears every colour override and returns to the default palette.
     *
     * All five channels are cleared the same way — the override key *and* the stored colour are
     * removed, so the channel goes back to "never customised". Text included: it previously wrote
     * `useText = true` plus [DefaultTextArgb], on the theory that text's `null` meant "keep the
     * base palette's ink" and therefore could not be trusted to reset. That left the text toggle
     * switched on by a "reset" — the opposite of what the button says — and the stale colour in
     * the key. A cleared text channel now resolves to the default ink at read time — see
     * `resolveColorScheme`, which no longer leaves the ink to the base palette — producing the
     * same black the old write did, without leaving the override behind.
     */
    suspend fun resetAppearance() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.useBackground)
            prefs.remove(Keys.background)
            prefs.remove(Keys.useButton)
            prefs.remove(Keys.button)
            prefs.remove(Keys.usePanel)
            prefs.remove(Keys.panel)
            prefs.remove(Keys.useText)
            prefs.remove(Keys.text)
            prefs.remove(Keys.useHighlight)
            prefs.remove(Keys.highlight)
        }
    }

    /**
     * Applies [theme] as the active palette, in **one** `edit` block.
     *
     * Every channel is written together and switched on, for the same reason a restore is atomic:
     * a theme is one visual statement, and applying it key by key would let the app repaint with
     * the new background against the old highlight for the duration of the write.
     *
     * Channels the theme does not carry are **cleared**, not left alone.
     *
     * This used to skip a null [ColorTheme.textArgb], on the theory that a preset should not undo
     * a text colour chosen by hand. That was wrong, and produced the worst kind of bug: applying
     * **Midnight** wrote its near-white text, then applying Sage or Ocean — which carry no text
     * colour — left the white behind. Every label became white on a pale page and the app looked
     * empty. A theme is a complete palette, so switching to one has to clear whatever the previous
     * one set; otherwise "apply a theme" is not a total operation and depends on history.
     *
     * Clearing the text *override* (rather than writing black) is the right reset: it returns the
     * channel to the palette's own ink, which is what a fresh install uses and what the theme was
     * designed against.
     */
    suspend fun applyTheme(theme: ColorTheme) {
        dataStore.edit { prefs ->
            prefs[Keys.useBackground] = true.toString()
            prefs[Keys.background] = theme.backgroundArgb.toString()
            prefs[Keys.useButton] = true.toString()
            prefs[Keys.button] = theme.buttonArgb.toString()
            prefs[Keys.usePanel] = true.toString()
            prefs[Keys.panel] = theme.panelArgb.toString()
            prefs[Keys.useHighlight] = true.toString()
            prefs[Keys.highlight] = theme.highlightArgb.toString()

            // A theme that carries no ink is **not** "leave text alone": it is "this palette does
            // not define one, so use the app's default ink". The difference matters because
            // removing the override (what this used to do) hands the channel back to the base
            // palette, and the base palette is not the one the user just picked — it left the
            // stored ARGB sitting there as well, so re-enabling the override later re-applied the
            // *previous* theme's colour. That is the Midnight → saved theme → white-on-white bug.
            prefs[Keys.useText] = true.toString()
            prefs[Keys.text] = (theme.textArgb ?: DefaultTextArgb).toString()
        }
    }

    /**
     * Stores a new theme at the front of the saved list.
     *
     * Newest first, so a theme just saved is the first thing the user sees on returning to the
     * picker — the alternative buries it under everything saved before.
     */
    suspend fun saveTheme(theme: ColorTheme) {
        dataStore.edit { prefs ->
            val existing = decodeThemes(prefs[Keys.savedThemes])
            prefs[Keys.savedThemes] = encodeThemes(listOf(theme) + existing)
        }
    }

    /** Removes a saved theme by id. Deleting an unknown id is a no-op. */
    suspend fun deleteTheme(id: String) {
        dataStore.edit { prefs ->
            val remaining = decodeThemes(prefs[Keys.savedThemes]).filterNot { it.id == id }
            prefs[Keys.savedThemes] = encodeThemes(remaining)
        }
    }

    /**
     * Merges settings read from a backup into this device.
     *
     * **One `edit` block, not thirty.** `dataStore.edit` is atomic, and applying the theme one key
     * at a time would let the app render a half-restored palette — a background from the backup
     * with the local text colour still on top of it — for the duration of the write. The whole
     * merge landing at once is the difference between a repaint and a flash of unreadable UI.
     *
     * Only non-null fields are written, so a field the file did not carry keeps its local value
     * (see [ImportedSettings]). The id-based fields are normalised through the same `fromId`
     * helpers the setters use, because a file can name a font or a tone that this build does not
     * have — that falls back to the default rather than storing an id nothing recognises.
     *
     * Deliberately untouched: the export folder and the three picked file names. See
     * [ImportedSettings] for why — they name files on the exporting device.
     *
     * @return true when anything was written.
     */
    suspend fun applyImported(imported: ImportedSettings): Boolean {
        if (imported.isEmpty) return false
        dataStore.edit { prefs ->
            // ---- Shop ---------------------------------------------------------
            imported.taxRateBasisPoints?.let {
                prefs[Keys.taxRate] = it.coerceIn(0, 10_000).toString()
            }
            imported.cardFeeRateBasisPoints?.let {
                prefs[Keys.cardFeeRate] = it.coerceIn(0, 10_000).toString()
            }
            imported.cardFeeFixedCents?.let {
                prefs[Keys.cardFeeFixed] = it.coerceIn(0L, MaxCardFeeFixedCents).toString()
            }
            imported.showMoneyDecimals?.let {
                prefs[Keys.showMoneyDecimals] = it.toString()
            }
            imported.currencyId?.let { prefs[Keys.currency] = Currency.fromId(it).id }
            imported.languageTag?.let { prefs[Keys.languageTag] = it }

            // ---- Appearance ---------------------------------------------------
            imported.fontFamilyId?.let { prefs[Keys.fontFamily] = AppFontFamily.fromId(it).id }
            imported.textSizeId?.let { prefs[Keys.textSize] = TextSize.fromId(it).id }
            imported.motionSpeedId?.let { prefs[Keys.motionSpeed] = MotionSpeed.fromId(it).id }
            imported.useAppBackgroundColor?.let { prefs[Keys.useBackground] = it.toString() }
            imported.backgroundColorArgb?.let { prefs[Keys.background] = it.toString() }
            imported.useAppButtonColor?.let { prefs[Keys.useButton] = it.toString() }
            imported.buttonColorArgb?.let { prefs[Keys.button] = it.toString() }
            imported.useAppPanelColor?.let { prefs[Keys.usePanel] = it.toString() }
            imported.panelColorArgb?.let { prefs[Keys.panel] = it.toString() }
            imported.useAppTextColor?.let { prefs[Keys.useText] = it.toString() }
            imported.textColorArgb?.let { prefs[Keys.text] = it.toString() }
            imported.useAppHighlightColor?.let { prefs[Keys.useHighlight] = it.toString() }
            imported.highlightColorArgb?.let { prefs[Keys.highlight] = it.toString() }
            imported.savedThemes?.takeIf { it.isNotEmpty() }?.let { incoming ->
                prefs[Keys.savedThemes] = encodeThemes(
                    mergeThemes(incoming, decodeThemes(prefs[Keys.savedThemes]))
                )
            }

            // ---- Startup screen -----------------------------------------------
            imported.showLoadingScreen?.let { prefs[Keys.showLoadingScreen] = it.toString() }
            imported.loadingIconId?.let {
                prefs[Keys.loadingIcon] = LoadingIcon.fromId(it).id
            }
            imported.loadingSpinId?.let {
                prefs[Keys.loadingSpin] = LoadingSpin.fromId(it).id
            }
            imported.loadingCircleId?.let {
                prefs[Keys.loadingCircle] = LoadingCircle.fromId(it).id
            }
            imported.loadingImageSizeDp?.let {
                prefs[Keys.loadingImageSize] = it
                    .coerceIn(MinLoadingImageSizeDp, MaxLoadingImageSizeDp).toString()
            }
            imported.loadingCircleSizeDp?.let {
                prefs[Keys.loadingCircleSize] = it
                    .coerceIn(MinLoadingCircleSizeDp, MaxLoadingCircleSizeDp).toString()
            }

            // ---- Scanner --------------------------------------------------------
            imported.scannerBeepEnabled?.let { prefs[Keys.scannerBeepEnabled] = it.toString() }
            imported.scannerBeepId?.let { prefs[Keys.scannerBeep] = ScanBeep.fromId(it).id }
            imported.saleSoundEnabled?.let { prefs[Keys.saleSoundEnabled] = it.toString() }
            imported.saleSoundId?.let { prefs[Keys.saleSound] = SaleSound.fromId(it).id }
            imported.saleAnimationId?.let {
                prefs[Keys.saleAnimation] = SaleAnimation.fromId(it).id
            }
            imported.saleAnimationSizeDp?.let {
                prefs[Keys.saleAnimationSize] = it
                    .coerceIn(MinSaleAnimationSizeDp, MaxSaleAnimationSizeDp).toString()
            }
            imported.scanDelayMillis?.let {
                prefs[Keys.scanDelay] = it
                    .coerceIn(MinScanDelayMillis, MaxScanDelayMillis).toString()
            }
            imported.productChangeDelayMillis?.let {
                prefs[Keys.productChangeDelay] = it
                    .coerceIn(MinProductChangeDelayMillis, MaxProductChangeDelayMillis).toString()
            }
            imported.showScanPopup?.let { prefs[Keys.showScanPopup] = it.toString() }
            imported.scanVibrationEnabled?.let {
                prefs[Keys.scanVibrationEnabled] = it.toString()
            }
            imported.scanVibrationId?.let {
                prefs[Keys.scanVibration] = ScanVibration.fromId(it).id
            }
            imported.scanVibrationStrength?.let {
                prefs[Keys.scanVibrationStrength] = it
                    .coerceIn(MinScanVibrationStrength, MaxScanVibrationStrength).toString()
            }
        }
        return true
    }

    /**
     * Applies only the appearance half of an imported settings block.
     *
     * Used by the Wi-Fi import: the desktop can change the palette, so its backup has to be able
     * to carry that back. It deliberately stops at colours, font and text size — tax rates,
     * currency, language and scanner choices belong to the phone and should not be replaced by a
     * routine stock transfer.
     */
    suspend fun applyImportedAppearance(imported: ImportedSettings): Boolean {
        if (!imported.hasAppearance) return false
        dataStore.edit { prefs ->
            imported.fontFamilyId?.let { prefs[Keys.fontFamily] = AppFontFamily.fromId(it).id }
            imported.textSizeId?.let { prefs[Keys.textSize] = TextSize.fromId(it).id }
            imported.motionSpeedId?.let { prefs[Keys.motionSpeed] = MotionSpeed.fromId(it).id }
            imported.useAppBackgroundColor?.let { prefs[Keys.useBackground] = it.toString() }
            imported.backgroundColorArgb?.let { prefs[Keys.background] = it.toString() }
            imported.useAppButtonColor?.let { prefs[Keys.useButton] = it.toString() }
            imported.buttonColorArgb?.let { prefs[Keys.button] = it.toString() }
            imported.useAppPanelColor?.let { prefs[Keys.usePanel] = it.toString() }
            imported.panelColorArgb?.let { prefs[Keys.panel] = it.toString() }
            imported.useAppTextColor?.let { prefs[Keys.useText] = it.toString() }
            imported.textColorArgb?.let { prefs[Keys.text] = it.toString() }
            imported.useAppHighlightColor?.let { prefs[Keys.useHighlight] = it.toString() }
            imported.highlightColorArgb?.let { prefs[Keys.highlight] = it.toString() }
            imported.savedThemes?.takeIf { it.isNotEmpty() }?.let { incoming ->
                prefs[Keys.savedThemes] = encodeThemes(
                    mergeThemes(incoming, decodeThemes(prefs[Keys.savedThemes]))
                )
            }
        }
        return true
    }
}

// ---- Saved colour themes ---------------------------------------------------
//
// Stored as one delimited string rather than JSON. A theme is five numbers and a label, so the
// "format" is trivial; the hand-rolled encoder below avoids threading a serialiser through a file
// that otherwise has no idea what JSON is, and keeps a corrupt entry from taking the app's whole
// settings load down with it.
//
// Format: one theme per line, fields separated by '|' —
//   id|name|background|button|panel|highlight|text
// where `name` is escaped (see escapeField) and `text` may be empty for "not set".

private const val ThemeFieldSeparator = '|'
private const val ThemeRecordSeparator = '\n'

/**
 * Adds [incoming] themes to [existing] by id, newest first.
 *
 * Merging rather than replacing on a transfer is deliberate: the desktop may have an older copy
 * of the phone's list, and a stock transfer must not delete a theme the user saved on the phone
 * afterwards. The file's copy wins for an id both sides know, so a renamed or recoloured theme
 * still updates.
 */
private fun mergeThemes(incoming: List<ColorTheme>, existing: List<ColorTheme>): List<ColorTheme> {
    val byId = LinkedHashMap<String, ColorTheme>()
    incoming.forEach { byId[it.id] = it }
    existing.forEach { byId.putIfAbsent(it.id, it) }
    return byId.values.toList()
}

/** Renders [themes] into the storage format. */
internal fun encodeThemes(themes: List<ColorTheme>): String =
    themes.joinToString(ThemeRecordSeparator.toString()) { theme ->
        listOf(
            theme.id,
            escapeField(theme.name.orEmpty()),
            theme.backgroundArgb.toString(),
            theme.buttonArgb.toString(),
            theme.panelArgb.toString(),
            theme.highlightArgb.toString(),
            theme.textArgb?.toString().orEmpty(),
        ).joinToString(ThemeFieldSeparator.toString())
    }

/**
 * Parses the storage format, dropping anything malformed.
 *
 * **Drops rather than throws.** This runs inside the settings flow's `map`, so an exception here
 * would take down every preference the app reads — one bad theme would cost the user their export
 * folder, language and database settings. A theme that cannot be parsed is simply not offered; the
 * user can re-save it in seconds, and everything else keeps working.
 */
internal fun decodeThemes(encoded: String?): List<ColorTheme> {
    if (encoded.isNullOrBlank()) return emptyList()
    return encoded.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val f = line.split(ThemeFieldSeparator)
            if (f.size < 7) return@mapNotNull null
            val background = f[2].toLongOrNull() ?: return@mapNotNull null
            val button = f[3].toLongOrNull() ?: return@mapNotNull null
            val panel = f[4].toLongOrNull() ?: return@mapNotNull null
            val highlight = f[5].toLongOrNull() ?: return@mapNotNull null
            val id = f[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ColorTheme(
                id = id,
                // Saved themes always carry a literal name; a built-in's would be a resource id,
                // which is meaningless across a reinstall and is looked up from the enum instead.
                name = unescapeField(f[1]).ifBlank { null },
                backgroundArgb = background,
                buttonArgb = button,
                panelArgb = panel,
                highlightArgb = highlight,
                textArgb = f[6].toLongOrNull(),
            )
        }
        .toList()
}

/**
 * Escapes the separators a user-typed theme name could contain.
 *
 * Without this a name like "Red|Blue" would split into an extra field and silently shift every
 * number along by one — the theme would still parse, just with the wrong colours.
 */
private fun escapeField(value: String): String = value
    .replace("\\", "\\\\")
    .replace("|", "\\p")
    .replace("\n", "\\n")

/** Reverses [escapeField]. Order matters: backslashes first, or `\\p` would decode to `|`. */
private fun unescapeField(value: String): String {
    val out = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '\\' && i + 1 < value.length) {
            when (value[i + 1]) {
                'p' -> out.append('|')
                'n' -> out.append('\n')
                '\\' -> out.append('\\')
                else -> out.append(value[i + 1])
            }
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}
