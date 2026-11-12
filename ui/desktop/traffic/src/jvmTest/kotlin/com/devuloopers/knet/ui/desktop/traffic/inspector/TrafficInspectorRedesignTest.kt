package com.devuloopers.knet.ui.desktop.traffic.inspector

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.layout.onSizeChanged
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorSummary
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorSummaryHeader
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorMetric
import com.devuloopers.knet.ui.core.components.scrollbar.KNetHorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.use
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.inspection.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.traffic.model.message.*
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePresentation
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorTone
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.ui.desktop.traffic.model.*
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Truthful protocol presentation and headless rendering without a running proxy, desktop app, or user database. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TrafficInspectorRedesignTest {
    private fun exchange(status: Int = 200, headers: List<HeaderField> = emptyList(),
        state: ExchangeState = ExchangeState.COMPLETED, path: String = "/sessions/v2/track?device=one") = HttpExchangeSnapshot(
        ExchangeId("selected"), connectionId = ConnectionId("connection"), streamId = StreamId(3),
        request = HttpRequestSnapshot(RequestHead(HttpMethod.POST,
            RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("api.example.test", 443), path),
            ApplicationProtocol.fromToken("HTTP/2"), emptyList())),
        response = HttpResponseSnapshot(ResponseHead(ApplicationProtocol.fromToken("HTTP/2"), HttpStatus(status),
            if (status == 101) "Switching Protocols" else "Captured response", headers)),
        state = state, startedAtEpochMillis = 1_000,
        timings = ExchangeTimings(totalMillis = if (state.isTerminal) 120 else null))
    private fun header(name: String, value: String) = HeaderField(HeaderName(name), value)
    private fun prepared(exchange: HttpExchangeSnapshot, annotations: List<InspectionAnnotation> = emptyList()) =
        InspectorPreparedState(exchange.id.value, exchange, annotations = annotations, loadState = InspectorLoadState.READY)
    private fun present(exchange: HttpExchangeSnapshot, kind: RequestKindId = RequestKindId.HTTP,
        annotations: List<InspectionAnnotation> = emptyList(), messages: ProtocolMessagesUiState = ProtocolMessagesUiState()) =
        inspectorPresentation(exchange.toTrafficRowUiState().copy(requestKind = kind), prepared(exchange, annotations), messages)
    private fun annotation(state: InspectionAnnotationState, document: InspectionDocument? = null, id: String = "selected") =
        InspectionAnnotation(ExchangeId(id), InspectorId("graphql"), 1, state, document, createdAtEpochMillis = 1)
    private fun message(protocol: MessageProtocolId = MessageProtocolId.WEBSOCKET, id: String = "message") = ProtocolMessageSnapshot(
        ProtocolMessageId(id), ConnectionId("connection"), ExchangeId("selected"), StreamId(3), protocol,
        if (protocol == MessageProtocolId.SSE) ProtocolMessageKind.RECORD else ProtocolMessageKind.TEXT,
        TrafficDirection.SERVER_TO_CLIENT, 1, 1_100, null, 18, false, null, MessageBodyRef.Empty, ProtocolMessageState.COMPLETE)

    @Test fun `ordinary POST hides skipped failed and title-only semantic candidates`() {
        val value = present(exchange(), annotations = listOf(annotation(InspectionAnnotationState.SKIPPED),
            annotation(InspectionAnnotationState.FAILED), annotation(InspectionAnnotationState.COMPLETED,
                InspectionDocument("graphql", "GraphQL candidate"))))
        assertTrue(value.documents.isEmpty())
        assertTrue(value.analysisUnavailable)
        assertEquals(TrafficInspectorProtocol.HTTP, value.protocol)
        assertEquals("200 Captured response", value.summary.metrics.first().value)
    }

    @Test fun `confirmed protocol fields are inline and cross-exchange documents are excluded`() {
        val doc = InspectionDocument("graphql", "GraphQL", fields = listOf(InspectionField("Operation", "GetAccount")))
        val value = present(exchange(), RequestKindId.GRAPHQL, listOf(annotation(InspectionAnnotationState.COMPLETED, doc),
            annotation(InspectionAnnotationState.COMPLETED, doc.copy(title = "Foreign operation"), "other")))
        assertEquals(listOf(doc), value.documents)
        assertTrue(value.technical.any { it.label == "Connection ID" })
        assertFalse(value.connection.any { it.label == "Connection ID" })
    }

    @Test fun `grpc reports rpc failure separately from successful HTTP transport`() {
        val base = exchange(headers = listOf(header("content-type", "application/grpc+proto")), path = "/demo.Accounts/GetAccount")
        val value = present(base.copy(response = base.response!!.copy(trailers = listOf(header("grpc-status", "7")))))
        assertEquals(TrafficInspectorProtocol.GRPC, value.protocol)
        assertEquals("7 PERMISSION_DENIED", value.summary.metrics[0].value)
        assertEquals(KNetInspectorTone.ERROR, value.summary.metrics[0].tone)
        assertEquals("200 Captured response", value.summary.metrics[1].value)
        assertEquals(listOf("demo.Accounts", "GetAccount"), value.protocolFields.map { it.value })
    }

    @Test fun `missing malformed duplicate or out of range grpc status cannot report success`() {
        listOf(emptyList(), listOf(header("grpc-status", "")), listOf(header("grpc-status", "OK")),
            listOf(header("grpc-status", "17")), listOf(header("grpc-status", "+0")), listOf(header("grpc-status", "-1")),
            listOf(header("grpc-status", "0"), header("grpc-status", "7"))).forEach { trailers ->
            val base = exchange()
            val value = present(base.copy(response = base.response!!.copy(trailers = trailers)), RequestKindId.GRPC)
            assertEquals("Unavailable", value.summary.metrics[0].value)
            assertEquals(KNetInspectorTone.WARNING, value.summary.metrics[0].tone)
        }
    }

    @Test fun `trailers only grpc response is supported and cancellation is not colored successful`() {
        val base = exchange(headers = listOf(header("grpc-status", "0")))
        assertEquals("0 OK", present(base, RequestKindId.GRPC).summary.metrics[0].value)
        val cancelled = base.copy(state = ExchangeState.CANCELLED,
            terminalOutcome = ExchangeTerminalOutcome.Cancelled(TrafficTerminationReason.Transport.DOWNSTREAM_CANCELLED))
        assertEquals(KNetInspectorTone.WARNING, present(cancelled, RequestKindId.GRPC).summary.metrics[0].tone)
    }

    @Test fun `websocket handshake state is distinct from connection lifetime and message count`() {
        val base = exchange(101, listOf(header("upgrade", "websocket")), ExchangeState.RESPONSE_STREAMING)
        val active = present(base, RequestKindId.WEBSOCKET)
        assertEquals("Active", active.summary.metrics[0].value)
        assertEquals("101 Switching Protocols", active.summary.metrics[1].value)
        assertEquals("0", active.summary.metrics[2].value)
        assertTrue(active.protocol.streaming)
        assertEquals("Closed", present(base.copy(state = ExchangeState.COMPLETED,
            terminalOutcome = ExchangeTerminalOutcome.Completed), RequestKindId.WEBSOCKET).summary.metrics[0].value)
        assertEquals("Failed", present(base.copy(state = ExchangeState.FAILED,
            terminalOutcome = ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Unspecified.FAILURE)),
            RequestKindId.WEBSOCKET).summary.metrics[0].value)
    }

    @Test fun `non upgraded websocket handshake remains distinct from closed or failed connections`() {
        for (status in listOf(200, 400, 401, 503)) {
            val value = present(exchange(status), RequestKindId.WEBSOCKET)
            assertEquals("Not upgraded", value.summary.metrics[0].value)
            assertEquals(if (status >= 400) KNetInspectorTone.ERROR else KNetInspectorTone.WARNING, value.summary.metrics[0].tone)
        }
        val failed = exchange(503, state = ExchangeState.FAILED)
        assertEquals("Failed", present(failed, RequestKindId.WEBSOCKET).summary.metrics[0].value)
    }

    @Test fun `accepted graphql websocket protocol establishes semantic mode without payload guessing`() {
        assertEquals(TrafficInspectorProtocol.GRAPHQL_WEBSOCKET, present(exchange(101,
            listOf(header("sec-websocket-protocol", "graphql-transport-ws")))).protocol)
        assertEquals(TrafficInspectorProtocol.HTTP, present(exchange(headers =
            listOf(header("sec-websocket-protocol", "graphql-transport-ws")))).protocol)
    }

    @Test fun `SSE uses event workspace but counts records and retains true stream lifecycle`() {
        val value = present(exchange(headers = listOf(header("content-type", "text/event-stream; charset=utf-8")),
            state = ExchangeState.RESPONSE_STREAMING), messages = ProtocolMessagesUiState(exchangeId = "selected", totalCount = 5))
        assertEquals(TrafficInspectorProtocol.SSE, value.protocol)
        assertEquals("Events", value.messagesTitle)
        assertEquals("Streaming", value.summary.metrics[0].value)
        assertEquals("Records", value.summary.metrics[2].label)
        assertEquals("5", value.summary.metrics[2].value)
    }

    @Test fun `stale page and stale selected payload cannot leak into another exchange`() {
        val stale = ProtocolMessagesUiState("other", listOf(message()), totalCount = 9,
            selectedMessageId = ProtocolMessageId("message"), selectedBodyBytes = "secret".encodeToByteArray(),
            selectedBodyPresentation = ProtocolMessagePresentation("Foreign operation", "text/plain", "secret"))
        assertNull(stale.forSelection("selected").selectedBodyBytes)
        assertEquals(TrafficInspectorProtocol.HTTP, present(exchange(), messages = stale).protocol)
        assertEquals(0, stale.copy(exchangeId = "selected", items = listOf(message().copy(exchangeId = ExchangeId("other"))))
            .forSelection("selected").items.size)
    }

    @Test fun `all application protocols render with common header and no analyzer footer`() = EventQueue.invokeAndWait {
        val cases = listOf(
            Triple("http", exchange(), RequestKindId.HTTP),
            Triple("http2", exchange(headers = listOf(header("content-type", "application/json"))), RequestKindId.HTTP),
            Triple("graphql", exchange(path = "/graphql"), RequestKindId.GRAPHQL),
            Triple("grpc", exchange(headers = listOf(header("grpc-status", "0")), path = "/demo.Stream/Watch"), RequestKindId.GRPC),
            Triple("websocket", exchange(101, state = ExchangeState.RESPONSE_STREAMING), RequestKindId.WEBSOCKET),
            Triple("graphql-ws", exchange(101, listOf(header("sec-websocket-protocol", "graphql-transport-ws")),
                ExchangeState.RESPONSE_STREAMING), RequestKindId.GRAPHQL_WEBSOCKET),
            Triple("sse", exchange(headers = listOf(header("content-type", "text/event-stream")), state = ExchangeState.RESPONSE_STREAMING), RequestKindId.HTTP))
        cases.forEach { (name, exchange, kind) ->
            val row = exchange.toTrafficRowUiState().copy(requestKind = kind)
            val annotations = if (name == "graphql") listOf(annotation(InspectionAnnotationState.COMPLETED,
                InspectionDocument("graphql", "GraphQL query", fields = listOf(InspectionField("Operation", "GetAccount"),
                    InspectionField("Type", "query"))))) else emptyList()
            scene(row, prepared(exchange, annotations), InspectorTab.OVERVIEW).use { scene ->
                scene.frame()
                assertTrue(scene.texts().any { it.startsWith("api.example.test") })
                assertTrue(scene.texts().contains("Connection"))
                assertFalse(scene.texts().any { it == "Semantic inspection" || it == "Connection ID" })
                val tab = if (name == "sse") "Events" else "Messages (0)"
                if (name in listOf("grpc", "websocket", "graphql-ws", "sse")) assertTrue(tab in scene.texts())
                scene.save(name)
            }
        }
    }

    @Test fun `messages and SSE records render selected metadata binary fallback and unavailable previews`() = EventQueue.invokeAndWait {
        for (sse in listOf(false, true)) {
            val exchange = if (sse) exchange(headers = listOf(header("content-type", "text/event-stream")), state = ExchangeState.RESPONSE_STREAMING)
                else exchange(101, state = ExchangeState.RESPONSE_STREAMING)
            val row = exchange.toTrafficRowUiState().copy(requestKind = if (sse) RequestKindId.HTTP else RequestKindId.WEBSOCKET)
            val msg = message(if (sse) MessageProtocolId.SSE else MessageProtocolId.WEBSOCKET)
                .let { if (sse) it else it.copy(kind = ProtocolMessageKind.BINARY) }
            val state = ProtocolMessagesUiState("selected", listOf(msg), 1, selectedMessageId = msg.id,
                selectedBodyBytes = byteArrayOf(0, -1, 10), selectedBodyTruncated = true)
            scene(row, prepared(exchange), InspectorTab.MESSAGES, state).use { scene ->
                scene.frame(); assertTrue("Received" in scene.texts())
                assertTrue(scene.texts().any { it.startsWith("Preview truncated") })
                scene.save(if (sse) "events" else "binary-message")
            }
            scene(row, prepared(exchange), InspectorTab.MESSAGES, state.copy(selectedBodyBytes = null,
                selectedBodyUnavailable = true)).use { scene ->
                scene.frame(); assertTrue("Message Body Unavailable" in scene.texts())
            }
        }
    }

    @Test fun `narrow inspector larger fonts and changing selected item do not retain technical expansion`() = EventQueue.invokeAndWait {
        val exchange = exchange()
        val selected = mutableStateOf(exchange.toTrafficRowUiState())
        ImageComposeScene(300, 740, Density(1f, 1.4f), content = { KNetTheme {
            TrafficInspectorPanel(selected.value, InspectorTab.OVERVIEW, preparedState = prepared(exchange), onTabSelected = {})
        }}).use { scene ->
            scene.frame()
            val node = scene.nodes().first { it.text() == "Technical details" && it.config.getOrNull(SemanticsActions.OnClick) != null }
            node.config[SemanticsActions.OnClick].action!!.invoke(); scene.frame()
            assertTrue("Connection ID" in scene.texts())
            selected.value = exchange.copy(id = ExchangeId("different")).toTrafficRowUiState()
            scene.frame(); assertFalse("Connection ID" in scene.texts())
            scene.save("narrow-large-font")
        }
    }

    @Test fun `request response and timeline retain navigation and export with one endpoint header`() = EventQueue.invokeAndWait {
        val exchange = exchange()
        val row = exchange.toTrafficRowUiState()
        var exported: String? = null
        for (tab in listOf(InspectorTab.REQUEST, InspectorTab.RESPONSE, InspectorTab.TIMELINE)) {
            ImageComposeScene(680, 900, content = { KNetTheme {
                TrafficInspectorPanel(row, tab, preparedState = prepared(exchange).copy(requestBodyTruncated = true,
                    responseBodyTruncated = true), onTabSelected = {}, onSendToApiStudio = { exported = it })
            }}).use { scene ->
                scene.frame()
                assertEquals(1, scene.texts().count { it.startsWith("api.example.test") })
                if (tab == InspectorTab.REQUEST) {
                    val export = scene.nodes().first { it.text() == "Open in API Studio" && it.config.getOrNull(SemanticsActions.OnClick) != null }
                    export.config[SemanticsActions.OnClick].action!!.invoke(); assertEquals(row.transactionId, exported)
                    assertTrue(scene.texts().any { it.startsWith("Request body preview truncated") })
                }
                if (tab == InspectorTab.RESPONSE) assertTrue(scene.texts().any { it.startsWith("Response body preview truncated") })
                scene.save(tab.name.lowercase())
            }
        }
    }

    @Test fun `protocol owned selected fields keep usable body space without a fixed semantic footer`() = EventQueue.invokeAndWait {
        val cases = listOf(
            Triple("graphql-operation", RequestKindId.GRAPHQL_WEBSOCKET, listOf(InspectionField("Message", "next"),
                InspectionField("Operation ID", "prices-1"), InspectionField("Operation", "LivePrices"), InspectionField("Type", "subscription"))),
            Triple("grpc-message", RequestKindId.GRPC, listOf(InspectionField("Service", "demo.Accounts"),
                InspectionField("Method", "Watch"), InspectionField("Message type", "demo.AccountUpdate"))),
            Triple("event-record", RequestKindId.HTTP, listOf(InspectionField("Record", "Event"),
                InspectionField("Event type", "update"), InspectionField("Record ID", "42"), InspectionField("Retry", "3000 ms"))))
        for ((name, kind, fields) in cases) {
            val sse = name == "event-record"
            val grpc = kind == RequestKindId.GRPC
            val exchange = exchange(if (grpc || sse) 200 else 101,
                if (sse) listOf(header("content-type", "text/event-stream")) else emptyList(), ExchangeState.RESPONSE_STREAMING)
            val msg = message(if (sse) MessageProtocolId.SSE else if (grpc) MessageProtocolId.GRPC else MessageProtocolId.WEBSOCKET)
            val state = ProtocolMessagesUiState("selected", listOf(msg), 1, selectedMessageId = msg.id,
                selectedBodyBytes = "{\"price\":42}".encodeToByteArray(), selectedBodyPresentation =
                    ProtocolMessagePresentation("Captured $name", "application/json", "{\"price\":42}", fields = fields))
            scene(exchange.toTrafficRowUiState().copy(requestKind = kind), prepared(exchange), InspectorTab.MESSAGES, state).use { scene ->
                scene.frame(); assertTrue("Captured $name" in scene.texts())
                assertTrue(fields.all { field -> field.label in scene.texts() })
                assertFalse(scene.texts().contains("Semantic inspection"))
                scene.save(name)
            }
        }
    }

    @Test fun `switching overview rows updates content without replacing the viewport nodes`() = EventQueue.invokeAndWait {
        val selected = mutableStateOf(exchange())
        ImageComposeScene(680, 900, content = { KNetTheme {
            TrafficInspectorPanel(selected.value.toTrafficRowUiState(), InspectorTab.OVERVIEW,
                preparedState = prepared(selected.value), onTabSelected = {})
        }}).use { scene ->
            scene.frame()
            val connectionNode = scene.nodes().single { it.text() == "Connection" }.id
            selected.value = exchange(path = "/another").copy(id = ExchangeId("another"))
            scene.frame()
            assertEquals(connectionNode, scene.nodes().single { it.text() == "Connection" }.id,
                "Changing the selected capture must update the existing inspector composition.")
        }
    }

    @Test fun `new capture resets scrolling without replacing the inspector viewport`() = EventQueue.invokeAndWait {
        val selected = mutableStateOf(exchange())
        val doc = InspectionDocument("protocol", "Captured fields", fields = List(100) { InspectionField("Field $it", "Value $it") })
        ImageComposeScene(680, 600, content = { KNetTheme {
            val value = selected.value
            TrafficInspectorPanel(value.toTrafficRowUiState(), InspectorTab.OVERVIEW,
                preparedState = prepared(value, listOf(annotation(InspectionAnnotationState.COMPLETED, doc, value.id.value))),
                onTabSelected = {})
        }}).use { scene ->
            scene.frame()
            val scroll = scene.nodes().first { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke()?.let { it > 0 } == true }
            assertTrue(scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 500f))
            scene.frame()
            assertTrue(scene.nodes().first { it.id == scroll.id }.config[SemanticsProperties.VerticalScrollAxisRange].value() > 0)
            selected.value = exchange().copy(id = ExchangeId("next")); scene.frame()
            val next = scene.nodes().first { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke()?.let { it > 0 } == true }
            assertEquals(scroll.id, next.id)
            assertEquals(0f, next.config[SemanticsProperties.VerticalScrollAxisRange].value())
        }
    }

    @Test fun `changing a selected message keeps metadata mounted and replaces its captured values`() = EventQueue.invokeAndWait {
        val one = message(id = "one")
        val two = message(id = "two")
        val state = mutableStateOf(ProtocolMessagesUiState("selected", listOf(one, two), 2, selectedMessageId = one.id,
            selectedBodyPresentation = ProtocolMessagePresentation("First message", "text/plain", "first")))
        ImageComposeScene(680, 800, content = { KNetTheme { ProtocolMessagesPanel(state.value, {}, {}) } }).use { scene ->
            scene.frame()
            val direction = scene.nodes().single { it.text() == "Direction" }.id
            state.value = state.value.copy(selectedMessageId = two.id,
                selectedBodyPresentation = ProtocolMessagePresentation("Second message", "text/plain", "second"))
            scene.frame()
            assertEquals(direction, scene.nodes().single { it.text() == "Direction" }.id)
            assertFalse("First message" in scene.texts())
            assertTrue("Second message" in scene.texts())
        }
    }

    @Test fun `unmeasured horizontal scrollbar must not reserve phantom height`() = EventQueue.invokeAndWait {
        var height = 0
        ImageComposeScene(680, 300, content = { KNetTheme {
            KNetHorizontalScrollbar(ScrollState(0), Modifier.onSizeChanged { height = it.height })
        }}).use { scene ->
            scene.frame()
            assertEquals(0, height, "An unmeasured scroll state has no proven overflow and must not add a scrollbar.")
        }
    }

    @Test fun `summary height stays stable while switching URL on wide and narrow viewports`() = EventQueue.invokeAndWait {
        val steadyHeights = mutableMapOf<Int, Int>()
        for (width in listOf(680, 300)) {
            val value = mutableStateOf("https://example.test/first")
            val heights = mutableListOf<Int>()
            ImageComposeScene(width, 300, content = { KNetTheme {
                KNetInspectorSummaryHeader(KNetInspectorSummary("GET", "example.test", value.value, value.value,
                    listOf(KNetInspectorMetric("Status", "200 OK"))), Modifier.onSizeChanged { heights += it.height })
            }}).use { scene ->
                scene.frame()
                val steady = heights.last()
                steadyHeights[width] = steady
                heights.clear()
                value.value = "https://example.test/second"
                scene.frame()
                assertTrue(heights.all { it == steady }, "Header height pulsed at width $width: $steady -> $heights")
            }
        }
        assertTrue(steadyHeights.getValue(300) > steadyHeights.getValue(680),
            "Real narrow-pane overflow must retain its visible scrollbar.")
    }

    @Test fun `selection keeps navigation at the same position on every rendered frame`() = EventQueue.invokeAndWait {
        for (width in listOf(680, 300)) {
            val value = mutableStateOf(exchange())
            ImageComposeScene(width, 900, content = { KNetTheme {
                TrafficInspectorPanel(value.value.toTrafficRowUiState(), InspectorTab.OVERVIEW,
                    preparedState = prepared(value.value), onTabSelected = {})
            }}).use { scene ->
                scene.frame()
                val top = scene.nodes().first { it.text() == "Overview" }.boundsInWindow.top
                value.value = exchange(path = "/second").copy(id = ExchangeId("second"))
                repeat(4) { frame ->
                    scene.render(++nanos * 1_000_000_000).close()
                    assertEquals(top, scene.nodes().first { it.text() == "Overview" }.boundsInWindow.top,
                        "Navigation jumped at width $width, selection frame $frame")
                }
            }
        }
    }

    private fun scene(row: TrafficRowUiState, prepared: InspectorPreparedState, tab: InspectorTab,
        messages: ProtocolMessagesUiState = ProtocolMessagesUiState()) = ImageComposeScene(680, 900, content = { KNetTheme {
        TrafficInspectorPanel(row, tab, preparedState = prepared, protocolMessages = messages, onTabSelected = {})
    }})
    private var nanos = 0L
    private fun ImageComposeScene.frame() { repeat(3) { render(++nanos * 1_000_000_000).close() } }
    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { owner ->
        fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
        descend(owner.rootSemanticsNode)
    }
    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun ImageComposeScene.texts() = nodes().map { it.text() }
    private fun ImageComposeScene.save(name: String) {
        val output = File("/tmp/knet-inspector-redesign-renders").apply { mkdirs() }
        render(++nanos * 1_000_000_000).use { image -> image.encodeToData()?.use { File(output, "$name.png").writeBytes(it.bytes) } }
    }
}
