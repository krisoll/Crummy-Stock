package com.example.stockapplication.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * What the scanner plays for an accepted scan.
 *
 * The same two-part model as [LoadingImage], for the same reason: a picked file and a built-in tone
 * are mutually exclusive, and "an id plus an optional file" would make every caller guess which one
 * wins.
 */
sealed interface ScanBeepSound {

    /** One of the [ScanBeep] tones, synthesised on first use. */
    data class BuiltIn(val beep: ScanBeep) : ScanBeepSound

    /** One of the [SaleSound] chimes, synthesised on first use. */
    data class SaleChime(val sound: SaleSound) : ScanBeepSound

    /** An audio file picked from the device, copied into app storage under [fileName]. */
    data class Picked(val fileName: String) : ScanBeepSound

    companion object {
        /**
         * A stored file wins over the tone, so picking a sound takes effect immediately and
         * clearing it falls back to whichever tone is still selected.
         */
        fun from(beepId: String?, fileName: String?): ScanBeepSound {
            val name = fileName?.trim().orEmpty()
            return if (name.isEmpty()) BuiltIn(ScanBeep.fromId(beepId)) else Picked(name)
        }

        /** The scanner's version for the sale-sound list. Same file-wins rule as [from]. */
        fun fromSale(soundId: String?, fileName: String?): ScanBeepSound {
            val name = fileName?.trim().orEmpty()
            return if (name.isEmpty()) SaleChime(SaleSound.fromId(soundId)) else Picked(name)
        }
    }
}

/**
 * The scanner's beep: built-in tones synthesised once into the cache, plus the player that fires
 * them.
 *
 * **Why synthesise instead of shipping files.** Five short WAVs in `res/raw` would add a few
 * hundred kilobytes to the APK for sounds that are nothing but a tone. Generating them costs one
 * `sin()` loop on first use, and the result is cached as a real file so [SoundPool] can load it the
 * normal way.
 *
 * **Why [SoundPool] rather than MediaPlayer.** Scanning is a burst activity: a cashier can ring up
 * a dozen items in a few seconds, and each beep has to start within milliseconds of the code being
 * recognised. [SoundPool] keeps decoded samples in memory and mixes overlapping plays
 * (`maxStreams`), where MediaPlayer would need a `prepare()` and a fresh instance per beep. The
 * picked-file path needs the same guarantees, so it goes through [SoundPool] too.
 *
 * Everything here is best-effort: an unreadable picked file, or a device with no audio output, must
 * never break scanning. Failures are logged and the caller simply hears nothing.
 */
object ScanBeepStore {

    private const val TAG = "ScanBeepStore"

    /**
     * Folder under `files/` for picked sounds: they must survive a cache wipe.
     *
     * Shared by the scan beep and the sale chime. They are both short audio files that the user
     * chose in the same way, so a file picked for one is a perfectly good choice for the other —
     * and one folder means the cleanup on uninstall is a single directory.
     */
    private const val PICKED_FOLDER = "scan_beeps"

    /** Folder under `cache/` for generated tones: they can always be made again. */
    private const val TONE_FOLDER = "scan_tones"

    /** How many beeps may overlap. Enough for a fast burst; more would just be noise. */
    private const val MaxStreams = 4

    /**
     * How many *sale* chimes may overlap. Deliberately one.
     *
     * A chime is twice the length of a beep, and a second sale landing on top of the first would
     * turn a celebration into noise. With a single stream, a quick next sale steals the channel and
     * restarts it, which reads as "that sale just went through" — the correct meaning.
     */
    private const val SaleMaxStreams = 1

    /** Sample rate of the generated tones. 44.1 kHz is universally supported by [SoundPool]. */
    const val SampleRate = 44_100

    /**
     * Bump when any tone's shape changes, so cached files are not silently reused with a sound
     * that no longer matches the code.
     */
    const val ToneVersion = 1

    /** Bytes in the RIFF header [writeWav] emits; used to tell a real file from a stub. */
    private const val WavHeaderBytes = 44L

    // ---- Files -------------------------------------------------------------

