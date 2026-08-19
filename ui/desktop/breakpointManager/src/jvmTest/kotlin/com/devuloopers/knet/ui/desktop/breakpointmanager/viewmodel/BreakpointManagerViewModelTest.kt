package com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel

import com.devuloopers.knet.application.port.breakpoint.BreakpointBody
import com.devuloopers.knet.application.port.breakpoint.BreakpointCandidate
import com.devuloopers.knet.application.port.breakpoint.BreakpointControlPort
import com.devuloopers.knet.application.port.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.port.breakpoint.BreakpointRequestEdit
import com.devuloopers.knet.application.port.breakpoint.BreakpointResponseEdit
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint
import com.devuloopers.knet.application.usecase.breakpoint.ClearPendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ObservePendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ResolveBreakpointUseCase
import com.devuloopers.knet.application.usecase.breakpoint.BreakpointProtocolRuleUseCase
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.domain.rules.usecase.DeleteRuleUseCase
import com.devuloopers.knet.domain.rules.usecase.GetRulesUseCase
import com.devuloopers.knet.domain.rules.usecase.ObserveGlobalInterceptionUseCase
import com.devuloopers.knet.domain.rules.usecase.SaveRuleUseCase
import com.devuloopers.knet.domain.rules.usecase.ToggleGlobalInterceptionUseCase
import com.devuloopers.knet.domain.rules.usecase.ToggleRuleUseCase
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeRulesRepository : RulesRepository {
    private val rules = MutableStateFlow<List<BreakpointRule>>(emptyList())
    private val enabled = MutableStateFlow(true)
    override val rulesFlow: Flow<List<BreakpointRule>> = rules.asStateFlow()
    override val isGlobalInterceptionEnabled: Flow<Boolean> = enabled.asStateFlow()

    override suspend fun toggleGlobalInterception(enabled: Boolean) {
        this.enabled.value = enabled
    }

    override suspend fun toggleRule(ruleId: String, enabled: Boolean) {
        rules.value = rules.value.map { if (it.id == ruleId) it.copy(enabled = enabled) else it }
    }

    override suspend fun saveRule(rule: BreakpointRule) {
        rules.value = rules.value.filterNot { it.id == rule.id } + rule
    }

    override suspend fun deleteRule(ruleId: String) {
        rules.value = rules.value.filterNot { it.id == ruleId }
    }
}

private class FakeBreakpointControl : BreakpointControlPort {
    private val pending = MutableStateFlow<List<PendingBreakpoint>>(emptyList())
    override val pendingBreakpoints = pending.asStateFlow()
    override val isEnabled = MutableStateFlow(true)
    var lastResolution: Pair<String, BreakpointDecision>? = null

    fun emit(items: List<PendingBreakpoint>) {
        pending.value = items
    }

    override fun replaceRules(rules: List<BreakpointRule>) = Unit
    override fun setEnabled(enabled: Boolean) {
        isEnabled.value = enabled
    }
    override fun setDecisionTimeoutMillis(timeoutMillis: Long) = Unit

    override suspend fun resolve(pendingId: String, decision: BreakpointDecision): Boolean {
        lastResolution = pendingId to decision
        pending.value = pending.value.filterNot { it.id == pendingId }
        return true
    }

    override suspend fun dropMatching(url: String, method: String): Int = 0

