package com.example.stockapplication

import com.example.stockapplication.data.transfer.BackupServer
import com.example.stockapplication.data.transfer.BackupTransfer
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the temporary HTTP server the desktop page talks to.
 *
 * These are real sockets on 127.0.0.1 rather than a mocked transport: the part worth testing is
 * the hand-written request parsing, the CORS preflight, and the one-transfer lifetime, none of
 * which a mock would cover.
 */
class BackupServerTest {

    private lateinit var scope: CoroutineScope
    private var server: BackupServer? = null

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    @After
    fun tearDown() {
        server?.close()
        scope.cancel()
    }

    @Test
    fun preflightIsAnsweredWithoutACodeAndAdvertisesTheUploadHeaders() {
        val running = start(export = null)

        val response = request(running.port, "OPTIONS", BackupTransfer.UPLOAD_PATH)

        assertEquals(204, response.status)
        assertEquals(BackupTransfer.CORS_ALLOW_ORIGIN, response.header("Access-Control-Allow-Origin"))
        assertTrue(response.header("Access-Control-Allow-Headers").orEmpty().contains(BackupTransfer.CODE_HEADER))
        assertTrue(running.server.isRunning)
    }

    @Test
    fun wrongCodeIsRejectedWithCorsHeadersAndTheServerKeepsWaiting() {
        val running = start(export = null)

        val response = request(
            running.port,
            "GET",
            BackupTransfer.EXPORT_PATH,
            code = "000000",
        )

        assertEquals(401, response.status)
        // Without this header a browser cannot even read the 401; it reports a network failure.
        assertEquals(BackupTransfer.CORS_ALLOW_ORIGIN, response.header("Access-Control-Allow-Origin"))
        assertTrue(running.server.isRunning)
    }

    @Test
    fun exportReturnsTheArchiveAndStopsTheServer() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val exported = CountDownLatch(1)
        val running = start(export = bytes)
        running.server.onExported = { exported.countDown() }

        val response = request(
            running.port,
            "GET",
            BackupTransfer.EXPORT_PATH,
            code = BackupTransfer.codeFrom(CODE_SEED),
        )

        assertEquals(200, response.status)
        assertArrayEquals(bytes, response.body)
        assertEquals("application/zip", response.header("Content-Type"))
        assertEquals(BackupTransfer.CORS_ALLOW_ORIGIN, response.header("Access-Control-Allow-Origin"))
        assertTrue("onExported was not called", exported.await(5, TimeUnit.SECONDS))
        assertFalse("the server must retire after one archive", running.server.isRunning)
    }

    @Test
    fun uploadIsReceivedAndStopsTheServer() {
        val received = CountDownLatch(1)
        var arrived: ByteArray? = null
        val running = start(export = null)
        running.server.onReceived = {
            arrived = it
            received.countDown()
        }

        val response = request(
            running.port,
            "POST",
            BackupTransfer.UPLOAD_PATH,
            code = BackupTransfer.codeFrom(CODE_SEED),
            body = byteArrayOf(9, 8, 7),
        )

        assertEquals(200, response.status)
        assertTrue("onReceived was not called", received.await(5, TimeUnit.SECONDS))
        assertArrayEquals(byteArrayOf(9, 8, 7), arrived)
        assertFalse(running.server.isRunning)
    }

    private data class Running(val server: BackupServer, val port: Int)

    private fun start(export: ByteArray?): Running {
        val port = ServerSocket(0).use { it.localPort }
        val created = BackupServer(scope = scope, port = port, codeSeed = CODE_SEED)
        if (export != null) {
            created.exportProvider = { export }
        }
        assertTrue("could not bind port $port", created.start())
        server = created
        return Running(created, port)
    }

    private data class Response(
        val status: Int,
        val body: ByteArray,
        val headers: Map<String, List<String>>,
    ) {
        fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
    }

    private fun request(
        port: Int,
        method: String,
        path: String,
        code: String? = null,
        body: ByteArray? = null,
    ): Response {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("Origin", "null")
        if (code != null) connection.setRequestProperty(BackupTransfer.CODE_HEADER, code)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/zip")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
        }

        return try {
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            Response(status, bytes, connection.headerFields)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CODE_SEED = 123_456L
    }
}
