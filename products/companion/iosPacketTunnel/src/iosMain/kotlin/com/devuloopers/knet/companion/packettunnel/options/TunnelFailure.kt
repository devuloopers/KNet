package com.devuloopers.knet.companion.packettunnel.options

import platform.Foundation.NSError
import platform.Foundation.NSLocalizedDescriptionKey

internal enum class TunnelFailure(private val description: String) {
    INVALID_START_OPTIONS("KNet tunnel start options are invalid."),
    INVALID_ROOT_CERTIFICATE("The paired KNet root certificate is invalid."),
    UNABLE_TO_START_GATEWAY("The local KNet proxy gateway could not start."),
    UNABLE_TO_CONFIGURE_TUNNEL("The KNet tunnel network settings could not be applied."),
    UNABLE_TO_OPEN_TUN("The iOS tunnel descriptor is unavailable."),
    TUNNEL_ENGINE_STOPPED("The packet forwarding engine stopped unexpectedly."),
    PROXY_IDENTITY_REJECTED("The paired KNet Desktop TLS identity was rejected."),
    PROXY_CONNECTION_FAILED("The paired KNet Desktop proxy is unavailable."),
    ;

    fun exception(cause: Throwable? = null): TunnelException = TunnelException(this, description, cause)
}

internal class TunnelException(
    val failure: TunnelFailure,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {
    fun asNSError(): NSError = NSError.errorWithDomain(
        domain = ERROR_DOMAIN,
        code = failure.ordinal.toLong() + 1L,
        userInfo = mapOf(NSLocalizedDescriptionKey to message),
    )

    private companion object {
        const val ERROR_DOMAIN: String = "com.devuloopers.knet.companion.PacketTunnel"
    }
}
