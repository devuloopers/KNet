package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.model.CompanionDesktopDisplayName
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.application.contract.CompanionCertificateEnrollmentRepository
import com.devuloopers.knet.companion.application.contract.CompanionCertificateDownloadResult
import com.devuloopers.knet.companion.application.contract.CompanionCertificateInstallationArtifactSource
import com.devuloopers.knet.companion.application.contract.CompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.application.contract.CompanionCredentialRefreshResult
import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceDisplayNameProvider
import com.devuloopers.knet.companion.application.contract.CompanionEndpointRecoveryResult
import com.devuloopers.knet.companion.application.contract.CompanionEndpointResolver
import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.application.contract.CompanionInvitationCodec
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolutionResult
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolver
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionPairingClient
import com.devuloopers.knet.companion.application.contract.CompanionPairingClientResult
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.CompanionRootCertificateSource
import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.application.contract.CompanionTransportResult
import com.devuloopers.knet.companion.application.contract.InvitationDecodeResult
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionCertificateEnrollment
import com.devuloopers.knet.companion.model.CompanionBootstrapId
import com.devuloopers.knet.companion.model.CompanionBootstrapSecret
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDeviceIdentity
import com.devuloopers.knet.companion.model.CompanionDeviceDisplayName
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionPairingBootstrap
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.companion.model.UnsupportedTrafficPolicy
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import com.devuloopers.knet.pairing.PairingInvitation
import com.devuloopers.knet.pairing.PairingInvitationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest

class CompanionUseCasesTest {
    @Test
    fun expiredInvitationIsRejectedBeforePairing() = runTest {
        val bootstrap = bootstrap(expiresAt = 999L)
        val useCase = AcceptPairingInvitationUseCase(
            codec = CompanionInvitationCodec { InvitationDecodeResult.Accepted(bootstrap) },
            resolver = CompanionInvitationResolver { error("Expired bootstrap must not perform network I/O.") },
            nowEpochMillis = { 1_000L },
        )

        val result = assertIs<AcceptPairingInvitationResult.Rejected>(useCase.execute("payload"))

        assertEquals(CompanionFailureCode.INVITATION_EXPIRED, result.failure.code)
    }

    @Test
    fun resolvedInvitationMustMatchPinnedBootstrapMetadata() = runTest {
        val bootstrap = bootstrap()
        val mismatches = listOf(
            invitation().copy(transportIdentitySha256 = Sha256Fingerprint("c".repeat(64))),
            invitation().copy(rootCertificateSha256 = Sha256Fingerprint("c".repeat(64))),
        )

        mismatches.forEach { mismatched ->
            val useCase = AcceptPairingInvitationUseCase(
                codec = CompanionInvitationCodec { InvitationDecodeResult.Accepted(bootstrap) },
                resolver = CompanionInvitationResolver {
                    CompanionInvitationResolutionResult.Resolved(mismatched)
                },
                nowEpochMillis = { 1_000L },
            )

            val result = assertIs<AcceptPairingInvitationResult.Rejected>(useCase.execute("payload"))

            assertEquals(CompanionFailureCode.INVITATION_INVALID, result.failure.code)
        }
    }

    @Test
    fun pairingStoresSecretSeparatelyAndActivatesRegistration() = runTest {
        val repository = FakeRegistrationRepository()
        val credentials = FakeCredentialStore()
        val pairing = FakePairingClient()
        val useCase = PairCompanionDeviceUseCase(
            identityProvider(),
            displayNameProvider(),
            pairing,
            credentials,
            repository,
        ) { 1_000L }

        val result = assertIs<PairCompanionDeviceResult.Paired>(
            useCase.execute(invitation()),
        )

        assertEquals(result.registration, repository.activeRegistration.value)
        assertEquals("issued-secret", credentials.values[result.registration.credentialReference])
        assertFalse(result.registration.toString().contains("issued-secret"))
        assertEquals(invitation().rootCertificate, result.registration.rootCertificate)
        assertEquals("Pixel 9 · ICE1", pairing.pairedDisplayName)
    }

    @Test
    fun pairingRollsBackCredentialWhenRegistrationCommitFails() = runTest {
        val repository = FakeRegistrationRepository(failWrites = true)
        val credentials = FakeCredentialStore()
        val useCase = PairCompanionDeviceUseCase(
            identityProvider(),
            displayNameProvider(),
            FakePairingClient(),
            credentials,
            repository,
        ) { 1_000L }

        val result = assertIs<PairCompanionDeviceResult.Rejected>(useCase.execute(invitation()))

        assertEquals(CompanionFailureCode.PERSISTENCE_FAILED, result.failure.code)
        assertTrue(credentials.values.isEmpty())
    }

