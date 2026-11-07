package com.example.stockapplication.data.transfer

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The receiving half of a Wi-Fi transfer: a tiny HTTP server that accepts exactly one backup.
 *
 * ## Why it dies quickly
 *
 * A listening socket on a shop's Wi-Fi is reachable by every device on that network, so the design
 * minimises the window rather than trying to make the window safe. The server:
 *
 *  - is only created when the transfer screen asks for it, never at app start;
 *  - accepts **one** successful upload and then shuts itself down;
 *  - gives up after [BackupTransfer.IDLE_TIMEOUT_MILLIS] with no connection;
 *  - is stopped by [close] the moment the screen closes or the app leaves the foreground.
 *
 * ## Why a one-time code
 *
 * Short-lived is not the same as safe: thirty seconds is ample for an automated scan on a shared
 * network. The code the user reads off their own screen is what actually authorises the upload, and
 * it dies with the server.
 *
 * ## Threading
 *
 * [start] returns immediately; the accept loop runs on [Dispatchers.IO]. One connection is handled
 * at a time — two simultaneous uploads would be two writers racing on one database, and there is no
 * reason for a phone to receive two backups at once.
 */
class BackupServer(
    private val scope: CoroutineScope,
    val port: Int = BackupTransfer.DEFAULT_PORT,
    codeSeed: Long = System.nanoTime(),
) {

    /** The code the desktop must send. Shown on screen, discarded when the server stops. */
    val code: String = BackupTransfer.codeFrom(codeSeed)

    private val running = AtomicBoolean(false)
    private var acceptJob: Job? = null
    private var serverSocket: ServerSocket? = null

    /** Called with the received bytes once an upload completes. */
    var onReceived: ((ByteArray) -> Unit)? = null

    /**
     * Supplies this phone's archive when the desktop downloads it.
     *
     * A suspending lambda rather than a pre-built `ByteArray` so the snapshot is taken at the
     * moment the desktop asks, not when the server starts. Null means "this server only receives";
     * [BackupTransfer.EXPORT_PATH] then answers 404 rather than serving something stale.
     */
    var exportProvider: (suspend () -> ByteArray?)? = null

    /** Called after the archive was written to the desktop, for the "sent" state in the UI. */
    var onExported: (() -> Unit)? = null

    /** Called as bytes arrive, for a progress bar. */
    var onProgress: ((bytes: Long, total: Long) -> Unit)? = null

    /** Called when the server stops for any reason, so the UI can stop showing "waiting". */
    var onStopped: (() -> Unit)? = null

    /** True while the socket is bound and accepting. */
    val isRunning: Boolean get() = running.get()

    /**
     * Binds the port and starts accepting.
     *
     * @return false when the port is already in use — a real possibility on a busy machine, and the
     *   reason the caller surfaces it rather than retrying silently.
     */
    fun start(): Boolean {
        if (running.get()) return true

        val socket = try {
            ServerSocket().apply {
                // Reuse so a socket left in TIME_WAIT from a previous attempt does not block the
                // user from trying again on the same port.
                reuseAddress = true
                bind(InetSocketAddress(port))
                soTimeout = BackupTransfer.IDLE_TIMEOUT_MILLIS.toInt()
            }
        } catch (e: IOException) {
            return false
        }

        serverSocket = socket
        running.set(true)

        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                // `soTimeout` makes accept() throw once nothing has arrived for the idle window,
                // which is how the server retires itself rather than waiting forever.
                while (isActive && running.get()) {
                    val client = try {
                        socket.accept()
                    } catch (e: SocketTimeoutException) {
                        break
                    } catch (e: IOException) {
                        break
                    }
                    handle(client)
                }
            } finally {
                close()
            }
        }
        return true
    }

    /** Stops the server and closes the port. Safe to call repeatedly, from any thread. */
    fun close() {
        if (!running.getAndSet(false)) return
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        onStopped?.invoke()
    }

    /**
     * Reads one request, decides whether to keep it, and closes the connection.
     *
     * Written against a raw [Socket] because the request is a single `POST` with a binary body; the
     * parsing needed is a request line, a handful of headers, and a length-delimited read.
     */
    private suspend fun handle(client: Socket) = withContext(Dispatchers.IO) {
        client.use { socket ->
            socket.soTimeout = BackupTransfer.READ_TIMEOUT_MILLIS
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            val request = try {
                readRequestHead(input)
            } catch (e: IOException) {
                null
            } ?: return@use

            // ---- CORS preflight ----------------------------------------------------------
            // Answered before routing and before the code check, because a browser sends it
            // without the custom headers it is asking permission to use — there is no code to
            // check yet, and refusing it here would make every cross-origin upload fail.
            if (request.method == "OPTIONS") {
                respond(
                    output = output,
                    status = "204 No Content",
                    body = ByteArray(0),
                    includeCors = true,
                )
                return@use
            }

            // ---- Health probe ------------------------------------------------------------
            // Answers "is anything there?" without touching the database, so the desktop can tell
            // a wrong IP from a server that is not running.
            if (request.method == "GET" && request.path == BackupTransfer.PING_PATH) {
                respond(output, "200 OK", """{"app":"crummystock","ready":true}""".toByteArray())
                return@use
            }

            // ---- Download (phone -> desktop) --------------------------------------------
            // The desktop page cannot listen for inbound connections, so the reverse direction
            // is a pull. Authorisation and the one-transfer contract are identical to an upload:
            // the code is checked before the archive is built, and the server retires after one
            // successful download.
            if (request.method == "GET" && request.path == BackupTransfer.EXPORT_PATH) {
                if (!BackupTransfer.codesMatch(code, request.headers[BackupTransfer.CODE_HEADER.lowercase()])) {
                    respond(output, "401 Unauthorized", "Bad code".toByteArray())
                    return@use
                }

                val provider = exportProvider
                if (provider == null) {
                    respond(output, "404 Not Found", "Export not enabled".toByteArray())
                    return@use
                }

                val bytes = try {
                    provider()
                } catch (e: Exception) {
                    respond(output, "500 Internal Server Error", "Could not build backup".toByteArray())
                    return@use
                }

                if (bytes == null || bytes.isEmpty()) {
                    respond(output, "404 Not Found", "No backup".toByteArray())
                    return@use
                }

                val sent = respond(
                    output = output,
                    status = "200 OK",
                    body = bytes,
                    contentType = "application/zip",
                    fileName = "stock-backup.zip",
                )
                // Only a completed download retires the server. A client that disconnected
                // half-way should be able to try again while the code is still on screen.
                if (sent) {
                    onExported?.invoke()
                    close()
                }
                return@use
            }

            if (request.method != "POST" || request.path != BackupTransfer.UPLOAD_PATH) {
                respond(output, "404 Not Found", "Not found".toByteArray())
                return@use
            }

            // ---- Authorisation -----------------------------------------------------------
            // Checked before reading a byte of the body: an unauthorised client should not be able
            // to make the phone spend memory on its payload.
            if (!BackupTransfer.codesMatch(code, request.headers[BackupTransfer.CODE_HEADER.lowercase()])) {
                respond(output, "401 Unauthorized", "Bad code".toByteArray())
                return@use
            }

            val declaredLength = request.headers["content-length"]?.toLongOrNull()
            if (declaredLength != null && declaredLength > BackupTransfer.MAX_UPLOAD_BYTES) {
                respond(output, "413 Payload Too Large", "Too large".toByteArray())
                return@use
            }

            // ---- Body --------------------------------------------------------------------
            val body = try {
                readBody(input, declaredLength)
            } catch (e: OversizeException) {
                respond(output, "413 Payload Too Large", "Too large".toByteArray())
                return@use
            } catch (e: IOException) {
                return@use
            }

            if (body.isEmpty()) {
                respond(output, "400 Bad Request", "Empty body".toByteArray())
                return@use
            }

            respond(output, "200 OK", """{"received":${body.size}}""".toByteArray())

            // Hand off, then stop accepting: one successful upload is the whole contract.
            onReceived?.invoke(body)
            close()
        }
    }

    /** A parsed request: only what this server needs to act on. */
    private data class RequestHead(
        val method: String,
        val path: String,
        /** Keys lower-cased, because HTTP header names are case-insensitive. */
        val headers: Map<String, String>,
    )

    /**
     * Reads the request line and headers.
     *
     * Returns null on anything malformed rather than throwing: a bad request from a stray port
     * scanner is noise, not an error worth surfacing.
     */
    private fun readRequestHead(input: BufferedInputStream): RequestHead? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null

        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            headers[name] = value
        }
        return RequestHead(
            method = parts[0].uppercase(),
            path = parts[1].substringBefore('?'),
            headers = headers,
        )
    }

    /** Thrown internally when the body exceeds the cap while streaming. */
    private class OversizeException : IOException()

    /**
     * Reads exactly the body.
     *
     * When `Content-Length` is present it is authoritative — that is what a well-behaved desktop app
     * or `curl` sends. Without it, bytes are read until the client closes, with the cap enforced as
     * they arrive, so a client cannot dodge the limit by omitting the header.
     */
    private fun readBody(input: BufferedInputStream, declaredLength: Long?): ByteArray {
        val buffer = ByteArrayOutputStream(
            (declaredLength ?: (64L * 1024)).coerceAtMost(8L * 1024 * 1024).toInt().coerceAtLeast(1024)
        )
        val chunk = ByteArray(16 * 1024)

        if (declaredLength != null) {
            var remaining = declaredLength
            while (remaining > 0) {
                val read = input.read(chunk, 0, minOf(chunk.size.toLong(), remaining).toInt())
                if (read < 0) break
                buffer.write(chunk, 0, read)
                remaining -= read
                onProgress?.invoke(buffer.size().toLong(), declaredLength)
            }
            return buffer.toByteArray()
        }

        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            buffer.write(chunk, 0, read)
            if (buffer.size().toLong() > BackupTransfer.MAX_UPLOAD_BYTES) throw OversizeException()
            onProgress?.invoke(buffer.size().toLong(), 0L)
        }
        return buffer.toByteArray()
    }

    /**
     * Writes a minimal HTTP response. Body length is always known, so chunking is never needed.
     *
     * CORS headers go on **every** response, not just the preflight. A browser discards the body of
     * a cross-origin response whose headers lack `Access-Control-Allow-Origin`, so without them the
     * caller would see a network error for what is really a 401 — and the desktop app would report
     * "could not connect" when the actual problem is the code.
     *
     * @param includeCors false only for responses where CORS would be meaningless. Kept as a
     *   parameter so the one exception is visible rather than omitted by accident.
     */
    private fun respond(
        output: OutputStream,
        status: String,
        body: ByteArray,
        contentType: String = "application/json; charset=utf-8",
        fileName: String? = null,
        includeCors: Boolean = true,
    ): Boolean {
        val head = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            if (contentType.isNotEmpty()) {
                append("Content-Type: ").append(contentType).append("\r\n")
            }
            if (fileName != null) {
                // A browser downloading the archive directly gets a sensible name; the HTML app
                // ignores this and reads the response as a Blob.
                append("Content-Disposition: attachment; filename=\"").append(fileName).append("\"\r\n")
            }
            append("Content-Length: ").append(body.size).append("\r\n")
            if (includeCors) {
                append("Access-Control-Allow-Origin: ")
                append(BackupTransfer.CORS_ALLOW_ORIGIN).append("\r\n")
                append("Access-Control-Allow-Methods: ")
                append(BackupTransfer.CORS_ALLOW_METHODS).append("\r\n")
                append("Access-Control-Allow-Headers: ")
                append(BackupTransfer.CORS_ALLOW_HEADERS).append("\r\n")
                append("Access-Control-Max-Age: ")
                append(BackupTransfer.CORS_MAX_AGE_SECONDS).append("\r\n")
                // Chrome's Private Network Access preflight asks for this when a page opened
                // from a file or local server talks to a LAN address.
                append("Access-Control-Allow-Private-Network: true\r\n")
            }
            append("Cache-Control: no-store\r\n")
            // No keep-alive: one request per connection keeps the socket lifetime obvious.
            append("Connection: close\r\n")
            append("\r\n")
        }
        return runCatching {
            output.write(head.toByteArray(Charsets.US_ASCII))
            output.write(body)
            output.flush()
            true
        }.getOrDefault(false)
    }

    /** Reads one CRLF-terminated line, returning null at EOF. */
    private fun readLine(input: BufferedInputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (builder.isEmpty()) null else builder.toString()
            when (byte) {
                '\n'.code -> return builder.toString().trimEnd('\r')
                else -> {
                    builder.append(byte.toChar())
                    // Guards against a client that never sends a newline and would otherwise grow
                    // this string without bound.
                    if (builder.length > 8192) return null
                }
            }
        }
    }
}
