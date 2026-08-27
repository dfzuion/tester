package uk.co.rodrunners.raffles.core

import java.text.NumberFormat
import java.util.Locale

/** Integer pence throughout. Prices are computed server-side; this only formats them. */
object Money {
    private val gbp: NumberFormat = NumberFormat.getCurrencyInstance(Locale.UK)

    fun format(pence: Long): String = gbp.format(pence / 100.0)
    fun format(pence: Int): String = format(pence.toLong())

    /** "£2" rather than "£2.00" when the amount is whole - used on dense cards. */
    fun formatCompact(pence: Int): String =
        if (pence % 100 == 0) "£${pence / 100}" else format(pence)
}
