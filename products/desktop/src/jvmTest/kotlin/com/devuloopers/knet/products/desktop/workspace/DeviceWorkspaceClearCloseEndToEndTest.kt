package com.devuloopers.knet.products.desktop.workspace

import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.contract.device.DevicePresence
import com.devuloopers.knet.application.contract.device.DevicePresenceReader
import com.devuloopers.knet.application.contract.device.DevicePresenceStatus
import com.devuloopers.knet.application.contract.device.DeviceWorkspacePreferences
import com.devuloopers.knet.application.contract.pairing.RegisteredDeviceStore
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.products.desktop.config.DesktopConfiguration
import com.devuloopers.knet.products.desktop.di.DesktopModules
import com.devuloopers.knet.storage.capture.entity.CaptureSessionEntity
import com.devuloopers.knet.storage.capture.entity.OpaqueFlowEntity
import com.devuloopers.knet.storage.capture.entity.TrafficConnectionEntity
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.storage.device.entity.RegisteredDeviceEntity
import com.devuloopers.knet.ui.desktop.app.workspace.DeviceWorkspaceViewModel
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.*

/** Exercises the same Clear/Close callback chain as MainWindow with actual scoped Room and DataStore adapters. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceWorkspaceClearCloseEndToEndTest {
    @Test
    fun `offline Clear retains its tab while confirmed Close deletes only that device history and tab`() =
        verifyClearAndClose(interrupted = false)

    @Test
    fun `Clear before starting capture recovers and deletes interrupted sessions after restart`() =
        verifyClearAndClose(interrupted = true)

    @Test
    fun `online VPN presence cannot block clearing interrupted history while capture is stopped`() =
        verifyClearAndClose(interrupted = true, connected = true)

    @Test
    fun `confirmed Close recovers interrupted history without requiring an earlier Clear`() =
        verifyClearAndClose(interrupted = true, clearFirst = false)

    private fun verifyClearAndClose(interrupted: Boolean, connected: Boolean = false, clearFirst: Boolean = true): Unit = runBlocking {
        // Production Main serializes UI state. Unconfined resumes on Room/DataStore threads and permits
        // source selection to race the clearing flag, which is not representative of desktop event dispatch.
        val mainDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(mainDispatcher)
        val root = Files.createTempDirectory("knet-workspace-clear-close-")
        val observedPresence = MutableStateFlow<Map<RegisteredDeviceId, DevicePresence>>(if (connected)
            mapOf(RegisteredDeviceId("a") to DevicePresence(DevicePresenceStatus.CONNECTED, "vpn-run", 1)) else emptyMap())
        val application = koinApplication {
            allowOverride(true)
            modules(DesktopModules.all + module {
                single { DesktopConfiguration(appDirectory = root) }
                single<DevicePresenceReader> { object : DevicePresenceReader {
                    override val presence = observedPresence
                } }
            })
        }
        val database = application.koin.get<KNetDatabase>()
        val scope = application.koin.get<CoroutineScope>()
        val owner = ViewModelStore()
        var navigation: Job? = null
        try {
            val dao = database.canonicalCaptureDao()
            dao.insertSession(CaptureSessionEntity("shared", 1, if (interrupted) null else 2,
                if (interrupted) "ACTIVE" else "CLOSED", 1))
            for (device in listOf("a", "b")) {
                database.registeredDeviceDao().upsertRegisteredDevice(
                    RegisteredDeviceEntity(device, "Device $device", "PAIRED_COMPANION", 1, 1, null))
                dao.insertConnection(connection(device).let {
                    if (interrupted) it.copy(state = "OPEN", closedAtEpochMillis = null) else it
                })
                dao.insertOpaqueFlow(flow("$device-before", device).let {
                    if (interrupted) it.copy(state = "ACTIVE", completedAtEpochMillis = null) else it
                })
            }
            val workspaces = application.koin.get<DeviceWorkspaceViewModel>().also { owner.put("workspaces", it) }
            val traffic = application.koin.get<TrafficViewModel>().also { owner.put("traffic", it) }
            navigation = launch(Dispatchers.Main.immediate) {
                workspaces.state.map { it.source }.distinctUntilChanged().collect(traffic::selectSource)
            }
            withTimeout(5_000) { workspaces.state.first { it.devices.size == 2 } }
            val a = TrafficSource.Device(RegisteredDeviceId("a"))
            withContext(Dispatchers.Main) { workspaces.select(a) }
            withTimeout(5_000) { traffic.uiState.first { it.source == a && !it.isPageLoading } }

            val preferences = application.koin.get<DeviceWorkspacePreferences>()
            if (clearFirst) {
                withContext(Dispatchers.Main) {
                    val clearTarget = assertNotNull(workspaces.requestClear(a))
                    traffic.clearSource(clearTarget, beforeClear = { workspaces.retainForClear(clearTarget) })
                }
                withTimeout(5_000) { traffic.uiState.first { !it.isClearingHistory } }
                assertNull(traffic.uiState.value.trafficErrorMessage)
                assertNull(dao.getOpaqueFlow("a-before"), "Clear must delete actual storage records")
                assertNotNull(dao.getOpaqueFlow("b-before"), "A sibling's traffic must survive Clear")
                assertEquals(a, workspaces.state.value.source)
                assertTrue(workspaces.state.value.devices.any { it.device.id == a.id })
                assertTrue(a.id in preferences.openDevices.first(), "The empty tab is durably retained")
                if (connected) {
                    assertEquals(DevicePresenceStatus.CONNECTED, workspaces.state.value.devices.first { it.device.id == a.id }.status)
                    observedPresence.value = emptyMap()
                    withTimeout(5_000) { workspaces.state.first { state ->
                        state.devices.firstOrNull { it.device.id == a.id }?.status == DevicePresenceStatus.OFFLINE
                    } }
                }
            }

            dao.insertOpaqueFlow(flow("a-after", "a"))
            val failures = mutableListOf<String>()
            withContext(Dispatchers.Main) {
                workspaces.requestClose(a.id)
                val closeTarget = assertNotNull(workspaces.confirmClear())
                traffic.clearSource(closeTarget, beforeClear = { workspaces.retainForClear(closeTarget) },
                    afterClear = { workspaces.finishClose(closeTarget) }, onFailure = { failures += "close failed" })
            }
            withTimeout(5_000) { traffic.uiState.first { !it.isClearingHistory } }
            withTimeout(5_000) { workspaces.state.first { state -> state.devices.none { it.device.id == a.id } } }
            assertTrue(failures.isEmpty())
            assertNull(dao.getOpaqueFlow("a-before"))
            assertNull(dao.getOpaqueFlow("a-after"), "Close must delete traffic added after the earlier Clear")
            assertNotNull(dao.getOpaqueFlow("b-before"), "Close must leave sibling history intact")
            assertTrue(a.id !in preferences.openDevices.first())
            assertEquals(TrafficSource.Other, workspaces.state.value.source)
            assertNotNull(application.koin.get<RegisteredDeviceStore>().getRegisteredDevice(a.id), "Pairing is retained")
        } finally {
            navigation?.cancelAndJoin()
            withContext(Dispatchers.Main) { owner.clear() }
            application.close()
            scope.coroutineContext.job.join()
            database.close()
            root.toFile().deleteRecursively()
            Dispatchers.resetMain()
            mainDispatcher.close()
        }
    }

    private fun connection(device: String) = TrafficConnectionEntity(device, "shared", 1, 1, 2,
        "COMPANION_DIRECT", device, "192.0.2.1", 1234, "127.0.0.1", 8080, "tcp", 0, 0, "CLOSED", null)

    private fun flow(id: String, device: String) = OpaqueFlowEntity(id = id, sessionId = "shared", connectionId = device,
        connectionSequence = 1, version = 1, state = "COMPLETED", startedAtEpochMillis = 1, completedAtEpochMillis = 2,
        destinationHost = "protected.example", destinationPort = 443, serverName = "protected.example", transport = "TCP",
        security = "TLS", sourceApplicationId = null, policyRuleId = null, uploadedBytes = 10, downloadedBytes = 20,
        terminalErrorCode = null)
}
