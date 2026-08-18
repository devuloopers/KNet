package com.devuloopers.knet.core.http.util

/** Platform-specific network failure categories consumed by common HTTP policy. */
internal enum class PlatformNetworkFailure {
    DNS,
    TIMEOUT,
    TLS,
    UNREACHABLE,
}

/** Returns a platform network category for this exception, or `null` when it is not recognized. */
internal expect fun Throwable.platformNetworkFailure(): PlatformNetworkFailure?
