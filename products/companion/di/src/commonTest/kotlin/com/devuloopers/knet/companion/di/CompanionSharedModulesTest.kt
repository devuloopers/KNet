package com.devuloopers.knet.companion.di

import com.devuloopers.knet.companion.application.contract.CompanionCertificateInstallationArtifactSource
import com.devuloopers.knet.companion.application.contract.CompanionCertificateStoreChangeObserver
import com.devuloopers.knet.companion.application.contract.CompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.application.contract.CompanionControlTransport
import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionDesktopDiscovery
import com.devuloopers.knet.companion.application.contract.CompanionDeviceDisplayNameProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceProofSigner
import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolver
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionPairingClient
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.CompanionRootCertificateSource
import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.application.contract.CompanionTransportResult
import com.devuloopers.knet.companion.application.usecase.StartCompanionInspectionUseCase
import com.devuloopers.knet.companion.connectivity.platform.CompanionPlatformAdapters
import com.devuloopers.knet.companion.data.ProtectedCompanionCredentialStore
import com.devuloopers.knet.companion.data.VersionedCompanionStateRepository
import com.devuloopers.knet.companion.data.store.CompanionRecordStore
import com.devuloopers.knet.companion.data.store.CompanionSecretStore
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDiscoveryState
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.presentation.viewmodel.CompanionViewModelDependencies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class CompanionSharedModulesTest {
    @Test
    fun `shared modules resolve a complete portable graph from typed platform prerequisites`() {
        val recordStore = MemoryRecordStore()
        val secretStore = MemorySecretStore()
        val adapters = FakePlatformAdapters()
        val transport = FakeTransport()
        val sharedModules = CompanionSharedModules.create()
        val platformPrerequisites = module {
            single<CompanionRecordStore> { recordStore }
            single<CompanionSecretStore> { secretStore }
            single<CompanionPlatformAdapters> { adapters }
            single<CompanionDeviceIdentityProvider> { CompanionDeviceIdentityProvider { error("Not called") } }
            single<CompanionDeviceDisplayNameProvider> {
                CompanionDeviceDisplayNameProvider { error("Not called") }
            }
            single<CompanionDeviceProofSigner> { CompanionDeviceProofSigner { _, _ -> error("Not called") } }
            single<CompanionTransport> { transport }
        }
        val application = koinApplication {
            allowOverride(false)
            modules(listOf(platformPrerequisites) + sharedModules)
        }

        try {
            assertEquals(4, sharedModules.distinct().size)
            assertSame(adapters.networkObserver, application.koin.get<CompanionNetworkObserver>())
            assertSame(adapters.desktopDiscovery, application.koin.get<CompanionDesktopDiscovery>())
            assertSame(adapters.invitationResolver, application.koin.get<CompanionInvitationResolver>())
            assertSame(adapters.controlTransport, application.koin.get<CompanionControlTransport>())
            assertSame(adapters.inspectionController, application.koin.get<CompanionInspectionController>())
            assertSame(transport, application.koin.get<CompanionTransport>())
            assertIs<ProtectedCompanionCredentialStore>(application.koin.get<CompanionCredentialStore>())

            val registrationRepository = application.koin.get<CompanionRegistrationRepository>()
            assertIs<VersionedCompanionStateRepository>(registrationRepository)
            assertSame(
                registrationRepository,
                application.koin.get<com.devuloopers.knet.companion.application.contract.CompanionCertificateEnrollmentRepository>(),
            )
            assertNotNull(application.koin.get<CompanionPairingClient>())

            val dependencies = application.koin.get<CompanionViewModelDependencies>()
            assertSame(application.koin.get<StartCompanionInspectionUseCase>(), dependencies.startInspection)
        } finally {
            application.close()
        }
    }

    private class MemoryRecordStore : CompanionRecordStore {
        private val mutableContent = MutableStateFlow<String?>(null)
        override val content: StateFlow<String?> = mutableContent

        override suspend fun write(content: String?) {
            mutableContent.value = content
        }
    }

    private class MemorySecretStore : CompanionSecretStore {
        private val values = mutableMapOf<String, String>()

        override suspend fun write(key: String, value: String) {
            values[key] = value
        }

        override suspend fun read(key: String): String? = values[key]

        override suspend fun remove(key: String) {
            values.remove(key)
        }
    }

    private class FakePlatformAdapters : CompanionPlatformAdapters {
        override val networkObserver: CompanionNetworkObserver = CompanionNetworkObserver {
            MutableStateFlow(CompanionNetworkState.Unknown)
        }
        override val desktopDiscovery: CompanionDesktopDiscovery = object : CompanionDesktopDiscovery {
            override val state: StateFlow<CompanionDiscoveryState> =
                MutableStateFlow(CompanionDiscoveryState.Idle)

            override fun start(targetDesktopIds: Set<CompanionDesktopId>): Unit = Unit
            override fun stop(): Unit = Unit
        }
        override val invitationResolver: CompanionInvitationResolver = CompanionInvitationResolver { error("Not called") }
        override val controlTransport: CompanionControlTransport = CompanionControlTransport { error("Not called") }
        override val rootCertificateSource: CompanionRootCertificateSource =
            CompanionRootCertificateSource { _, _ -> error("Not called") }
        override val certificateInstallationArtifactSource: CompanionCertificateInstallationArtifactSource =
            CompanionCertificateInstallationArtifactSource { _, _ -> error("Not called") }
        override val trustVerifier: CompanionCertificateTrustVerifier =
            CompanionCertificateTrustVerifier { _, _, _ -> error("Not called") }
        override val certificateStoreChanges: CompanionCertificateStoreChangeObserver =
            CompanionCertificateStoreChangeObserver { emptyFlow() }
        override val inspectionController: CompanionInspectionController = FakeInspectionController()

        override fun close(): Unit = Unit
    }

    private class FakeInspectionController : CompanionInspectionController {
        override val state: StateFlow<CompanionInspectionState> = MutableStateFlow(CompanionInspectionState.Stopped)

        override suspend fun prepare(): CompanionInspectionPreparationResult =
            CompanionInspectionPreparationResult.Ready

        override suspend fun start(
            configuration: CompanionInspectionConfiguration,
        ): CompanionInspectionStartResult = CompanionInspectionStartResult.Started

        override suspend fun stop(): Unit = Unit
    }

    private class FakeTransport : CompanionTransport {
        override val state: StateFlow<CompanionConnectionState> =
            MutableStateFlow(CompanionConnectionState.Disconnected)

        override suspend fun connect(
            registration: CompanionRegistration,
            credential: String,
        ): CompanionTransportResult = CompanionTransportResult.Connected

        override suspend fun disconnect(): Unit = Unit
    }
}
