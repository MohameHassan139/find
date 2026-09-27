package com.example.myapplication.utils

import java.text.NumberFormat
import java.util.Locale

/** One price format for every screen: "12,500" / "12,500.5", and "—" when there's no price. */
object PriceFormatter {

    private const val NO_PRICE = "—"
    private val grouped = NumberFormat.getNumberInstance(Locale.US)

    /** For display (cards, ad details, My Ads, share text). */
    fun display(price: Double?): String {
        if (price == null) return NO_PRICE
        return if (price % 1 == 0.0) grouped.format(price.toLong()) else grouped.format(price)
    }

    /** Digits only, no separators — for pre-filling the editable price field. */
    fun plain(price: Double): String =
        if (price % 1 == 0.0) price.toLong().toString() else price.toString()
}
