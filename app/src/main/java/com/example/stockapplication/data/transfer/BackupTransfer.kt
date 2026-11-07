package com.example.stockapplication.data.transfer

/**
 * The wire protocol shared by the phone and the desktop app.
 *
 * ## Why hand-rolled HTTP
 *
 * The whole exchange is one `POST` with a binary body and one health probe. Ktor or NanoHTTPD would
 * add roughly a megabyte across the app bundle to solve that, and this codebase already hand-writes
 * its JSON writer and its ZIP reader for the same reason. What is written here is a deliberately
 * small subset of HTTP/1.1 — enough for a desktop app or `curl` to talk to, and no more.
 *
 * ## What is deliberately absent
 *
 * No TLS, no authentication beyond [CODE_HEADER], no keep-alive, no chunked encoding. Each of those
 * would be real work and none of them makes a LAN transfer meaningfully safer; the honest mitigation
 * is a short-lived server and a code the user reads off their own screen. The UI says the traffic is
 * unencrypted rather than implying otherwise.
 */
object BackupTransfer {

    /**
     * Default listening port.
     *
     * Fixed rather than random so it is easy to type on the desktop, and high enough to avoid the
     * usual service range. A user with a conflict can change it in the UI.
     */
    const val DEFAULT_PORT = 8756

    /** Where the receiving server accepts an upload. */
    const val UPLOAD_PATH = "/backup"

    /**
     * Where a browser can download this phone's own backup.
     *
     * The desktop page cannot listen for inbound connections, so the phone-to-desktop direction
     * is a pull: the desktop asks the phone for its archive instead of the phone pushing it.
     * The same one-time code protects it as [UPLOAD_PATH].
     */
    const val EXPORT_PATH = "/export"

    /** Cheap liveness probe, so the desktop can tell "wrong IP" from "server not started". */
    const val PING_PATH = "/ping"

    /**
     * Header carrying the one-time code the phone displays.
     *
     * Without it, any device on the same Wi-Fi could push a backup and overwrite the shop's data.
     * The code is generated per server start and discarded with it.
     */
    const val CODE_HEADER = "X-Transfer-Code"

    /** Stamped on the upload so a mismatched desktop build fails loudly rather than silently. */
    const val FORMAT_HEADER = "X-Backup-Format"

    /** Matches [com.example.stockapplication.data.JSON_FORMAT]; duplicated here to stay Android-free. */
    const val FORMAT_VALUE = "stockapplication.backup"

    /**
     * Allowed origins for cross-origin requests.
     *
     * A literal `*` rather than an echo of the caller's `Origin`, and that is a deliberate, narrow
     * decision rather than laziness:
     *
     *  - the server is bound to the LAN and lives for one transfer, so the usual reason to avoid
     *    `*` (an internet-exposed API whose cookies could be ridden from any site) does not apply;
     *  - the transfer carries **no credentials** — the one-time code is sent as an explicit header,
     *    not as a cookie or `Authorization`, so a malicious page gains nothing by calling it that a
     *    direct socket could not already do on a network it is connected to;
     *  - echoing an `Origin` would mean the server implemented logic a header can defeat, which is
     *    worse than a constant that cannot be argued with.
     *
     * The one-time code remains the actual gate. CORS is a browser mechanism, not a security
     * boundary, and treating it as one would be the mistake — see the note in [BackupServer].
     */
    const val CORS_ALLOW_ORIGIN = "*"

    /**
     * Headers a browser must be permitted to send on the preflighted upload.
     *
     * `Content-Type: application/zip` and the code header are both non-simple, so a browser sends
     * an `OPTIONS` preflight first and will refuse the real request unless these are listed.
     */
    const val CORS_ALLOW_HEADERS = "Content-Type, $CODE_HEADER, $FORMAT_HEADER"

    /** Methods the browser is allowed to use. Only these three are ever routed. */
    const val CORS_ALLOW_METHODS = "GET, POST, OPTIONS"

    /**
     * How long a browser may cache the preflight result, in seconds.
     *
     * Short on purpose. The server is alive for minutes at most, so a long-lived preflight cache
     * would refer to a port that no longer exists and produce a confusing failure on the next
     * attempt.
     */
    const val CORS_MAX_AGE_SECONDS = 60

    /**
     * Hard ceiling on an accepted upload.
     *
     * Equal to the archive reader's own limit, so a file that arrives is one the importer will
     * accept. Checked against `Content-Length` before a byte is read, and again while reading, so a
     * client cannot lie about the size and then stream indefinitely.
     */
    const val MAX_UPLOAD_BYTES = 512L * 1024 * 1024

    /** How long the server keeps waiting for a connection before giving up on its own. */
    const val IDLE_TIMEOUT_MILLIS = 5L * 60 * 1000

    /** How long a single read may stall before the connection is dropped. */
    const val READ_TIMEOUT_MILLIS = 60 * 1000

    /** Length of the one-time code, in digits. Six is short enough to retype, long enough to guess-proof. */
    const val CODE_LENGTH = 6

    /**
     * A six-digit code derived from a seed.
     *
     * Zero-padded to [CODE_LENGTH] so it always looks like a code rather than a number — a display
     * reading "4021" next to one reading "018842" makes the second look like a different kind of
     * thing.
     *
     * Pure, so the generator is testable without starting a server or touching a random source.
     */
    fun codeFrom(seed: Long): String {
        // Absolute value, then fold into six digits. `Long.MIN_VALUE` cannot be negated, so it is
        // handled explicitly rather than left to overflow to a negative modulus.
        val magnitude = if (seed == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(seed)
        val sixDigits = magnitude % 1_000_000L
        return sixDigits.toString().padStart(CODE_LENGTH, '0')
    }

    /**
     * Compares two codes without an early exit.
     *
     * The timing difference is irrelevant against a human retyping a code across a room, but a
     * constant-time compare costs nothing and removes the question. Both sides are normalised so a
     * leading zero typed as a space still matches.
     */
    fun codesMatch(expected: String, received: String?): Boolean {
        val a = expected.trim()
        val b = received?.trim() ?: return false
        if (a.length != b.length) return false
        var difference = 0
        for (index in a.indices) {
            difference = difference or (a[index].code xor b[index].code)
        }
        return difference == 0
    }
}

/** Progress of an upload or a receive, for the screen to show. */
sealed interface TransferProgress {
    data object Idle : TransferProgress

    /** Connected and transferring. [bytes] of [totalBytes]; total is 0 when unknown. */
    data class Running(val bytes: Long, val totalBytes: Long) : TransferProgress
}

/** Why a transfer failed, in terms the UI can turn into a sentence. */
sealed interface TransferFailure {
    /** Nothing was listening at that address. Usually the wrong IP or a stopped server. */
    data object CannotConnect : TransferFailure

    /** The server answered, but refused the code. */
    data object WrongCode : TransferFailure

    /** The file is bigger than [BackupTransfer.MAX_UPLOAD_BYTES]. */
    data object TooLarge : TransferFailure

    /** The bytes arrived but were not a backup this app can read. */
    data object NotABackup : TransferFailure

    /** The server was stopped, or the screen closed, before anything arrived. */
    data object Cancelled : TransferFailure

    /** Anything else, with the platform's message for diagnosis. */
    data class Unexpected(val message: String?) : TransferFailure
}
