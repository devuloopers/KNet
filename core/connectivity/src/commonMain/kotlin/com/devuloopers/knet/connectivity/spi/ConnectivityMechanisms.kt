package com.devuloopers.knet.connectivity.spi

import com.devuloopers.knet.connectivity.model.ConnectivityAvailability
import com.devuloopers.knet.connectivity.model.ConnectivityCapability
import com.devuloopers.knet.connectivity.model.ConnectivityContext
import com.devuloopers.knet.connectivity.model.ConnectivityContextVersion
import com.devuloopers.knet.connectivity.model.ConnectivityHealth
import com.devuloopers.knet.connectivity.model.ConnectivityLifecycle
import com.devuloopers.knet.connectivity.model.ConnectivityMechanismId
import com.devuloopers.knet.connectivity.model.SetupDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Produces setup instructions or artifacts for a mechanism that does not necessarily own runtime activation.
 */
public interface SetupDescriptorProvider {
    /** Stable mechanism identifier used for registration and persisted selection. */
    public val id: ConnectivityMechanismId

    /** Capabilities rendered by generic application and UI code. */
    public val capabilities: Set<ConnectivityCapability>

    /**
     * Observes whether the provider can produce a usable descriptor in the supplied context.
     *
     * @param context Immutable connectivity inputs.
     * @return Availability updates that remain separate from runtime lifecycle and health.
     */
    public fun availability(context: ConnectivityContext): Flow<ConnectivityAvailability>

    /**
     * Produces an immutable setup description.
     *
     * @param context Immutable connectivity inputs.
     * @return Descriptor tied to [ConnectivityContext.version].
     * @throws IllegalStateException When the provider is not available for the context.
     */
    public suspend fun describe(context: ConnectivityContext): SetupDescriptor
}

/**
 * Request to activate a mechanism that owns a runtime process, device mapping, VPN, or tunnel.
 *
 * @property contextVersion Connectivity context version accepted by the caller.
 */
public data class ActivationRequest(public val contextVersion: ConnectivityContextVersion)

/**
 * Result of requesting managed connectivity activation.
 */
public sealed interface ActivationResult {
    /** Activation was accepted; lifecycle state reports continuing progress. */
    public data object Accepted : ActivationResult

    /**
     * Activation was rejected before a lifecycle transition.
     *
     * @property reason Stable safe rejection code.
     */
    public data class Rejected(public val reason: String) : ActivationResult
}

/**
 * Reason supplied when a managed connectivity session is deactivated.
 */
public enum class DeactivationReason {
    USER_REQUEST,
    APPLICATION_SHUTDOWN,
    NETWORK_CHANGED,
    SECURITY_REVOKED,
}

/**
 * Result of deactivating a managed connectivity mechanism.
 */
public sealed interface DeactivationResult {
    /** The mechanism is inactive and its owned resources are closed. */
    public data object Inactive : DeactivationResult

    /**
     * The mechanism could not close every resource cleanly.
     *
     * @property reason Stable safe failure code.
     */
    public data class Failed(public val reason: String) : DeactivationResult
}

/**
 * Owns activation only for connectivity mechanisms with a genuine runtime lifecycle.
 *
 * Artifact-only providers such as manual proxy or PAC must not implement fake no-op activation.
 */
public interface ManagedConnectivityMechanism {
    /** Stable mechanism identifier used for registration and persisted selection. */
    public val id: ConnectivityMechanismId

    /** Capabilities rendered by generic application and UI code. */
    public val capabilities: Set<ConnectivityCapability>

    /** Current support/requirement availability. */
    public val availability: Flow<ConnectivityAvailability>

    /** Serialized runtime lifecycle state. */
    public val lifecycle: StateFlow<ConnectivityLifecycle>

    /** Health independent from availability and lifecycle. */
    public val health: StateFlow<ConnectivityHealth>

    /**
     * Starts or negotiates the mechanism.
     *
     * @param request Versioned activation request.
     * @return Immediate acceptance or typed rejection; continuing state is observed through [lifecycle].
     */
    public suspend fun activate(request: ActivationRequest): ActivationResult

    /**
     * Closes the mechanism's owned runtime resources.
     *
     * @param reason Reason used for audit and recovery policy.
     * @return Final deactivation result.
     */
    public suspend fun deactivate(reason: DeactivationReason): DeactivationResult
}
