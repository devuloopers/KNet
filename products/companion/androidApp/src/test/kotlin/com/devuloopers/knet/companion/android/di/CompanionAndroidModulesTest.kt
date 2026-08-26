package com.devuloopers.knet.companion.android.di

import com.devuloopers.knet.companion.application.contract.CompanionCertificateDownloadResult
import com.devuloopers.knet.companion.application.contract.CompanionCertificateStoreChangeObserver
import com.devuloopers.knet.companion.application.contract.CompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.application.contract.CompanionControlTransport
import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceProofSigner
import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.application.contract.CompanionInvitationCodec
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolver
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.CompanionRootCertificateSource
import com.devuloopers.knet.companion.application.usecase.ConnectCompanionUseCase
import com.devuloopers.knet.companion.connectivity.platform.CompanionPlatformAdapters
import com.devuloopers.knet.companion.connectivity.fallback.UnavailableCompanionInvitationResolver
import com.devuloopers.knet.companion.data.ProtectedCompanionCredentialStore
import com.devuloopers.knet.companion.data.VersionedCompanionInvitationCodec
import com.devuloopers.knet.companion.data.VersionedCompanionRegistrationRepository
import com.devuloopers.knet.companion.data.store.CompanionRecordStore
import com.devuloopers.knet.companion.data.store.CompanionSecretStore
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.presentation.viewmodel.CompanionViewModel
import com.devuloopers.knet.companion.presentation.viewmodel.CompanionViewModelDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.dsl.koinApplication
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionAndroidModulesTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `module set resolves contracts use cases and lifecycle ViewModel`() {
        val recordStore = MemoryRecordStore()
        val secretStore = MemorySecretStore()
        val platformAdapters = FakePlatformAdapters()
        val modules = CompanionAndroidModules.create(
            AndroidCompanionBootstrap(recordStore, secretStore, platformAdapters),
        )
        val application = koinApplication {
            allowOverride(false)
            modules(modules)
        }

        try {
            assertEquals(5, modules.distinct().size)
            assertSame(recordStore, application.koin.get<CompanionRecordStore>())
            assertSame(secretStore, application.koin.get<CompanionSecretStore>())
            assertSame(platformAdapters, application.koin.get<CompanionPlatformAdapters>())
            assertIs<VersionedCompanionRegistrationRepository>(
                application.koin.get<CompanionRegistrationRepository>(),
            )
            assertIs<ProtectedCompanionCredentialStore>(application.koin.get<CompanionCredentialStore>())
            assertIs<VersionedCompanionInvitationCodec>(application.koin.get<CompanionInvitationCodec>())
            assertSame(platformAdapters.invitationResolver, application.koin.get<CompanionInvitationResolver>())
            assertSame(platformAdapters.controlTransport, application.koin.get<CompanionControlTransport>())
            assertNotNull(application.koin.get<CompanionDeviceIdentityProvider>())
            assertNotNull(application.koin.get<CompanionDeviceProofSigner>())

            val dependencies = application.koin.get<CompanionViewModelDependencies>()
            assertSame(application.koin.get<ConnectCompanionUseCase>(), dependencies.connect)
            assertNotNull(application.koin.get<CompanionViewModel>())
        } finally {
            application.close()
        }

        assertEquals(1, platformAdapters.closeCount)
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
        var closeCount: Int = 0
            private set

        override val networkObserver: CompanionNetworkObserver = CompanionNetworkObserver {
            MutableStateFlow(CompanionNetworkState.Unknown)
        }
        override val invitationResolver: CompanionInvitationResolver =
            UnavailableCompanionInvitationResolver("composition test")
        override val controlTransport: CompanionControlTransport = CompanionControlTransport {
            error("Unavailable in composition test.")
        }
        override val rootCertificateSource: CompanionRootCertificateSource = CompanionRootCertificateSource { _, _ ->
            CompanionCertificateDownloadResult.Failed(
                com.devuloopers.knet.companion.model.CompanionFailure(
                    com.devuloopers.knet.companion.model.CompanionFailureCode.CERTIFICATE_UNAVAILABLE,
                    "Unavailable in composition test.",
                    true,
                ),
            )
        }
        override val trustVerifier: CompanionCertificateTrustVerifier = CompanionCertificateTrustVerifier { _, _, _ ->
            CompanionCertificateState.InstallationRequired
        }
        override val certificateStoreChanges: CompanionCertificateStoreChangeObserver =
            CompanionCertificateStoreChangeObserver { emptyFlow() }
        override val inspectionController: CompanionInspectionController = StoppedInspectionController()

        override fun close() {
            closeCount += 1
        }
    }

    private class StoppedInspectionController : CompanionInspectionController {
        override val state: StateFlow<CompanionInspectionState> = MutableStateFlow(CompanionInspectionState.Stopped)

        override suspend fun prepare(): CompanionInspectionPreparationResult =
            CompanionInspectionPreparationResult.ConsentRequired

        override suspend fun start(
            configuration: CompanionInspectionConfiguration,
        ): CompanionInspectionStartResult = CompanionInspectionStartResult.Started

        override suspend fun stop(): Unit = Unit
    }
}
