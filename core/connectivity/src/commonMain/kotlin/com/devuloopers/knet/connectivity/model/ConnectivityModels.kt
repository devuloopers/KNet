package com.devuloopers.knet.connectivity.model

import kotlin.jvm.JvmInline

/**
 * Stable identifier for a connectivity mechanism such as manual proxy, PAC, ADB, or companion.
 *
 * @property value Non-blank registration identifier.
 */
@JvmInline
public value class ConnectivityMechanismId(public val value: String) {
    init {
        require(value.isNotBlank()) { "ConnectivityMechanismId must not be blank." }
    }
}

/**
 * Version of the complete connectivity context used to invalidate derived setup artifacts.
 *
 * @property value Monotonically increasing non-negative version.
 */
@JvmInline
public value class ConnectivityContextVersion(public val value: Long) {
    init {
        require(value >= 0L) { "ConnectivityContextVersion must not be negative." }
    }
}

/**
 * Version of the proxy endpoints published by the application lifecycle.
 *
 * @property value Monotonically increasing non-negative version.
 */
@JvmInline
public value class ProxyEndpointVersion(public val value: Long) {
    init {
        require(value >= 0L) { "ProxyEndpointVersion must not be negative." }
    }
}

/**
 * Stable identifier for an artifact produced by a setup provider.
 *
 * @property value Non-blank artifact identifier.
 */
@JvmInline
public value class SetupArtifactId(public val value: String) {
    init {
        require(value.isNotBlank()) { "SetupArtifactId must not be blank." }
    }
}

/**
 * Scope describing who may reach a proxy endpoint.
 */
public enum class ProxyEndpointScope {
    LOOPBACK,
    LAN,
    INTERNAL_GATEWAY,
}

/**
 * Access requirement advertised with an endpoint without exposing a credential.
 */
public enum class ProxyAccessRequirement {
    LOCAL_PROCESS,
    OPEN_LAN_CLIENT,
    PAIRED_CLIENT_CREDENTIAL,
    INTERNAL_GATEWAY_CREDENTIAL,
}

/**
 * Reachable proxy endpoint published to connectivity providers.
 *
 * @property host Host name or IP address suitable for the intended scope.
 * @property port TCP port.
 * @property scope Endpoint exposure scope.
 * @property accessRequirement Credential category required by the access gate.
 */
public data class ProxyEndpoint(
    public val host: String,
    public val port: Int,
    public val scope: ProxyEndpointScope,
    public val accessRequirement: ProxyAccessRequirement,
) {
    init {
        require(host.isNotBlank()) { "Proxy endpoint host must not be blank." }
        require(port in 1..65_535) { "Proxy endpoint port must be between 1 and 65535." }
    }
}

/**
 * Versioned read-only proxy endpoint publication.
 *
 * @property version Endpoint version used for setup artifact invalidation.
 * @property endpoints Currently available endpoints.
 */
public data class ProxyEndpointSnapshot(
    public val version: ProxyEndpointVersion,
    public val endpoints: List<ProxyEndpoint>,
)

/**
 * Address family represented by a platform-neutral network snapshot.
 */
public enum class NetworkAddressFamily {
    IPV4,
    IPV6,
}

/**
 * Platform-neutral address associated with a stable network interface identifier.
 *
 * @property interfaceId Stable platform adapter identifier for the interface.
 * @property address Address text without an inferred port.
 * @property family Address family.
 * @property loopback Whether the address belongs to a loopback interface.
 */
public data class NetworkAddress(
    public val interfaceId: String,
    public val address: String,
    public val family: NetworkAddressFamily,
    public val loopback: Boolean,
) {
    init {
        require(interfaceId.isNotBlank()) { "Network interface identifier must not be blank." }
        require(address.isNotBlank()) { "Network address must not be blank." }
    }
}

/**
 * Versioned platform-neutral network state observed by a platform adapter.
 *
 * @property version Monotonically increasing adapter snapshot version.
 * @property addresses Active interface addresses.
 * @property defaultRouteAvailable Whether a default route is currently known.
 * @property vpnActive Whether the platform adapter observes an active VPN route.
 * @property observedAtEpochMillis Wall-clock observation time.
 */
public data class NetworkSnapshot(
    public val version: Long,
    public val addresses: List<NetworkAddress>,
    public val defaultRouteAvailable: Boolean,
    public val vpnActive: Boolean,
    public val observedAtEpochMillis: Long,
) {
    init {
        require(version >= 0L) { "Network snapshot version must not be negative." }
        require(observedAtEpochMillis >= 0L) { "Network observation timestamp must not be negative." }
    }
}

/**
 * Complete immutable inputs supplied by the application to connectivity providers.
 *
 * @property version Version covering all context inputs.
 * @property proxyEndpoints Current proxy endpoints.
 * @property network Current platform network state.
 */
public data class ConnectivityContext(
    public val version: ConnectivityContextVersion,
    public val proxyEndpoints: ProxyEndpointSnapshot,
    public val network: NetworkSnapshot,
)

/**
 * Capability advertised by a connectivity provider or managed mechanism.
 */
public enum class ConnectivityCapability {
    MANUAL_CONFIGURATION,
    WIFI_SHARING,
    PAC_ARTIFACT,
    CERTIFICATE_INSTALLATION,
    DEVICE_COMMAND,
    DEVICE_PAIRING,
    DIRECT_TUNNEL,
    RELAY_TUNNEL,
    REQUIRES_ACTIVATION,
}

/**
 * Reason that a connectivity mechanism is not currently available.
 */
public sealed interface ConnectivityAvailability {
    /** The mechanism can be described or activated in the current context. */
    public data object Available : ConnectivityAvailability