    @Test
    fun failedRepairDoesNotRestoreTheCredentialInvalidatedByTheDesktop() = runTest {
        val existing = registration()
        val repository = FakeRegistrationRepository(failWrites = true).apply {
            mutableRegistrations.value = listOf(existing)
            mutableActive.value = existing
        }
        val credentials = FakeCredentialStore().apply {
            values[existing.credentialReference] = "previous-secret"
        }
        val useCase = PairCompanionDeviceUseCase(
            identityProvider(),
            displayNameProvider(),
            FakePairingClient(),
            credentials,
            repository,
        ) { 1_000L }

        val result = assertIs<PairCompanionDeviceResult.Rejected>(useCase.execute(invitation()))

        assertEquals(CompanionFailureCode.PERSISTENCE_FAILED, result.failure.code)
        assertTrue(credentials.values.isEmpty())
        assertTrue(repository.registrations.value.isEmpty())
        assertNull(repository.activeRegistration.value)
    }

    @Test
    fun credentialRefreshPersistsTheRotatedSecretAndExpiry() = runTest {
        val existing = registration()
        val repository = FakeRegistrationRepository().apply {
            mutableRegistrations.value = listOf(existing)
            mutableActive.value = existing
        }
        val credentials = FakeCredentialStore().apply {
            values[existing.credentialReference] = "previous-secret"
        }
        val useCase = RefreshCompanionCredentialUseCase(
            repository,
            credentials,
            FakePairingClient(
                refreshResult = CompanionCredentialRefreshResult.Refreshed("rotated-secret", 3_000L),
            ),
        ) { 1_000L }

        val result = assertIs<RefreshCompanionCredentialResult.Refreshed>(useCase.execute())

        assertEquals("rotated-secret", credentials.values[existing.credentialReference])
        assertEquals(3_000L, result.registration.credentialExpiresAtEpochMillis)
        assertEquals(result.registration, repository.activeRegistration.value)
    }

    @Test
    fun refreshStorageFailureRemovesTheNowInvalidPreviousSecret() = runTest {
        val existing = registration()
        val repository = FakeRegistrationRepository().apply {
            mutableRegistrations.value = listOf(existing)
            mutableActive.value = existing
        }
        val credentials = FakeCredentialStore(failWrites = true).apply {
            values[existing.credentialReference] = "previous-secret"
        }
        val useCase = RefreshCompanionCredentialUseCase(
            repository,
            credentials,
            FakePairingClient(
                refreshResult = CompanionCredentialRefreshResult.Refreshed("rotated-secret", 3_000L),
            ),
        ) { 1_000L }

        val result = assertIs<RefreshCompanionCredentialResult.Rejected>(useCase.execute())

        assertEquals(CompanionFailureCode.PERSISTENCE_FAILED, result.failure.code)
        assertTrue(credentials.values.isEmpty())
    }

    @Test
    fun refreshKeepsTheValidRotatedSecretWhenOnlyRegistrationUpdateFails() = runTest {
        val existing = registration()
        val repository = FakeRegistrationRepository(failWrites = true).apply {
            mutableRegistrations.value = listOf(existing)
            mutableActive.value = existing
        }
        val credentials = FakeCredentialStore().apply {
            values[existing.credentialReference] = "previous-secret"
        }
        val useCase = RefreshCompanionCredentialUseCase(
            repository,
            credentials,
            FakePairingClient(
                refreshResult = CompanionCredentialRefreshResult.Refreshed("rotated-secret", 3_000L),
            ),
        ) { 1_000L }

        val result = assertIs<RefreshCompanionCredentialResult.Rejected>(useCase.execute())

        assertEquals(CompanionFailureCode.PERSISTENCE_FAILED, result.failure.code)
        assertEquals("rotated-secret", credentials.values[existing.credentialReference])
    }