    override suspend fun clear(): Int = pending.value.size.also { pending.value = emptyList() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BreakpointManagerViewModelTest {
    private val rules = FakeRulesRepository()
    private val breakpoints = FakeBreakpointControl()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): BreakpointManagerViewModel = BreakpointManagerViewModel(
        getRulesUseCase = GetRulesUseCase(rules),
        observeGlobalInterceptionUseCase = ObserveGlobalInterceptionUseCase(rules),
        observePendingBreakpointsUseCase = ObservePendingBreakpointsUseCase(breakpoints),
        saveRuleUseCase = SaveRuleUseCase(rules),
        toggleRuleUseCase = ToggleRuleUseCase(rules),
        deleteRuleUseCase = DeleteRuleUseCase(rules),
        toggleGlobalInterceptionUseCase = ToggleGlobalInterceptionUseCase(rules),
        resolveBreakpointUseCase = ResolveBreakpointUseCase(breakpoints),
        clearPendingBreakpointsUseCase = ClearPendingBreakpointsUseCase(breakpoints),
        breakpointProtocolRuleUseCase = BreakpointProtocolRuleUseCase(BreakpointProtocolRegistry()),
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `rules and global state remain reactive`() = runTest {
        val viewModel = viewModel()
        viewModel.saveRule(".*stripe.*", HttpMethod.POST, BreakpointPhase.REQUEST, true)
        assertEquals(1, viewModel.uiState.value.rules.size)

        val ruleId = viewModel.uiState.value.rules.single().id
        viewModel.toggleRuleStatus(ruleId)
        assertFalse(viewModel.uiState.value.rules.single().enabled)

        viewModel.toggleGlobalInterception(false)
        assertFalse(viewModel.uiState.value.isGlobalInterceptionEnabled)
    }

    @Test
    fun `pending candidates remain canonical and payloads are prepared once`() = runTest {
        val viewModel = viewModel()
        val event = pending("event-1", BreakpointPhase.REQUEST, "request-body")

        breakpoints.emit(listOf(event))

        assertEquals(event, viewModel.uiState.value.activeEvent)
        assertEquals("request-body", viewModel.uiState.value.resolvedPayloads.getValue("event-1")
            .requestPayloadSpec.rawBody)
    }

    @Test
    fun `request and response edits resolve through the application boundary`() = runTest {
        val viewModel = viewModel()
        val requestEvent = pending("request-event", BreakpointPhase.REQUEST)
        breakpoints.emit(listOf(requestEvent))
        val requestEdit = BreakpointRequestEdit(requestEvent.candidate.request, null)

        viewModel.forwardRequest(requestEvent.id, requestEdit)

        assertEquals(requestEvent.id, breakpoints.lastResolution?.first)
        assertIs<BreakpointDecision.Resume>(breakpoints.lastResolution?.second)

        val responseEvent = pending("response-event", BreakpointPhase.RESPONSE)
        breakpoints.emit(listOf(responseEvent))
        val responseEdit = BreakpointResponseEdit(requireNotNull(responseEvent.candidate.response), null)

        viewModel.forwardResponse(responseEvent.id, responseEdit)

        assertEquals(responseEvent.id, breakpoints.lastResolution?.first)
        assertIs<BreakpointDecision.Resume>(breakpoints.lastResolution?.second)
    }

    @Test
    fun `selection drop and clear operate on pending ids`() = runTest {
        val viewModel = viewModel()
        val first = pending("first", BreakpointPhase.REQUEST)
        val second = pending("second", BreakpointPhase.REQUEST)
        breakpoints.emit(listOf(first, second))

        viewModel.selectActiveEvent(second.id)
        assertEquals(second.id, viewModel.uiState.value.activeEvent?.id)

        viewModel.dropEvent(second.id)
        assertIs<BreakpointDecision.Drop>(breakpoints.lastResolution?.second)
        assertEquals(first.id, viewModel.uiState.value.activeEvent?.id)

        viewModel.dropAllEvents()
        assertTrue(viewModel.uiState.value.activeEvents.isEmpty())
        assertNull(viewModel.uiState.value.activeEvent)
    }

    private fun pending(
        id: String,
        phase: BreakpointPhase,
        requestBody: String? = null,
    ): PendingBreakpoint {
        val request = HttpRequestSnapshot(
            RequestHead(
                method = HttpMethod.POST,
                target = RequestTarget.Absolute(
                    HttpScheme.fromToken("https"),
                    Authority("api.example.test"),
                    "/graphql",
                ),
                protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                headers = listOf(HeaderField(HeaderName("Content-Type"), "application/json")),
            )
        )
        val response = if (phase == BreakpointPhase.RESPONSE) {
            HttpResponseSnapshot(
                ResponseHead(
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    status = HttpStatus(200),
                    reasonPhrase = "OK",
                    headers = emptyList(),
                )
            )
        } else {
            null
        }
        return PendingBreakpoint(
            id = id,
            ruleId = "rule-$id",
            candidate = BreakpointCandidate(
                exchangeId = ExchangeId("exchange-$id"),
                phase = phase,
                request = request,
                requestBody = requestBody?.encodeToByteArray()?.let(::BreakpointBody),
                response = response,
                startedAtEpochMillis = 1_000L,
            ),
        )
    }
}
