package com.example.stockapplication.data.transfer

import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The sending half of a Wi-Fi transfer: POSTs an archive to a desktop app that is listening.
 *
 * Deliberately the mirror image of [BackupServer] — same protocol, same hand-written HTTP — so the
 * two sides can be read side by side and a change to one is obviously a change to the other.
 *
 * ## Why the phone connects rather than the desktop polling
 *
 * Exporting is initiated by the user on the phone at the moment they want it. Having the phone dial
 * out means the desktop needs no way of discovering the phone, and the transfer cannot begin until
 * the person holding the phone presses the button.
 */
class BackupUploader {

    /**
     * Sends [bytes] to `host:port`.
     *
     * @param onProgress called with (sent, total) as the body streams, for a progress bar.
     * @return null on success, or the failure to show the user.
     */
    suspend fun upload(
        host: String,
        port: Int,
        bytes: ByteArray,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): TransferFailure? = withContext(Dispatchers.IO) {
        val address = host.trim()
        if (address.isEmpty()) return@withContext TransferFailure.CannotConnect

        val socket = Socket()
        try {
            // A connect timeout matters more than a read timeout here: a wrong IP on a Wi-Fi
            // network usually fails fast, but a device that has gone to sleep can hang for the
            // platform default of several minutes, which reads as a frozen app.
            socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MILLIS)
            socket.soTimeout = READ_TIMEOUT_MILLIS

            val output = socket.getOutputStream()
            val input = BufferedInputStream(socket.getInputStream())

            val head = buildString {
                append("POST ").append(BackupTransfer.UPLOAD_PATH).append(" HTTP/1.1\r\n")
                append("Host: ").append(address).append(':').append(port).append("\r\n")
                append("Content-Type: application/zip\r\n")
                append("Content-Length: ").append(bytes.size).append("\r\n")
                append(BackupTransfer.FORMAT_HEADER).append(": ")
                append(BackupTransfer.FORMAT_VALUE).append("\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(head.toByteArray(Charsets.US_ASCII))

            // Streamed in chunks rather than one `write(bytes)`: a 75 MB archive written in a single
            // call can take long enough that the user sees nothing move, and a mid-transfer failure
            // would be impossible to report progress for.
            var sent = 0L
            val chunk = 32 * 1024
            while (sent < bytes.size) {
                val length = minOf(chunk.toLong(), (bytes.size - sent)).toInt()
                output.write(bytes, sent.toInt(), length)
                sent += length
                onProgress(sent, bytes.size.toLong())
            }
            output.flush()

            val status = readStatusLine(input)
            // 200 is the only success this server sends. A 401 means the code was wrong; anything
            // else means something other than this app answered on that port.
            when {
                status == null -> TransferFailure.Unexpected("No response")
                status.contains(" 200") -> null
                status.contains(" 401") -> TransferFailure.WrongCode
                status.contains(" 413") -> TransferFailure.TooLarge
                else -> TransferFailure.Unexpected(status)
            }
        } catch (e: UnknownHostException) {
            TransferFailure.CannotConnect
        } catch (e: SocketTimeoutException) {
            TransferFailure.CannotConnect
        } catch (e: IOException) {
            // The commonest cause by far is "connection refused", which means nothing is listening:
            // a stopped server, or the wrong port.
            TransferFailure.CannotConnect
        } catch (e: SecurityException) {
            // Thrown when the INTERNET permission is missing from a build.
            TransferFailure.Unexpected(e.message)
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * Pings `host:port` to tell "wrong address" from "server not started".
     *
     * Kept separate from [upload] so the UI can offer a Test button that does not send a 75 MB file
     * just to find out the IP was mistyped.
     */
    suspend fun ping(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        val address = host.trim()
        if (address.isEmpty()) return@withContext false
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MILLIS)
            socket.soTimeout = READ_TIMEOUT_MILLIS
            socket.getOutputStream().apply {
                write(
                    (
                        "GET " + BackupTransfer.PING_PATH + " HTTP/1.1\r\n" +
                            "Host: " + address + ':' + port + "\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(Charsets.US_ASCII)
                )
                flush()
            }
            readStatusLine(BufferedInputStream(socket.getInputStream()))?.contains(" 200") == true
        } catch (e: Exception) {
            false
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun readStatusLine(input: BufferedInputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (builder.isEmpty()) null else builder.toString()
            if (byte == '\n'.code) return builder.toString().trimEnd('\r')
            builder.append(byte.toChar())
            if (builder.length > 512) return null
        }
    }

    private companion object {
        /** Long enough for a slow Wi-Fi handshake, short enough not to look frozen. */
        const val CONNECT_TIMEOUT_MILLIS = 10_000

        /** A stalled read mid-upload is a broken transfer, not a slow one. */
        const val READ_TIMEOUT_MILLIS = 30_000
    }
}
