package com.example.stockapplication

import com.example.stockapplication.data.AppSettings
import com.example.stockapplication.data.BackupArchive
import com.example.stockapplication.data.DatabaseSnapshot
import com.example.stockapplication.data.JsonImport
import com.example.stockapplication.data.Product
import com.example.stockapplication.data.toJson
import com.example.stockapplication.data.transfer.BackupServer
import com.example.stockapplication.data.transfer.BackupTransfer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Starts the real server and talks to it over a real socket.
 *
 * This is the test that proves the wire format is lossless: what the uploader sends is rebuilt on
 * the other side into the same product data, without anything having been reinterpreted. The pieces
 * are individually tested elsewhere — this is the only place they are exercised together.
 */
@RunWith(RobolectricTestRunner::class)
class BackupServerRoundTripTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * An ephemeral port.
     *
     * Hardcoding 8756 would make this test fail on a machine where the app (or a previous run) is
     * already listening there, which is the least useful possible failure.
     */
    private var port = 0

    @Before
    fun pickPort() {
        port = java.net.ServerSocket(0).use { it.localPort }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun sampleArchive(): ByteArray {
        val snapshot = DatabaseSnapshot(
            schemaVersion = 10,
            appVersionName = "test",
            categories = emptyList(),
            products = listOf(
                Product(id = 1, barcode = "A1", name = "Coffee", priceCents = 350, quantity = 12),
                Product(id = 2, barcode = "A2", name = "Tea", quantity = 4, favourite = true),
            ),
            orders = emptyList(),
            saleLines = emptyList(),
            legacySales = emptyList(),
            settings = AppSettings(),
            exportedAt = 1_700_000_000_000,
        )
        return BackupArchive.write(snapshot.toJson(), emptyMap())
    }

    /** A minimal client, so the test exercises the protocol rather than our own uploader. */
    private fun post(bytes: ByteArray, code: String?): String {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 5_000)
            socket.soTimeout = 5_000
            val head = buildString {
                append("POST ").append(BackupTransfer.UPLOAD_PATH).append(" HTTP/1.1\r\n")
                append("Host: 127.0.0.1\r\n")
                append("Content-Length: ").append(bytes.size).append("\r\n")
                if (code != null) {
                    append(BackupTransfer.CODE_HEADER).append(": ").append(code).append("\r\n")
                }
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                write(bytes)
                flush()
            }
            val response = ByteArrayOutputStream()
            socket.getInputStream().copyTo(response)
            return response.toString("US-ASCII")
        }
    }

    @Test
    fun `a correct upload arrives intact and imports back to the same data`() = runBlocking {
        val received = CompletableDeferred<ByteArray>()
        val server = BackupServer(scope = scope, port = port, codeSeed = 123_456L)
        server.onReceived = { received.complete(it) }
        assertTrue("server failed to bind port $port", server.start())

        try {
            val archive = sampleArchive()
            val response = post(archive, server.code)
            assertTrue("expected 200, got: $response", response.contains(" 200"))

            val bytes = withTimeout(10_000) { received.await() }
            assertEquals(archive.size, bytes.size)

            val contents = BackupArchive.read(bytes)
            val parsed = JsonImport.readBackup(contents.json)
            assertEquals(2, parsed.products.size)
            assertEquals("Coffee", parsed.products.first { it.id == 1L }.name)
            assertTrue(parsed.products.first { it.id == 2L }.favourite)
        } finally {
            server.close()
        }
    }

    @Test
    fun `a wrong code is rejected and nothing is received`() = runBlocking {
        val received = CompletableDeferred<ByteArray>()
        val server = BackupServer(scope = scope, port = port, codeSeed = 123_456L)
        server.onReceived = { received.complete(it) }
        assertTrue(server.start())

        try {
            val response = post(sampleArchive(), code = "000000")
            assertTrue("expected 401, got: $response", response.contains(" 401"))
            assertNull(
                "a rejected upload must not reach the importer",
                received.awaitOrNull(),
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun `a missing code is rejected`() = runBlocking {
        val server = BackupServer(scope = scope, port = port, codeSeed = 123_456L)
        assertTrue(server.start())
        try {
            val response = post(sampleArchive(), code = null)
            assertTrue("expected 401, got: $response", response.contains(" 401"))
        } finally {
            server.close()
        }
    }

    @Test
    fun `the server stops itself after one successful upload`() = runBlocking {
        val server = BackupServer(scope = scope, port = port, codeSeed = 7L)
        assertTrue(server.start())
        post(sampleArchive(), server.code)

        // The contract is one transfer, then gone. Give the accept loop a moment to unwind.
        var stopped = false
        repeat(50) {
            if (!server.isRunning) stopped = true
            if (!stopped) Thread.sleep(50)
        }
        assertTrue("server should have stopped after one upload", stopped)
    }

    @Test
    fun `the ping probe answers`() = runBlocking {
        val server = BackupServer(scope = scope, port = port, codeSeed = 7L)
        assertTrue(server.start())
        try {
            val response = Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 5_000)
                socket.soTimeout = 5_000
                socket.getOutputStream().apply {
                    write(
                        (
                            "GET " + BackupTransfer.PING_PATH + " HTTP/1.1\r\n" +
                                "Host: 127.0.0.1\r\nConnection: close\r\n\r\n"
                            ).toByteArray(Charsets.US_ASCII)
                        )
                    flush()
                }
                val out = ByteArrayOutputStream()
                socket.getInputStream().copyTo(out)
                out.toString("US-ASCII")
            }
            assertTrue("expected 200, got: $response", response.contains(" 200"))
        } finally {
            server.close()
        }
    }

    @Test
    fun `closing the server releases the port`() = runBlocking {
        val server = BackupServer(scope = scope, port = port, codeSeed = 7L)
        assertTrue(server.start())
        server.close()

        // Rebinding the same port proves the socket was actually released; a leaked listener would
        // make the user's second attempt fail for no visible reason.
        val rebound = java.net.ServerSocket()
        rebound.reuseAddress = true
        rebound.bind(InetSocketAddress(port))
        rebound.close()
    }
}

/** Awaits without throwing when nothing arrives, so "nothing was received" can fail cleanly. */
private suspend fun <T> CompletableDeferred<T>.awaitOrNull(): T? =
    runCatching { withTimeout(1_000) { await() } }.getOrNull()