    fun pickedDir(context: Context): File =
        File(context.applicationContext.filesDir, PICKED_FOLDER).apply { mkdirs() }

    fun toneDir(context: Context): File =
        File(context.applicationContext.cacheDir, TONE_FOLDER).apply { mkdirs() }

    /** Resolves a stored name to its file. Returns null for a null/blank/unsafe name. */
    fun pickedFile(context: Context, fileName: String?): File? {
        val name = fileName?.trim().orEmpty()
        if (!isSafeName(name)) return null
        return File(pickedDir(context), name)
    }

    /**
     * The file to load [sound] from, or null when it cannot be resolved.
     *
     * Generates the built-in tone on demand, so the first scan of a fresh install is just as
     * audible as the second.
     */
    fun soundFile(context: Context, sound: ScanBeepSound): File? = when (sound) {
        is ScanBeepSound.Picked -> pickedFile(context, sound.fileName)?.takeIf { it.isFile }
        is ScanBeepSound.BuiltIn -> cachedToneFile(context, sound.beep)
        is ScanBeepSound.SaleChime -> cachedSaleToneFile(context, sound.sound)
    }

    private fun isSafeName(name: String): Boolean =
        name.isNotEmpty() &&
            !name.contains('/') &&
            !name.contains('\\') &&
            name != "." &&
            name != ".."

    /** File name of the synthesised tone, e.g. `tone-classic-v1.wav`. */
    fun toneFileName(beep: ScanBeep): String = "tone-${beep.id}-v$ToneVersion.wav"

    /** File name of a synthesised sale chime, e.g. `sale-chime-v1.wav`. */
    fun saleToneFileName(sound: SaleSound): String = "sale-${sound.id}-v$ToneVersion.wav"

    /** The generated sale chime, creating it if this is the first use. */
    fun cachedSaleToneFile(context: Context, sound: SaleSound): File? {
        val target = File(toneDir(context), saleToneFileName(sound))
        if (target.isFile && target.length() > WavHeaderBytes) return target
        return runCatching {
            writeWav(target, renderSaleTone(sound))
            target.takeIf { it.isFile && it.length() > WavHeaderBytes }
        }.onFailure { Log.w(TAG, "Could not generate sale sound ${sound.id}", it) }.getOrNull()
    }

    /** The generated tone for [beep], creating it if this is the first use. */
    fun cachedToneFile(context: Context, beep: ScanBeep): File? {
        val target = File(toneDir(context), toneFileName(beep))
        if (target.isFile && target.length() > WavHeaderBytes) return target
        return runCatching {
            writeWav(target, renderTone(beep))
            target.takeIf { it.isFile && it.length() > WavHeaderBytes }
        }.onFailure { Log.w(TAG, "Could not generate tone ${beep.id}", it) }.getOrNull()
    }

    /**
     * Copies a picked audio file into app storage.
     *
     * Copied rather than referenced because the picker's read grant is short-lived — the same
     * reasoning as [LoadingImageStore]. The stored name always ends in `.wav`; [SoundPool] sniffs
     * the actual content, and one fixed extension keeps [fileNameFor] trivial.
     *
     * @return the stored file name, or null when the file could not be read or written.
     */
    fun save(context: Context, uri: Uri, now: Long = System.currentTimeMillis()): String? =
        runCatching {
            val fileName = fileNameFor(now)
            val target = File(pickedDir(context), fileName)
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
            if (!copied || target.length() <= 0L) {
                target.delete()
                return@runCatching null
            }
            fileName
        }.onFailure { Log.w(TAG, "Could not save scan beep", it) }.getOrNull()

    /** Deletes a stored picked beep. Missing files and blank names are ignored. */
    fun delete(context: Context, fileName: String?) {
        runCatching { pickedFile(context, fileName)?.delete() }
            .onFailure { Log.w(TAG, "Could not delete scan beep", it) }
    }

    /** A fresh name per pick, so replacing a beep never overwrites a file still being played. */
    fun fileNameFor(now: Long = System.currentTimeMillis()): String =
        "beep-${now.toString(36)}-${counter.incrementAndGet().toString(36)}.wav"

