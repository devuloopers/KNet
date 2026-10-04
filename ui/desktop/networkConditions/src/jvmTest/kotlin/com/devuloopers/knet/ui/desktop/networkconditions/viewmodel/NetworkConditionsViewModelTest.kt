package com.devuloopers.knet.ui.desktop.networkconditions.viewmodel

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeTelemetry
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionsRepository
import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.TrafficGeneration
import com.devuloopers.knet.application.contract.traffic.TrafficPage
import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.application.usecase.networkconditions.PrepareNetworkConditionRuleUseCase
import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkConditionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `traffic quick add prepares confirms and remembers a canonical domain rule`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val exchange = exchange()
        val viewModel = NetworkConditionsViewModel(
            repository = repository,
            runtimeTelemetry = FakeTelemetry(),
            prepareRule = PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange), repository),
        )
        advanceUntilIdle()

        viewModel.prepareFromTraffic(exchange.id.value)
        advanceUntilIdle()

        val draft = assertNotNull(viewModel.state.value.ruleDraft)
        assertEquals("video.example.test", draft.hostPattern)
        assertEquals("443", draft.port)
        assertEquals(NetworkConditionBuiltIns.STREAMING_100_KBPS.id, draft.profileId)

        viewModel.selectDraftProfile(NetworkConditionBuiltIns.SLOW_3G.id)
        viewModel.confirmRuleDraft()
        advanceUntilIdle()

        assertNull(viewModel.state.value.ruleDraft)
        assertEquals(NetworkConditionBuiltIns.SLOW_3G.id, repository.configuration.value.lastQuickAddProfileId)
        assertEquals("video.example.test:443", repository.configuration.value.rules.single().target.displayValue)
        assertEquals(NetworkConditionBuiltIns.SLOW_3G.id, repository.configuration.value.rules.single().profileId)
    }

    @Test
    fun `manual wildcard rule can be created and edited without losing disabled state`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = NetworkConditionsViewModel(
            repository = repository,
            runtimeTelemetry = FakeTelemetry(),
            prepareRule = PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange()), repository),
        )
        advanceUntilIdle()

        viewModel.beginAddRule()
        viewModel.updateDraftHostPattern("*.Example.Test.")
        viewModel.updateDraftPort("443")
        viewModel.updateDraftEnabled(false)
        viewModel.confirmRuleDraft()
        advanceUntilIdle()

        val created = repository.configuration.value.rules.single()
        assertEquals("*.example.test:443", created.target.displayValue)
        assertEquals(false, created.enabled)

        viewModel.beginEditRule(created)
        viewModel.updateDraftPort("")
        viewModel.confirmRuleDraft()
        advanceUntilIdle()

        val edited = repository.configuration.value.rules.single()
        assertEquals(created.id, edited.id)
        assertEquals("*.example.test", edited.target.displayValue)
        assertEquals(false, edited.enabled)
    }

    @Test
    fun `runtime telemetry and repository failures are presented without payload data`() = runTest(dispatcher) {
        val repository = FakeRepository(deleteProfiles = false)
        val telemetry = FakeTelemetry()
        val viewModel = NetworkConditionsViewModel(
            repository = repository,
            runtimeTelemetry = telemetry,
            prepareRule = PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange()), repository),
        )
        viewModel.startRuntimeMonitoring()
        advanceUntilIdle()
        telemetry.mutable.value = NetworkConditionRuntimeSnapshot(
            sampledAtNanos = 1_000_000_000L,
            activeFlows = 2,
            queuedBytes = 4_096,
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.runtime.activeFlows)
        assertEquals(4_096L, viewModel.state.value.runtime.queuedBytes)

        viewModel.deleteProfile(NetworkConditionProfileId("custom-profile"))
        advanceUntilIdle()
        assertEquals(
            "Profile is still used by a global setting or domain rule.",
            viewModel.state.value.errorMessage,
        )
    }

    @Test
    fun `runtime monitoring derives actual bounded rates and stops with the screen`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val telemetry = FakeTelemetry()
        val viewModel = NetworkConditionsViewModel(
            repository = repository,
            runtimeTelemetry = telemetry,
            prepareRule = PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange()), repository),
        )
        viewModel.startRuntimeMonitoring()
        advanceUntilIdle()

        telemetry.mutable.value = NetworkConditionRuntimeSnapshot(
            sampledAtNanos = 1_000_000_000L,
            downloadedBytes = 1_000L,
        )
        advanceUntilIdle()
        telemetry.mutable.value = NetworkConditionRuntimeSnapshot(
            sampledAtNanos = 2_000_000_000L,
            downloadedBytes = 13_500L,
            uploadedBytes = 6_250L,
        )
        advanceUntilIdle()

        assertEquals(100_000L, viewModel.state.value.throughput.currentDownloadBitsPerSecond)
        assertEquals(50_000L, viewModel.state.value.throughput.currentUploadBitsPerSecond)

        viewModel.stopRuntimeMonitoring()
        telemetry.mutable.value = NetworkConditionRuntimeSnapshot(
            sampledAtNanos = 3_000_000_000L,
            downloadedBytes = 100_000L,
            uploadedBytes = 100_000L,
        )
        advanceUntilIdle()

        assertEquals(100_000L, viewModel.state.value.throughput.currentDownloadBitsPerSecond)
        assertEquals(50_000L, viewModel.state.value.throughput.currentUploadBitsPerSecond)
    }

    @Test
    fun `master global profile and reset actions preserve saved profiles and rules`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = NetworkConditionsViewModel(
            repository = repository,
            runtimeTelemetry = FakeTelemetry(),
            prepareRule = PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange()), repository),
        )
        advanceUntilIdle()
        val custom = NetworkConditionProfile(
            id = NetworkConditionProfileId("custom-stream"),
            name = "Custom stream",
        )
        val rule = NetworkConditionRule(
            NetworkConditionRuleId("saved-rule"),
            com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget.parse("video.example"),
            custom.id,
        )

        viewModel.saveProfile(custom)
        advanceUntilIdle()
        repository.upsertRule(rule)
        viewModel.selectGlobalProfile(custom.id)
        viewModel.setEnabled(true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.configuration.enabled)
        assertEquals(custom.id, viewModel.state.value.configuration.globalProfileId)

        viewModel.reset()
        advanceUntilIdle()
        val reset = viewModel.state.value.configuration
        assertEquals(false, reset.enabled)
        assertNull(reset.globalProfileId)
        assertEquals(custom, reset.customProfiles.single())
        assertEquals(rule, reset.rules.single())
    }

    @Test
    fun `rule toggle delete invalid port and missing traffic errors are recoverable`() = runTest(dispatcher) {
        val rule = NetworkConditionRule(
            NetworkConditionRuleId("rule"),
            com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget.parse("video.example"),
            NetworkConditionBuiltIns.SLOW_3G.id,
        )
        val repository = FakeRepository(NetworkConditionConfiguration(rules = listOf(rule)))
        val viewModel = NetworkConditionsViewModel(
            repository = repository,
            runtimeTelemetry = FakeTelemetry(),
            prepareRule = PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange()), repository),
        )
        advanceUntilIdle()

        viewModel.setRuleEnabled(rule, false)
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.configuration.rules.single().enabled)
        viewModel.deleteRule(rule.id)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.configuration.rules.isEmpty())

        viewModel.beginAddRule()
        viewModel.updateDraftHostPattern("video.example")
        viewModel.updateDraftPort("70000")
        viewModel.confirmRuleDraft()
        advanceUntilIdle()
        assertEquals("Network condition port is invalid.", viewModel.state.value.errorMessage)
        assertNotNull(viewModel.state.value.ruleDraft)
        viewModel.clearError()
        assertNull(viewModel.state.value.errorMessage)
        viewModel.dismissRuleDraft()

        viewModel.prepareFromTraffic("missing")
        advanceUntilIdle()
        assertEquals("The selected traffic row no longer exists.", viewModel.state.value.errorMessage)
        assertNull(viewModel.state.value.ruleDraft)
    }

    @Test
    fun `returning to the screen starts a fresh throughput window`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val telemetry = FakeTelemetry()
        val viewModel = NetworkConditionsViewModel(
            repository = repository,
            runtimeTelemetry = telemetry,
            prepareRule = PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange()), repository),
        )
        viewModel.startRuntimeMonitoring()
        advanceUntilIdle()
        telemetry.mutable.value = NetworkConditionRuntimeSnapshot(sampledAtNanos = 1_000_000_000L)
        advanceUntilIdle()
        telemetry.mutable.value = NetworkConditionRuntimeSnapshot(
            sampledAtNanos = 2_000_000_000L,
            downloadedBytes = 12_500L,
        )
        advanceUntilIdle()
        assertEquals(100_000L, viewModel.state.value.throughput.currentDownloadBitsPerSecond)

        viewModel.stopRuntimeMonitoring()
        viewModel.startRuntimeMonitoring()
        assertEquals(0L, viewModel.state.value.throughput.currentDownloadBitsPerSecond)
        assertEquals(false, viewModel.state.value.throughput.currentSampleIsValid)
    }

    private fun exchange(): HttpExchangeSnapshot = HttpExchangeSnapshot(
        id = ExchangeId("exchange"),
        request = HttpRequestSnapshot(
            RequestHead(
                method = HttpMethod.GET,
                target = RequestTarget.Absolute(
                    HttpScheme.fromToken("https"),
                    Authority("Video.Example.Test.", null),
                    "/manifest.m3u8?token=secret",
                ),
                protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                headers = emptyList(),
            ),
        ),
        state = ExchangeState.COMPLETED,
        startedAtEpochMillis = 1L,
    )

    private class FakeTelemetry : NetworkConditionRuntimeTelemetry {
        val mutable = MutableStateFlow(NetworkConditionRuntimeSnapshot())
        override val snapshot = mutable
    }

    private class FakeTrafficQuery(private val exchange: HttpExchangeSnapshot) : TrafficQuery {
        override val generations: Flow<TrafficGeneration> = emptyFlow()
        override suspend fun query(query: TrafficPageQuery): TrafficPage =
            TrafficPage(items = emptyList(), nextCursor = null, totalCount = 0L, generation = 0L)
        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? =
            exchange.takeIf { it.id == exchangeId }
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk = BodyChunk(byteArrayOf(), 0L, true)
    }

    private class FakeRepository(
        initial: NetworkConditionConfiguration = NetworkConditionConfiguration(),
        private val deleteProfiles: Boolean = true,
    ) : NetworkConditionsRepository {
        override val configuration = MutableStateFlow(initial)

        override suspend fun setEnabled(enabled: Boolean) {
            configuration.value = configuration.value.copy(enabled = enabled)
        }

        override suspend fun setGlobalProfile(profileId: NetworkConditionProfileId?) {
            configuration.value = configuration.value.copy(globalProfileId = profileId)
        }

        override suspend fun upsertProfile(profile: NetworkConditionProfile) {
            configuration.value = configuration.value.copy(
                customProfiles = configuration.value.customProfiles.filterNot { it.id == profile.id } + profile,
            )
        }

        override suspend fun deleteProfile(profileId: NetworkConditionProfileId): Boolean = deleteProfiles

        override suspend fun upsertRule(rule: NetworkConditionRule) {
            configuration.value = configuration.value.copy(
                rules = configuration.value.rules.filterNot { it.id == rule.id } + rule,
            )
        }

        override suspend fun deleteRule(ruleId: NetworkConditionRuleId) {
            configuration.value = configuration.value.copy(
                rules = configuration.value.rules.filterNot { it.id == ruleId },
            )
        }

        override suspend fun setLastQuickAddProfile(profileId: NetworkConditionProfileId?) {
            configuration.value = configuration.value.copy(lastQuickAddProfileId = profileId)
        }

        override suspend fun resetShaping() {
            configuration.value = configuration.value.copy(enabled = false, globalProfileId = null)
        }
    }
}
