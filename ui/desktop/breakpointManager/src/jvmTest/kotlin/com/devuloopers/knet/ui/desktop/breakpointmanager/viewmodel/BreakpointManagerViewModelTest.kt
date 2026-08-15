package com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.InterceptedTransaction
import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.repository.InterceptionSessionRepository
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.domain.rules.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

private class FakeTestRulesRepository : RulesRepository {
    private val _rules = MutableStateFlow<List<RuleModel>>(emptyList())
    override val rulesFlow: Flow<List<RuleModel>> = _rules.asStateFlow()

    private val _isGlobalInterceptionEnabled = MutableStateFlow(true)
    override val isGlobalInterceptionEnabled: Flow<Boolean> = _isGlobalInterceptionEnabled.asStateFlow()

    override suspend fun toggleGlobalInterception(enabled: Boolean) {
        _isGlobalInterceptionEnabled.value = enabled
    }

    override suspend fun saveRule(rule: RuleModel) {
        val current = _rules.value.toMutableList()
        val index = current.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            current[index] = rule
        } else {
            current.add(rule)
        }
        _rules.value = current
    }

    override suspend fun toggleRule(ruleId: String, enabled: Boolean) {
        val current = _rules.value.map {
            if (it.id == ruleId) it.copy(enabled = enabled) else it
        }
        _rules.value = current
    }

    override suspend fun deleteRule(ruleId: String) {
        _rules.value = _rules.value.filterNot { it.id == ruleId }
    }
}

private class FakeTestInterceptionSessionRepository : InterceptionSessionRepository {
    private val _activeInterceptions = MutableStateFlow<List<InterceptedTransaction>>(emptyList())
    override val activeInterceptions: Flow<List<InterceptedTransaction>> = _activeInterceptions.asStateFlow()

    var lastForwardedRequestId: String? = null
    var lastForwardedResponseId: String? = null
    var lastDroppedId: String? = null

    fun emitInterceptions(items: List<InterceptedTransaction>) {
        _activeInterceptions.value = items
    }

    override suspend fun forwardRequest(transactionId: String, modifiedRequest: HttpRequest) {
        lastForwardedRequestId = transactionId
        _activeInterceptions.value = _activeInterceptions.value.filterNot { it.id == transactionId }
    }

    override suspend fun forwardResponse(transactionId: String, modifiedResponse: HttpResponse) {
        lastForwardedResponseId = transactionId
        _activeInterceptions.value = _activeInterceptions.value.filterNot { it.id == transactionId }
    }

    override suspend fun dropTransaction(transactionId: String) {
        lastDroppedId = transactionId
        _activeInterceptions.value = _activeInterceptions.value.filterNot { it.id == transactionId }
    }

    override suspend fun dropMatching(url: String, method: String) {
        _activeInterceptions.value = _activeInterceptions.value.filterNot {
            it.url.equals(url, ignoreCase = true) && it.method.equals(method, ignoreCase = true)
        }
    }

    override suspend fun clearAll() {
        _activeInterceptions.value = emptyList()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BreakpointManagerViewModelTest {

    private val rulesRepository = FakeTestRulesRepository()
    private val sessionRepository = FakeTestInterceptionSessionRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        rulesRepo: RulesRepository = rulesRepository,
        sessionRepo: InterceptionSessionRepository = sessionRepository
    ): BreakpointManagerViewModel {
        return BreakpointManagerViewModel(
            getRulesUseCase = GetRulesUseCase(rulesRepo),
            observeGlobalInterceptionUseCase = ObserveGlobalInterceptionUseCase(rulesRepo),
            observeActiveInterceptionsUseCase = ObserveActiveInterceptionsUseCase(sessionRepo),
            saveRuleUseCase = SaveRuleUseCase(rulesRepo),
            toggleRuleUseCase = ToggleRuleUseCase(rulesRepo),
            deleteRuleUseCase = DeleteRuleUseCase(rulesRepo),
            toggleGlobalInterceptionUseCase = ToggleGlobalInterceptionUseCase(rulesRepo),
            forwardInterceptedRequestUseCase = ForwardInterceptedRequestUseCase(sessionRepo),
            forwardInterceptedResponseUseCase = ForwardInterceptedResponseUseCase(sessionRepo),
            dropInterceptedTransactionUseCase = DropInterceptedTransactionUseCase(sessionRepo),
            clearInterceptionSessionsUseCase = ClearInterceptionSessionsUseCase(sessionRepo),
            ioDispatcher = UnconfinedTestDispatcher()
        )
    }

    @Test
    fun `toggle global interception updates state correctly`() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isGlobalInterceptionEnabled)