    @Test
    fun pairingRejectsScopesThatWereNotRequestedByTheInvitation() = runTest {
        val useCase = PairCompanionDeviceUseCase(
            identityProvider(),
            displayNameProvider(),
            FakePairingClient(setOf(DeviceScope.PROXY_STREAM, DeviceScope.TRAFFIC_METADATA_READ)),
            FakeCredentialStore(),
            FakeRegistrationRepository(),
        ) { 1_000L }

        val result = assertIs<PairCompanionDeviceResult.Rejected>(useCase.execute(invitation()))

        assertEquals(CompanionFailureCode.PAIRING_REJECTED, result.failure.code)
    }

    @Test
    fun expiredCredentialIsRejectedBeforeProtectedStorageOrTransportIsUsed() = runTest {
        val repository = FakeRegistrationRepository().apply {
            mutableRegistrations.value = listOf(registration())
            mutableActive.value = registration()
        }
        val credentials = FakeCredentialStore().apply {
            values[registration().credentialReference] = "issued-secret"
        }
        val transport = FakeTransport()
        val connect = ConnectCompanionUseCase(
            repository,
            credentials,
            CompanionNetworkObserver { MutableStateFlow(CompanionNetworkState.Available(metered = false)) },
            transport,
            nowEpochMillis = { 2_000L },
            endpointResolver = CompanionEndpointResolver {
                error("Expired credentials must be rejected before discovery.")
            },
        )

        val result = assertIs<ConnectCompanionResult.Rejected>(connect.execute())

        assertEquals(CompanionFailureCode.CREDENTIAL_EXPIRED, result.failure.code)
        assertEquals(0, transport.connectCalls)
    }

    @Test
    fun startRequestsVpnConsentWithoutStartingBackend() = runTest {
        val environment = StartEnvironment(CompanionInspectionPreparationResult.ConsentRequired)

        val result = environment.start.execute()

        assertIs<StartCompanionInspectionResult.VpnConsentRequired>(result)
        assertNull(environment.inspection.startedConfiguration)
        assertEquals(0, environment.transport.connectCalls)
    }

    @Test
    fun untrustedCertificateStartsLimitedInspectionWithExplicitUdpPolicy() = runTest {
        val environment = StartEnvironment(CompanionInspectionPreparationResult.Ready)

        val result = assertIs<StartCompanionInspectionResult.Started>(environment.start.execute())

        assertFalse(result.fullHttpsInspection)
        assertFalse(environment.inspection.startedConfiguration?.fullHttpsInspection ?: true)
        assertEquals(UnsupportedTrafficPolicy.REJECT, environment.inspection.startedConfiguration?.unsupportedTrafficPolicy)
        assertEquals(1, environment.transport.connectCalls)
    }

    @Test
    fun failedInspectionStartReleasesBackendAndAuthenticatedTransport() = runTest {
        val environment = StartEnvironment(CompanionInspectionPreparationResult.Ready)
        val failure = CompanionFailure(CompanionFailureCode.VPN_START_FAILED, "Could not start.", true)
        environment.inspection.startResult = CompanionInspectionStartResult.Failed(failure)

        val result = assertIs<StartCompanionInspectionResult.Rejected>(environment.start.execute())

        assertEquals(failure, result.failure)
        assertEquals(1, environment.inspection.stopCalls)
        assertEquals(1, environment.transport.disconnectCalls)
    }

    @Test
    fun stopInspectionReleasesPacketBackendBeforeTransport() = runTest {
        val events = mutableListOf<String>()
        val inspection = object : CompanionInspectionController {
            override val state: StateFlow<CompanionInspectionState> =
                MutableStateFlow(CompanionInspectionState.Stopped)

            override suspend fun prepare(): CompanionInspectionPreparationResult =
                CompanionInspectionPreparationResult.Ready

            override suspend fun start(
                configuration: CompanionInspectionConfiguration,
            ): CompanionInspectionStartResult = CompanionInspectionStartResult.Started

            override suspend fun stop() {
                events += "inspection"
            }
        }
        val transport = object : CompanionTransport {
            override val state: StateFlow<CompanionConnectionState> =
                MutableStateFlow(CompanionConnectionState.Disconnected)

            override suspend fun connect(
                registration: CompanionRegistration,
                credential: String,
            ): CompanionTransportResult = CompanionTransportResult.Connected

            override suspend fun disconnect() {
                events += "transport"
            }
        }

        StopCompanionInspectionUseCase(inspection, transport).execute()

        assertEquals(listOf("inspection", "transport"), events)
    }

