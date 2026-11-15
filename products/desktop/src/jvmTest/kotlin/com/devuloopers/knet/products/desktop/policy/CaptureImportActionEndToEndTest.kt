package com.devuloopers.knet.products.desktop.policy

import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.contract.device.DevicePolicyLibrary
import com.devuloopers.knet.application.contract.importcapture.*
import com.devuloopers.knet.application.usecase.traffic.*
import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.products.desktop.config.DesktopConfiguration
import com.devuloopers.knet.products.desktop.di.DesktopModules
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.storage.device.entity.RegisteredDeviceEntity
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel.BreakpointManagerViewModel
import com.devuloopers.knet.ui.desktop.networkconditions.viewmodel.NetworkConditionsViewModel
import com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel.ProtectedTrafficViewModel
import com.devuloopers.knet.ui.desktop.traffic.importcapture.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.koin.dsl.*
import java.nio.file.Files
import kotlin.test.*

/** Actual HAR parser, product DI, protocol extensions and editor handoffs; policy stays unmodified until Save. */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureImportActionEndToEndTest {
    @Test fun validatedHarPreparesFourUnsentPrivateEditorDraftsAndRetiresTheImport() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val root = Files.createTempDirectory("knet-import-actions-")
        val file = root.resolve("session.har").toFile().apply { writeText(har) }
        val application = koinApplication { allowOverride(true); modules(DesktopModules.all + module { single { DesktopConfiguration(appDirectory = root) } }) }
        val database = application.koin.get<KNetDatabase>()
        val scope = application.koin.get<CoroutineScope>(); val store = ViewModelStore()
        val repository = application.koin.get<CaptureImportRepository>()
        var capture: ImportedCapture? = null
        try {
            for (id in listOf("a", "b")) database.registeredDeviceDao().upsertRegisteredDevice(RegisteredDeviceEntity(id, "Phone $id", "PAIRED_COMPANION", 1, 1, null))
            val library = application.koin.get<DevicePolicyLibrary>()
            val before = withTimeout(5_000) { library.snapshot.first { it.ready && it.workspaceNames.size >= 2 } }
            val workspace = PolicyWorkspace.Device(RegisteredDeviceId("a"))
            val dialog = application.koin.get<CaptureImportDialogViewModel>(); store.put("dialog", dialog)
            dialog.show(workspace, "Phone a")
            assertTrue(dialog.state.value.visible); assertNull(dialog.state.value.capture)
            dialog.completeDrop(assertNotNull(dialog.beginDrop(workspace, "Phone a")), listOf(file.absolutePath))
            capture = withTimeout(5_000) { dialog.state.first { !it.loading && it.capture != null && it.selected != null } }.capture
            val validated = assertNotNull(capture)
            assertEquals(CaptureImportFormat.HAR, validated.format); assertEquals(1L, validated.records)
            val prepare = application.koin.get<PrepareCaptureImportActionUseCase>()
            val studio = prepare.execute(validated, 0, CaptureImportAction.API_STUDIO, workspace) as CaptureImportActionDraft.ApiStudio
            val intercept = prepare.execute(validated, 0, CaptureImportAction.INTERCEPT, workspace) as CaptureImportActionDraft.Intercept
            val conditions = prepare.execute(validated, 0, CaptureImportAction.NETWORK_CONDITIONS, workspace) as CaptureImportActionDraft.NetworkConditions
            val protected = prepare.execute(validated, 0, CaptureImportAction.PROTECTED_TRAFFIC, workspace) as CaptureImportActionDraft.ProtectedTraffic
            assertTrue(intercept.values.any { it.value == "ImportedQuery" }, "Registered GraphQL suggestions must survive import")
            assertTrue(conditions.criteria.protocolId != NetworkConditionProtocolId.TRANSPORT)
            assertTrue(conditions.values.any { it.value == "ImportedQuery" })
            val api = application.koin.get<ApiStudioViewModel>(); store.put("api", api)
            withTimeout(5_000) { api.uiState.first { !it.isRestoring } }
            api.importRequestSpec(studio.request)
            assertTrue(api.uiState.value.editorState.url.contains("graphql")); assertNull(api.uiState.value.responseInspection)
            val bp = application.koin.get<BreakpointManagerViewModel>(); store.put("bp", bp)
            bp.openImportedDraft(intercept)
            assertEquals(workspace, bp.uiState.value.editingContext.workspace)
            val nc = application.koin.get<NetworkConditionsViewModel>(); store.put("nc", nc)
            nc.openImportedDraft(conditions)
            assertEquals(workspace, nc.state.value.ruleDraft?.context?.workspace)
            assertEquals(NetworkConditionBuiltIns.NO_THROTTLING.id, nc.state.value.ruleDraft?.profileId)
            assertEquals("import.test", nc.state.value.ruleDraft?.hostPattern)
            assertEquals("9443", nc.state.value.ruleDraft?.port)
            val pt = application.koin.get<ProtectedTrafficViewModel>(); store.put("pt", pt)
            pt.openImportedDraft(protected)
            assertEquals(workspace, pt.state.value.ruleDraft?.context?.workspace)
            assertEquals("import.test", pt.state.value.ruleDraft?.destination)
            assertFailsWith<IllegalStateException> { bp.openImportedDraft(intercept) }
            assertFailsWith<IllegalStateException> { nc.openImportedDraft(conditions) }
            assertFailsWith<IllegalStateException> { pt.openImportedDraft(protected) }
            val after = library.snapshot.value
            assertEquals(before.breakpoints, after.breakpoints); assertEquals(before.conditions, after.conditions)
            assertEquals(before.protectedRules, after.protectedRules)
            dialog.perform(CaptureImportAction.API_STUDIO) { assertIs<CaptureImportActionDraft.ApiStudio>(it) }
            withTimeout(5_000) {
                dialog.state.first { !it.presented }
                while (runCatching { repository.page(validated.id, 0) }.isSuccess) delay(10)
            }
            assertEquals(4, CaptureImportAction.entries.size)
            capture = null // Successful editor handoff retired the temporary parser spool.

        } finally {
            capture?.let { repository.close(it.id) }
            store.clear(); application.close(); scope.coroutineContext.job.join()
            database.close(); root.toFile().deleteRecursively(); Dispatchers.resetMain()
        }
    }

    private companion object {
        val har = """{"log":{"version":"1.2","creator":{"name":"fixture","version":"1"},"entries":[{"startedDateTime":"2026-11-15T00:00:00Z","time":15,"request":{"method":"GET","url":"https://import.test:9443/graphql?query=query%20ImportedQuery%20%7B%20viewer%20%7B%20id%20%7D%20%7D&operationName=ImportedQuery","httpVersion":"HTTP/1.1","headers":[],"queryString":[],"cookies":[],"headersSize":-1,"bodySize":0},"response":{"status":200,"statusText":"OK","httpVersion":"HTTP/1.1","headers":[{"name":"Content-Type","value":"application/json"}],"cookies":[],"content":{"mimeType":"application/json","size":2,"text":"{}"},"redirectURL":"","headersSize":-1,"bodySize":2},"cache":{},"timings":{"send":0,"wait":10,"receive":5}}]}}"""
    }
}
