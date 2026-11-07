package com.example.stockapplication.data

import androidx.annotation.StringRes
import com.example.stockapplication.R

/**
 * The currency symbol the app writes in front of every amount.
 *
 * ## What this does *not* do
 *
 * It does not convert anything. Every amount in the app is stored as integer **minor units** —
 * cents — with no currency attached, so switching from [EUR] to [USD] reinterprets the same
 * numbers rather than translating their value: 12.99 becomes $12.99, not $14.10. That is the right
 * behaviour for a shop that set the wrong symbol on a fresh install, and the wrong one for a shop
 * that has moved country; the picker says so, and there is deliberately no exchange-rate machinery
 * behind it.
 *
 * ## Why a fixed list
 *
 * A free-text symbol field invites a typo that then appears on every receipt, and the PDF report
 * renders symbols at 7 pt in fixed-width columns — an arbitrary string could overflow where a
 * known one cannot. Adding a currency is one line here plus a string resource.
 *
 * ## One entry per symbol
 *
 * The picker shows **the symbol alone**, so no two entries may share one: the user has no other
 * way to tell the options apart. That is what rules out a second "$" currency such as the
 * Mexican or Chilean peso — either would appear as a duplicate row with no way to choose between
 * them, and every dollar amount would be ambiguous besides.
 *
 * All of these are **prefix** currencies, because the formatter writes the symbol before the
 * number (`$12.99`, `-$12.99`). Suffix currencies such as Swedish `12,99 kr` would need a
 * placement rule in [formatCents] and are deliberately not offered rather than half-supported.
 */
/**
 * Currency used by a fresh install, and the fallback for an unreadable id.
 *
 * US dollar because it is what every amount in the app was implicitly written in before this
 * setting existed — a default of anything else would silently restyle every existing install.
 */
const val DefaultCurrencyId = "usd"

enum class Currency(
    val id: String,
    /**
     * Prefixed to every amount, and shown as the prefix in money input fields.
     *
     * This is also the **whole of what the picker shows**, so it must be unique across the enum
     * — see the note on the class.
     *
     * `CHF` carries a trailing space because it is a word rather than a glyph: without it the
     * amount runs straight into the letters ("CHF12.99"). The one-character symbols never
     * include one.
     */
    val symbol: String,
    /**
     * Spoken name, used only as the accessibility label on the picker row.
     *
     * Kept out of the visible UI on purpose: the list reads as a row of symbols, and a symbol is
     * what someone looking for "the euro one" recognises. Screen readers still get the full name,
     * because "€" on its own is not useful read aloud.
     */
    @param:StringRes val labelRes: Int,
) {
    USD("usd", "$", R.string.currency_usd),
    EUR("eur", "€", R.string.currency_eur),
    GBP("gbp", "£", R.string.currency_gbp),
    JPY("jpy", "¥", R.string.currency_jpy),
    BRL("brl", "R$", R.string.currency_brl),
    INR("inr", "₹", R.string.currency_inr),
    CHF("chf", "CHF ", R.string.currency_chf),
    ;

    companion object {
        fun fromId(id: String?): Currency = entries.firstOrNull { it.id == id } ?: USD
    }
}
