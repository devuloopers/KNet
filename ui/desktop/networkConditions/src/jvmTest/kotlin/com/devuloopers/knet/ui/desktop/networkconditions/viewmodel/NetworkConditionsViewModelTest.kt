package com.devuloopers.knet.ui.desktop.networkconditions.viewmodel

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeTelemetry
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionsRepository
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.CompiledNetworkConditionProtocolCriteria
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionInterceptionUnit
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolDefinition
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolExtension
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolObservation
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.TrafficGeneration
import com.devuloopers.knet.application.contract.traffic.TrafficPage
import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.application.usecase.networkconditions.PrepareNetworkConditionRuleUseCase
import com.devuloopers.knet.application.usecase.networkconditions.NetworkConditionProtocolRuleUseCase
import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolCriteria
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionsTab
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.networkconditions.view.NetworkConditionsRuntimeMonitoring
import com.devuloopers.knet.ui.desktop.networkconditions.view.NetworkConditionsScreen
import com.devuloopers.knet.ui.desktop.networkconditions.view.NetworkThroughputIndicator
import java.awt.EventQueue
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
    fun `traffic quick add defaults a new canonical domain rule to no throttling`() = runTest(dispatcher) {
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
        assertEquals(NetworkConditionBuiltIns.NO_THROTTLING.id, draft.profileId)

        viewModel.selectDraftProfile(NetworkConditionBuiltIns.SLOW_3G.id)
        viewModel.confirmRuleDraft()
        advanceUntilIdle()

        assertNull(viewModel.state.value.ruleDraft)
        assertEquals(NetworkConditionBuiltIns.SLOW_3G.id, repository.configuration.value.lastQuickAddProfileId)
        assertEquals("video.example.test:443", repository.configuration.value.rules.single().target.displayValue)
        assertEquals(NetworkConditionBuiltIns.SLOW_3G.id, repository.configuration.value.rules.single().profileId)
    }

    @Test
    fun `manual add defaults to no throttling even when a prior profile is remembered`() = runTest(dispatcher) {
        val repository = FakeRepository(
            initial = NetworkConditionConfiguration(
                lastQuickAddProfileId = NetworkConditionBuiltIns.FAST_3G.id,
            ),
        )
        val viewModel = NetworkConditionsViewModel(
            repository = repository,
            runtimeTelemetry = FakeTelemetry(),
            prepareRule = PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange()), repository),
        )
        advanceUntilIdle()

        viewModel.beginAddRule()

        assertEquals(
            NetworkConditionBuiltIns.NO_THROTTLING.id,
            assertNotNull(viewModel.state.value.ruleDraft).profileId,
        )
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
    fun `off 4g and 100 kbps switches retain one graph tick per second despite chunk telemetry`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val telemetry = FakeTelemetry()
        val viewModel = NetworkConditionsViewModel(repository, telemetry,
            PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange()), repository))
        viewModel.startRuntimeMonitoring()
        advanceUntilIdle()
        telemetry.mutable.value = NetworkConditionRuntimeSnapshot(sampledAtNanos = 1_000_000_000L)
        advanceUntilIdle()
        val profiles = listOf(null, NetworkConditionBuiltIns.MOBILE_4G.id, NetworkConditionBuiltIns.STREAMING_100_KBPS.id, null)
        profiles.forEachIndexed { second, profileId ->
            if (profileId == null) viewModel.setEnabled(false) else viewModel.activateGlobalProfile(profileId)
            advanceUntilIdle()
            assertEquals(second, viewModel.state.value.throughput.sampleCount)
            repeat(100) { index ->
                val chunk = second * 100L + index + 1L
                telemetry.mutable.value = NetworkConditionRuntimeSnapshot(
                    sampledAtNanos = 1_000_000_000L + chunk * 10_000_000L,
                    downloadedBytes = chunk * 125L, uploadedBytes = chunk * 25L, queuedBytes = chunk,
                )
                advanceUntilIdle()
                assertEquals(chunk, viewModel.state.value.runtime.queuedBytes)
                assertEquals(if (index == 99) second + 1 else second, viewModel.state.value.throughput.sampleCount)
            }
            assertEquals(100_000L, viewModel.state.value.throughput.currentDownloadBitsPerSecond)
            assertEquals(20_000L, viewModel.state.value.throughput.currentUploadBitsPerSecond)
        }
        viewModel.stopRuntimeMonitoring()
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

    @Test
    fun `semantic rule editor persists protocol criteria and priority`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val protocolId = NetworkConditionProtocolId("test-semantic")
        val fieldId = ProtocolCriteriaFieldId("operation")
        val extension = EditorExtension(protocolId, fieldId)
        val viewModel = NetworkConditionsViewModel(
            repository = repository,
            runtimeTelemetry = FakeTelemetry(),
            prepareRule = PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange()), repository),
            protocolRules = NetworkConditionProtocolRuleUseCase(
                NetworkConditionProtocolRegistry(listOf(extension)),
            ),
        )
        advanceUntilIdle()

        viewModel.beginAddRule()
        viewModel.updateDraftHostPattern("api.example.test")
        viewModel.updateDraftPort("443")
        viewModel.updateDraftPriority("25")
        viewModel.selectDraftProtocol(protocolId)
        viewModel.updateDraftProtocolValue(fieldId, "LoadFeed")
        viewModel.confirmRuleDraft()
        advanceUntilIdle()

        val rule = repository.configuration.value.rules.single()
        assertEquals(25, rule.priority)
        assertEquals(protocolId, rule.protocolCriteria.protocolId)
        assertEquals("LoadFeed", rule.protocolCriteria.encodedPayload)
        assertEquals("Operation: LoadFeed", viewModel.state.value.ruleDetails.getValue(rule.id).criteria)

        viewModel.selectTab(NetworkConditionsTab.PROFILES)
        viewModel.beginEditRule(rule)
        assertEquals(NetworkConditionsTab.RULES, viewModel.state.value.activeTab)
        viewModel.updateDraftProtocolValue(fieldId, "LoadNext")
        viewModel.confirmRuleDraft()
        advanceUntilIdle()
        assertEquals("Operation: LoadNext", viewModel.state.value.ruleDetails.getValue(rule.id).criteria)

        viewModel.deleteRule(rule.id)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.ruleDetails.isEmpty())
    }

    @Test
    fun `tab changes leave policy and monitoring intact and traffic quick add returns to rules`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val telemetry = FakeTelemetry()
        val viewModel = NetworkConditionsViewModel(repository, telemetry,
            PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange()), repository))
        advanceUntilIdle()
        val originalConfiguration = repository.configuration.value
        assertEquals(NetworkConditionsTab.OVERVIEW, viewModel.state.value.activeTab)

        viewModel.startRuntimeMonitoring()
        viewModel.selectTab(NetworkConditionsTab.PROFILES)
        advanceUntilIdle()
        assertEquals(originalConfiguration, repository.configuration.value)
        assertEquals(NetworkConditionsTab.PROFILES, viewModel.state.value.activeTab)
        telemetry.mutable.value = NetworkConditionRuntimeSnapshot(sampledAtNanos = 1_000_000_000L)
        advanceUntilIdle()
        telemetry.mutable.value = NetworkConditionRuntimeSnapshot(sampledAtNanos = 2_000_000_000L, downloadedBytes = 12_500L)
        advanceUntilIdle()
        assertEquals(100_000L, viewModel.state.value.throughput.currentDownloadBitsPerSecond)
        assertEquals(NetworkConditionsTab.PROFILES, viewModel.state.value.activeTab)

        viewModel.selectTab(NetworkConditionsTab.RULES)
        telemetry.mutable.value = NetworkConditionRuntimeSnapshot(sampledAtNanos = 3_000_000_000L, downloadedBytes = 25_000L)
        advanceUntilIdle()
        viewModel.selectTab(NetworkConditionsTab.OVERVIEW)
        advanceUntilIdle()
        assertEquals(NetworkConditionsTab.OVERVIEW, viewModel.state.value.activeTab)
        assertEquals(2, viewModel.state.value.throughput.sampleCount)
        assertEquals(100_000L, viewModel.state.value.throughput.currentDownloadBitsPerSecond)
        assertEquals(originalConfiguration, repository.configuration.value)

        viewModel.prepareFromTraffic(exchange().id.value)
        advanceUntilIdle()
        assertEquals(NetworkConditionsTab.RULES, viewModel.state.value.activeTab)
        assertEquals(NetworkConditionBuiltIns.NO_THROTTLING.id, assertNotNull(viewModel.state.value.ruleDraft).profileId)
        viewModel.stopRuntimeMonitoring()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `shared host keeps one sampler across traffic and overview and stops on departure or disposal`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val telemetry = FakeTelemetry()
        val viewModel = NetworkConditionsViewModel(repository, telemetry,
            PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange()), repository))
        val active = mutableStateOf(true)
        val traffic = mutableStateOf(true)
        lateinit var scene: ImageComposeScene
        var frame = 0L
        fun render() = EventQueue.invokeAndWait {
            repeat(2) { frame += 1_000_000_000L; scene.render(frame).close() }
        }
        EventQueue.invokeAndWait {
            scene = ImageComposeScene(1280, 800, content = {
                KNetTheme {
                    NetworkConditionsRuntimeMonitoring(viewModel, active.value)
                    if (active.value) {
                        if (traffic.value) NetworkThroughputIndicator(viewModel.state.collectAsState().value.throughput, {})
                        else NetworkConditionsScreen(viewModel)
                    }
                }
            })
        }
        try {
            render(); advanceUntilIdle()
            assertEquals(1, telemetry.mutable.subscriptionCount.value)
            telemetry.mutable.value = NetworkConditionRuntimeSnapshot(sampledAtNanos = 1_000_000_000L)
            advanceUntilIdle()
            telemetry.mutable.value = NetworkConditionRuntimeSnapshot(sampledAtNanos = 2_000_000_000L,
                downloadedBytes = 16_000L, uploadedBytes = 3_000L)
            advanceUntilIdle()
            assertEquals(128_000L, viewModel.state.value.throughput.currentDownloadBitsPerSecond)
            assertEquals(24_000L, viewModel.state.value.throughput.currentUploadBitsPerSecond)
            assertEquals(false, repository.configuration.value.enabled)

            traffic.value = false; render(); advanceUntilIdle()
            assertEquals(1, telemetry.mutable.subscriptionCount.value)
            assertEquals(1, viewModel.state.value.throughput.sampleCount)
            telemetry.mutable.value = NetworkConditionRuntimeSnapshot(sampledAtNanos = 3_000_000_000L,
                downloadedBytes = 32_000L, uploadedBytes = 6_000L)
            advanceUntilIdle()
            traffic.value = true; render(); advanceUntilIdle()
            assertEquals(1, telemetry.mutable.subscriptionCount.value)
            assertEquals(2, viewModel.state.value.throughput.sampleCount)

            active.value = false; render(); advanceUntilIdle()
            assertEquals(0, telemetry.mutable.subscriptionCount.value)
            telemetry.mutable.value = NetworkConditionRuntimeSnapshot(sampledAtNanos = 4_000_000_000L,
                downloadedBytes = 1_000_000L)
            advanceUntilIdle()
            active.value = true; render(); advanceUntilIdle()
            assertEquals(1, telemetry.mutable.subscriptionCount.value)
            assertEquals(0, viewModel.state.value.throughput.sampleCount)
            assertEquals(false, viewModel.state.value.throughput.currentSampleIsValid)
        } finally {
            EventQueue.invokeAndWait { scene.close() }
            advanceUntilIdle()
        }
        assertEquals(0, telemetry.mutable.subscriptionCount.value)
    }

    @Test
    fun `manual add selects rules and configuration updates preserve the selected tab`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = NetworkConditionsViewModel(repository, FakeTelemetry(),
            PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange()), repository))
        advanceUntilIdle()
        viewModel.selectTab(NetworkConditionsTab.PROFILES)
        viewModel.selectGlobalProfile(NetworkConditionBuiltIns.STREAMING_100_KBPS.id)
        advanceUntilIdle()
        assertEquals(NetworkConditionsTab.PROFILES, viewModel.state.value.activeTab)
        viewModel.beginAddRule()
        assertEquals(NetworkConditionsTab.RULES, viewModel.state.value.activeTab)
        assertEquals(NetworkConditionBuiltIns.NO_THROTTLING.id, assertNotNull(viewModel.state.value.ruleDraft).profileId)
    }

    @Test
    fun `quick profile activation synchronizes state and off preserves the selected global profile`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = NetworkConditionsViewModel(repository, FakeTelemetry(),
            PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange()), repository))
        advanceUntilIdle()
        viewModel.activateGlobalProfile(NetworkConditionBuiltIns.STREAMING_100_KBPS.id)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.configuration.enabled)
        assertEquals(NetworkConditionBuiltIns.STREAMING_100_KBPS.id, viewModel.state.value.configuration.globalProfileId)
        viewModel.setEnabled(false)
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.configuration.enabled)
        assertEquals(NetworkConditionBuiltIns.STREAMING_100_KBPS.id, viewModel.state.value.configuration.globalProfileId)
        repository.setGlobalProfile(NetworkConditionBuiltIns.FAST_3G.id)
        advanceUntilIdle()
        assertEquals(NetworkConditionBuiltIns.FAST_3G.id, viewModel.state.value.configuration.globalProfileId)
        viewModel.activateGlobalProfile(null)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.configuration.enabled)
        assertNull(viewModel.state.value.configuration.globalProfileId)
    }

    @Test
    fun `stale quick profile selection reports an error and preserves the existing configuration`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = NetworkConditionsViewModel(repository, FakeTelemetry(),
            PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange()), repository))
        advanceUntilIdle()
        val before = viewModel.state.value.configuration
        viewModel.activateGlobalProfile(NetworkConditionProfileId("removed-profile"))
        advanceUntilIdle()
        assertEquals(before, viewModel.state.value.configuration)
        assertEquals("Unknown global profile.", viewModel.state.value.errorMessage)
        viewModel.clearError()
        assertNull(viewModel.state.value.errorMessage)
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

        override suspend fun activateGlobalProfile(profileId: NetworkConditionProfileId?) {
            require(profileId == null || configuration.value.profile(profileId) != null) { "Unknown global profile." }
            configuration.value = configuration.value.copy(enabled = true, globalProfileId = profileId)
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

    private class EditorExtension(
        private val id: NetworkConditionProtocolId,
        private val fieldId: ProtocolCriteriaFieldId,
    ) : NetworkConditionProtocolExtension {
        override val definition = NetworkConditionProtocolDefinition(
            protocolId = id,
            displayName = "Test semantic",
            criteriaVersion = 1,
            interceptionUnit = NetworkConditionInterceptionUnit.HTTP_EXCHANGE,
            fields = listOf(ProtocolCriteriaFieldDefinition.Text(
                id = fieldId,
                label = "Operation",
                description = "Operation name",
                placeholder = "LoadFeed",
            )),
        )

        override fun compile(
            criteria: NetworkConditionProtocolCriteria,
        ): CompiledNetworkConditionProtocolCriteria? = criteria
            .takeIf { value -> value.protocolId == id && value.encodedPayload.isNotBlank() }
            ?.let {
                object : CompiledNetworkConditionProtocolCriteria {
                    override val protocolId: NetworkConditionProtocolId = id
                    override fun matches(observation: NetworkConditionProtocolObservation?): Boolean = true
                }
            }

        override fun editorValues(criteria: NetworkConditionProtocolCriteria): List<ProtocolCriteriaValue> =
            listOf(ProtocolCriteriaValue(fieldId, criteria.encodedPayload))

        override fun createCriteria(values: List<ProtocolCriteriaValue>): NetworkConditionProtocolCriteria? =
            values.singleOrNull { value -> value.fieldId == fieldId }
                ?.value
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { value -> NetworkConditionProtocolCriteria(id, value) }
    }
}