    @Test
    fun certificateVerificationUsesProtectedCredentialAndExactRegistrationRoot() = runTest {
        val repository = FakeRegistrationRepository().apply {
            mutableRegistrations.value = listOf(registration())
            mutableActive.value = registration()
        }
        val credentials = FakeCredentialStore().apply {
            values[registration().credentialReference] = "issued-secret"
        }
        var downloadedCredential: String? = null
        var verifiedCredential: String? = null
        val source = CompanionRootCertificateSource { _, credential ->
            downloadedCredential = credential
            CompanionCertificateDownloadResult.Downloaded(
                CompanionCertificateArtifact(byteArrayOf(1, 2, 3), "knet-root-ca.crt"),
            )
        }
        val verifier = CompanionCertificateTrustVerifier { active, credential, _ ->
            verifiedCredential = credential
            CompanionCertificateState.Trusted(active.rootCertificateSha256, 1_500L)
        }
        val useCase = VerifyCompanionCertificateTrustUseCase(
            repository,
            credentials,
            source,
            verifier,
            nowEpochMillis = { 1_000L },
        )

        val result = assertIs<CompanionCertificateState.Trusted>(useCase.execute())

        assertEquals(registration().rootCertificateSha256, result.rootCertificateSha256)
        assertEquals("issued-secret", downloadedCredential)
        assertEquals("issued-secret", verifiedCredential)
    }

    @Test
    fun completingCertificateEnrollmentReverifiesAndPersistsTheExactActiveRoot() = runTest {
        val active = registration()
        val repository = FakeRegistrationRepository().apply {
            mutableRegistrations.value = listOf(active)
            mutableActive.value = active
        }
        val credentials = FakeCredentialStore().apply {
            values[active.credentialReference] = "issued-secret"
        }
        val enrollmentRepository = FakeCertificateEnrollmentRepository(repository)
        val verify = VerifyCompanionCertificateTrustUseCase(
            repository,
            credentials,
            CompanionRootCertificateSource { _, _ ->
                CompanionCertificateDownloadResult.Downloaded(
                    CompanionCertificateArtifact(byteArrayOf(1), "knet-root-ca.crt"),
                )
            },
            CompanionCertificateTrustVerifier { registration, _, _ ->
                CompanionCertificateState.Trusted(registration.rootCertificateSha256, 1_500L)
            },
            nowEpochMillis = { 1_000L },
        )
        val useCase = CompleteCompanionCertificateEnrollmentUseCase(
            repository,
            enrollmentRepository,
            verify,
            nowEpochMillis = { 1_600L },
        )

        val result = assertIs<CompleteCompanionCertificateEnrollmentResult.Completed>(
            useCase.execute(active.desktopId),
        )

        assertEquals(active.rootCertificateSha256, result.enrollment.rootCertificateSha256)
        assertEquals(1_600L, result.enrollment.completedAtEpochMillis)
        assertEquals(listOf(result.enrollment), enrollmentRepository.enrollments.value)
    }

    @Test
    fun certificateDownloadUsesThePlatformInstallationArtifactSource() = runTest {
        val active = registration()
        val repository = FakeRegistrationRepository().apply {
            mutableRegistrations.value = listOf(active)
            mutableActive.value = active
        }
        val credentials = FakeCredentialStore().apply {
            values[active.credentialReference] = "issued-secret"
        }
        val expected = CompanionCertificateArtifact("mobileconfig".encodeToByteArray(), "knet-ca.mobileconfig")
        var receivedCredential: String? = null
        val useCase = DownloadCompanionRootCertificateUseCase(
            repository,
            credentials,
            CompanionCertificateInstallationArtifactSource { registration, credential ->
                assertEquals(active, registration)
                receivedCredential = credential
                CompanionCertificateDownloadResult.Downloaded(expected)
            },
            nowEpochMillis = { 1_000L },
        )

        val result = assertIs<DownloadCompanionRootCertificateResult.Downloaded>(useCase.execute())

        assertEquals(expected, result.artifact)
        assertEquals("issued-secret", receivedCredential)
    }

    @Test
    fun missingCertificateCredentialFailsBeforeNetworkAdapters() = runTest {
        val repository = FakeRegistrationRepository().apply {
            mutableRegistrations.value = listOf(registration())
            mutableActive.value = registration()
        }
        var sourceCalls = 0
        val useCase = VerifyCompanionCertificateTrustUseCase(
            repository,
            FakeCredentialStore(),
            CompanionRootCertificateSource { _, _ ->
                sourceCalls += 1
                error("must not run")
            },
            CompanionCertificateTrustVerifier { _, _, _ -> error("must not run") },
            nowEpochMillis = { 1_000L },
        )

        val result = assertIs<CompanionCertificateState.Rejected>(useCase.execute())

        assertEquals(CompanionFailureCode.CREDENTIAL_NOT_FOUND, result.reason.code)
        assertEquals(0, sourceCalls)
    }

