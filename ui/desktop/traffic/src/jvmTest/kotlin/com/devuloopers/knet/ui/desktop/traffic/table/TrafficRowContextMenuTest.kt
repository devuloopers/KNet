package com.devuloopers.knet.ui.desktop.traffic.table

import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.ui.core.components.menu.*
import com.devuloopers.knet.ui.desktop.traffic.model.*
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import kotlin.test.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue

/** Typed leaf snapshots, body-free capabilities and nested real popup interactions at standard/Retina density. */
class TrafficRowContextMenuTest {
    @Test fun `copy replaces flat actions and protected sidebar commands preserve order and identity`() {
        for (id in listOf(exchange("same"), opaque("same"))) {
            val calls = mutableListOf<Pair<TrafficRecordId, ProtectedTrafficAction>>()
            val menu = menu(id, TrafficRowSelection(setOf(id)), actions(protect = { record, action -> calls += record to action }))
            val features = if (id is TrafficRecordId.Exchange) listOf("Send to API Studio", "Add Breakpoint Rule", "Add to Network Conditions...", "Protected Traffic")
                else listOf("Add to Network Conditions...", "Protected Traffic")
            assertEquals(listOf("Export this item", "Copy", "divider") + features, menu.map(::label))
            assertTrue(menu.none { label(it) in listOf("Copy details", "Copy destination", "Clear selection", "Deselect item") })
            val protected = menu.filterIsInstance<ContextMenuSubmenu>().single { it.label == "Protected Traffic" }
            assertEquals(KNetIcons.Shield, protected.icon)
            assertEquals(listOf("Always tunnel this destination", "Attempt inspection next time", "Block this destination"), protected.items.map(::label))
            protected.items.filterIsInstance<ContextMenuItem>().forEach { it.onClick() }
            assertEquals(listOf(ProtectedTrafficAction.TUNNEL, ProtectedTrafficAction.INSPECT, ProtectedTrafficAction.BLOCK).map { id to it }, calls)
        }
    }

    @Test fun `every copy preset is reachable and source selection is frozen for every nested leaf`() {
        val first = exchange("first"); val second = opaque("second"); val mutable = linkedSetOf(first, second)
        val copied = mutableListOf<Pair<List<TrafficRecordId>, TrafficCopyPreset>>()
        val copy = trafficCopyMenu(mutable.toList(), allCapabilities) { records, preset -> copied += records to preset }
        mutable.clear(); mutable += exchange("later")
        leaves(copy.items).forEach { it.onClick() }
        assertEquals(TrafficCopyPreset.entries.toSet(), copied.map { it.second }.toSet())
        assertTrue(copied.all { it.first == listOf(first, second) })
    }

    @Test fun `export group membership and copy presets agree while sidebar actions target clicked row`() {
        val first = exchange("first"); val second = opaque("second"); val outside = exchange("outside")
        var exported: TrafficRecordId? = null; var selected = 0; var studio = ""
        var copy: Pair<List<TrafficRecordId>, TrafficCopyPreset>? = null
        val actions = actions(export = { exported = it }, selected = { selected++ }, studio = { studio = it }, details = { records, preset -> copy = records to preset })
        val selection = TrafficRowSelection(setOf(first, second))
        val group = menu(first, selection, actions)
        group.filterIsInstance<ContextMenuItem>().single { it.label == "Export 2 selected items" }.onClick()
        assertEquals(1, selected); assertNull(exported)
        group.filterIsInstance<ContextMenuItem>().single { it.label == "Send to API Studio" }.onClick(); assertEquals("first", studio)
        leaves(group.filterIsInstance<ContextMenuSubmenu>().single { it.label == "Copy (2)" }.items).single { it.label == "Host and port" }.onClick()
        assertEquals(listOf(first, second) to TrafficCopyPreset.DESTINATION, copy)
        val single = menu(outside, selection, actions)
        single.filterIsInstance<ContextMenuItem>().single { it.label == "Export this item" }.onClick(); assertEquals(outside, exported)
        leaves(single.filterIsInstance<ContextMenuSubmenu>().single { it.label == "Copy" }.items).single { it.label == "Full traffic details" }.onClick()
        assertEquals(listOf(outside) to TrafficCopyPreset.FULL, copy)
    }

    @Test fun `pending and opaque groups disable response and curl while mixed response remains available`() {
        val pending = row("pending", response = false)
        val caps = TrafficCopyMenuCapabilities.from(listOf(pending.recordId), listOf(pending))
        assertTrue(caps.hasHttp); assertFalse(caps.hasResponse)
        val copy = trafficCopyMenu(listOf(pending.recordId), caps) { _, _ -> }
        assertFalse(copy.items.filterIsInstance<ContextMenuSubmenu>().single { it.label == "Response" }.enabled)
        val tls = trafficCopyMenu(listOf(opaque("tls")), TrafficCopyMenuCapabilities.from(listOf(opaque("tls")), emptyList())) { _, _ -> }
        assertFalse(tls.items.filterIsInstance<ContextMenuSubmenu>().single { it.label == "Request" }.enabled)
        assertFalse(leaves(tls.items).single { it.label == "cURL" }.enabled)
        val response = row("complete", response = true)
        assertTrue(TrafficCopyMenuCapabilities.from(listOf(pending.recordId, response.recordId), listOf(pending, response)).hasResponse)
        // Selection may outlive its loaded page. Unknown HTTP metadata is resolved by the use case instead of silently excluding it.
        assertTrue(TrafficCopyMenuCapabilities.from(listOf(exchange("unloaded")), emptyList()).hasResponse)
    }

