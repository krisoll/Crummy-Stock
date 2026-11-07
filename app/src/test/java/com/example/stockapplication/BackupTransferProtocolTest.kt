package com.example.stockapplication

import com.example.stockapplication.data.BackupArchive
import com.example.stockapplication.data.JSON_FORMAT
import com.example.stockapplication.data.transfer.BackupTransfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared protocol constants and the code comparison.
 *
 * The security-relevant behaviour is [BackupTransfer.codesMatch]: it is what stands between a
 * listening port on a shop's Wi-Fi and anyone on that network pushing a backup.
 */
class BackupTransferProtocolTest {

    @Test
    fun theCodeIsAlwaysSixDigits() {
        // A code that sometimes reads "4021" and sometimes "018842" looks like two different kinds
        // of thing at exactly the moment the user is trying to read it aloud.
        listOf(0L, 1L, 42L, 9_999L, 123_456L, 999_999L, -7L).forEach { seed ->
            val code = BackupTransfer.codeFrom(seed)
            assertEquals("seed $seed produced '$code'", BackupTransfer.CODE_LENGTH, code.length)
            assertTrue("seed $seed produced '$code'", code.all { it.isDigit() })
        }
    }

    @Test
    fun theSameSeedAlwaysProducesTheSameCode() {
        assertEquals(BackupTransfer.codeFrom(123_456L), BackupTransfer.codeFrom(123_456L))
    }

    @Test
    fun theSmallestLongDoesNotCrashOrProduceANegative() {
        // `Long.MIN_VALUE` cannot be negated, so a naive `abs()` would overflow here.
        val code = BackupTransfer.codeFrom(Long.MIN_VALUE)
        assertEquals(BackupTransfer.CODE_LENGTH, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun matchingCodesAreAccepted() {
        assertTrue(BackupTransfer.codesMatch("018842", "018842"))
        // Whitespace from a text field or a shell should not defeat the user.
        assertTrue(BackupTransfer.codesMatch("018842", " 018842 "))
        assertTrue(BackupTransfer.codesMatch("018842", "018842\n"))
    }

    @Test
    fun aWrongOrMissingCodeIsRejected() {
        assertFalse(BackupTransfer.codesMatch("018842", "018843"))
        assertFalse(BackupTransfer.codesMatch("018842", "01884"))
        assertFalse(BackupTransfer.codesMatch("018842", "0018842"))
        assertFalse(BackupTransfer.codesMatch("018842", ""))
        assertFalse(BackupTransfer.codesMatch("018842", null))
    }

    @Test
    fun theUploadCapMatchesTheArchiveReaderLimit() {
        // A file that arrives must be one the importer will accept; if these drifted apart the
        // server would happily receive archives that then fail to open.
        assertEquals(BackupArchive.MaxArchiveBytes, BackupTransfer.MAX_UPLOAD_BYTES)
    }

    @Test
    fun theFormatValueMatchesTheJsonFormatId() {
        assertEquals(JSON_FORMAT, BackupTransfer.FORMAT_VALUE)
    }
}
