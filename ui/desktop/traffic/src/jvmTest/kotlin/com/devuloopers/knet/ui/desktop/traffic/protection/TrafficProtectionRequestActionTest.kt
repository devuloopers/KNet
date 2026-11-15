package com.devuloopers.knet.ui.desktop.traffic.protection

import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.application.usecase.protectedtraffic.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficProtectionViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Request actions preserve typed identity, serialize writes and report failed canonical lookups. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrafficProtectionRequestActionTest {
    @Test fun `request ID matching an opaque ID still saves the request destination and duplicate clicks coalesce`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = ProtectionFixture(); var reads = 0
        val query = object : TrafficQuery by fixture.query {
            override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? {
                reads++
                return HttpExchangeSnapshot(exchangeId, request = HttpRequestSnapshot(RequestHead(HttpMethod.fromToken("POST"),
                    RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("request.example.test", 9443), "/graphql"),
                    ApplicationProtocol.fromToken("h2"), emptyList())), state = ExchangeState.COMPLETED, startedAtEpochMillis = 1)
            }
        }
        val viewModel = TrafficProtectionViewModel(ObserveProtectedTrafficFlowHandlingUseCase(query, fixture.repository, fixture.fallback),
            SetProtectedTrafficFlowActionUseCase(query, fixture.repository, fixture.fallback), ResetProtectedTrafficFlowActionUseCase(query, fixture.repository, fixture.fallback))
        val owner = ViewModelStore().apply { put("vm", viewModel) }
        val gate = CompletableDeferred<Unit>(); fixture.beforeSave = { gate.await() }
        try {
            val id = TrafficRecordId.Exchange(ExchangeId(fixture.captured.id.value))
            val failures = mutableListOf<String>()
            repeat(3) { viewModel.applyRecordAction(id, ProtectedTrafficAction.TUNNEL, failures::add) }
            runCurrent(); assertEquals(1, reads); assertEquals(0, fixture.reads)
            gate.complete(Unit); runCurrent()
            assertTrue(failures.isEmpty()); assertEquals(1, fixture.writes)
            assertEquals(ProtectedDestinationSelector.parse("request.example.test"), fixture.repository.configuration.value.rules.single().destination)
            assertEquals(9443, fixture.repository.configuration.value.rules.single().port)
            assertFalse(viewModel.uiState.value.isApplying)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun `missing request reports failure without falling back to an opaque row with the same ID`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = ProtectionFixture()
        val viewModel = TrafficProtectionViewModel(ObserveProtectedTrafficFlowHandlingUseCase(fixture.query, fixture.repository, fixture.fallback),
            SetProtectedTrafficFlowActionUseCase(fixture.query, fixture.repository, fixture.fallback), ResetProtectedTrafficFlowActionUseCase(fixture.query, fixture.repository, fixture.fallback))
        val owner = ViewModelStore().apply { put("vm", viewModel) }
        try {
            val errors = mutableListOf<String>()
            viewModel.applyRecordAction(TrafficRecordId.Exchange(ExchangeId("flow")), ProtectedTrafficAction.BLOCK, errors::add)
            runCurrent(); assertEquals(1, errors.size); assertEquals(0, fixture.writes); assertEquals(0, fixture.reads)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
}