    @Test
    fun certificateVerificationFailsClosedWhenTheExpectedDesktopIsNoLongerActive() = runTest {
        val repository = FakeRegistrationRepository().apply {
            mutableRegistrations.value = listOf(registration())
            mutableActive.value = registration()
        }
        val credentials = FakeCredentialStore().apply {
            values[registration().credentialReference] = "issued-secret"
        }
        var sourceCalls = 0
        val useCase = VerifyCompanionCertificateTrustUseCase(
            repository,
            credentials,
            CompanionRootCertificateSource { _, _ ->
                sourceCalls += 1
                error("must not run")
            },
            CompanionCertificateTrustVerifier { _, _, _ -> error("must not run") },
            nowEpochMillis = { 1_000L },
        )

        val result = assertIs<CompanionCertificateState.VerificationDeferred>(
            useCase.execute(CompanionDesktopId("desktop-2")),
        )

        assertEquals(CompanionFailureCode.REGISTRATION_NOT_FOUND, result.reason.code)
        assertEquals(0, sourceCalls)
    }

    @Test
    fun unavailableDesktopDefersCertificateVerificationWithoutReportingTrustRejection() = runTest {
        val active = registration()
        val repository = FakeRegistrationRepository().apply {
            mutableRegistrations.value = listOf(active)
            mutableActive.value = active
        }
        val credentials = FakeCredentialStore().apply {
            values[active.credentialReference] = "issued-secret"
        }
        var verifierCalls = 0
        val useCase = VerifyCompanionCertificateTrustUseCase(
            repository,
            credentials,
            CompanionRootCertificateSource { _, _ ->
                CompanionCertificateDownloadResult.Failed(
                    CompanionFailure(
                        CompanionFailureCode.TRANSPORT_UNAVAILABLE,
                        "Desktop unavailable.",
                        true,
                    ),
                )
            },
            CompanionCertificateTrustVerifier { _, _, _ ->
                verifierCalls += 1
                error("must not run")
            },
            nowEpochMillis = { 1_000L },
        )

        val result = assertIs<CompanionCertificateState.VerificationDeferred>(useCase.execute())

        assertEquals(CompanionFailureCode.TRANSPORT_UNAVAILABLE, result.reason.code)
        assertEquals(0, verifierCalls)
    }

    private class StartEnvironment(preparation: CompanionInspectionPreparationResult) {
        val repository = FakeRegistrationRepository().apply {
            val registration = registration()
            mutableRegistrations.value = listOf(registration)
            mutableActive.value = registration
        }
        val credentials = FakeCredentialStore().apply {
            values[registration().credentialReference] = "issued-secret"
        }
        val transport = FakeTransport()
        val inspection = FakeInspectionController(preparation)
        private val verifyCertificate = VerifyCompanionCertificateTrustUseCase(
            registrations = repository,
            credentials = credentials,
            certificates = CompanionRootCertificateSource { _, _ ->
                CompanionCertificateDownloadResult.Downloaded(
                    CompanionCertificateArtifact(byteArrayOf(1), "knet-root-ca.crt"),
                )
            },
            verifier = CompanionCertificateTrustVerifier { _, _, _ ->
                CompanionCertificateState.InstallationRequired
            },
            nowEpochMillis = { 1_000L },
        )
        private val connect = ConnectCompanionUseCase(
            repository,
            credentials,
            CompanionNetworkObserver { MutableStateFlow(CompanionNetworkState.Available(metered = false)) },
            transport,
            nowEpochMillis = { 1_000L },
            endpointResolver = passThroughEndpointResolver(),
        )
        val start = StartCompanionInspectionUseCase(repository, connect, verifyCertificate, inspection, transport)
    }

