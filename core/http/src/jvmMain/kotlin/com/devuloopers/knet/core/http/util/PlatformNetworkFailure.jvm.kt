package com.devuloopers.knet.core.http.util

import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.ClosedChannelException
import java.nio.channels.UnresolvedAddressException
import javax.net.ssl.SSLException

/** JVM exception mapping kept outside the portable HTTP policy source set. */
internal actual fun Throwable.platformNetworkFailure(): PlatformNetworkFailure? = when (this) {
    is UnknownHostException, is UnresolvedAddressException -> PlatformNetworkFailure.DNS
    is SocketTimeoutException -> PlatformNetworkFailure.TIMEOUT
    is SSLException -> PlatformNetworkFailure.TLS
    is SocketException, is ClosedChannelException -> PlatformNetworkFailure.UNREACHABLE
    else -> null
}
