package com.devuloopers.knet.ui.desktop.traffic.inspector

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.use
import com.devuloopers.knet.application.contract.inspection.GraphQLInspectionMetadata
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.inspection.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.components.GraphQLBodySubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.*
import com.devuloopers.knet.ui.desktop.traffic.model.*
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Overview stays metadata-only and query navigation targets the selected request's existing body viewer. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class GraphQLQueryShortcutTest {
    private val query = "query FormattedQuotes { quotes { symbol } }"
    private val path = "/graphql?operationName=FormattedQuotes"
    private val payload = """{"operationName":"FormattedQuotes","query":"$query","variables":{"symbol":"AAPL"},"extensions":{"client":"test"}}"""
    private fun exchange() = HttpExchangeSnapshot(ExchangeId("selected"),
        request = HttpRequestSnapshot(RequestHead(HttpMethod.POST,
            RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("example.test", 443), path),
            ApplicationProtocol.fromToken("HTTP/2"), listOf(HeaderField(HeaderName("content-type"), "application/json")))),
        response = HttpResponseSnapshot(ResponseHead(ApplicationProtocol.fromToken("HTTP/2"), HttpStatus(200), "OK",
            listOf(HeaderField(HeaderName("content-type"), "application/json")))),
        state = ExchangeState.COMPLETED, startedAtEpochMillis = 1)
    private fun document(kind: String = GraphQLInspectionMetadata.DOCUMENT_KIND) = InspectionDocument(kind,
        "GraphQL Query: FormattedQuotes", summary = query, fields = listOf(
            InspectionField("Operation type", "Query"), InspectionField("Operation name", "FormattedQuotes"),
            InspectionField(GraphQLInspectionMetadata.REQUEST_TARGET_FIELD, path)))
    private fun prepared(document: InspectionDocument = document()) = InspectorPreparedState("selected", exchange(),
        requestPayloadSpec = PayloadInspectionSpec.fromPayload(listOf("content-type" to "application/json"), payload),
        annotations = listOf(InspectionAnnotation(ExchangeId("selected"), InspectorId("graphql"), 1,
            InspectionAnnotationState.COMPLETED, document, createdAtEpochMillis = 1)), loadState = InspectorLoadState.READY)
    private fun row() = exchange().toTrafficRowUiState().copy(requestKind = RequestKindId.GRAPHQL)
    private fun present(state: InspectorPreparedState) = inspectorPresentation(row(), state, ProtocolMessagesUiState())

    @Test fun `overview drops duplicated query and target while preserving original annotations and operation fields`() {
        val source = prepared()
        val overview = present(source)
        assertEquals(GraphQLQueryAvailability.AVAILABLE, overview.graphQLQueryAvailability)
        val display = overview.documents.single()
        assertNull(display.summary)
        assertEquals(listOf("Operation type", "Operation name"), display.fields.map { it.label })
        assertEquals(query, source.annotations.single().document!!.summary)
        assertTrue(overview.summary.copyValue.endsWith(path))
    }

    @Test fun `other annotation kinds retain their summary and request target`() {
        val original = document("custom-protocol")
        val display = inspectorPresentation(exchange().toTrafficRowUiState(), prepared(original), ProtocolMessagesUiState())
        assertEquals(original, display.documents.single())
        assertNull(display.graphQLQueryAvailability)
    }

    @Test fun `query availability distinguishes preparation missing payload and foreign identity`() {
        assertEquals(GraphQLQueryAvailability.PREPARING,
            present(InspectorPreparedState.loading("selected")).graphQLQueryAvailability)
        assertEquals(GraphQLQueryAvailability.UNAVAILABLE,
            present(prepared().copy(requestPayloadSpec = PayloadInspectionSpec.EMPTY)).graphQLQueryAvailability)
        assertEquals(GraphQLQueryAvailability.UNAVAILABLE,
            present(prepared().copy(transactionId = "other")).graphQLQueryAvailability)
        assertEquals(GraphQLQueryAvailability.UNAVAILABLE,
            present(prepared().copy(exchange = exchange().copy(id = ExchangeId("other")))).graphQLQueryAvailability)
    }

    @Test fun `truncated body retains its warning and raw graphql query can use the same viewer`() {
        val truncated = prepared().copy(requestBodyTruncated = true)
        assertEquals(GraphQLQueryAvailability.AVAILABLE, present(truncated).graphQLQueryAvailability)
        val raw = prepared().copy(requestPayloadSpec = PayloadInspectionSpec.fromPayload(
            listOf("content-type" to "application/graphql"), query))
        assertEquals(GraphQLQueryAvailability.AVAILABLE, present(raw).graphQLQueryAvailability)
        val batch = prepared().copy(requestPayloadSpec = PayloadInspectionSpec.fromPayload(
            listOf("content-type" to "application/json"), "[$payload]"))
        assertEquals(GraphQLQueryAvailability.UNAVAILABLE, present(batch).graphQLQueryAvailability,
            "A raw batch must not claim it can open a single-operation Query viewer.")
    }

    @Test fun `view query selects Request Body Query after other request and graphql sub tabs`() = EventQueue.invokeAndWait {
        for ((prior, requestTab) in listOf(GraphQLBodySubTab.VARIABLES to InspectorSubTab.HEADERS,
            GraphQLBodySubTab.EXTENSIONS to InspectorSubTab.COOKIES, GraphQLBodySubTab.RAW_JSON to InspectorSubTab.PARAMS)) {
            val main = mutableStateOf(InspectorTab.REQUEST)
            val request = mutableStateOf(InspectorSubTab.BODY)
            ImageComposeScene(680, 900, content = { KNetTheme {
                TrafficInspectorPanel(row(), main.value, activeRequestSubTab = request.value, preparedState = prepared().copy(requestBodyTruncated = true),
                    onTabSelected = { main.value = it }, onRequestSubTabSelected = { request.value = it })
            }}).use { scene ->
                scene.frame(); scene.click(prior.label); scene.frame()
                assertTrue(scene.tab(prior.label).config[SemanticsProperties.Selected])
                main.value = InspectorTab.OVERVIEW; request.value = requestTab; scene.frame()
                assertFalse(query in scene.texts())
                assertFalse(GraphQLInspectionMetadata.REQUEST_TARGET_FIELD in scene.texts())
                assertTrue("Operation name" in scene.texts())
                scene.save()
                scene.click("View query"); scene.frame()
                assertEquals(InspectorTab.REQUEST, main.value)
                assertEquals(InspectorSubTab.BODY, request.value)
                assertTrue(scene.tab("Query").config[SemanticsProperties.Selected])
                assertFalse(scene.tab(prior.label).config[SemanticsProperties.Selected])
                assertTrue(scene.texts().any { it.startsWith("Request body preview truncated") })
            }
        }
    }

    @Test fun `unavailable query has a disabled shortcut and accessible explanation`() = EventQueue.invokeAndWait {
        for (state in listOf(prepared().copy(requestPayloadSpec = PayloadInspectionSpec.EMPTY), InspectorPreparedState.loading("selected"))) {
            var opened = false
            ImageComposeScene(680, 900, content = { KNetTheme {
                TrafficOverviewPanel("selected", present(state), onViewQuery = { opened = true })
            }}).use { scene ->
                scene.frame()
                val button = scene.nodes().first { it.text() == "View query" && it.config.getOrNull(SemanticsActions.OnClick) != null }
                assertNotNull(button.config.getOrNull(SemanticsProperties.Disabled))
                assertEquals(present(state).graphQLQueryAvailability!!.description, button.config[SemanticsProperties.StateDescription])
                button.config[SemanticsActions.OnClick].action?.invoke()
                assertFalse(opened)
            }
        }
    }

    @Test fun `GET shortcut opens Params Query and raw parameters without creating a body`() = EventQueue.invokeAndWait {
        val target = "/startup?query=query+BootStrapQuery+%7B+bootstrap+%7B+id+%7D+%7D&variables=%7B%22device%22%3A%22ANDROID%22%7D"
        val source = exchange().let { it.copy(request = it.request.copy(head = it.request.head.copy(method = HttpMethod.GET,
            target = RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("example.test", 443), target)))) }
        val value = prepared().copy(exchange = source, requestPayloadSpec = PayloadInspectionSpec.EMPTY,
            requestParameterSpec = PayloadInspectionSpec.fromGraphQLParameters("GET", target))
        for (width in listOf(680, 300)) {
            val main = mutableStateOf(InspectorTab.OVERVIEW)
            val request = mutableStateOf(InspectorSubTab.HEADERS)
            ImageComposeScene(width, 900, content = { KNetTheme {
                TrafficInspectorPanel(source.toTrafficRowUiState().copy(requestKind = RequestKindId.GRAPHQL), main.value,
                    activeRequestSubTab = request.value, preparedState = value,
                    onTabSelected = { main.value = it }, onRequestSubTabSelected = { request.value = it })
            }}).use { scene ->
                scene.frame(); scene.click("View query"); scene.frame()
                assertEquals(InspectorSubTab.PARAMS, request.value)
                assertTrue(scene.tab("Query").config[SemanticsProperties.Selected])
                assertTrue("Source: URL parameters" in scene.texts())
                assertFalse("Source: JSON body" in scene.texts())
                scene.click("Variables"); scene.frame()
                assertTrue(scene.tab("Variables").config[SemanticsProperties.Selected])
                scene.click("Raw parameters"); scene.frame()
                assertTrue("query" in scene.texts())
                main.value = InspectorTab.OVERVIEW; scene.frame(); scene.click("View query"); scene.frame()
                assertTrue(scene.tab("Query").config[SemanticsProperties.Selected])
                val nav = scene.tab("Request").boundsInRoot.top
                repeat(4) { scene.render(++nanos * 1_000_000_000).close(); assertEquals(nav, scene.tab("Request").boundsInRoot.top) }
                scene.render(++nanos * 1_000_000_000).use { image -> image.encodeToData()?.use {
                    File("/tmp/knet-graphql-get-viewer-$width.png").writeBytes(it.bytes)
                }}
            }
        }
        assertTrue(value.requestPayloadSpec.isEmpty)
        assertTrue(value.estimatedRetainedBytes > value.copy(requestParameterSpec = PayloadInspectionSpec.EMPTY).estimatedRetainedBytes)
    }

    @Test fun `GET parameter availability is isolated from a foreign selected exchange`() {
        val target = "/graphql?query=%7B+viewer+%7B+id+%7D+%7D"
        val source = prepared().copy(requestPayloadSpec = PayloadInspectionSpec.EMPTY,
            requestParameterSpec = PayloadInspectionSpec.fromGraphQLParameters("GET", target))
        assertEquals(GraphQLQueryAvailability.UNAVAILABLE, present(source.copy(transactionId = "foreign")).graphQLQueryAvailability)
    }

    @Test fun `new annotation schema supersedes legacy operation facts without duplicate overview sections`() {
        val source = prepared()
        val legacy = source.annotations.single().copy(schemaVersion = 1, document = document().copy(title = "Legacy facts"))
        val current = legacy.copy(schemaVersion = 2, document = document().copy(title = "Current facts"))
        val shown = present(source.copy(annotations = listOf(legacy, current)))
        assertEquals(listOf("Current facts"), shown.documents.map { it.title })
        assertEquals(2, source.copy(annotations = listOf(legacy, current)).annotations.size)
    }

    private var nanos = 0L
    private fun ImageComposeScene.frame() { repeat(3) { render(++nanos * 1_000_000_000).close() } }
    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { owner ->
        fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
        descend(owner.rootSemanticsNode)
    }
    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun ImageComposeScene.texts() = nodes().map { it.text() }
    private fun ImageComposeScene.tab(label: String) = nodes().first { it.text() == label && it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
    private fun ImageComposeScene.click(label: String) {
        nodes().first { it.text() == label && it.config.getOrNull(SemanticsActions.OnClick) != null }
            .config[SemanticsActions.OnClick].action!!.invoke()
    }
    private fun ImageComposeScene.save() {
        render(++nanos * 1_000_000_000).use { image -> image.encodeToData()?.use {
            File("/tmp/knet-graphql-query-shortcut-overview.png").writeBytes(it.bytes)
        }}
    }
}
