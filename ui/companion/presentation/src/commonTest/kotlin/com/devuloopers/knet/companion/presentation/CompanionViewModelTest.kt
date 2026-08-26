package com.devuloopers.knet.companion.presentation

import com.devuloopers.knet.companion.application.contract.CompanionCertificateController
import com.devuloopers.knet.companion.application.contract.CompanionCertificateDownloadResult
import com.devuloopers.knet.companion.application.contract.CompanionCredentialRefreshResult
import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.application.contract.CompanionInvitationCodec
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionPairingClient
import com.devuloopers.knet.companion.application.contract.CompanionPairingClientResult
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.application.contract.CompanionTransportResult
import com.devuloopers.knet.companion.application.contract.InvitationDecodeResult
import com.devuloopers.knet.companion.application.usecase.AcceptPairingInvitationUseCase
import com.devuloopers.knet.companion.application.usecase.ConnectCompanionUseCase
import com.devuloopers.knet.companion.application.usecase.DisconnectCompanionUseCase
import com.devuloopers.knet.companion.application.usecase.DownloadCompanionRootCertificateUseCase
import com.devuloopers.knet.companion.application.usecase.ForgetCompanionDesktopUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionConnectionUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionNetworkUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionRegistrationsUseCase
import com.devuloopers.knet.companion.application.usecase.PairCompanionDeviceUseCase
import com.devuloopers.knet.companion.application.usecase.RefreshCompanionCredentialUseCase
import com.devuloopers.knet.companion.application.usecase.SelectCompanionRegistrationUseCase
import com.devuloopers.knet.companion.application.usecase.StartCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.StopCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.VerifyCompanionCertificateTrustUseCase
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDeviceIdentity
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.CompanionTransportKind
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingInvitation
import com.devuloopers.knet.pairing.PairingInvitationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionViewModelTest {
    @Test
    fun invitationSecretNeverEntersObservableUiState() = runTest {
        val fixture = Fixture()
        val viewModel = fixture.viewModel(this)

        viewModel.dispatch(CompanionAction.InvitationSubmitted("invitation"))

        assertEquals("Development Mac", viewModel.state.value.invitationDesktopName)
        assertFalse(viewModel.state.value.toString().contains(INVITATION_SECRET))
        viewModel.close()
    }

    @Test
    fun vpnConsentIsDeliveredAsTypedEffectAndDoesNotStartCapture() = runTest {
        val fixture = Fixture(activeRegistration = registration())
        fixture.credentials.values[registration().credentialReference] = "credential"
        fixture.inspection.preparation = CompanionInspectionPreparationResult.ConsentRequired
        val viewModel = fixture.viewModel(this)

        viewModel.dispatch(CompanionAction.StartInspectionRequested)
        advanceUntilIdle()

        assertIs<CompanionEffect.RequestVpnConsent>(viewModel.effects.first())
        assertEquals(0, fixture.inspection.startCalls)
        assertFalse(viewModel.state.value.operationInProgress)
        viewModel.close()
    }

    @Test
    fun closeStopsRepositoryCollectorsOwnedByTheViewModel() = runTest {
        val fixture = Fixture()
        val viewModel = fixture.viewModel(this)
        advanceUntilIdle()
        viewModel.close()

        fixture.repository.mutableRegistrations.value = listOf(registration())
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.state.value.registrations)
    }

    private class Fixture(activeRegistration: CompanionRegistration? = null) {
        val repository = FakeRegistrationRepository(activeRegistration)
        val credentials = FakeCredentialStore()
        val transport = FakeTransport()
        val inspection = FakeInspectionController()
        private val certificates = FakeCertificateController()
        private val network = CompanionNetworkObserver {
            MutableStateFlow(CompanionNetworkState.Available(metered = false))
        }
        private val pairingClient = FakePairingClient()
        private val identityProvider = CompanionDeviceIdentityProvider {
            CompanionDeviceIdentity(
                deviceId = RegisteredDeviceId("device-1"),
                publicKeyEncoded = "public-key",
                privateKeyReference = "private-key-reference",
                proofAlgorithm = DeviceProofAlgorithm.ECDSA_P256_SHA256,
            )
        }

        fun viewModel(scope: kotlinx.coroutines.CoroutineScope): CompanionViewModel {
            val connect = ConnectCompanionUseCase(repository, credentials, network, transport) { 1_000L }
            return CompanionViewModel(
                useCases = CompanionViewModelUseCases(
                    acceptInvitation = AcceptPairingInvitationUseCase(
                        CompanionInvitationCodec { InvitationDecodeResult.Accepted(invitation()) },
                    ) { 1_000L },
                    pair = PairCompanionDeviceUseCase(
                        identityProvider,
                        pairingClient,
                        credentials,
                        repository,
                    ) { 1_000L },
                    observeRegistrations = ObserveCompanionRegistrationsUseCase(repository),
                    selectRegistration = SelectCompanionRegistrationUseCase(repository),
                    connect = connect,
                    disconnect = DisconnectCompanionUseCase(transport),
                    observeConnection = ObserveCompanionConnectionUseCase(transport),
                    observeNetwork = ObserveCompanionNetworkUseCase(network),
                    startInspection = StartCompanionInspectionUseCase(
                        repository,
                        connect,
                        certificates,
                        inspection,
                        transport,
                    ),
                    stopInspection = StopCompanionInspectionUseCase(inspection, transport),
                    observeInspection = ObserveCompanionInspectionUseCase(inspection),
                    downloadCertificate = DownloadCompanionRootCertificateUseCase(repository, certificates),
                    verifyCertificateTrust = VerifyCompanionCertificateTrustUseCase(repository, certificates),
                    refreshCredential = RefreshCompanionCredentialUseCase(
                        repository,
                        credentials,
                        pairingClient,
                    ) { 1_000L },
                    forgetDesktop = ForgetCompanionDesktopUseCase(repository, credentials, inspection, transport),
                ),
                parentScope = scope,
            )
        }
    }

    private class FakeRegistrationRepository(active: CompanionRegistration?) : CompanionRegistrationRepository {
        val mutableRegistrations = MutableStateFlow(active?.let(::listOf).orEmpty())
        private val mutableActive = MutableStateFlow(active)
        override val registrations: StateFlow<List<CompanionRegistration>> = mutableRegistrations
        override val activeRegistration: StateFlow<CompanionRegistration?> = mutableActive

        override suspend fun upsert(registration: CompanionRegistration, makeActive: Boolean) {
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
    }

    private class FakeCredentialStore : CompanionCredentialStore {
        val values = mutableMapOf<CompanionCredentialReference, String>()
        override suspend fun write(reference: CompanionCredentialReference, credential: String) {
            values[reference] = credential
        }
        override suspend fun read(reference: CompanionCredentialReference): String? = values[reference]
        override suspend fun remove(reference: CompanionCredentialReference) {
            values.remove(reference)
        }
    }

    private class FakePairingClient : CompanionPairingClient {
        override suspend fun pair(
            invitation: CompanionPairingInvitation,
            identity: CompanionDeviceIdentity,
            displayName: String,
        ): CompanionPairingClientResult = CompanionPairingClientResult.Paired(
            credential = "credential",
            scopes = setOf(DeviceScope.PROXY_STREAM),
            credentialExpiresAtEpochMillis = 5_000L,
        )

        override suspend fun refresh(
            registration: CompanionRegistration,
            currentCredential: String,
        ): CompanionCredentialRefreshResult = CompanionCredentialRefreshResult.Refreshed("new-credential", 6_000L)
    }

    private class FakeTransport : CompanionTransport {
        private val mutableState = MutableStateFlow<CompanionConnectionState>(CompanionConnectionState.Disconnected)
        override val state: StateFlow<CompanionConnectionState> = mutableState

        override suspend fun connect(
            registration: CompanionRegistration,
            credential: String,
        ): CompanionTransportResult {
            mutableState.value = CompanionConnectionState.Connected(
                desktopId = registration.desktopId,
                transport = CompanionTransportKind.DIRECT_LAN,
                connectedAtEpochMillis = 1_000L,
            )
            return CompanionTransportResult.Connected
        }

        override suspend fun disconnect() {
            mutableState.value = CompanionConnectionState.Disconnected
        }
    }

    private class FakeInspectionController : CompanionInspectionController {
        private val mutableState = MutableStateFlow<CompanionInspectionState>(CompanionInspectionState.Stopped)
        override val state: StateFlow<CompanionInspectionState> = mutableState
        var preparation: CompanionInspectionPreparationResult = CompanionInspectionPreparationResult.Ready
        var startCalls: Int = 0

        override suspend fun prepare(): CompanionInspectionPreparationResult = preparation
        override suspend fun start(configuration: CompanionInspectionConfiguration): CompanionInspectionStartResult {
            startCalls += 1
            return CompanionInspectionStartResult.Started
        }
        override suspend fun stop() {
            mutableState.value = CompanionInspectionState.Stopped
        }
    }

    private class FakeCertificateController : CompanionCertificateController {
        override fun observe(registration: CompanionRegistration): Flow<CompanionCertificateState> =
            flowOf(CompanionCertificateState.Missing)
        override suspend fun download(registration: CompanionRegistration): CompanionCertificateDownloadResult =
            CompanionCertificateDownloadResult.Failed(
                com.devuloopers.knet.companion.model.CompanionFailure(
                    com.devuloopers.knet.companion.model.CompanionFailureCode.CERTIFICATE_UNAVAILABLE,
                    "Unavailable",
                    true,
                ),
            )
        override suspend fun verifyTrust(registration: CompanionRegistration): CompanionCertificateState =
            CompanionCertificateState.Missing
    }

    private companion object {
        const val INVITATION_SECRET = "one-time-secret!"

        fun invitation(): CompanionPairingInvitation = CompanionPairingInvitation(
            protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
            desktopId = CompanionDesktopId("desktop-1"),
            desktopDisplayName = "Development Mac",
            pairing = PairingInvitation(
                id = PairingInvitationId("invitation-1"),
                secret = INVITATION_SECRET,
                expiresAtEpochMillis = 5_000L,
                scopes = setOf(DeviceScope.PROXY_STREAM),
            ),
            controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, true),
            proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, true),
            transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
            rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
        )

        fun registration(): CompanionRegistration = CompanionRegistration(
            desktopId = CompanionDesktopId("desktop-1"),
            desktopDisplayName = "Development Mac",
            deviceId = RegisteredDeviceId("device-1"),
            controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, true),
            proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, true),
            transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
            rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
            credentialReference = CompanionCredentialReference("credential-reference"),
            scopes = setOf(DeviceScope.PROXY_STREAM),
            pairedAtEpochMillis = 1_000L,
            credentialExpiresAtEpochMillis = 5_000L,
        )
    }
}
