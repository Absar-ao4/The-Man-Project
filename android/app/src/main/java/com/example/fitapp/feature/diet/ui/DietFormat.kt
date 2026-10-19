package com.example.fitapp.feature.diet.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Plain number formatting for the diet screens (same output on every device, whatever its locale). */
object DietFormat {
    private val symbols = DecimalFormatSymbols(Locale.US)

    /** "200", "2.4", "1,250.5": up to two decimals, none if the amount is whole. */
    fun number(value: Double): String = DecimalFormat("#,##0.##", symbols).format(value)

    /** "200 INR", "2.4 USD", "1,250.5 INR": up to two decimals, none if the amount is whole. */
    fun money(
        value: Double,
        currency: String,
    ): String = "${number(value)} $currency"

    /** "120 g" for a whole number of grams, "7.5 g" for small or fractional ones. */
    fun grams(value: Double): String {
        val text = if (value >= 10) Math.round(value).toString() else DecimalFormat("0.#", symbols).format(value)
        return "$text g"
    }

    /** The day part of an ISO timestamp, "2026-03-02". */
    fun day(createdAt: String): String = createdAt.take(10)
}