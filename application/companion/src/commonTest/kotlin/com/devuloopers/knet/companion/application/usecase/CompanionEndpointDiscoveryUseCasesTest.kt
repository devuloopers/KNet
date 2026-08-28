package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.model.CompanionDesktopDisplayName
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionDesktopDiscovery
import com.devuloopers.knet.companion.application.contract.CompanionEndpointReconciliationClient
import com.devuloopers.knet.companion.application.contract.CompanionEndpointReconciliationResult
import com.devuloopers.knet.companion.application.contract.CompanionEndpointRecoveryResult
import com.devuloopers.knet.companion.application.contract.CompanionEndpointResolver
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.application.contract.CompanionTransportResult
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDesktopAvailability
import com.devuloopers.knet.companion.model.CompanionDesktopRuntimeId
import com.devuloopers.knet.companion.model.CompanionDiscoveryAdvertisement
import com.devuloopers.knet.companion.model.CompanionDiscoveryCandidate
import com.devuloopers.knet.companion.model.CompanionDiscoveryProtocol
import com.devuloopers.knet.companion.model.CompanionDiscoveryState
import com.devuloopers.knet.companion.model.CompanionEndpointDescriptor
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.CompanionTransportKind
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CompanionEndpointDiscoveryUseCasesTest {
    @Test
    fun availabilityMonitorPublishesAfterAuthenticatedPersistedEndpoint() = runTest {
        val existing = registration()
        val repository = FakeRegistrationRepository(existing)
        val discovery = FakeDiscovery()
        val endpointResolver = recovery(repository, discovery) { _, endpoint, credential ->
            assertEquals(existing.controlEndpoint, endpoint)
            assertEquals("credential", credential)
            CompanionEndpointReconciliationResult.Verified(descriptor())
        }
        val monitor = MonitorCompanionDesktopAvailabilityUseCase(
            repository,
            CompanionNetworkObserver { MutableStateFlow(com.devuloopers.knet.companion.model.CompanionNetworkState.Available(false)) },
            endpointResolver,
            nowEpochMillis = { 4_000L },
            probeIntervalMillis = 1_000L,
        )

        val job = launch { monitor.execute() }
        runCurrent()
        val available = assertIs<CompanionDesktopAvailability.Available>(monitor.state.value)
        assertEquals(CANONICAL_ID, available.desktopId)
        assertEquals(4_000L, available.verifiedAtEpochMillis)
        assertEquals(0, discovery.startCalls)
        job.cancel()
    }

    @Test
    fun availabilityMonitorDoesNotProbeWhenLocalNetworkIsUnavailable() = runTest {
        val existing = registration()
        val repository = FakeRegistrationRepository(existing)
        var resolutionCalls = 0
        val monitor = MonitorCompanionDesktopAvailabilityUseCase(
            repository,
            CompanionNetworkObserver { MutableStateFlow(com.devuloopers.knet.companion.model.CompanionNetworkState.Unavailable) },
            CompanionEndpointResolver {
                resolutionCalls += 1
                CompanionEndpointRecoveryResult.Rejected(desktopUnavailable())
            },
            nowEpochMillis = { 4_000L },
            probeIntervalMillis = 1_000L,
        )

        val job = launch { monitor.execute() }
        runCurrent()

        assertIs<CompanionDesktopAvailability.Unavailable>(monitor.state.value)
        assertEquals(0, resolutionCalls)
        job.cancel()
    }

    @Test
    fun availabilityMonitorRecoversChangedAddressBeforePublishingAvailable() = runTest {
        val existing = registration()
        val repository = FakeRegistrationRepository(existing)
        val candidate = candidate(host = "192.168.1.77")
        val discovery = FakeDiscovery(candidate)
        val recovery = recovery(repository, discovery) { _, endpoint, _ ->
            if (endpoint == existing.controlEndpoint) {
                CompanionEndpointReconciliationResult.Rejected(desktopUnavailable())
            } else {
                assertEquals("192.168.1.77", endpoint.host)
                CompanionEndpointReconciliationResult.Verified(descriptor())
            }
        }
        val monitor = MonitorCompanionDesktopAvailabilityUseCase(
            repository,
            CompanionNetworkObserver { MutableStateFlow(com.devuloopers.knet.companion.model.CompanionNetworkState.Available(false)) },
            recovery,
            nowEpochMillis = { 5_000L },
            probeIntervalMillis = 1_000L,
        )

        val job = launch { monitor.execute() }
        runCurrent()
        advanceTimeBy(750L)
        runCurrent()

        assertIs<CompanionDesktopAvailability.Available>(monitor.state.value)
        assertEquals("192.168.1.77", repository.activeRegistration.value?.controlEndpoint?.host)
        job.cancel()
    }

    @Test
    fun availabilityMonitorKeepsUnavailableVisibleDuringBackgroundRetries() = runTest {
        val existing = registration(desktopId = CANONICAL_ID)
        val repository = FakeRegistrationRepository(existing)
        val monitor = MonitorCompanionDesktopAvailabilityUseCase(
            repository,
            CompanionNetworkObserver {
                MutableStateFlow(com.devuloopers.knet.companion.model.CompanionNetworkState.Available(false))
            },
            recovery(repository, FakeDiscovery(), timeoutMillis = 500L) { _, _, _ ->
                CompanionEndpointReconciliationResult.Rejected(desktopUnavailable())
            },
            nowEpochMillis = { 5_000L },
            probeIntervalMillis = 1_000L,
        )
        val observed = mutableListOf<CompanionDesktopAvailability>()
        val observation = launch { monitor.state.collect(observed::add) }
        val job = launch { monitor.execute() }
        runCurrent()

        advanceTimeBy(500L)
        runCurrent()
        assertIs<CompanionDesktopAvailability.Unavailable>(monitor.state.value)

        advanceTimeBy(1_000L)
        runCurrent()

        assertIs<CompanionDesktopAvailability.Unavailable>(monitor.state.value)
        assertEquals(1, observed.filterIsInstance<CompanionDesktopAvailability.Checking>().size)
        job.cancel()
        observation.cancel()
    }

    @Test
    fun availabilityMonitorPublishesAvailableWhenSilentRetrySucceeds() = runTest {
        val existing = registration(desktopId = CANONICAL_ID)
        val repository = FakeRegistrationRepository(existing)
        var desktopAvailable = false
        val monitor = MonitorCompanionDesktopAvailabilityUseCase(
            repository,
            CompanionNetworkObserver {
                MutableStateFlow(com.devuloopers.knet.companion.model.CompanionNetworkState.Available(false))
            },
            CompanionEndpointResolver {
                if (desktopAvailable) {
                    CompanionEndpointRecoveryResult.Recovered(existing)
                } else {
                    CompanionEndpointRecoveryResult.Rejected(desktopUnavailable())
                }
            },
            nowEpochMillis = { 6_000L },
            probeIntervalMillis = 1_000L,
        )
        val job = launch { monitor.execute() }
        runCurrent()
        advanceTimeBy(500L)
        runCurrent()
        assertIs<CompanionDesktopAvailability.Unavailable>(monitor.state.value)

        desktopAvailable = true
        advanceTimeBy(1_000L)
        runCurrent()

        val available = assertIs<CompanionDesktopAvailability.Available>(monitor.state.value)
        assertEquals(CANONICAL_ID, available.desktopId)
        assertEquals(6_000L, available.verifiedAtEpochMillis)
        job.cancel()
    }

    @Test
    fun legacyIdentityAndChangedAddressAreMigratedOnlyAfterAuthentication() = runTest {
        val existing = registration()
        val repository = FakeRegistrationRepository(existing)
        val candidate = candidate(host = "192.168.1.44")
        val recovery = recovery(repository, FakeDiscovery(candidate)) { _, endpoint, _ ->
            if (endpoint == existing.controlEndpoint) {
                CompanionEndpointReconciliationResult.Rejected(desktopUnavailable())
            } else {
                assertEquals("192.168.1.44", endpoint.host)
                CompanionEndpointReconciliationResult.Verified(descriptor())
            }
        }

        val result = assertIs<CompanionEndpointRecoveryResult.Recovered>(recovery.resolve(existing))

        assertEquals(CANONICAL_ID, result.registration.desktopId)
        assertEquals("192.168.1.44", result.registration.controlEndpoint.host)
        assertEquals(CONTROL_PORT, result.registration.controlEndpoint.port)
        assertEquals("192.168.1.44", result.registration.proxyEndpoint.host)
        assertEquals(PROXY_PORT, result.registration.proxyEndpoint.port)
        assertEquals(result.registration, repository.activeRegistration.value)
        assertEquals(1, repository.migrationCount)
    }

    @Test
    fun unrelatedDesktopAdvertisementCannotChangeTheRegistration() = runTest {
        val existing = registration()
        val repository = FakeRegistrationRepository(existing)
        var reconciliationCalls = 0
        val recovery = recovery(repository, FakeDiscovery()) { _, _, _ ->
            reconciliationCalls += 1
            CompanionEndpointReconciliationResult.Verified(descriptor())
        }
        val unrelated = candidate(
            desktopId = CompanionDesktopId("33333333-3333-4333-8333-333333333333"),
            legacyIds = emptySet(),
        )

        val result = assertIs<CompanionEndpointRecoveryResult.Rejected>(recovery.execute(existing, listOf(unrelated)))

        assertEquals(CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH, result.failure.code)
        assertEquals(0, reconciliationCalls)
        assertSame(existing, repository.activeRegistration.value)
    }

    @Test
    fun unreachableMatchingEndpointRemainsATransientTransportFailure() = runTest {
        val existing = registration()
        val repository = FakeRegistrationRepository(existing)
        val recovery = recovery(repository, FakeDiscovery()) { _, _, _ ->
            CompanionEndpointReconciliationResult.Rejected(desktopUnavailable())
        }

        val result = assertIs<CompanionEndpointRecoveryResult.Rejected>(
            recovery.execute(existing, listOf(candidate())),
        )

        assertEquals(CompanionFailureCode.TRANSPORT_UNAVAILABLE, result.failure.code)
        assertEquals(true, result.failure.recoverable)
        assertSame(existing, repository.activeRegistration.value)
    }

    @Test
    fun authenticatedIdentityFailureTakesPrecedenceOverAnUnreachableEndpoint() = runTest {
        val existing = registration()
        val repository = FakeRegistrationRepository(existing)
        val unreachable = candidate(host = "192.168.1.44")
        val wrongIdentity = candidate(host = "192.168.1.45", instanceName = "KNet wrong identity")
        val recovery = recovery(repository, FakeDiscovery()) { _, endpoint, _ ->
            if (endpoint.host == unreachable.endpoints.single().host) {
                CompanionEndpointReconciliationResult.Rejected(desktopUnavailable())
            } else {
                CompanionEndpointReconciliationResult.Rejected(identityMismatch())
            }
        }

        val result = assertIs<CompanionEndpointRecoveryResult.Rejected>(
            recovery.execute(existing, listOf(unreachable, wrongIdentity)),
        )

        assertEquals(CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH, result.failure.code)
        assertEquals(false, result.failure.recoverable)
        assertSame(existing, repository.activeRegistration.value)
    }

    @Test
    fun spoofedCandidateDoesNotHideALaterAuthenticatedCandidate() = runTest {
        val existing = registration()
        val repository = FakeRegistrationRepository(existing)
        val spoof = candidate(host = "192.168.1.90")
        val legitimate = candidate(host = "192.168.1.44", instanceName = "KNet legitimate")
        val discovery = FakeDiscovery(spoof) {
            launch {
                delay(1_000L)
                publish(CompanionDiscoveryState.Candidates(LEGACY_ID, listOf(spoof, legitimate)))
            }
        }
        val recovery = recovery(repository, discovery, timeoutMillis = 4_000L) { _, endpoint, _ ->
            if (endpoint.host == "192.168.1.44") {
                CompanionEndpointReconciliationResult.Verified(descriptor())
            } else {
                CompanionEndpointReconciliationResult.Rejected(
                    if (endpoint == existing.controlEndpoint) desktopUnavailable() else identityMismatch(),
                )
            }
        }

        val result = assertIs<CompanionEndpointRecoveryResult.Recovered>(recovery.resolve(existing))

        assertEquals("192.168.1.44", result.registration.controlEndpoint.host)
    }

    @Test
    fun twoAuthenticatedRuntimeIdsForOneIdentityFailClosed() = runTest {
        val existing = registration()
        val repository = FakeRegistrationRepository(existing)
        val first = candidate(host = "192.168.1.44", runtimeId = RUNTIME_ID)
        val secondRuntime = CompanionDesktopRuntimeId.parse("44444444-4444-4444-8444-444444444444")
        val second = candidate(host = "192.168.1.45", runtimeId = secondRuntime, instanceName = "KNet clone")
        val recovery = recovery(repository, FakeDiscovery()) { _, endpoint, _ ->
            CompanionEndpointReconciliationResult.Verified(
                descriptor(runtimeId = if (endpoint.host == "192.168.1.44") RUNTIME_ID else secondRuntime),
            )
        }

        val result = assertIs<CompanionEndpointRecoveryResult.Rejected>(
            recovery.execute(existing, listOf(first, second)),
        )

        assertEquals(CompanionFailureCode.DESKTOP_IDENTITY_CONFLICT, result.failure.code)
        assertEquals(0, repository.migrationCount)
    }

    @Test
    fun activeInspectionReconnectsAfterAnAuthenticatedAddressChange() = runTest {
        val existing = registration(desktopId = CANONICAL_ID)
        val repository = FakeRegistrationRepository(existing)
        val discovery = FakeDiscovery()
        val transport = FakeTransport(existing)
        val recovery = recovery(repository, discovery) { _, _, _ ->
            CompanionEndpointReconciliationResult.Verified(descriptor(legacyIds = emptySet()))
        }
        val connect = ConnectCompanionUseCase(
            repository,
            FakeCredentialStore(),
            CompanionNetworkObserver { MutableStateFlow(com.devuloopers.knet.companion.model.CompanionNetworkState.Available(false)) },
            transport,
            nowEpochMillis = { 2_000L },
            endpointResolver = recovery,
        )
        val maintain = MaintainCompanionEndpointUseCase(repository, discovery, recovery, transport, connect)
        val job = launch { maintain.execute() }
        runCurrent()

        discovery.publish(
            CompanionDiscoveryState.Candidates(
                CANONICAL_ID,
                listOf(candidate(host = "192.168.1.77", legacyIds = emptySet())),
            ),
        )
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals("192.168.1.77", repository.activeRegistration.value?.proxyEndpoint?.host)
        assertEquals(1, transport.connectCalls)
        job.cancel()
    }

    @Test
    fun connectionUsesAuthenticatedPersistedAddressWithoutDiscovery() = runTest {
        val existing = registration(desktopId = CANONICAL_ID)
        val repository = FakeRegistrationRepository(existing)
        val discovery = FakeDiscovery()
        val endpointResolver = recovery(repository, discovery) { _, endpoint, credential ->
            assertEquals(existing.controlEndpoint, endpoint)
            assertEquals("credential", credential)
            CompanionEndpointReconciliationResult.Verified(descriptor(legacyIds = emptySet()))
        }
        val transport = FakeTransport(existing)
        val connect = ConnectCompanionUseCase(
            repository,
            FakeCredentialStore(),
            CompanionNetworkObserver {
                MutableStateFlow(com.devuloopers.knet.companion.model.CompanionNetworkState.Available(false))
            },
            transport,
            nowEpochMillis = { 2_000L },
            endpointResolver = endpointResolver,
        )

        assertIs<ConnectCompanionResult.Connected>(connect.execute())

        assertEquals("192.168.1.2", transport.lastRegistration?.controlEndpoint?.host)
        assertEquals("192.168.1.2", transport.lastRegistration?.proxyEndpoint?.host)
        assertEquals(0, discovery.startCalls)
    }

    @Test
    fun connectionFallsBackToAuthenticatedDiscoveryWhenPersistedAddressIsStale() = runTest {
        val existing = registration(desktopId = CANONICAL_ID)
        val repository = FakeRegistrationRepository(existing)
        val discovered = candidate(host = "192.168.1.77", legacyIds = emptySet())
        val discovery = FakeDiscovery(discovered)
        val endpointResolver = recovery(repository, discovery) { _, endpoint, credential ->
            assertEquals("credential", credential)
            if (endpoint == existing.controlEndpoint) {
                CompanionEndpointReconciliationResult.Rejected(desktopUnavailable())
            } else {
                CompanionEndpointReconciliationResult.Verified(descriptor(legacyIds = emptySet()))
            }
        }
        val transport = FakeTransport(existing)
        val connect = ConnectCompanionUseCase(
            repository,
            FakeCredentialStore(),
            CompanionNetworkObserver {
                MutableStateFlow(com.devuloopers.knet.companion.model.CompanionNetworkState.Available(false))
            },
            transport,
            nowEpochMillis = { 2_000L },
            endpointResolver = endpointResolver,
        )

        assertIs<ConnectCompanionResult.Connected>(connect.execute())

        assertEquals("192.168.1.77", transport.lastRegistration?.controlEndpoint?.host)
        assertEquals("192.168.1.77", transport.lastRegistration?.proxyEndpoint?.host)
        assertEquals("192.168.1.77", repository.activeRegistration.value?.controlEndpoint?.host)
        assertEquals(1, discovery.startCalls)
    }

    private fun recovery(
        repository: FakeRegistrationRepository,
        discovery: FakeDiscovery,
        timeoutMillis: Long = 2_000L,
        reconcile: suspend (CompanionRegistration, CompanionServiceEndpoint, String) -> CompanionEndpointReconciliationResult,
    ): RecoverCompanionEndpointUseCase = RecoverCompanionEndpointUseCase(
        repository,
        FakeCredentialStore(),
        discovery,
        CompanionEndpointReconciliationClient(reconcile),
        discoveryTimeoutMillis = timeoutMillis,
    )

    private class FakeDiscovery(
        private val initialCandidate: CompanionDiscoveryCandidate? = null,
        private val afterStart: (FakeDiscovery.() -> Unit)? = null,
    ) : CompanionDesktopDiscovery {
        private val mutableState = MutableStateFlow<CompanionDiscoveryState>(CompanionDiscoveryState.Idle)
        override val state: StateFlow<CompanionDiscoveryState> = mutableState
        var startCalls: Int = 0

        override fun start(targetDesktopIds: Set<CompanionDesktopId>) {
            startCalls += 1
            mutableState.value = initialCandidate?.let { candidate ->
                CompanionDiscoveryState.Candidates(targetDesktopIds.first(), listOf(candidate))
            } ?: CompanionDiscoveryState.Searching(targetDesktopIds.first())
            afterStart?.invoke(this)
        }

        override fun stop() {
            mutableState.value = CompanionDiscoveryState.Idle
        }

        fun publish(state: CompanionDiscoveryState) {
            mutableState.value = state
        }
    }

    private class FakeRegistrationRepository(active: CompanionRegistration) : CompanionRegistrationRepository {
        private val mutableRegistrations = MutableStateFlow(listOf(active))
        private val mutableActive = MutableStateFlow<CompanionRegistration?>(active)
        override val registrations: StateFlow<List<CompanionRegistration>> = mutableRegistrations
        override val activeRegistration: StateFlow<CompanionRegistration?> = mutableActive
        var migrationCount: Int = 0

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

        override suspend fun migrateIdentity(
            previousDesktopId: CompanionDesktopId,
            registration: CompanionRegistration,
            makeActive: Boolean,
        ): Boolean {
            migrationCount += 1
            mutableRegistrations.value = mutableRegistrations.value
                .filterNot { it.desktopId == previousDesktopId || it.desktopId == registration.desktopId } + registration
            if (makeActive || mutableActive.value?.desktopId == previousDesktopId) mutableActive.value = registration
            return true
        }
    }

    private class FakeCredentialStore : CompanionCredentialStore {
        override suspend fun write(reference: CompanionCredentialReference, credential: String) = Unit
        override suspend fun read(reference: CompanionCredentialReference): String = "credential"
        override suspend fun remove(reference: CompanionCredentialReference) = Unit
    }

    private class FakeTransport(registration: CompanionRegistration) : CompanionTransport {
        private val mutableState = MutableStateFlow<CompanionConnectionState>(
            CompanionConnectionState.Connected(registration.desktopId, CompanionTransportKind.DIRECT_LAN, 1_000L),
        )
        override val state: StateFlow<CompanionConnectionState> = mutableState
        var connectCalls: Int = 0
        var lastRegistration: CompanionRegistration? = null

        override suspend fun connect(
            registration: CompanionRegistration,
            credential: String,
        ): CompanionTransportResult {
            connectCalls += 1
            lastRegistration = registration
            mutableState.value = CompanionConnectionState.Connected(
                registration.desktopId,
                CompanionTransportKind.DIRECT_LAN,
                2_000L,
            )
            return CompanionTransportResult.Connected
        }

        override suspend fun disconnect() {
            mutableState.value = CompanionConnectionState.Disconnected
        }
    }

    private companion object {
        val LEGACY_ID: CompanionDesktopId = CompanionDesktopId("knet-${"b".repeat(64)}")
        val CANONICAL_ID: CompanionDesktopId = CompanionDesktopId("11111111-1111-4111-8111-111111111111")
        val RUNTIME_ID: CompanionDesktopRuntimeId =
            CompanionDesktopRuntimeId.parse("22222222-2222-4222-8222-222222222222")
        const val CONTROL_PORT: Int = 8183
        const val PROXY_PORT: Int = 8182

        fun candidate(
            host: String = "192.168.1.44",
            desktopId: CompanionDesktopId = CANONICAL_ID,
            legacyIds: Set<CompanionDesktopId> = setOf(LEGACY_ID),
            runtimeId: CompanionDesktopRuntimeId = RUNTIME_ID,
            instanceName: String = "KNet Desktop",
        ): CompanionDiscoveryCandidate = CompanionDiscoveryCandidate(
            instanceName = instanceName,
            advertisement = CompanionDiscoveryAdvertisement(
                protocolVersion = CompanionDiscoveryProtocol.VERSION,
                desktopId = desktopId,
                legacyDesktopIds = legacyIds,
                runtimeId = runtimeId,
            ),
            endpoints = listOf(CompanionServiceEndpoint(host, CONTROL_PORT, scheme = CompanionEndpointScheme.HTTPS)),
        )

        fun descriptor(
            runtimeId: CompanionDesktopRuntimeId = RUNTIME_ID,
            legacyIds: Set<CompanionDesktopId> = setOf(LEGACY_ID),
        ): CompanionEndpointDescriptor = CompanionEndpointDescriptor(
            protocolVersion = CompanionDiscoveryProtocol.VERSION,
            desktopId = CANONICAL_ID,
            acceptedLegacyIds = legacyIds,
            runtimeId = runtimeId,
            controlPort = CONTROL_PORT,
            proxyPort = PROXY_PORT,
        )

        fun registration(desktopId: CompanionDesktopId = LEGACY_ID): CompanionRegistration = CompanionRegistration(
            desktopId = desktopId,
            desktopDisplayName = CompanionDesktopDisplayName("Development Mac"),
            deviceId = RegisteredDeviceId("device-1"),
            controlEndpoint = CompanionServiceEndpoint("192.168.1.2", CONTROL_PORT, scheme = CompanionEndpointScheme.HTTPS),
            proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", PROXY_PORT, scheme = CompanionEndpointScheme.HTTPS),
            transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
            rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
            rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
            credentialReference = CompanionCredentialReference("credential-reference"),
            scopes = setOf(DeviceScope.PROXY_STREAM),
            pairedAtEpochMillis = 1_000L,
            credentialExpiresAtEpochMillis = 10_000L,
        )

        fun identityMismatch(): CompanionFailure = CompanionFailure(
            CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH,
            "identity mismatch",
            false,
        )

        fun desktopUnavailable(): CompanionFailure = CompanionFailure(
            CompanionFailureCode.TRANSPORT_UNAVAILABLE,
            "Unavailable",
            true,
        )
    }
}
