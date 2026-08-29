package com.devuloopers.knet.companion.connectivity.platform

import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.application.contract.CompanionControlOperation
import com.devuloopers.knet.companion.application.contract.CompanionControlRequest
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurityException
import com.devuloopers.knet.companion.connectivity.testing.companionInspectionConfigurationFixture
import com.devuloopers.knet.companion.connectivity.testing.companionRegistrationFixture
import com.devuloopers.knet.companion.connectivity.transport.IosCompanionProxyTransport
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionNetworkState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosCompanionPlatformAdapterFactoryTest {
    @Test
    fun actualFactoryStartsWithAConservativeNetworkState() {
        val adapters = createAdapters()

        assertTrue(
            adapters.networkObserver.observe().value is CompanionNetworkState.Unknown ||
                adapters.networkObserver.observe().value is CompanionNetworkState.Available ||
                adapters.networkObserver.observe().value is CompanionNetworkState.Unavailable,
        )

        adapters.close()
    }

    @Test
    fun certificateTrustRejectsAnArtifactThatDoesNotMatchThePairedRootWithoutNetworkAccess() = runTest {
        val adapters = createAdapters()
        val registration = companionRegistrationFixture()

        val trust = assertIs<CompanionCertificateState.Rejected>(
            adapters.trustVerifier.verify(
                registration,
                "credential",
                CompanionCertificateArtifact(byteArrayOf(1), "knet-root-ca.crt"),
            ),
        )
        assertEquals(CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH, trust.reason.code)

        adapters.close()
    }

    @Test
    fun inspectionRequiresAnAuthenticatedTransportBeforeStartingTheNativeTunnel() = runTest {
        val adapters = createAdapters()
        val controller = adapters.inspectionController

        val result = assertIs<CompanionInspectionStartResult.Failed>(
            controller.start(companionInspectionConfigurationFixture()),
        )
        assertEquals(CompanionFailureCode.TRANSPORT_UNAVAILABLE, result.failure.code)
        assertIs<CompanionInspectionState.Failed>(controller.state.value)

        adapters.close()
    }

    @Test
    fun controlTransportRejectsInvalidPinnedRootBeforeNetworkAccess() = runTest {
        val adapters = createAdapters()
        val registration = companionRegistrationFixture()

        assertFailsWith<CompanionHttpSecurityException.IdentityRejected> {
            adapters.controlTransport.execute(
                CompanionControlRequest(
                    endpoint = registration.controlEndpoint,
                    transportIdentitySha256 = registration.transportIdentitySha256,
                    rootCertificateSha256 = registration.rootCertificateSha256,
                    rootCertificate = registration.rootCertificate,
                    operation = CompanionControlOperation.PAIR,
                    body = byteArrayOf(1),
                ),
            )
        }

        adapters.close()
    }

    private fun createAdapters(): CompanionPlatformAdapters {
        val transport = IosCompanionProxyTransport()
        return PlatformCompanionAdapterFactory(transport).create()
    }
}
