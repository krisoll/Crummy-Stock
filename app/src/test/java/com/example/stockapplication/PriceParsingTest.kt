package com.example.stockapplication

import com.example.stockapplication.data.formatCents
import com.example.stockapplication.ui.screens.toCentsOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceParsingTest {

    @Test
    fun parsesWholeAndDecimalAmounts() {
        assertEquals(1299L, "12.99".toCentsOrNull())
        assertEquals(1200L, "12".toCentsOrNull())
        assertEquals(1250L, "12.5".toCentsOrNull())
        assertEquals(1299L, "12,99".toCentsOrNull())
        assertEquals(99L, "0.99".toCentsOrNull())
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull("".toCentsOrNull())
        assertNull("abc".toCentsOrNull())
        assertNull("1.2.3".toCentsOrNull())
    }

    @Test
    fun formatsCents() {
        // `formatCents` prefixes the currency symbol, so every expectation carries one. The
        // symbol itself is covered in CurrencyFormatTest; here it is just part of the shape.
        assertEquals("$12.99", formatCents(1299))
        assertEquals("$0.05", formatCents(5))
        assertEquals("$100.00", formatCents(10000))
        // The sign goes before the symbol, not after it.
        assertEquals("-$1.50", formatCents(-150))
    }
}
