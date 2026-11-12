package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.usecase.traffic.PrepareCapturedNetworkRequestUseCase
import com.devuloopers.knet.application.usecase.traffic.PrepareCapturedNetworkRequestResult
import com.devuloopers.knet.application.usecase.traffic.PrepareTrafficRequestUseCase
import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficQueryAdapter
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.graphql.GraphQLHttpParameter
import com.devuloopers.knet.domain.util.UrlQueryStringParser
import com.devuloopers.knet.engine.formatter.graphql.GraphQLHttpRequestFormatter
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collect
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import java.nio.file.Files
import kotlin.test.*

/** Real production client -> proxy -> isolated Room capture -> import -> replay, entirely on loopback. */
class GraphQLHttpCaptureReplayTest {
    @Test fun `GET capture import and replay preserve method raw target headers and an empty body`() = exercise(HttpMethod.GET)
    @Test fun `POST capture import and replay preserve JSON placement and unknown envelope fields`() = exercise(HttpMethod.POST)

    @Test fun `HTTP 1_1 GET replay retains interleaved repeated parameters`() = exercise(HttpMethod.GET, HttpVersionPreference.HTTP_1_1)
    @Test fun `HTTP 1_1 POST replay retains original JSON bytes`() = exercise(HttpMethod.POST, HttpVersionPreference.HTTP_1_1)

    @Test fun `cancelling a GET before response headers closes its transport without replaying it`() =
        exercise(HttpMethod.GET, HttpVersionPreference.HTTP_1_1, cancel = true)

    private fun exercise(method: HttpMethod, version: HttpVersionPreference = HttpVersionPreference.AUTO, cancel: Boolean = false) = runBlocking {
        data class Observed(val method: String, val target: String, val marker: String?, val body: String)
        val received = ConcurrentLinkedQueue<Observed>()
        val observed = CompletableDeferred<Unit>()
        val releaseOrigin = CountDownLatch(1)
        val origin = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                received += Observed(exchange.requestMethod, exchange.requestURI.toASCIIString(), exchange.requestHeaders.getFirst("X-Fixture"),
                    exchange.requestBody.readBytes().decodeToString())
                observed.complete(Unit)
                if (cancel) releaseOrigin.await(10, TimeUnit.SECONDS)
                val response = "{\"data\":{\"ok\":true}}".encodeToByteArray()
                exchange.responseHeaders.set("Content-Type", "application/json")
                runCatching {
                    exchange.sendResponseHeaders(200, response.size.toLong())
                    exchange.responseBody.use { it.write(response) }
                }
            }
            start()
        }
        val root = Files.createTempDirectory("knet-graphql-http-replay-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(root.resolve("bodies"))
        val port = ServerSocket(0).use { it.localPort }
        val session = CanonicalCaptureSessionFactory(database, bodyStore, bodyStore).openStreamingProxy(port, 1)
        val proxy = KNetProxyServer(port = port, captureSink = session,
            serverTlsContextProvider = com.devuloopers.knet.data.desktop.certificate.DesktopServerTlsContextProvider(
                com.devuloopers.knet.engine.certificate.CertificateAuthority.generate(), com.devuloopers.knet.engine.certificate.CertificateCache()))
        val client = KNetApiClient()
        val execute = ExecuteClientApiRequestUseCase(client)
        val target = if (method == HttpMethod.GET) "/startup?tracking=a%2bb&flag&query=query%20BootStrapQuery%20%7B%20bootstrap%20%7B%20id%20%7D%20%7D&variables=%7B%22device%22%3A%22ANDROID%22%7D&tracking=2"
            else "/startup?tracking=a%2bb&tracking=2"
        val url = "http://127.0.0.1:${origin.address.port}$target"
        val payload = if (method == HttpMethod.GET) "" else " {\"query\":\"query BootStrapQuery { bootstrap { id } }\",\"variables\":{\"device\":\"ANDROID\"},\"client\":{\"id\":7}} "
        val headers = mapOf("X-Fixture" to "capture-replay", "Content-Type" to "application/json")
        proxy.start()
        try {
            if (cancel) {
                val job = launch {
                    client.executeStreaming(url, method, headers, proxyPort = port, httpVersionPreference = version).collect { }
                }
                withTimeout(5_000) { observed.await() }
                withTimeout(5_000) { job.cancelAndJoin() }
                assertEquals(1, received.size, "Cancellation must not replay an application request")
                proxy.stop()
                session.flush()
                val query = DesktopTrafficQueryAdapter(database.canonicalCaptureDao(), bodyStore)
                val captured = query.query(TrafficPageQuery(sessionId = session.sessionId, limit = 10)).items.single().exchange
                assertEquals(HttpMethod.GET, captured.request.head.method)
                assertTrue(captured.request.head.target.toString().contains("BootStrapQuery"))
                return@runBlocking
            }
            val first = execute(url, method, headers, body = if (payload.isEmpty()) OutboundRequestBody.None else OutboundRequestBody.GraphQl(payload), proxyPort = port, httpVersionPreference = version)
            assertEquals(200, first.statusCode)
            session.flush()
            val query = DesktopTrafficQueryAdapter(database.canonicalCaptureDao(), bodyStore)
            val id = query.query(TrafficPageQuery(sessionId = session.sessionId, limit = 10)).items.single().exchange.id
            val prepared = assertIs<PrepareCapturedNetworkRequestResult.Found>(
                PrepareCapturedNetworkRequestUseCase(PrepareTrafficRequestUseCase(query)).execute(id)).spec
            assertEquals(method, prepared.method)
            assertEquals(url, prepared.url)
            assertEquals(payload, prepared.bodyPayload)
            val facts = GraphQLHttpRequestFormatter.inspect(prepared.method.token, prepared.url,
                body = prepared.bodyPayload.encodeToByteArray())!!
            assertEquals("BootStrapQuery", facts.selectedOperation?.name)
            val replay = execute(prepared.url, prepared.method, prepared.headers.toMap(), queryParams = prepared.queryParams,
                body = if (prepared.bodyPayload.isEmpty()) OutboundRequestBody.None else OutboundRequestBody.GraphQl(prepared.bodyPayload), proxyPort = port, httpVersionPreference = version)
            assertEquals(200, replay.statusCode)
            assertEquals(2, received.size)
            assertEquals(received.first(), received.last(), "Replay must preserve the server-observed request")
            assertEquals(method.token, received.first().method)
            assertEquals(target, received.first().target)
            assertEquals("capture-replay", received.first().marker)
            assertEquals(payload, received.first().body)
            if (method == HttpMethod.GET) {
                val edited = GraphQLHttpRequestFormatter.updateUrl(prepared.url,
                    mapOf(GraphQLHttpParameter.VARIABLES to """{"value":"a+b & café"}""")).getOrThrow()
                val result = execute(edited, method, headers, queryParams = UrlQueryStringParser.parseQueryParams(edited), proxyPort = port, httpVersionPreference = version)
                assertEquals(200, result.statusCode)
                assertEquals("", received.last().body)
                assertEquals("GET", received.last().method)
                val parsed = GraphQLHttpRequestFormatter.inspect("GET", received.last().target)!!
                assertEquals("""{"value":"a+b & café"}""", parsed.variablesJson)
                assertTrue(received.last().target.contains("tracking=a%2bb&flag"))
                assertTrue(received.last().target.endsWith("tracking=2"))
            }
        } finally {
            releaseOrigin.countDown()
            client.close(); proxy.stop(); session.close(); database.close(); origin.stop(0); root.deleteRecursively()
        }
    }
}
