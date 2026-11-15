package com.devuloopers.knet.products.desktop.policy

import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.contract.device.DevicePolicyLibrary
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.domain.policy.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.engine.proxy.tls.*
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.products.desktop.config.DesktopConfiguration
import com.devuloopers.knet.products.desktop.di.DesktopModules
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.storage.device.entity.RegisteredDeviceEntity
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficProtectionViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.koin.dsl.*
import java.nio.file.Files
import kotlin.test.*

/** Same request-row command chain as Traffic: product DI, canonical metadata, Room policy and runtime TLS adapter. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrafficRequestProtectionEndToEndTest {
    @Test fun ordinaryRequestChoicesPersistPrivateDeviceRulesAndReachTheRuntimeAdapter() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val root = Files.createTempDirectory("knet-request-protection-")
        val query = Query()
        val application = koinApplication { allowOverride(true); modules(DesktopModules.all + module {
            single { DesktopConfiguration(appDirectory = root) }
            single<TrafficQuery> { query }
        }) }
        val database = application.koin.get<KNetDatabase>()
        val owner = application.koin.get<CoroutineScope>()
        val store = ViewModelStore()
        try {
            for (device in listOf("a", "b")) database.registeredDeviceDao().upsertRegisteredDevice(
                RegisteredDeviceEntity(device, "Device $device", "PAIRED_COMPANION", 1, 1, null))
            val library = application.koin.get<DevicePolicyLibrary>()
            withTimeout(5_000) { library.snapshot.first { it.ready } }
            val viewModel = application.koin.get<TrafficProtectionViewModel>(); store.put("traffic-protection", viewModel)
            val policy = application.koin.get<TlsInterceptionPolicy>()
            val a = PolicyWorkspace.Device(RegisteredDeviceId("a")); val b = PolicyWorkspace.Device(RegisteredDeviceId("b"))
            val failures = mutableListOf<String>(); var ruleId: ProtectedTrafficRuleId? = null
            for ((action, mode) in listOf(ProtectedTrafficAction.TUNNEL to TlsInterceptionMode.TUNNEL,
                ProtectedTrafficAction.INSPECT to TlsInterceptionMode.INSPECT, ProtectedTrafficAction.BLOCK to TlsInterceptionMode.BLOCK)) {
                viewModel.applyRecordAction(TrafficRecordId.Exchange(ExchangeId("request")), action, failures::add)
                val saved = withTimeout(5_000) { library.snapshot.first { it.protectedRules.singleOrNull()?.rule?.action == action } }.protectedRules.single()
                withTimeout(5_000) { viewModel.uiState.first { !it.isApplying } }
                assertEquals(RuleAvailability.Private(a), saved.availability); assertEquals(setOf(a), saved.enabledOn)
                if (ruleId == null) ruleId = saved.rule.id else assertEquals(ruleId, saved.rule.id)
                assertEquals(8443, saved.rule.port)
                assertEquals(mode, policy.decide(TlsInterceptionRequest("api.test", 8443, workspace = a)).mode)
                assertEquals(TlsInterceptionMode.INSPECT, policy.decide(TlsInterceptionRequest("api.test", 8443, workspace = b)).mode)
                assertTrue(failures.isEmpty())
            }
        } finally {
            store.clear(); application.close(); owner.coroutineContext.job.join()
            database.close(); root.toFile().deleteRecursively(); Dispatchers.resetMain()
        }
    }
    private class Query : TrafficQuery {
        override val generations = emptyFlow<TrafficGeneration>()
        override suspend fun query(query: TrafficPageQuery) = TrafficPage(items = emptyList(), nextCursor = null, totalCount = 0, generation = 0)
        override suspend fun getExchange(exchangeId: ExchangeId) = HttpExchangeSnapshot(exchangeId, ConnectionId("captured-a"),
            request = HttpRequestSnapshot(RequestHead(HttpMethod.fromToken("POST"),
                RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("api.test", 8443), "/graphql"),
                ApplicationProtocol.fromToken("h2"), emptyList())), state = ExchangeState.COMPLETED, startedAtEpochMillis = 1)
        override suspend fun getConnectionIngress(connectionId: ConnectionId) = TrafficConnectionIngress(
            IngressContext(IngressKind.LanPairedDevice, ClientIdentity("a")), "192.0.2.1")
        override suspend fun readBody(bodyId: BodyId, range: BodyRange) = error("Policy shortcuts do not load payloads")
    }
}
