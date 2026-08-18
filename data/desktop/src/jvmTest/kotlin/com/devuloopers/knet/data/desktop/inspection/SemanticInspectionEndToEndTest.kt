package com.devuloopers.knet.data.desktop.inspection

import com.devuloopers.knet.application.port.inspection.SemanticInspectionScheduler
import com.devuloopers.knet.application.port.traffic.RecordHttpExchangeCommand
import com.devuloopers.knet.application.port.traffic.TrafficBodyPayload
import com.devuloopers.knet.data.desktop.capture.CanonicalCaptureSessionFactory
import com.devuloopers.knet.data.desktop.capture.CanonicalTrafficQueryAdapter
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLSemanticInspector
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.inspection.InspectionAnnotationState
import com.devuloopers.knet.traffic.model.ExchangeState
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
        val session = CanonicalCaptureSessionFactory(database, bodyStore, bodyStore).openDirect(1L)
        val exchangeId = ExchangeId("semantic-e2e-exchange")
        try {
            session.recordCanonical(
                RecordHttpExchangeCommand(
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
                    requestBody = TrafficBodyPayload(
                        """{"operationName":"Products","query":"query Products { products { id } }"}"""
                            .encodeToByteArray(),
                    ),
                    response = ResponseHead(
                        protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                        status = HttpStatus(200),
                        reasonPhrase = "OK",
                        headers = emptyList(),
                    ),
                    responseBody = null,
                    state = ExchangeState.COMPLETED,
                    startedAtEpochMillis = 10L,
                    completedAtEpochMillis = 20L,
                ),
            )
            session.flush()

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
}
