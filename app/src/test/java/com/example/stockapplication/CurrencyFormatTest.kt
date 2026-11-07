package com.example.stockapplication

import com.example.stockapplication.data.Currency
import com.example.stockapplication.data.DefaultCurrencyId
import com.example.stockapplication.data.currencySymbol
import com.example.stockapplication.data.formatCents
import com.example.stockapplication.data.showCents
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `formatCents` with a currency symbol in front, and the [Currency] list behind it.
 *
 * The two process-wide flags ([showCents], [currencySymbol]) are restored after every test: they
 * are mutable global state, and a test that changed one would otherwise leak into whichever test
 * ran next.
 */
class CurrencyFormatTest {

    private val originalShowCents = showCents
    private val originalSymbol = currencySymbol

    @After
    fun restoreGlobals() {
        showCents = originalShowCents
        currencySymbol = originalSymbol
    }

    @Test
    fun `the symbol is prefixed to the amount`() {
        showCents = true
        currencySymbol = "$"
        assertEquals("$12.99", formatCents(1299))
    }

    @Test
    fun `the sign goes before the symbol`() {
        showCents = true
        currencySymbol = "€"
        // Not "€-1.50": the sign qualifies the whole amount, and every prefix currency is
        // written this way.
        assertEquals("-€1.50", formatCents(-150))
    }

    @Test
    fun `hiding cents keeps the symbol`() {
        showCents = true
        currencySymbol = "£"
        assertEquals("£12.99", formatCents(1299))

        showCents = false
        // Rounding is untouched by the currency: half-up, symbol still in front.
        assertEquals("£13", formatCents(1299))
        assertEquals("£13", formatCents(1250))
        assertEquals("-£13", formatCents(-1250))
    }

    @Test
    fun `changing the symbol affects the next call`() {
        // The formatter reads the process-wide symbol rather than a captured copy, which is what
        // lets the exporters and the PDF renderer follow the setting without a parameter.
        showCents = true
        currencySymbol = "$"
        assertEquals("$5.00", formatCents(500))

        currencySymbol = "R$"
        assertEquals("R$5.00", formatCents(500))
    }

    @Test
    fun `a word symbol keeps its trailing space against the amount`() {
        showCents = true
        currencySymbol = Currency.CHF.symbol
        // "CHF12.00" would run the letters into the number.
        assertEquals("CHF 12.00", formatCents(1200))
    }

    @Test
    fun `the default is the dollar every existing amount was already written in`() {
        assertEquals(Currency.USD, Currency.fromId(DefaultCurrencyId))
        assertEquals("$", Currency.fromId(DefaultCurrencyId).symbol)
    }

    @Test
    fun `an unknown or missing id falls back to the default`() {
        // A backup from a build that offered a currency this one does not.
        assertEquals(Currency.USD, Currency.fromId(null))
        assertEquals(Currency.USD, Currency.fromId(""))
        assertEquals(Currency.USD, Currency.fromId("xyz"))
    }

    @Test
    fun `every currency has a distinct id and round-trips through it`() {
        val ids = Currency.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        Currency.entries.forEach { currency ->
            assertEquals(currency, Currency.fromId(currency.id))
        }
    }

    @Test
    fun `every currency has a non-blank symbol`() {
        // A blank symbol would silently render amounts with no currency at all.
        Currency.entries.forEach { currency ->
            assertTrue("${currency.id} has a blank symbol", currency.symbol.isNotBlank())
        }
    }

    @Test
    fun `no two currencies share a symbol`() {
        // The picker now shows the symbol and nothing else, so a duplicate would be two
        // indistinguishable rows — a user in a "$" shop could not tell which one they had
        // selected. This is why a second dollar currency (MXN, CLP) is not offered.
        val bySymbol = Currency.entries.groupBy { it.symbol.trim() }
        val clashes = bySymbol.filterValues { it.size > 1 }
        assertTrue("symbols must be unique, clashing on: ${clashes.keys}", clashes.isEmpty())
    }
}