    private class FakeRegistrationRepository(
        private val failWrites: Boolean = false,
    ) : CompanionRegistrationRepository {
        val mutableRegistrations = MutableStateFlow<List<CompanionRegistration>>(emptyList())
        val mutableActive = MutableStateFlow<CompanionRegistration?>(null)
        override val registrations: StateFlow<List<CompanionRegistration>> = mutableRegistrations
        override val activeRegistration: StateFlow<CompanionRegistration?> = mutableActive

        override suspend fun upsert(registration: CompanionRegistration, makeActive: Boolean) {
            if (failWrites) error("write failed")
            mutableRegistrations.value = mutableRegistrations.value.filterNot { it.desktopId == registration.desktopId } + registration
            if (makeActive) mutableActive.value = registration
        }

        override suspend fun setActive(desktopId: CompanionDesktopId?): Boolean {
            val selected = desktopId?.let { id -> mutableRegistrations.value.firstOrNull { it.desktopId == id } }
            if (desktopId != null && selected == null) return false
            mutableActive.value = selected
            return true
        }

        override suspend fun remove(desktopId: CompanionDesktopId): CompanionRegistration? {
            val removed = mutableRegistrations.value.firstOrNull { it.desktopId == desktopId } ?: return null
            mutableRegistrations.value = mutableRegistrations.value.filterNot { it.desktopId == desktopId }
            if (mutableActive.value?.desktopId == desktopId) mutableActive.value = null
            return removed
        }

        override suspend fun migrateIdentity(
            previousDesktopId: CompanionDesktopId,
            registration: CompanionRegistration,
            makeActive: Boolean,
        ): Boolean {
            if (failWrites) error("write failed")
            if (mutableRegistrations.value.none { it.desktopId == previousDesktopId }) return false
            mutableRegistrations.value = mutableRegistrations.value
                .filterNot { it.desktopId == previousDesktopId || it.desktopId == registration.desktopId } + registration
            if (makeActive || mutableActive.value?.desktopId == previousDesktopId) mutableActive.value = registration
            return true
        }
    }

    private class FakeCredentialStore(
        private val failWrites: Boolean = false,
    ) : CompanionCredentialStore {
        val values = mutableMapOf<CompanionCredentialReference, String>()
        override suspend fun write(reference: CompanionCredentialReference, credential: String) {
            if (failWrites) error("write failed")
            values[reference] = credential
        }
        override suspend fun read(reference: CompanionCredentialReference): String? = values[reference]
        override suspend fun remove(reference: CompanionCredentialReference) {
            values.remove(reference)
        }
    }

    private class FakeCertificateEnrollmentRepository(
        private val registrations: CompanionRegistrationRepository,
    ) : CompanionCertificateEnrollmentRepository {
        private val mutableEnrollments = MutableStateFlow<List<CompanionCertificateEnrollment>>(emptyList())
        override val enrollments: StateFlow<List<CompanionCertificateEnrollment>> = mutableEnrollments

        override suspend fun complete(enrollment: CompanionCertificateEnrollment): Boolean {
            val registration = registrations.registrations.value.firstOrNull {
                it.desktopId == enrollment.desktopId
            } ?: return false
            if (!enrollment.matches(registration)) return false
            mutableEnrollments.value = mutableEnrollments.value.filterNot {
                it.desktopId == enrollment.desktopId
            } + enrollment
            return true
        }

        override suspend fun removeEnrollment(desktopId: CompanionDesktopId): Boolean {
            val existed = mutableEnrollments.value.any { it.desktopId == desktopId }
            mutableEnrollments.value = mutableEnrollments.value.filterNot { it.desktopId == desktopId }
            return existed
        }
    }

    private class FakePairingClient(
        private val grantedScopes: Set<DeviceScope> = setOf(DeviceScope.PROXY_STREAM),
        private val refreshResult: CompanionCredentialRefreshResult = CompanionCredentialRefreshResult.Rejected(
            CompanionFailure(CompanionFailureCode.PAIRING_REJECTED, "not used", false),
        ),
    ) : CompanionPairingClient {
        var pairedDisplayName: String? = null

        override suspend fun pair(
            invitation: CompanionPairingInvitation,
            identity: CompanionDeviceIdentity,
            displayName: String,
        ): CompanionPairingClientResult {
            pairedDisplayName = displayName
            return CompanionPairingClientResult.Paired(
                credential = "issued-secret",
                scopes = grantedScopes,
                credentialExpiresAtEpochMillis = 2_000L,
            )
        }

        override suspend fun refresh(
            registration: CompanionRegistration,
            currentCredential: String,
        ): CompanionCredentialRefreshResult = refreshResult
    }