    @Test fun `protocol menus follow semantic row types and do not add GraphQL actions to ordinary HTTP`() {
        val ordinary = row("http")
        assertTrue(TrafficCopyMenuCapabilities.from(listOf(ordinary.recordId), listOf(ordinary)).protocols.isEmpty())
        val variants = listOf(RequestKindId.GRAPHQL to TrafficCopyProtocol.GRAPHQL, RequestKindId.WEBSOCKET to TrafficCopyProtocol.WEBSOCKET,
            RequestKindId.GRAPHQL_WEBSOCKET to TrafficCopyProtocol.GRAPHQL_WEBSOCKET, RequestKindId.GRPC to TrafficCopyProtocol.GRPC)
        for ((kind, expected) in variants) {
            val value = ordinary.copy(requestKind = kind)
            assertEquals(setOf(expected), TrafficCopyMenuCapabilities.from(listOf(value.recordId), listOf(value)).protocols)
        }
        val sse = ordinary.copy(contentType = "text/event-stream; charset=utf-8")
        assertEquals(setOf(TrafficCopyProtocol.SSE), TrafficCopyMenuCapabilities.from(listOf(sse.recordId), listOf(sse)).protocols)
    }

    @OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
    @Test fun `real nested copy popups route all selected records and dismiss at both densities`() = EventQueue.invokeAndWait {
        for (density in listOf(1f, 2f)) for (width in listOf(800, 1280)) {
            val records = listOf(exchange("graphql"), opaque("tls"))
            val calls = mutableListOf<Pair<List<TrafficRecordId>, TrafficCopyPreset>>()
            val copy = trafficCopyMenu(records, allCapabilities.copy(protocols = setOf(TrafficCopyProtocol.GRAPHQL, TrafficCopyProtocol.OPAQUE))) { ids, preset -> calls += ids to preset }
            ImageComposeScene((width * density).toInt(), (800 * density).toInt(), Density(density), content = { KNetTheme {
                KNetContextMenuArea(listOf(copy), Modifier.fillMaxSize().background(KNetTheme.colors.background)) { Text("Traffic", Modifier.padding(24.dp)) }
            } }).use { scene ->
                var time = 0L
                fun frames() { repeat(6) { scene.render(time.also { time += 100_000_000 }).close() } }
                fun nodes(): List<SemanticsNode> {
                    fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
                    return scene.semanticsOwners.flatMap { descend(it.rootSemanticsNode) }
                }
                fun node(value: String) = nodes().first { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == value } }
                fun hover(value: String) { scene.sendPointerEvent(PointerEventType.Move, node(value).boundsInWindow.center); frames() }
                frames(); val point = Offset(60 * density, 50 * density)
                scene.sendPointerEvent(PointerEventType.Press, point, buttons = PointerButtons(isSecondaryPressed = true), button = PointerButton.Secondary)
                scene.sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Secondary); frames()
                hover("Copy (2)"); hover("Protocol details"); hover("GraphQL")
                val target = node("Variables").boundsInWindow.center
                scene.sendPointerEvent(PointerEventType.Press, target, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
                scene.sendPointerEvent(PointerEventType.Release, target, button = PointerButton.Primary); frames()
                assertEquals(listOf(records to TrafficCopyPreset.GRAPHQL_VARIABLES), calls)
                assertTrue(nodes().none { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == "Copy (2)" } })
            }
        }
    }

    private fun menu(id: TrafficRecordId, selection: TrafficRowSelection, actions: TrafficContextMenuActions) = trafficRowContextMenu(id, selection, actions,
        allCapabilities.copy(hasHttp = id is TrafficRecordId.Exchange || selection.records.any { it is TrafficRecordId.Exchange }))
    private fun leaves(entries: List<ContextMenuEntry>): List<ContextMenuItem> = entries.flatMap { when (it) {
        is ContextMenuItem -> listOf(it); is ContextMenuSubmenu -> leaves(it.items); ContextMenuSeparator -> emptyList()
    } }
    private fun label(entry: ContextMenuEntry): String = when (entry) { is ContextMenuItem -> entry.label; is ContextMenuSubmenu -> entry.label; ContextMenuSeparator -> "divider" }
    private fun exchange(id: String) = TrafficRecordId.Exchange(ExchangeId(id))
    private fun opaque(id: String) = TrafficRecordId.Opaque(OpaqueFlowId(id))
    private val allCapabilities = TrafficCopyMenuCapabilities(true, true, true, TrafficCopyProtocol.entries.toSet())
    private fun actions(protect: (TrafficRecordId, ProtectedTrafficAction) -> Unit = { _, _ -> }, export: (TrafficRecordId) -> Unit = {}, selected: () -> Unit = {},
        studio: (String) -> Unit = {}, details: (List<TrafficRecordId>, TrafficCopyPreset) -> Unit = { _, _ -> }) = TrafficContextMenuActions(export, selected, studio, {}, {}, protect, details)
    private fun row(id: String, response: Boolean = true): TrafficRowUiState = HttpExchangeSnapshot(ExchangeId(id), request = HttpRequestSnapshot(RequestHead(HttpMethod.GET,
        RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("copy.test", 443), "/"), ApplicationProtocol.fromToken("HTTP/1.1"), emptyList())),
        response = if (response) HttpResponseSnapshot(ResponseHead(ApplicationProtocol.fromToken("HTTP/1.1"), HttpStatus(200), headers = emptyList())) else null,
        state = if (response) ExchangeState.COMPLETED else ExchangeState.REQUEST_COMPLETE, startedAtEpochMillis = 1).toTrafficRowUiState()
}
