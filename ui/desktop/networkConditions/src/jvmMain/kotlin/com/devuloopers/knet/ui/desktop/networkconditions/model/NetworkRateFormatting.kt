package com.devuloopers.knet.ui.desktop.networkconditions.model

/**
 * Formats actual/configured bit rates identically across Overview and Traffic using decimal network units.
 * One fractional digit preserves useful precision without floating-point or locale-dependent formatting.
 * @param bitsPerSecond Nonnegative rate, or null for an unlimited configured direction.
 */
internal fun formatNetworkRate(bitsPerSecond: Long?): String {
    if (bitsPerSecond == null) return "Unlimited"
    val value = bitsPerSecond.coerceAtLeast(0L)
    if (value == 0L) return "0 kbps"
    if (value < 1_000L) return "$value bps"
    val (divisor, unit) = when {
        value >= 1_000_000_000L -> 1_000_000_000L to "Gbps"
        value >= 1_000_000L -> 1_000_000L to "Mbps"
        else -> 1_000L to "kbps"
    }
    val fractionalTenths = ((value % divisor) * 10L + divisor / 2L) / divisor
    val whole = value / divisor + fractionalTenths / 10L
    val fraction = fractionalTenths % 10L
    return if (fraction == 0L) "$whole $unit" else "$whole.$fraction $unit"
}
