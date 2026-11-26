package com.example.ui.components

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

fun formatUsdFromCents(cents: Long): String {
    val dollars = cents / 100.0
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    format.maximumFractionDigits = if (cents % 100L == 0L) 0 else 2
    return format.format(dollars)
}

fun formatUsdDouble(dollars: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    format.maximumFractionDigits = 0
    return format.format(dollars.roundToLong())
}

fun formatCompactUsdFromCents(cents: Long): String {
    val dollars = cents / 100.0
    return when {
        dollars >= 1_000_000.0 -> String.format(Locale.US, "$%.2fM", dollars / 1_000_000.0)
        dollars >= 10_000.0 -> String.format(Locale.US, "$%.1fk", dollars / 1_000.0)
        else -> formatUsdFromCents(cents)
    }
}

fun formatCompactUsdDouble(dollars: Double): String {
    return when {
        dollars >= 1_000_000.0 -> String.format(Locale.US, "$%.2fM", dollars / 1_000_000.0)
        dollars >= 10_000.0 -> String.format(Locale.US, "$%.1fk", dollars / 1_000.0)
        else -> formatUsdDouble(dollars)
    }
}

fun parseDollarsToCents(input: String): Long? {
    val cleaned = input.trim().replace("$", "").replace(",", "")
    if (cleaned.isEmpty()) return null
    val parsed = cleaned.toDoubleOrNull() ?: return null
    if (parsed < 0.0) return null
    return (parsed * 100.0).roundToLong()
}
