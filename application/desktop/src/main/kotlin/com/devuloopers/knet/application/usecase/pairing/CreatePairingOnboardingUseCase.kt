package com.devuloopers.knet.application.usecase.pairing

import com.devuloopers.knet.application.coordinator.pairing.PairingCoordinator
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingInvitation

/** Secret-bearing onboarding data; presentations must avoid logs, analytics, and persistent state. */
public data class PairingOnboardingDescriptor(
    public val invitation: PairingInvitation,
    public val deepLink: String,
    public val qrPayload: String,
)

/** Creates a short-lived invitation and deterministic companion deep-link/QR payload. */
public class CreatePairingOnboardingUseCase(
    private val pairing: PairingCoordinator,
) {
    public suspend fun execute(
        scopes: Set<DeviceScope> = setOf(DeviceScope.PROXY_STREAM),
    ): PairingOnboardingDescriptor {
        val invitation = pairing.createInvitation(scopes)
        val scopeToken = invitation.scopes.sortedBy(DeviceScope::name).joinToString(",", transform = DeviceScope::name)
        val payload = "knet://pair/v1?id=${invitation.id.value}" +
            "&secret=${invitation.secret}" +
            "&expires=${invitation.expiresAtEpochMillis}" +
            "&scopes=$scopeToken"
        return PairingOnboardingDescriptor(invitation, payload, payload)
    }
}
