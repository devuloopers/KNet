package com.devuloopers.knet.ui.desktop.apistudio.copy

import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.contract.export.StudioExportSession
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset
import com.devuloopers.knet.application.usecase.traffic.PrepareStudioCopyUseCase
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.ui.core.components.button.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.model.ResponseInspectorState
import com.devuloopers.knet.ui.desktop.apistudio.model.LiveHttpResponseState
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.apistudio.export.StudioCopyAction
import com.devuloopers.knet.ui.desktop.apistudio.response.*
import com.devuloopers.knet.ui.desktop.httppanel.copy.StudioDetailsCopyViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.*
import java.awt.EventQueue
import kotlin.test.*

/** Actual response tabs, quick-copy callbacks, enabled gating and nested themed popup at both desktop densities. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class StudioCopyRenderTest {
    @Test fun tabsCopyTheirOwnPresetAndOwnerChangesClearMenusWithoutMovingActions() = EventQueue.invokeAndWait {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try { for (scale in listOf(1f, 2f)) for (width in listOf(420, 1100)) {
            val calls = mutableListOf<Pair<String, TrafficCopyPreset>>()
            val application = koinApplication { modules(module {
                factory { StudioDetailsCopyViewModel(PrepareStudioCopyUseCase { id, preset -> calls += id to preset; "copied" }) }
            }) }
            val store = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
            val tab = mutableStateOf(ResponseSubTab.BODY); val session = mutableStateOf<StudioExportSession?>(null)
            val ready = Session("A")
            val response = mutableStateOf(ResponseInspectorState(statusCode = 503, responseBody = "response"))
            val native = mutableStateOf<RequestKindId?>(null)
            val clipboard = object : Clipboard {
                override suspend fun getClipEntry(): ClipEntry? = null
                override suspend fun setClipEntry(clipEntry: ClipEntry?) {}
            }
            ImageComposeScene((width * scale).toInt(), (650 * scale).toInt(), Density(scale), content = {
                KoinIsolatedContext(application) { CompositionLocalProvider(LocalViewModelStoreOwner provides store, LocalClipboard provides clipboard) {
                    KNetTheme {
                      if (native.value != null) StudioCopyAction(session.value, native.value!!.value, native.value!!,
                          if (native.value == RequestKindId.GRPC) TrafficCopyPreset.GRPC_MESSAGES else TrafficCopyPreset.WEBSOCKET_MESSAGES, "Copy messages")
                      else ResponseInspectorView(response.value, activeSubTab = tab.value,
                        onSubTabSelected = { tab.value = it }, exportAction = { KNetButton({}) { androidx.compose.material3.Text("Export") } },
                        execution = session.value, executionCopyEnabled = true) }
                } }
            }).use { scene ->
                var time = 0L
                fun frame() { repeat(4) { scene.render(time.also { time += 250_000_000 }).close() } }
                fun nodes(): List<SemanticsNode> {
                    fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::visit)
                    return scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
                }
                fun description(value: String) = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains(value) }
                fun text(value: String) = nodes().first { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> t.text == value } }
                fun click(node: SemanticsNode) { val point = node.boundsInWindow.center
                    scene.sendPointerEvent(PointerEventType.Press, point, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
                    scene.sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary); frame()
                }
                frame(); assertNotNull(description("Copy body").config.getOrNull(SemanticsProperties.Disabled))
                session.value = ready; frame(); assertNotNull(description("Copy body").config.getOrNull(SemanticsProperties.Disabled))
                ready.responseReceived.value = true; frame()
                assertNull(description("Copy body").config.getOrNull(SemanticsProperties.Disabled))
                val initial = description("Copy options").boundsInWindow
                assertTrue(initial.right <= width * scale, "Copy trigger must stay inside narrow response pane")
                for ((subTab, label, preset) in listOf(Triple(ResponseSubTab.BODY, "Copy body", TrafficCopyPreset.RESPONSE_BODY),
                    Triple(ResponseSubTab.HEADERS, "Copy headers", TrafficCopyPreset.RESPONSE_HEADERS), Triple(ResponseSubTab.COOKIES, "Copy cookies", TrafficCopyPreset.RESPONSE_COOKIES))) {
                    if (tab.value != subTab) click(nodes().first { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab &&
                        it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> t.text.startsWith(subTab.baseLabel) } })
                    click(description(label)); assertEquals("A" to preset, calls.last())
                    assertEquals(initial, description("Copy options").boundsInWindow)
                }
                click(description("Copy options")); click(text("Response"))
                if (scale == 1f && width == 1100) scene.render(time.also { time += 250_000_000 }).use { image ->
                    image.encodeToData()!!.use { java.io.File("/tmp/knet-studio-copy-menu.png").writeBytes(it.bytes) }
                }
                click(text("Original text"))
                assertEquals("A" to TrafficCopyPreset.RESPONSE_ORIGINAL_TEXT, calls.last())
                click(description("Copy options")); ready.responseReceived.value = false; frame()
                assertTrue(nodes().none { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> t.text == "Full exchange details" } })
                ready.responseReceived.value = true; frame()
                click(description("Copy options")); session.value = Session("B").apply { responseReceived.value = true }; frame()
                assertTrue(nodes().none { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> t.text == "Full exchange details" } })
                click(description("Copy cookies")); assertEquals("B" to TrafficCopyPreset.RESPONSE_COOKIES, calls.last())
                session.value = null; frame(); assertNotNull(description("Copy cookies").config.getOrNull(SemanticsProperties.Disabled))
                session.value = ready
                response.value = ResponseInspectorState(statusCode = 200, liveResponse = LiveHttpResponseState("SSE"))
                frame()
                val scroll = nodes().first { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                scroll.config[SemanticsActions.ScrollBy].action!!.invoke(10_000f, 0f); frame()
                assertTrue(description("Copy events").boundsInWindow.width > 0)
                click(description("Copy events")); assertEquals("A" to TrafficCopyPreset.SSE_EVENTS, calls.last())
                click(description("Copy options")); click(text("Protocol details")); click(text("Event data"))
                assertEquals("A" to TrafficCopyPreset.SSE_DATA, calls.last())
                for (kind in listOf(RequestKindId.WEBSOCKET, RequestKindId.GRAPHQL_WEBSOCKET, RequestKindId.GRPC)) {
                    native.value = kind; frame(); click(description("Copy messages"))
                    assertEquals("A" to if (kind == RequestKindId.GRPC) TrafficCopyPreset.GRPC_MESSAGES else TrafficCopyPreset.WEBSOCKET_MESSAGES, calls.last())
                    click(description("Copy options")); click(text("Protocol details"))
                    click(text(if (kind == RequestKindId.GRPC) "Metadata and trailers" else "Handshake details"))
                    assertEquals("A" to if (kind == RequestKindId.GRPC) TrafficCopyPreset.GRPC_METADATA else TrafficCopyPreset.WEBSOCKET_HANDSHAKE, calls.last())
                }
            }
            store.viewModelStore.clear(); application.close()
        } } finally { Dispatchers.resetMain() }
    }
    private class Session(override val id: String) : StudioExportSession {
        override val eligible = MutableStateFlow(false)
        override val responseReceived = MutableStateFlow(false)
        override suspend fun response(head: HttpExecutionResponseHead) {}
        override suspend fun append(bytes: ByteArray) {}
        override suspend fun complete(result: ExecutionResult) {}
        override suspend fun event(event: ApiStudioProtocolExecutionEvent) {}
        override fun interrupted() {}
        override fun release() {}
    }
}