    private val counter = AtomicLong(0L)

    // ---- Playback ----------------------------------------------------------

    /**
     * The scanner's pool, and the sale pool, kept apart on purpose.
     *
     * The scanner releases its pool when the camera dialog closes ([release]); if the sale chime
     * shared it, a sale rung up right after closing the scanner would play into a released pool and
     * be silently lost. They also want different mixing: four beeps overlapping is fine, two chimes
     * are not.
     */
    private var pool: SoundPool? = null
    private var salePool: SoundPool? = null

    /** Loaded sample ids per pool, keyed by absolute file path. */
    private val loaded = mutableMapOf<String, Int>()
    private val saleLoaded = mutableMapOf<String, Int>()

    /** Files whose load is already in flight per pool, so a burst does not queue the same file. */
    private val loading = mutableSetOf<String>()
    private val saleLoading = mutableSetOf<String>()

    /**
     * Plays [sound], generating or loading it first if this is the first use.
     *
     * Must be called on the main thread: [SoundPool] delivers load callbacks on the looper it was
     * created on, and every caller here is a Compose effect.
     */
    fun play(context: Context, sound: ScanBeepSound, volume: Float = 1f) {
        val file = soundFile(context, sound) ?: return
        val soundPool = pool ?: createPool(MaxStreams).also { pool = it }
        playFrom(soundPool, loaded, loading, file, volume)
    }

    /**
     * Plays the chime for a completed sale.
     *
     * Separate from [play] because it outlives the scanner: it uses its own pool, which is never
     * released when the camera closes, and a single stream, so a burst of sales restarts the chime
     * instead of stacking it on top of itself.
     */
    fun playSale(context: Context, sound: ScanBeepSound, volume: Float = 1f) {
        val file = soundFile(context, sound) ?: return
        val soundPool = salePool ?: createPool(SaleMaxStreams).also { salePool = it }
        playFrom(soundPool, saleLoaded, saleLoading, file, volume)
    }

    /** Shared load-then-play path for both pools. */
    private fun playFrom(
        soundPool: SoundPool,
        loadedIds: MutableMap<String, Int>,
        loadingIds: MutableSet<String>,
        file: File,
        volume: Float,
    ) {
        val key = file.absolutePath

        loadedIds[key]?.let { sampleId ->
            soundPool.play(sampleId, volume, volume, 1, 0, 1f)
            return
        }

        // Not decoded yet. Load it and play from the callback, so even the very first sound of a
        // session is audible instead of being silently dropped as a "warm-up".
        if (!loadingIds.add(key)) return
        soundPool.load(key, 1)
        soundPool.setOnLoadCompleteListener { sp, sample, status ->
            loadingIds.remove(key)
            if (status != 0) {
                Log.w(TAG, "Could not load sound $key (status $status)")
                return@setOnLoadCompleteListener
            }
            loadedIds[key] = sample
            sp.play(sample, volume, volume, 1, 0, 1f)
        }
    }