    /**
     * Host platform does not support the mechanism.
     *
     * @property platform Stable platform token.
     */
    public data class PlatformUnsupported(public val platform: String) : ConnectivityAvailability

    /**
     * A required executable, service, or device dependency is missing.
     *
     * @property dependency Stable dependency token.
     */
    public data class DependencyMissing(public val dependency: String) : ConnectivityAvailability

    /**
     * User or operating-system permission must be granted.
     *
     * @property permission Stable permission token.
     */
    public data class PermissionRequired(public val permission: String) : ConnectivityAvailability

    /**
     * Current network state cannot support the mechanism.
     *
     * @property reason Safe user-facing reason token.
     */
    public data class NetworkUnavailable(public val reason: String) : ConnectivityAvailability

    /**
     * Product or security policy disabled the mechanism.
     *
     * @property reason Safe policy reason token.
     */
    public data class PolicyDisabled(public val reason: String) : ConnectivityAvailability

    /**
     * A recoverable runtime condition temporarily prevents use.
     *
     * @property reason Safe reason token.
     * @property retryAfterMillis Optional retry hint.
     */
    public data class TemporarilyUnavailable(
        public val reason: String,
        public val retryAfterMillis: Long? = null,
    ) : ConnectivityAvailability
}

/**
 * Runtime lifecycle for a connectivity mechanism that genuinely owns activation.
 */
public sealed interface ConnectivityLifecycle {
    /** The mechanism owns no active runtime session. */
    public data object Inactive : ConnectivityLifecycle

    /** The mechanism is starting or negotiating a session. */
    public data object Activating : ConnectivityLifecycle

    /**
     * Activation is waiting for an explicit user action.
     *
     * @property action Stable action token rendered by the UI.
     */
    public data class NeedsUserAction(public val action: String) : ConnectivityLifecycle

    /**
     * Mechanism has an active owned session.
     *
     * @property sessionId Stable non-secret session identifier.
     */
    public data class Active(public val sessionId: String) : ConnectivityLifecycle

    /** The mechanism is closing its owned session. */
    public data object Deactivating : ConnectivityLifecycle

    /**
     * Lifecycle operation failed.
     *
     * @property code Stable failure code.
     * @property recoverable Whether retry is allowed without external repair.
     */
    public data class Failed(
        public val code: String,
        public val recoverable: Boolean,
    ) : ConnectivityLifecycle
}

/**
 * Independent health state for an available or active connectivity mechanism.
 */
public sealed interface ConnectivityHealth {
    /** No health observation has completed. */
    public data object Unknown : ConnectivityHealth

    /**
     * Mechanism passed its latest health observation.
     *
     * @property verifiedAtEpochMillis Wall-clock verification time.
     */
    public data class Healthy(public val verifiedAtEpochMillis: Long) : ConnectivityHealth

    /**
     * Mechanism remains usable with a known limitation.
     *
     * @property reason Safe degradation token.
     */
    public data class Degraded(public val reason: String) : ConnectivityHealth

    /**
     * Mechanism cannot currently reach its peer or endpoint.
     *
     * @property reason Safe failure token.
     */
    public data class Unreachable(public val reason: String) : ConnectivityHealth
}

/**
 * Typed setup step rendered by a presentation without embedding callbacks in core state.
 */
public sealed interface SetupStep {
    /**
     * Opens an authorized setup URL.
     *
     * @property url URL supplied by the application artifact service.
     */
    public data class OpenUrl(public val url: String) : SetupStep

    /**
     * Configures a client with a proxy endpoint.
     *
     * @property endpoint Endpoint to configure.
     */
    public data class ConfigureProxy(public val endpoint: ProxyEndpoint) : SetupStep

    /**
     * Runs a platform command presented to or executed through an approved process adapter.
     *
     * @property commandToken Stable command descriptor token, not an unsanitized shell command.
     */
    public data class RunCommand(public val commandToken: String) : SetupStep

    /**
     * Requests explicit confirmation or platform trust action.
     *
     * @property actionToken Stable action descriptor token.
     */
    public data class ConfirmAction(public val actionToken: String) : SetupStep
}

/**
 * Artifact reference generated for a connectivity setup flow.
 *
 * @property id Stable artifact identifier.
 * @property mediaType Registered media type.
 * @property downloadUrl Authorized delivery URL.
 * @property digest Optional immutable artifact digest.
 */
public data class SetupArtifact(
    public val id: SetupArtifactId,
    public val mediaType: String,
    public val downloadUrl: String,
    public val digest: String? = null,
) {
    init {
        require(mediaType.isNotBlank()) { "Setup artifact media type must not be blank." }
        require(downloadUrl.isNotBlank()) { "Setup artifact URL must not be blank." }
    }
}

/**
 * Platform-neutral setup description produced from one immutable connectivity context.
 *
 * @property mechanismId Provider identifier.
 * @property titleToken Stable title resource token.
 * @property summaryToken Stable summary resource token.
 * @property capabilities Advertised capabilities.
 * @property steps Ordered setup steps.
 * @property artifacts Immutable artifact references.
 * @property contextVersion Context version used to generate this descriptor.
 */
public data class SetupDescriptor(
    public val mechanismId: ConnectivityMechanismId,
    public val titleToken: String,
    public val summaryToken: String,
    public val capabilities: Set<ConnectivityCapability>,
    public val steps: List<SetupStep>,
    public val artifacts: List<SetupArtifact>,
    public val contextVersion: ConnectivityContextVersion,
) {
    init {
        require(titleToken.isNotBlank()) { "Setup descriptor title token must not be blank." }
        require(summaryToken.isNotBlank()) { "Setup descriptor summary token must not be blank." }
    }
}
