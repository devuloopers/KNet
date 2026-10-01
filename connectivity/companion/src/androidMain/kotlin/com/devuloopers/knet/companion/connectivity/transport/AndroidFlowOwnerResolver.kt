package com.devuloopers.knet.companion.connectivity.transport

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.system.OsConstants
import com.devuloopers.knet.companion.model.CompanionFlowMetadata
import java.net.InetSocketAddress

/** Transport value used when Android resolves the owner of an original VPN flow. */
public enum class AndroidFlowTransport {
    /** Transmission Control Protocol. */
    TCP,

    /** User Datagram Protocol. */
    UDP,
}

/**
 * Original packet tuple required by Android's connection-owner API.
 *
 * @property transport IP transport protocol.
 * @property source Original application-side local endpoint, before TUN translation.
 * @property destination Original remote endpoint.
 */
public data class AndroidOriginalFlowTuple(
    public val transport: AndroidFlowTransport,
    public val source: InetSocketAddress,
    public val destination: InetSocketAddress,
) {
    init {
        require(!source.isUnresolved && source.port in 1..65_535) { "Flow source must be resolved and valid." }
        require(!destination.isUnresolved && destination.port in 1..65_535) {
            "Flow destination must be resolved and valid."
        }
    }
}

/** Reason that Android could not establish a unique OS-verified application identity. */
public enum class AndroidFlowAttributionUnavailableReason {
    /** The device predates Android 10's connection-owner API. */
    PLATFORM_API_UNAVAILABLE,

    /** Android did not report an owner for the original tuple. */
    OWNER_UNAVAILABLE,

    /** More than one installed package shares the reported UID. */
    SHARED_UID_AMBIGUOUS,
}

/** Result of resolving one original Android flow tuple. */
public sealed interface AndroidFlowOwnerResolution {
    /** A unique package identity verified through Android's connection-owner API. */
    public data class Verified(public val metadata: CompanionFlowMetadata) : AndroidFlowOwnerResolution

    /** Attribution is intentionally absent and must not be guessed from the translated socket. */
    public data class Unavailable(
        public val reason: AndroidFlowAttributionUnavailableReason,
    ) : AndroidFlowOwnerResolution
}

/** Replaceable boundary between a packet parser and Android's connection-owner API. */
public fun interface AndroidFlowOwnerResolver {
    /** Resolves [flow] without falling back to a translated loopback SOCKS tuple. */
    public fun resolve(flow: AndroidOriginalFlowTuple): AndroidFlowOwnerResolution
}

/** Android 10+ implementation backed by `ConnectivityManager.getConnectionOwnerUid`. */
public class PlatformAndroidFlowOwnerResolver internal constructor(
    private val sdkInt: () -> Int,
    private val ownerUid: (AndroidOriginalFlowTuple) -> Int,
    private val packagesForUid: (Int) -> Array<String>?,
) : AndroidFlowOwnerResolver {
    /** Creates a resolver using application-scoped Android services. */
    public constructor(context: Context) : this(
        sdkInt = { Build.VERSION.SDK_INT },
        ownerUid = ownerLookup(context.applicationContext),
        packagesForUid = context.applicationContext.packageManager::getPackagesForUid,
    )

    override fun resolve(flow: AndroidOriginalFlowTuple): AndroidFlowOwnerResolution {
        if (sdkInt() < Build.VERSION_CODES.Q) {
            return AndroidFlowOwnerResolution.Unavailable(
                AndroidFlowAttributionUnavailableReason.PLATFORM_API_UNAVAILABLE,
            )
        }
        val uid = runCatching { ownerUid(flow) }.getOrDefault(INVALID_UID)
        if (uid < 0) {
            return AndroidFlowOwnerResolution.Unavailable(AndroidFlowAttributionUnavailableReason.OWNER_UNAVAILABLE)
        }
        val packages = packagesForUid(uid).orEmpty().filter(String::isNotBlank).distinct()
        if (packages.size != 1) {
            return AndroidFlowOwnerResolution.Unavailable(
                if (packages.isEmpty()) {
                    AndroidFlowAttributionUnavailableReason.OWNER_UNAVAILABLE
                } else {
                    AndroidFlowAttributionUnavailableReason.SHARED_UID_AMBIGUOUS
                },
            )
        }
        return AndroidFlowOwnerResolution.Verified(CompanionFlowMetadata(packages.single()))
    }

    private companion object {
        const val INVALID_UID: Int = -1

        fun ownerLookup(context: Context): (AndroidOriginalFlowTuple) -> Int {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            return { flow ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || connectivity == null) {
                    INVALID_UID
                } else {
                    val protocol = when (flow.transport) {
                        AndroidFlowTransport.TCP -> OsConstants.IPPROTO_TCP
                        AndroidFlowTransport.UDP -> OsConstants.IPPROTO_UDP
                    }
                    connectivity.getConnectionOwnerUid(protocol, flow.source, flow.destination)
                }
            }
        }
    }
}
