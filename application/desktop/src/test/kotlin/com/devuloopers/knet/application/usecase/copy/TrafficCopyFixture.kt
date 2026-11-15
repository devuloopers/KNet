package com.devuloopers.knet.application.usecase.copy

import com.devuloopers.knet.application.contract.export.CaptureExportSource
import com.devuloopers.knet.application.contract.inspection.InspectionAnnotationStore
import com.devuloopers.knet.application.contract.pairing.RegisteredDeviceStore
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.application.usecase.traffic.CopyTrafficDetailsUseCase
import com.devuloopers.knet.domain.graphql.GraphQLHttpDocument
import com.devuloopers.knet.identity.*
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.inspection.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.traffic.model.message.*
import kotlinx.coroutines.flow.*

/** Deterministic application ports; read ordering and leases are observable without opening sockets. */
internal class TrafficCopyFixture {
    val rows = mutableMapOf<ExchangeId, HttpExchangeSnapshot>()
    val flows = mutableMapOf<OpaqueFlowId, OpaqueFlowSnapshot>()
    val payloads = mutableMapOf<BodyId, ByteArray>()
    val reads = mutableListOf<BodyRange>()
    val readIds = mutableListOf<BodyId>()
    var messageQueries = 0
    val documents = mutableMapOf<ExchangeId, List<InspectionAnnotation>>()
    var framed = ProtocolMessagePage(emptyList(), null, 0)
    var leases = 0; var released = 0; var bodyFailure = false
    var ingress: TrafficConnectionIngress? = null
    var beforeRead: suspend () -> Unit = {}
    var graphQL: GraphQLHttpDocument? = null
    var maximumBody = 0
    val traffic = object : TrafficQuery {
        override val generations = emptyFlow<TrafficGeneration>()
        override suspend fun query(query: TrafficPageQuery): TrafficPage = error("Copy must never expand selection into a query")
        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? { beforeRead(); return rows[exchangeId] }
        override suspend fun getOpaqueFlow(flowId: OpaqueFlowId) = flows[flowId]
        override suspend fun getConnectionIngress(connectionId: ConnectionId) = ingress
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk = error("Use the pinned body store")
    }
    val bodies = object : BodyStore {
        override fun retainExportReads(): AutoCloseable { leases++; return AutoCloseable { released++ } }
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk {
            check(leases > released); reads += range; readIds += bodyId; maximumBody = maxOf(maximumBody, range.length)
            if (bodyFailure) error("deleted body")
            val bytes = payloads.getValue(bodyId); val end = minOf(bytes.size, range.length)
            return BodyChunk(bytes.copyOfRange(0, end), 0, end == bytes.size)
        }
        override suspend fun openWrite(bodyId: BodyId, policy: BodyWritePolicy, contentEncoding: ContentEncoding?): BodyWriteSession = error("Read only")
        override suspend fun delete(bodyId: BodyId): BodyDeleteResult = error("Read only")
        override suspend fun reconcileTemporaryObjects(): Int = error("Read only")
    }
    val messages = object : ProtocolMessageQuery {
        override fun observeChanges(exchangeId: ExchangeId) = emptyFlow<Long>()
        override suspend fun queryMessages(query: ProtocolMessagePageQuery): ProtocolMessagePage { check(query.limit <= 32); messageQueries++; return framed }
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk = error("Use pinned store")
    }
    val devices = object : RegisteredDeviceStore {
        override suspend fun putRegisteredDevice(device: RegisteredDevice) = error("Read only")
        override suspend fun getRegisteredDevice(id: RegisteredDeviceId): RegisteredDevice? = null
        override suspend fun markRegisteredDeviceSeen(id: RegisteredDeviceId, seenAtEpochMillis: Long) = false
        override suspend fun revokeRegisteredDevice(id: RegisteredDeviceId, revokedAtEpochMillis: Long) = false
        override fun observeRegisteredDevices() = emptyFlow<List<RegisteredDevice>>()
    }
    val annotations = object : InspectionAnnotationStore {
        override suspend fun put(sessionId: CaptureSessionId, annotation: InspectionAnnotation) = error("Read only")
        override suspend fun get(exchangeId: ExchangeId) = documents[exchangeId].orEmpty()
        override fun observe(exchangeId: ExchangeId) = emptyFlow<List<InspectionAnnotation>>()
        override fun observe(exchangeIds: Set<ExchangeId>) = emptyFlow<Map<ExchangeId, List<InspectionAnnotation>>>()
    }
    val formatter = object : TrafficDetailsFormatting {
        override fun json(text: String): String = if (text == "{\"ok\":true}") "{\n  \"ok\": true\n}" else text
        override fun graphQL(method: String, url: String, contentType: String, body: ByteArray?, complete: Boolean) = graphQL
    }
    var presentations = ProtocolMessagePresentationRegistry()
    val useCase get() = CopyTrafficDetailsUseCase(traffic, bodies, messages, devices, annotations, formatter, presentations)
    fun request(vararg ids: String) = CaptureExportSource.Selection(ids.map { TrafficRecordId.Exchange(ExchangeId(it)) }, TrafficSource.Other)
    fun body(value: ByteArray, outcome: BodyCaptureOutcome = BodyCaptureOutcome.Complete, observed: Long = value.size.toLong()): MessageBodyRef {
        val id = BodyId("body-${payloads.size}"); payloads[id] = value
        return MessageBodyRef.Available(BodyRef(id, observed, value.size.toLong(), outcome = outcome))
    }
    fun row(id: String = "one", time: Long = 1, path: String = "/users?page=1", requestBody: MessageBodyRef = MessageBodyRef.Empty,
        responseBody: MessageBodyRef = MessageBodyRef.Empty): HttpExchangeSnapshot = HttpExchangeSnapshot(ExchangeId(id), ConnectionId("connection"),
        request = HttpRequestSnapshot(RequestHead(HttpMethod.GET, RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("api.test", 443), path),
            ApplicationProtocol.fromToken("HTTP/1.1"), listOf(HeaderField(HeaderName("Accept"), "application/json"))), requestBody),
        response = HttpResponseSnapshot(ResponseHead(ApplicationProtocol.fromToken("HTTP/2"), HttpStatus(200), "OK", listOf(HeaderField(HeaderName("Content-Type"), "application/json"))), responseBody),
        state = ExchangeState.COMPLETED, timings = ExchangeTimings(totalMillis = 438), startedAtEpochMillis = time).also { rows[it.id] = it }
}
