package com.devuloopers.knet.companion.connectivity.platform

import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.application.contract.CompanionCertificateDownloadResult
import com.devuloopers.knet.companion.application.contract.CompanionControlOperation
import com.devuloopers.knet.companion.application.contract.CompanionControlRequest
import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurityException
import com.devuloopers.knet.companion.connectivity.testing.companionRegistrationFixture
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionNetworkState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosCompanionPlatformAdapterFactoryTest {
    @Test
    fun actualFactoryNetworkAdapterDoesNotClaimAvailability() {
        val adapters = PlatformCompanionAdapterFactory().create()

        assertIs<CompanionNetworkState.Unknown>(adapters.networkObserver.observe().value)

        adapters.close()
    }

    @Test
    fun unavailableCertificateCapabilitiesFailClosedAndEmitNoSyntheticTrustEvents() = runTest {
        val adapters = PlatformCompanionAdapterFactory().create()
        val registration = companionRegistrationFixture()

        val download = assertIs<CompanionCertificateDownloadResult.Failed>(
            adapters.rootCertificateSource.download(registration, "credential"),
        )
        assertEquals(CompanionFailureCode.PLATFORM_ADAPTER_UNAVAILABLE, download.failure.code)

        val trust = assertIs<CompanionCertificateState.Rejected>(
            adapters.trustVerifier.verify(
                registration,
                "credential",
                CompanionCertificateArtifact(byteArrayOf(1), "knet-root-ca.crt"),
            ),
        )
        assertEquals(CompanionFailureCode.PLATFORM_ADAPTER_UNAVAILABLE, trust.reason.code)
        assertTrue(adapters.certificateStoreChanges.observeChanges().toList().isEmpty())

        adapters.close()
    }

    @Test
    fun unavailableInspectionCapabilityFailsPreparationAndStopsDeterministically() = runTest {
        val adapters = PlatformCompanionAdapterFactory().create()
        val controller = adapters.inspectionController

        val result = assertIs<CompanionInspectionPreparationResult.Failed>(controller.prepare())
        assertEquals(CompanionFailureCode.PLATFORM_ADAPTER_UNAVAILABLE, result.failure.code)
        assertIs<CompanionInspectionState.Failed>(controller.state.value)

        controller.stop()
        assertIs<CompanionInspectionState.Stopped>(controller.state.value)

        adapters.close()
    }

    @Test
    fun controlTransportRejectsInvalidPinnedRootBeforeNetworkAccess() = runTest {
        val adapters = PlatformCompanionAdapterFactory().create()
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
}
