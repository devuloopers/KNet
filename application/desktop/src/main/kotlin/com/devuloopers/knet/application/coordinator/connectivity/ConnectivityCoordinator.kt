package com.devuloopers.knet.application.coordinator.connectivity

import com.devuloopers.knet.connectivity.model.ConnectivityAvailability
import com.devuloopers.knet.connectivity.model.ConnectivityContext
import com.devuloopers.knet.connectivity.model.ConnectivityMechanismId
import com.devuloopers.knet.connectivity.model.SetupDescriptor
import com.devuloopers.knet.connectivity.spi.ManagedConnectivityMechanism
import com.devuloopers.knet.connectivity.spi.SetupDescriptorProvider
import kotlinx.coroutines.flow.first

/** One provider result; failures remain local to the mechanism that produced them. */
public sealed interface ConnectivityDescriptorResult {
    public val mechanismId: ConnectivityMechanismId

    public data class Available(public val descriptor: SetupDescriptor) : ConnectivityDescriptorResult {
        override val mechanismId: ConnectivityMechanismId = descriptor.mechanismId
    }

    public data class Unavailable(
        override val mechanismId: ConnectivityMechanismId,
        public val availability: ConnectivityAvailability,
    ) : ConnectivityDescriptorResult

    public data class Failed(
        override val mechanismId: ConnectivityMechanismId,
        public val code: String,
    ) : ConnectivityDescriptorResult
}

/**
 * Application-owned additive registry for setup providers and managed mechanisms.
 * It has no knowledge of ADB, PAC syntax, Apple profiles, VPNs, companions, or relays.
 */
public class ConnectivityCoordinator(
    providers: List<SetupDescriptorProvider>,
    mechanisms: List<ManagedConnectivityMechanism> = emptyList(),
) {
    public val providers: List<SetupDescriptorProvider> = providers.requireUniqueIds("provider") { it.id }
    public val mechanisms: List<ManagedConnectivityMechanism> = mechanisms.requireUniqueIds("mechanism") { it.id }

    /** Resolves each provider independently so one broken integration cannot hide the others. */
    public suspend fun describe(context: ConnectivityContext): List<ConnectivityDescriptorResult> =
        providers.map { provider ->
            try {
                when (val availability = provider.availability(context).first()) {
                    ConnectivityAvailability.Available ->
                        ConnectivityDescriptorResult.Available(provider.describe(context))
                    else -> ConnectivityDescriptorResult.Unavailable(provider.id, availability)
                }
            } catch (_: Exception) {
                ConnectivityDescriptorResult.Failed(provider.id, "descriptor_failed")
            }
        }
}

private fun <T> List<T>.requireUniqueIds(
    type: String,
    id: (T) -> ConnectivityMechanismId,
): List<T> = also { values ->
    require(values.map(id).distinct().size == values.size) { "Connectivity $type IDs must be unique." }
}
