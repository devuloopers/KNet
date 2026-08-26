package com.devuloopers.knet.companion.connectivity.fallback

import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolutionResult
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolver
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode

/** Fail-closed invitation resolver for a platform without a qualified bootstrap transport. */
public class UnavailableCompanionInvitationResolver(
    private val platformName: String,
) : CompanionInvitationResolver {
    init {
        require(platformName.isNotBlank()) { "Platform name must not be blank." }
    }

    override suspend fun resolve(
        bootstrap: com.devuloopers.knet.companion.model.CompanionPairingBootstrap,
    ): CompanionInvitationResolutionResult = CompanionInvitationResolutionResult.Rejected(
        CompanionFailure(
            code = CompanionFailureCode.PLATFORM_ADAPTER_UNAVAILABLE,
            message = "$platformName companion invitation retrieval is unavailable in this build.",
            recoverable = true,
        ),
    )
}