    private fun createPool(maxStreams: Int): SoundPool = SoundPool.Builder()
        .setMaxStreams(maxStreams)
        .setAudioAttributes(
            AudioAttributes.Builder()
                // SONIFICATION, not MEDIA: a short UI tone. Keeps it at a sensible level, off the
                // music stream, and out of the way of calls and notifications.
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    /** Frees the scanner's decoded samples. Called when the scanner leaves the screen. */
    fun release() {
        runCatching { pool?.release() }
        pool = null
        loaded.clear()
        loading.clear()
    }

    /**
     * Frees the sale chime's samples.
     *
     * Not called on every sale: the chime is small, and a shop rung up several times a minute
     * would pay a decode per sale for no reason. Meant for the app going away, so a long session
     * does not hold a decoded clip forever.
     */
    fun releaseSalePool() {
        runCatching { salePool?.release() }
        salePool = null
        saleLoaded.clear()
        saleLoading.clear()
    }

    // ---- Synthesis ---------------------------------------------------------

    /**
     * Renders one tone as 16-bit mono PCM.
     *
     * The shapes are described because the *shape* is what makes one beep recognisable over the
     * others at a glance, not its exact frequency:
     *
     *  - **CLASSIC** — one 2 kHz blip with a 12 ms attack and a short decay. The supermarket till
     *    sound: bright, quick, unmistakable over shop noise.
     *  - **CHIRP** — a sine sweeping 1.2 kHz → 2.6 kHz. A rising pitch reads as "registered", the
     *    way rising intonation does in speech.
     *  - **CLICK** — 40 ms of filtered high noise. A mechanical click for users who find a till
     *    beep intrusive.
     *  - **BLIP** — a lower 880 Hz tone. Audible through background music without cutting through
     *    a conversation.
     *  - **DIGITAL** — three fast rising notes. Sits *between* spoken words, where a single blip is
     *    easy to miss.
     */
    fun renderTone(beep: ScanBeep): ShortArray {
        val durationMillis: Int
        val generator: (Double) -> Double
        when (beep) {
            ScanBeep.CLASSIC -> {
                durationMillis = 120
                generator = { t -> blip(t, 2_000.0, 0.012) }
            }

            ScanBeep.CHIRP -> {
                durationMillis = 160
                generator = { t -> chirp(t, 0.160) }
            }

            ScanBeep.CLICK -> {
                durationMillis = 40
                generator = { t -> click(t, 0.040) }
            }

            ScanBeep.BLIP -> {
                durationMillis = 140
                generator = { t -> blip(t, 880.0, 0.020) }
            }

            ScanBeep.DIGITAL -> {
                durationMillis = 260
                generator = { t -> digital(t, 0.260) }
            }
        }

        val count = (SampleRate * durationMillis / 1_000.0).toInt().coerceAtLeast(1)
        return ShortArray(count) { index ->
            val t = index / SampleRate.toDouble()
            // 0.6 of full scale: a beep that is deliberately loud but not a jump-scare.
            (generator(t) * (0.6 * Short.MAX_VALUE))
                .coerceIn(-Short.MAX_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                .toInt()
                .toShort()
        }
    }

    /** A decaying sine with a short attack: the body of a "beep". */
    private fun blip(t: Double, frequency: Double, attackSeconds: Double): Double =
        sin(2.0 * PI * frequency * t) * exp(-t / 0.045) * attack(t, attackSeconds)

    /**
     * An exponential frequency sweep.
     *
     * The phase is the integral of the instantaneous frequency rather than `2πft`: using the
     * instantaneous frequency directly would make the pitch jump at every sample and turn the
     * sweep into a buzz.
     */
    private fun chirp(t: Double, durationSeconds: Double): Double {
        val fromHz = 1_200.0
        val toHz = 2_600.0
        val attackSeconds = 0.015
        val ratio = toHz / fromHz
        val progress = (t / durationSeconds).coerceIn(0.0, 1.0)
        val phase =
            2.0 * PI * fromHz * durationSeconds * (ratio.pow(progress) - 1.0) / ln(ratio)
        val envelope = (1.0 - progress) * attack(t, attackSeconds)
        return sin(phase) * envelope
    }

    /** Short high-frequency noise: a "click" rather than a tone. */
    private fun click(t: Double, durationSeconds: Double): Double {
        val progress = (t / durationSeconds).coerceIn(0.0, 1.0)
        // Deterministic (no RNG): the same click every time, which is what a UI sound should be.
        val noise = sin(2.0 * PI * 3_000.0 * t) * 0.6 + sin(2.0 * PI * 5_100.0 * t) * 0.4
        return noise * exp(-progress * 6.0)
    }

    /** Three rising notes in quick succession. */
    private fun digital(t: Double, durationSeconds: Double): Double {
        val notes = doubleArrayOf(1_500.0, 2_000.0, 2_500.0)
        val noteSeconds = durationSeconds / notes.size
        val index = (t / noteSeconds).toInt().coerceIn(0, notes.lastIndex)
        val local = t - index * noteSeconds
        val envelope = exp(-local / 0.020) * (1.0 - index * 0.15)
        return sin(2.0 * PI * notes[index] * local) * envelope
    }

    /** Rises 0 → 1 over [seconds], so the tone does not begin with an audible click. */
    private fun attack(t: Double, seconds: Double): Double =
        if (seconds <= 0.0) 1.0 else (t / seconds).coerceIn(0.0, 1.0)

    // ---- Sale chimes -------------------------------------------------------
    //
    // These are allowed to be longer than a beep (up to ~0.8 s against a beep's 0.26 s) because
    // they fire once per sale rather than once per item, and none of them has to sit under the
    // next sound. They are still short enough not to postpone the receipt the user is waiting for.

    /**
     * Renders one sale chime as 16-bit mono PCM.
     *
     * The shapes, and why each one exists:
     *
     *  - **CASH_REGISTER** — a mechanical "ka-ching": a short noise burst for the drawer, then two
     *    bright partials a fifth apart for the bell. The most literal reading of "a sale happened".
     *  - **CHIME** — a soft two-note major third (E5 → G5). The default: it reads as completion
     *    without being loud enough to startle a customer standing at the counter.
     *  - **ASCENDING** — three rising notes. Same idea as the scanner's DIGITAL tone, but slower and
     *    in a major triad, so it resolves rather than merely announcing.
     *  - **BELL** — one struck note with its harmonics decaying together, the way a real bell does.
     *    For a quiet shop where a chime is too electronic.
     *  - **COINS** — a handful of short high metallic pings at irregular spacing: the sound of change
     *    dropping into a drawer.
     *  - **SUCCESS** — a fast major arpeggio that lands hard on the octave. The most celebratory of
     *    the set, and the one to pick if the chime needs to carry across a busy room.
     */
    fun renderSaleTone(sound: SaleSound): ShortArray {
        val durationMillis: Int
        val generator: (Double) -> Double
        when (sound) {
            SaleSound.CASH_REGISTER -> {
                durationMillis = 620
                generator = { t -> cashRegister(t) }
            }

            SaleSound.CHIME -> {
                durationMillis = 560
                generator = { t -> bell(t, 659.25, 0.55) + bell(t - 0.16, 783.99, 0.40) }
            }

            SaleSound.ASCENDING -> {
                durationMillis = 640
                generator = { t -> arpeggio(t, doubleArrayOf(523.25, 659.25, 783.99), 0.16) }
            }

            SaleSound.BELL -> {
                durationMillis = 800
                generator = { t -> bell(t, 880.0, 0.5) + bell(t, 1760.0, 0.22) + bell(t, 2640.0, 0.12) }
            }

            SaleSound.COINS -> {
                durationMillis = 520
                generator = { t -> coins(t) }
            }

            SaleSound.SUCCESS -> {
                durationMillis = 700
                generator = { t ->
                    arpeggio(t, doubleArrayOf(523.25, 659.25, 783.99, 1046.50), 0.10)
                }
            }
        }

        val count = (SampleRate * durationMillis / 1_000.0).toInt().coerceAtLeast(1)
        return ShortArray(count) { index ->
            val t = index / SampleRate.toDouble()
            // 0.5 of full scale: a chime is the loudest thing the app plays, but it still must not
            // clip when several partials line up on the same sample.
            (generator(t) * (0.5 * Short.MAX_VALUE))
                .coerceIn(-Short.MAX_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                .toInt()
                .toShort()
        }
    }

    /**
     * A single struck note.
     *
     * [t] is the time since the note started, so a negative value is silence — that is what lets
     * several notes be placed at different offsets by simple addition. The envelope is a fast
     * attack and a long exponential decay, which is what makes a sine read as a metal bar rather
     * than as a test tone.
     */
    private fun bell(t: Double, frequency: Double, gain: Double): Double {
        if (t < 0.0) return 0.0
        return sin(2.0 * PI * frequency * t) * exp(-t / 0.30) * attack(t, 0.006) * gain
    }

    /** Notes of [frequencies] played in sequence, [noteSeconds] each, at the same gain. */
    private fun arpeggio(t: Double, frequencies: DoubleArray, noteSeconds: Double): Double {
        var sum = 0.0
        frequencies.forEachIndexed { index, frequency ->
            val start = index * noteSeconds
            // Each note rings past its own slot into the next, the way overlapping notes on a real
            // keyboard do, so the arpeggio sounds played rather than sequenced.
            sum += bell(t - start, frequency, 1.0 - index * 0.12)
        }
        return sum
    }

    /** A drawer clunk followed by a two-partial bell: the till's "ka-ching". */
    private fun cashRegister(t: Double): Double {
        val clunkSeconds = 0.09
        val clunk = if (t < clunkSeconds) {
            // Filtered noise with a fast decay: a wooden knock rather than a hiss.
            val noise = sin(2.0 * PI * 180.0 * t) * 0.5 + sin(2.0 * PI * 420.0 * t) * 0.5
            noise * exp(-t / 0.030)
        } else {
            0.0
        }
        val chingStart = 0.07
        val chingOffset = t - chingStart
        val ching = if (chingOffset >= 0.0) {
            bell(chingOffset, 1_320.0, 0.55) + bell(chingOffset, 1_980.0, 0.30)
        } else {
            0.0
        }
        return clunk + ching
    }

    /**
     * Four short metallic pings at deliberately uneven spacing.
     *
     * Regular spacing would sound like a machine; real coins land in a jumble, so the offsets are
     * hand-picked rather than generated from an evenly divided duration.
     */
    private fun coins(t: Double): Double {
        val strikes = doubleArrayOf(0.0, 0.075, 0.135, 0.235)
        val pitches = doubleArrayOf(2_400.0, 3_100.0, 2_750.0, 3_400.0)
        var sum = 0.0
        strikes.forEachIndexed { index, start ->
            val offset = t - start
            if (offset >= 0.0) {
                sum += sin(2.0 * PI * pitches[index] * offset) *
                    exp(-offset / 0.045) * attack(offset, 0.002)
            }
        }
        return sum
    }



    /** Writes mono 16-bit PCM as a RIFF/WAVE stream, which [SoundPool] loads directly. */
    fun writeWav(target: File, samples: ShortArray, sampleRate: Int = SampleRate) {
        FileOutputStream(target).use { writeWav(it, samples, sampleRate) }
    }

    fun writeWav(out: OutputStream, samples: ShortArray, sampleRate: Int = SampleRate) {
        val dataBytes = samples.size * 2
        out.write(wavHeader(dataBytes, sampleRate))
        val buffer = ByteArray(dataBytes)
        samples.forEachIndexed { index, sample ->
            // Little-endian, as the WAVE format requires.
            buffer[index * 2] = (sample.toInt() and 0xFF).toByte()
            buffer[index * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        out.write(buffer)
        out.flush()
    }

    private fun wavHeader(dataBytes: Int, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign

        fun int32(value: Int) = byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )

        fun int16(value: Int) = byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
        )

        return "RIFF".toByteArray(Charsets.US_ASCII) +
            int32(36 + dataBytes) +
            "WAVE".toByteArray(Charsets.US_ASCII) +
            "fmt ".toByteArray(Charsets.US_ASCII) +
            int32(16) +
            int16(1) +
            int16(channels) +
            int32(sampleRate) +
            int32(byteRate) +
            int16(blockAlign) +
            int16(bitsPerSample) +
            "data".toByteArray(Charsets.US_ASCII) +
            int32(dataBytes)
    }

    /** Peak amplitude of a rendered sound, so a test can assert a shape is not silent. */
    fun peak(samples: ShortArray): Int = samples.maxOfOrNull { abs(it.toInt()) } ?: 0

    /** Zero-crossing count, a cheap stand-in for "how high is this tone" in tests. */
    fun zeroCrossings(samples: ShortArray): Int {
        var count = 0
        for (index in 1 until samples.size) {
            if (samples[index - 1] < 0 && samples[index] >= 0) count++
        }
        return count
    }
}
