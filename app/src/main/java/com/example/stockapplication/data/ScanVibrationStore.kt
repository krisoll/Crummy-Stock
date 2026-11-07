package com.example.stockapplication.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Fires the scanner's haptic feedback.
 *
 * **Why a separate object from [ScanBeepStore].** The two have different failure modes and
 * different lifetimes: the beep decodes audio into memory and must be released, while the vibrator
 * is a system service that costs nothing to hold. Keeping them apart also means a device with no
 * vibrator (many tablets, some POS terminals) loses only the buzz, not the sound.
 *
 * **Why every call is defensive.** `createWaveform` throws `IllegalArgumentException` for an
 * amplitude outside 0..255, and `hasVibrator()` is not reliably honest across OEMs. Scanner
 * feedback is a nicety, so a device that refuses to vibrate must never break scanning.
 */
object ScanVibrationStore {

    private const val TAG = "ScanVibrationStore"

    /**
     * Whether this device can vibrate at all.
     *
     * Checked before showing the vibration controls, so the settings page does not offer a switch
     * that can only ever do nothing. Defaults to false when the service cannot be resolved.
     */
    fun isSupported(context: Context): Boolean = runCatching {
        vibrator(context)?.hasVibrator() == true
    }.getOrDefault(false)

    /**
     * Plays [pattern] once at [amplitude].
     *
     * `amplitude == 0` is passed straight through: the platform documents 0 as "use the device's
     * own intensity", which is the only portable way to ask for a default — `DEFAULT_AMPLITUDE`
     * only exists from API 26 and this app supports API 24.
     *
     * @return true when a vibration was actually requested.
     */
    fun vibrate(
        context: Context,
        pattern: ScanVibration,
        amplitude: Int = DefaultScanVibrationStrength,
    ): Boolean {
        // `return` is not allowed inside `runCatching`, so the early exits are folded into the
        // result instead.
        val result = runCatching {
            val device = vibrator(context)
            if (device == null || (!device.hasVibrator())) {
                false
            } else {
                val safeAmplitude = amplitude.coerceIn(
                    MinScanVibrationStrength,
                    MaxScanVibrationStrength,
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitudes = amplitudesFor(pattern, safeAmplitude)
                    if (amplitudes.any { it > 0 }) {
                        device.vibrate(
                            VibrationEffect.createWaveform(
                                pattern.timings,
                                amplitudes,
                                // -1: do not repeat. A scan is a one-shot event.
                                -1,
                            )
                        )
                    }
                } else {
                    // API 24-25 has no amplitude control, so the timings are all that survive.
                    // Deliberately not emulating strength by scaling durations: a half-strength
                    // tap that is merely *shorter* reads as a different pattern, not a quieter
                    // one.
                    @Suppress("DEPRECATION")
                    device.vibrate(pattern.timings, -1)
                }
                true
            }
        }.onFailure { Log.w(TAG, "Could not vibrate for a scan", it) }
        return result.getOrDefault(false)
    }

    /**
     * One amplitude per timing entry.
     *
     * `createWaveform(timings, amplitudes, repeat)` reads `timings[i]` as the length of segment
     * `i` and `amplitudes[i]` as what to play during it — so the alternation lives here, not in the
     * pattern data. Because every pattern starts with a leading silence, the **odd** indices are
     * the pulses and the even ones are the silences around them.
     *
     * The array always matches [ScanVibration.timings] in length, which `createWaveform` requires.
     */
    fun amplitudesFor(pattern: ScanVibration, amplitude: Int): IntArray =
        IntArray(pattern.timings.size) { index ->
            if (isPulse(index)) amplitude else 0
        }

    /** True when timing entry [index] is a pulse rather than a silence. */
    fun isPulse(index: Int): Boolean = index % 2 == 1

    /**
     * Total on-time of a pattern, in milliseconds.
     *
     * Used by the settings screen to describe what the user is choosing, and by tests to assert no
     * pattern outlasts the shortest allowed scan delay — a buzz still running when the next scan
     * lands would feel like one long, broken vibration.
     */
    fun onTimeMillis(pattern: ScanVibration): Long =
        pattern.timings.filterIndexed { index, _ -> isPulse(index) }.sum()

    /**
     * The system vibrator.
     *
     * Goes through `VibratorManager` on API 31+, where the bare `VIBRATOR_SERVICE` lookup is
     * deprecated; older releases fetch the service the classic way.
     */
    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            }.onFailure { Log.w(TAG, "Could not resolve VibratorManager", it) }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }.getOrNull()
        }
}
