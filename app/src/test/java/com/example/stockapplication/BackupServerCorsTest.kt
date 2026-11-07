package com.example.stockapplication

import com.example.stockapplication.data.transfer.BackupServer
import com.example.stockapplication.data.transfer.BackupTransfer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CORS behaviour of the receiving server.
 *
 * CORS is a **browser** mechanism, not a security boundary — the one-time code is what authorises
 * an upload. These tests exist because getting the headers wrong does not fail loudly: the browser
 * swallows the response and the user is told "could not connect" when the real answer was a 401.
 */
@RunWith(RobolectricTestRunner::class)
class BackupServerCorsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var port = 0

    @Before
    fun pickPort() {
        port = java.net.ServerSocket(0).use { it.localPort }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** Sends a bare request and returns the full raw response, headers included. */
    private fun raw(method: String, path: String, extraHeaders: String = ""): String {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 5_000)
            socket.soTimeout = 5_000
            val head = buildString {
                append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                append("Host: 127.0.0.1\r\n")
                append("Origin: http://localhost:3000\r\n")
                append(extraHeaders)
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                flush()
            }
            val out = ByteArrayOutputStream()
            socket.getInputStream().copyTo(out)
            return out.toString("US-ASCII")
        }
    }

    @Test
    fun `an OPTIONS preflight is answered without a code`() = runBlocking {
        val server = BackupServer(scope = scope, port = port, codeSeed = 1L)
        assertTrue(server.start())
        try {
            // No X-Transfer-Code on purpose: a browser cannot send the custom headers it is asking
            // permission to use, so requiring the code here would break every cross-origin upload.
            val response = raw(
                method = "OPTIONS",
                path = BackupTransfer.UPLOAD_PATH,
                extraHeaders = "Access-Control-Request-Method: POST\r\n" +
                    "Access-Control-Request-Headers: content-type\r\n",
            )

            assertTrue("expected 204, got: $response", response.contains(" 204"))
            assertTrue(
                "preflight must allow the origin",
                response.contains("Access-Control-Allow-Origin: ${BackupTransfer.CORS_ALLOW_ORIGIN}"),
            )
            assertTrue(
                "preflight must advertise the code header",
                response.contains(BackupTransfer.CODE_HEADER),
            )
            assertTrue(
                "preflight must advertise POST",
                response.contains("Access-Control-Allow-Methods") && response.contains("POST"),
            )
            assertTrue(
                "preflight should be short-lived",
                response.contains("Access-Control-Max-Age: ${BackupTransfer.CORS_MAX_AGE_SECONDS}"),
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun `the ping response carries CORS headers`() = runBlocking {
        val server = BackupServer(scope = scope, port = port, codeSeed = 1L)
        assertTrue(server.start())
        try {
            val response = raw("GET", BackupTransfer.PING_PATH)
            assertTrue(response.contains(" 200"))
            assertTrue(
                "a browser must be able to read the ping body",
                response.contains("Access-Control-Allow-Origin"),
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun `a rejected upload still carries CORS headers`() = runBlocking {
        val server = BackupServer(scope = scope, port = port, codeSeed = 1L)
        assertTrue(server.start())
        try {
            // The important case: without CORS on the error response a browser reports a network
            // failure, and the user is told the connection failed rather than that the code was
            // wrong.
            val response = raw(
                method = "POST",
                path = BackupTransfer.UPLOAD_PATH,
                extraHeaders = "Content-Length: 0\r\n" +
                    "${BackupTransfer.CODE_HEADER}: 000000\r\n",
            )

            assertTrue("expected 401, got: $response", response.contains(" 401"))
            assertTrue(
                "an error response must still be readable by the browser",
                response.contains("Access-Control-Allow-Origin"),
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun `an unknown path carries CORS headers too`() = runBlocking {
        val server = BackupServer(scope = scope, port = port, codeSeed = 1L)
        assertTrue(server.start())
        try {
            val response = raw("GET", "/nope")
            assertTrue(response.contains(" 404"))
            assertTrue(response.contains("Access-Control-Allow-Origin"))
        } finally {
            server.close()
        }
    }

    @Test
    fun `CORS does not replace the code as the actual gate`() = runBlocking {
        // Guards against anyone "fixing" a CORS problem by loosening the auth: a preflight needs no
        // code, but a real upload still does.
        val server = BackupServer(scope = scope, port = port, codeSeed = 1L)
        assertTrue(server.start())
        try {
            val preflight = raw("OPTIONS", BackupTransfer.UPLOAD_PATH)
            assertTrue(preflight.contains(" 204"))

            val uploadWithoutCode = raw(
                method = "POST",
                path = BackupTransfer.UPLOAD_PATH,
                extraHeaders = "Content-Length: 0\r\n",
            )
            assertTrue(
                "upload without a code must still be refused",
                uploadWithoutCode.contains(" 401"),
            )
            assertFalse(uploadWithoutCode.contains(" 200"))
        } finally {
            server.close()
        }
    }
}