        viewModel.toggleGlobalInterception(false)
        assertFalse(viewModel.uiState.value.isGlobalInterceptionEnabled)
    }

    @Test
    fun `saveRule and toggleRuleStatus update domain registry and UI state`() = runTest {
        val viewModel = createViewModel()

        viewModel.saveRule(
            urlPattern = ".*stripe.*",
            method = HttpMethod.POST,
            phase = BreakpointPhase.REQUEST,
            enabled = true
        )

        assertEquals(1, viewModel.uiState.value.rules.size)
        val rule = viewModel.uiState.value.rules.first()
        assertEquals(".*stripe.*", rule.urlPattern)
        assertTrue(rule.enabled)

        viewModel.toggleRuleStatus(rule.id)
        val updated = viewModel.uiState.value.rules.first()
        assertFalse(updated.enabled)
    }

    @Test
    fun `search query filters rules by url pattern`() = runTest {
        val repositoryWithRules = FakeTestRulesRepository()
        repositoryWithRules.saveRule(
            RuleModel(
                id = "r1",
                name = ".*stripe.*",
                type = BreakpointPhase.REQUEST,
                condition = ".*stripe.*",
                action = "POST",
                enabled = true
            )
        )
        repositoryWithRules.saveRule(
            RuleModel(
                id = "r2",
                name = ".*auth/login.*",
                type = BreakpointPhase.BOTH,
                condition = ".*auth/login.*",
                action = "ALL",
                enabled = true
            )
        )

        val viewModel = createViewModel(rulesRepo = repositoryWithRules)

        assertEquals(2, viewModel.uiState.value.rules.size)

        viewModel.updateSearchQuery("stripe")
        assertEquals(1, viewModel.uiState.value.filteredRules.size)
        assertEquals("r1", viewModel.uiState.value.filteredRules.first().id)
    }

    @Test
    fun `deleteRule removes target rule from registry`() = runTest {
        val repositoryWithRule = FakeTestRulesRepository()
        repositoryWithRule.saveRule(
            RuleModel(
                id = "r1",
                name = ".*stripe.*",
                type = BreakpointPhase.REQUEST,
                condition = ".*stripe.*",
                action = "POST",
                enabled = true
            )
        )

        val viewModel = createViewModel(rulesRepo = repositoryWithRule)

        assertEquals(1, viewModel.uiState.value.rules.size)

        viewModel.deleteRule("r1")
        assertEquals(0, viewModel.uiState.value.rules.size)
    }

    @Test
    fun `in-flight intercepted session observation and forward actions execute properly`() = runTest {
        val viewModel = createViewModel()
        assertEquals(0, viewModel.uiState.value.activeEvents.size)

        val fakeRequest = HttpRequest(
            id = "tx-101",
            method = "GET",
            url = "https://api.stripe.com/v1/charges",
            protocol = "HTTP/1.1",
            headers = listOf("Authorization" to "Bearer test_123"),
            body = null,
            timestamp = 1000L
        )
        val fakeEvent = InterceptedTransaction(
            id = "event-1",
            phase = BreakpointPhase.REQUEST,
            method = "GET",
            url = fakeRequest.url,
            request = fakeRequest,
            response = null,
            timestamp = 1000L
        )

        sessionRepository.emitInterceptions(listOf(fakeEvent))

        assertEquals(1, viewModel.uiState.value.activeEvents.size)
        assertEquals("event-1", viewModel.uiState.value.activeEvent?.id)

        viewModel.forwardRequest("event-1", fakeRequest)
        assertEquals("event-1", sessionRepository.lastForwardedRequestId)
        assertEquals(0, viewModel.uiState.value.activeEvents.size)
    }

    @Test
    fun `multiple in-flight intercepted sessions are queued and cached in viewModel activeEvents`() = runTest {
        val viewModel = createViewModel()

        val req1 = HttpRequest(id = "tx-1", method = "GET", url = "https://api.one.com/v1", protocol = "HTTP/1.1", headers = emptyList(), body = null, timestamp = 1000L)
        val req2 = HttpRequest(id = "tx-2", method = "POST", url = "https://api.two.com/v2", protocol = "HTTP/1.1", headers = emptyList(), body = null, timestamp = 2000L)
        val event1 = InterceptedTransaction(id = "ev-1", phase = BreakpointPhase.REQUEST, method = "GET", url = req1.url, request = req1, response = null, timestamp = 1000L)
        val event2 = InterceptedTransaction(id = "ev-2", phase = BreakpointPhase.REQUEST, method = "POST", url = req2.url, request = req2, response = null, timestamp = 2000L)

        // Netty informs about 2 simultaneous in-flight interceptions
        sessionRepository.emitInterceptions(listOf(event1, event2))

        // ViewModel caches the queue and selects the head event for the drawer
        assertEquals(2, viewModel.uiState.value.activeEvents.size)
        assertEquals("ev-1", viewModel.uiState.value.activeEvent?.id)
        assertEquals("https://api.one.com/v1", viewModel.uiState.value.activeEvent?.url)

        // User forwards the first event -> next event is promoted to active
        viewModel.forwardRequest("ev-1", req1)
        assertEquals("ev-1", sessionRepository.lastForwardedRequestId)
        assertEquals(1, viewModel.uiState.value.activeEvents.size)
        assertEquals("ev-2", viewModel.uiState.value.activeEvent?.id)
        assertEquals("https://api.two.com/v2", viewModel.uiState.value.activeEvent?.url)

        // User drops the second event -> queue becomes empty
        viewModel.dropEvent("ev-2")
        assertEquals("ev-2", sessionRepository.lastDroppedId)
        assertEquals(0, viewModel.uiState.value.activeEvents.size)
        assertNull(viewModel.uiState.value.activeEvent)
    }

    @Test
    fun `forwardResponse executes domain useCase and clears active interception`() = runTest {
        val viewModel = createViewModel()

        val req = HttpRequest(id = "tx-3", method = "GET", url = "https://api.test.com/data", protocol = "HTTP/1.1", headers = emptyList(), body = null, timestamp = 1000L)
        val resp = HttpResponse(statusCode = 200, statusText = "OK", headers = emptyList(), body = null, timestamp = 1000L)
        val event = InterceptedTransaction(id = "ev-3", phase = BreakpointPhase.RESPONSE, method = "GET", url = req.url, request = req, response = resp, timestamp = 1000L)

        sessionRepository.emitInterceptions(listOf(event))
        assertEquals("ev-3", viewModel.uiState.value.activeEvent?.id)

        val modifiedResp = HttpResponse(statusCode = 201, statusText = "Created", headers = emptyList(), body = null, timestamp = 1000L)
        viewModel.forwardResponse("ev-3", modifiedResp)

        assertEquals("ev-3", sessionRepository.lastForwardedResponseId)
        assertEquals(0, viewModel.uiState.value.activeEvents.size)
        assertNull(viewModel.uiState.value.activeEvent)
    }

    @Test
    fun `selectActiveEvent switches focused event in drawer`() = runTest {
        val viewModel = createViewModel()

        val req1 = HttpRequest(id = "tx-1", method = "GET", url = "https://api.one.com/v1", protocol = "HTTP/1.1", headers = emptyList(), body = null, timestamp = 1000L)
        val req2 = HttpRequest(id = "tx-2", method = "POST", url = "https://api.two.com/v2", protocol = "HTTP/1.1", headers = emptyList(), body = null, timestamp = 2000L)
        val event1 = InterceptedTransaction(id = "ev-1", phase = BreakpointPhase.REQUEST, method = "GET", url = req1.url, request = req1, response = null, timestamp = 1000L)
        val event2 = InterceptedTransaction(id = "ev-2", phase = BreakpointPhase.REQUEST, method = "POST", url = req2.url, request = req2, response = null, timestamp = 2000L)

        sessionRepository.emitInterceptions(listOf(event1, event2))
        assertEquals("ev-1", viewModel.uiState.value.activeEvent?.id)

        viewModel.selectActiveEvent("ev-2")
        assertEquals("ev-2", viewModel.uiState.value.activeEvent?.id)
    }

    @Test
    fun `dropAllEvents terminates all queued suspensions`() = runTest {
        val viewModel = createViewModel()

        val req1 = HttpRequest(id = "tx-1", method = "GET", url = "https://api.one.com/v1", protocol = "HTTP/1.1", headers = emptyList(), body = null, timestamp = 1000L)
        val req2 = HttpRequest(id = "tx-2", method = "POST", url = "https://api.two.com/v2", protocol = "HTTP/1.1", headers = emptyList(), body = null, timestamp = 2000L)
        val event1 = InterceptedTransaction(id = "ev-1", phase = BreakpointPhase.REQUEST, method = "GET", url = req1.url, request = req1, response = null, timestamp = 1000L)
        val event2 = InterceptedTransaction(id = "ev-2", phase = BreakpointPhase.REQUEST, method = "POST", url = req2.url, request = req2, response = null, timestamp = 2000L)

        sessionRepository.emitInterceptions(listOf(event1, event2))
        assertEquals(2, viewModel.uiState.value.activeEvents.size)

        viewModel.dropAllEvents()
        assertEquals(0, viewModel.uiState.value.activeEvents.size)
        assertNull(viewModel.uiState.value.activeEvent)
    }

    @Test
    fun `active selection is retained when non-focused event is resolved`() = runTest {
        val viewModel = createViewModel()

        val req1 = HttpRequest(id = "tx-1", method = "GET", url = "https://api.one.com/v1", protocol = "HTTP/1.1", headers = emptyList(), body = null, timestamp = 1000L)
        val req2 = HttpRequest(id = "tx-2", method = "POST", url = "https://api.two.com/v2", protocol = "HTTP/1.1", headers = emptyList(), body = null, timestamp = 2000L)
        val event1 = InterceptedTransaction(id = "ev-1", phase = BreakpointPhase.REQUEST, method = "GET", url = req1.url, request = req1, response = null, timestamp = 1000L)
        val event2 = InterceptedTransaction(id = "ev-2", phase = BreakpointPhase.REQUEST, method = "POST", url = req2.url, request = req2, response = null, timestamp = 2000L)

        sessionRepository.emitInterceptions(listOf(event1, event2))
        // User selects ev-2
        viewModel.selectActiveEvent("ev-2")
        assertEquals("ev-2", viewModel.uiState.value.activeEvent?.id)

        // ev-1 is resolved/dropped in the background
        sessionRepository.emitInterceptions(listOf(event2))

        // Selection must remain on ev-2
        assertEquals(1, viewModel.uiState.value.activeEvents.size)
        assertEquals("ev-2", viewModel.uiState.value.activeEvent?.id)
    }

    @Test
    fun `viewModel surfaces GraphQL protocol metadata in activeEvent`() = runTest {
        val viewModel = createViewModel()

        val req = HttpRequest(
            id = "tx-gql",
            method = "POST",
            url = "https://api.example.com/graphql",
            protocol = "HTTP/1.1",
            headers = listOf("Content-Type" to "application/json"),
            body = "{\"query\":\"mutation CreateOrder { id }\"}".encodeToByteArray(),
            timestamp = 1000L
        )
        val gqlMeta = com.devuloopers.knet.domain.protocol.model.InterceptionMetadata.GraphQL(
            operationName = "CreateOrder",
            operationType = "Mutation",
            querySummary = "mutation CreateOrder { id }"
        )
        val event = InterceptedTransaction(
            id = "ev-gql",
            phase = BreakpointPhase.REQUEST,
            method = "POST",
            url = req.url,
            request = req,
            response = null,
            timestamp = 1000L,
            metadata = gqlMeta
        )

        sessionRepository.emitInterceptions(listOf(event))

        assertEquals(1, viewModel.uiState.value.activeEvents.size)
        val active = viewModel.uiState.value.activeEvent
        assertNotNull(active)
        assertEquals("ev-gql", active.id)
        val meta = active.metadata
        assertTrue(meta is com.devuloopers.knet.domain.protocol.model.InterceptionMetadata.GraphQL)
        assertEquals("CreateOrder", meta.operationName)
        assertEquals("Mutation", meta.operationType)
    }
}