    private class FakeTransport : CompanionTransport {
        private val mutableState = MutableStateFlow<CompanionConnectionState>(CompanionConnectionState.Disconnected)
        override val state: StateFlow<CompanionConnectionState> = mutableState
        var connectCalls: Int = 0
        var disconnectCalls: Int = 0

        override suspend fun connect(
            registration: CompanionRegistration,
            credential: String,
        ): CompanionTransportResult {
            connectCalls += 1
            mutableState.value = CompanionConnectionState.Connected(
                registration.desktopId,
                com.devuloopers.knet.companion.model.CompanionTransportKind.DIRECT_LAN,
                1_000L,
            )
            return CompanionTransportResult.Connected
        }

        override suspend fun disconnect() {
            disconnectCalls += 1
            mutableState.value = CompanionConnectionState.Disconnected
        }
    }

    private class FakeInspectionController(
        private val preparation: CompanionInspectionPreparationResult,
    ) : CompanionInspectionController {
        private val mutableState = MutableStateFlow<CompanionInspectionState>(CompanionInspectionState.Stopped)
        override val state: StateFlow<CompanionInspectionState> = mutableState
        var startedConfiguration: CompanionInspectionConfiguration? = null
        var startResult: CompanionInspectionStartResult = CompanionInspectionStartResult.Started
        var stopCalls: Int = 0

        override suspend fun prepare(): CompanionInspectionPreparationResult = preparation

        override suspend fun start(configuration: CompanionInspectionConfiguration): CompanionInspectionStartResult {
            startedConfiguration = configuration
            return startResult
        }

        override suspend fun stop() {
            stopCalls += 1
            mutableState.value = CompanionInspectionState.Stopped
        }
    }

    private fun identityProvider(): CompanionDeviceIdentityProvider = CompanionDeviceIdentityProvider {
        CompanionDeviceIdentity(
            deviceId = RegisteredDeviceId("device-1"),
            proofAlgorithm = DeviceProofAlgorithm.ECDSA_P256_SHA256,
            publicKeyEncoded = "public-key",
            privateKeyReference = "private-key-reference",
        )
    }

    private fun displayNameProvider(): CompanionDeviceDisplayNameProvider = CompanionDeviceDisplayNameProvider {
        CompanionDeviceDisplayName("Pixel 9 · ICE1")
    }

    private fun invitation(expiresAt: Long = 2_000L): CompanionPairingInvitation = CompanionPairingInvitation(
        protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
        desktopId = CompanionDesktopId("desktop-1"),
        desktopDisplayName = CompanionDesktopDisplayName("Development Mac"),
        pairing = PairingInvitation(
            id = PairingInvitationId("invitation-1"),
            secret = "s".repeat(32),
            expiresAtEpochMillis = expiresAt,
            scopes = setOf(DeviceScope.PROXY_STREAM),
        ),
        controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, scheme = CompanionEndpointScheme.HTTPS),
        proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, scheme = CompanionEndpointScheme.HTTPS),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
        rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
    )

    private fun bootstrap(expiresAt: Long = 2_000L): CompanionPairingBootstrap = CompanionPairingBootstrap(
        protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
        id = CompanionBootstrapId("bootstrap-1"),
        retrievalSecret = CompanionBootstrapSecret("r".repeat(32)),
        expiresAtEpochMillis = expiresAt,
        rootCertificateEndpoint = CompanionServiceEndpoint("192.168.1.2", 8181, scheme = CompanionEndpointScheme.HTTP),
        retrievalEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, scheme = CompanionEndpointScheme.HTTPS),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
    )

    companion object {
        fun registration(): CompanionRegistration = CompanionRegistration(
            desktopId = CompanionDesktopId("desktop-1"),
            desktopDisplayName = CompanionDesktopDisplayName("Development Mac"),
            deviceId = RegisteredDeviceId("device-1"),
            controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, scheme = CompanionEndpointScheme.HTTPS),
            proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, scheme = CompanionEndpointScheme.HTTPS),
            transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
            rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
            rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
            credentialReference = CompanionCredentialReference("credential-reference"),
            scopes = setOf(DeviceScope.PROXY_STREAM),
            pairedAtEpochMillis = 1_000L,
            credentialExpiresAtEpochMillis = 2_000L,
        )
    }
}

private fun passThroughEndpointResolver(): CompanionEndpointResolver = CompanionEndpointResolver { registration ->
    CompanionEndpointRecoveryResult.Recovered(
        registration = registration,
    )
}
