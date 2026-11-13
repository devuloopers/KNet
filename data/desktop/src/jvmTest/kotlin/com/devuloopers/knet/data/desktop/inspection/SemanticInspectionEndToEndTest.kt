package com.devuloopers.knet.data.desktop.inspection

import com.devuloopers.knet.application.coordinator.inspection.SemanticInspectionScheduler
import com.devuloopers.knet.data.desktop.capture.CanonicalCaptureSessionFactory
import com.devuloopers.knet.data.desktop.capture.CanonicalTrafficQueryAdapter
import com.devuloopers.knet.data.desktop.capture.recordTestProxyExchange
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLSemanticInspector
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.inspection.InspectionAnnotationState
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Evidence that capture, bounded body reads, inspection, Room persistence, and observation compose. */
class SemanticInspectionEndToEndTest {
    @Test
    fun `captured GraphQL body becomes a durable generic annotation`() = runTest {
        val root = Files.createTempDirectory("knet-semantic-e2e-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(root.resolve("bodies"))
        val session = CanonicalCaptureSessionFactory(database, bodyStore, bodyStore)
            .openStreamingProxy(localListenerPort = 8_080, startedAtEpochMillis = 1L)
        val exchangeId = ExchangeId("semantic-e2e-exchange")
        try {
            session.recordTestProxyExchange(
                exchangeId = exchangeId,
                request = RequestHead(
                    method = HttpMethod.fromToken("POST"),
                    target = RequestTarget.Absolute(
                        HttpScheme.fromToken("https"),
                        Authority("api.example.test"),
                        "/graphql",
                    ),
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    headers = listOf(HeaderField(HeaderName("Content-Type"), "application/json")),
                ),
                requestBody = """{"operationName":"Products","query":"query Products { products { id } }"}"""
                    .encodeToByteArray(),
                response = ResponseHead(
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    status = HttpStatus(200),
                    reasonPhrase = "OK",
                    headers = emptyList(),
                ),
                outcome = ExchangeTerminalOutcome.Completed,
                startedAtEpochMillis = 10L,
                completedAtEpochMillis = 20L,
            )

            val query = CanonicalTrafficQueryAdapter(session.sessionId, database.canonicalCaptureDao(), bodyStore)
            val annotations = RoomInspectionAnnotationAdapter(database.canonicalCaptureDao())
            val scheduler = SemanticInspectionScheduler(query, annotations, listOf(GraphQLSemanticInspector()))

            scheduler.inspect(session.sessionId, exchangeId, 30L)

            val observed = annotations.observe(exchangeId).first { it.isNotEmpty() }.single()
            assertEquals(InspectionAnnotationState.COMPLETED, observed.state)
            assertEquals("graphql", observed.document?.kind)
            assertEquals("GraphQL Query: Products", observed.document?.title)
        } finally {
            session.close()
            database.close()
            root.deleteRecursively()
        }
    }
    @Test
    fun `APQ fallback learns only verified captured source while Room retains untouched hash only requests`() = runTest {
        val root = Files.createTempDirectory("knet-persisted-query-e2e-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(root.resolve("bodies"))
        val session = CanonicalCaptureSessionFactory(database, bodyStore, bodyStore)
            .openStreamingProxy(localListenerPort = 8_080, startedAtEpochMillis = 1L)
        val catalog = com.devuloopers.knet.engine.protocol.inspector.graphql.BoundedGraphQLPersistedQueryCatalog()
        val parser = com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLDocumentParser(
            com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLPersistedQueryResolver(catalog))
        val source = "query ProductRead { products { id } }"
        val hash = com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLQueryHashing.sha256(source).value
        val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"$hash"}}"""
        val hashOnly = "{\"extensions\":$extensions,\"variables\":{\"id\":7}}"
        val full = "{\"query\":\"$source\",\"extensions\":$extensions}"
        val query = CanonicalTrafficQueryAdapter(session.sessionId, database.canonicalCaptureDao(), bodyStore)
        val annotations = RoomInspectionAnnotationAdapter(database.canonicalCaptureDao())
        val scheduler = SemanticInspectionScheduler(query, annotations, listOf(GraphQLSemanticInspector(parser)))
        try {
            for ((index, body) in listOf(hashOnly, full, hashOnly).withIndex()) {
                val id = ExchangeId("apq-$index")
                session.recordTestProxyExchange(exchangeId = id,
                    request = RequestHead(HttpMethod.POST, RequestTarget.Absolute(HttpScheme.fromToken("https"),
                        Authority("api.persisted.test", 443), "/graphql"), ApplicationProtocol.fromToken("HTTP/1.1"),
                        listOf(HeaderField(HeaderName("Content-Type"), "application/json"))),
                    requestBody = body.encodeToByteArray(),
                    response = ResponseHead(ApplicationProtocol.fromToken("HTTP/1.1"), HttpStatus(200), headers = emptyList()),
                    outcome = ExchangeTerminalOutcome.Completed, startedAtEpochMillis = index * 100L + 1,
                    completedAtEpochMillis = index * 100L + 20)
                scheduler.inspect(session.sessionId, id, index * 100L + 30)
                val observed = annotations.observe(id).first { it.isNotEmpty() }.single()
                assertEquals(InspectionAnnotationState.COMPLETED, observed.state)
                val fields = observed.document!!.fields.associate { it.label to it.value }
                assertEquals(hash, fields["Persisted query SHA-256"])
                assertEquals(listOf("Query source unavailable", "Request", "Captured traffic")[index], fields["Query document source"])
                assertEquals(if (index == 0) null else "ProductRead", fields["Operation name"])
                val request = query.getExchange(id)!!.request
                val bodyId = (request.body as com.devuloopers.knet.traffic.model.body.MessageBodyRef.Available).body.id
                assertEquals(body, query.readBody(bodyId, com.devuloopers.knet.application.contract.traffic.BodyRange(0, 1_048_576)).copyBytes().decodeToString())
            }
        } finally {
            session.close(); database.close(); root.deleteRecursively(); catalog.clear()
        }
    }

}
