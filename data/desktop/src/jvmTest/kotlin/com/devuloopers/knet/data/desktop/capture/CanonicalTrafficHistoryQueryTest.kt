package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficRecordFilter
import com.devuloopers.knet.application.contract.traffic.TrafficFacetQuery
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficQueryAdapter
import com.devuloopers.knet.storage.capture.entity.CanonicalExchangeEntity
import com.devuloopers.knet.storage.capture.entity.CaptureSessionEntity
import com.devuloopers.knet.storage.capture.entity.TrafficConnectionEntity
import com.devuloopers.knet.storage.capture.entity.OpaqueFlowEntity
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HttpScheme
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CanonicalTrafficHistoryQueryTest {
    @Test
    fun `desktop facade resolves retained opaque policy actions and trusted ingress`() = runTest {
        val root = Files.createTempDirectory("knet-opaque-lookup-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        try {
            val dao=database.canonicalCaptureDao()
            dao.insertSession(CaptureSessionEntity("session",1L,null,"ACTIVE",0L))
            dao.insertConnection(connection("session").copy(ingressKind="LAN_PAIRED_DEVICE",clientIdentity="device-a",downstreamHost="127.0.0.1"))
            dao.insertOpaqueFlow(opaqueFlow("selected-flow","session",2L))
            val query=DesktopTrafficQueryAdapter(dao,FileBodyStore(root.resolve("bodies")))
            val selected=assertNotNull(query.getOpaqueFlow(com.devuloopers.knet.traffic.id.OpaqueFlowId("selected-flow")))
            assertEquals("protected.example",selected.serverName)
            val ingress=assertNotNull(query.getConnectionIngress(selected.connectionId))
            assertEquals(com.devuloopers.knet.traffic.model.IngressKind.LanPairedDevice,ingress.ingress.kind)
            assertEquals("device-a",ingress.ingress.clientIdentity!!.value)
            kotlin.test.assertTrue(ingress.downstreamIsLoopback)
            dao.insertConnection(connection("session").copy(id="unknown-ingress",ingressKind="CUSTOM:future",clientIdentity="device-a"))
            kotlin.test.assertNull(query.getConnectionIngress(com.devuloopers.knet.traffic.id.ConnectionId("unknown-ingress")))
            kotlin.test.assertNull(query.getOpaqueFlow(com.devuloopers.knet.traffic.id.OpaqueFlowId("deleted")))
            kotlin.test.assertNull(query.getConnectionIngress(com.devuloopers.knet.traffic.id.ConnectionId("deleted")))
        } finally { database.close();root.deleteRecursively() }
    }

    @Test
    fun `opaque flow mutations invalidate the desktop live Traffic generation`() = runBlocking {
        val root = Files.createTempDirectory("knet-opaque-generation-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val dao = database.canonicalCaptureDao()
        try {
            dao.insertSession(CaptureSessionEntity("session", 1L, null, "ACTIVE", 0L))
            dao.insertConnection(connection("session"))
            val query = DesktopTrafficQueryAdapter(dao, FileBodyStore(root.resolve("bodies")))
            val updates = Channel<com.devuloopers.knet.application.contract.traffic.TrafficGeneration>(
                capacity = Channel.UNLIMITED,
            )
            val collector = launch(Dispatchers.Default) {
                query.generations.collect { generation -> updates.send(generation) }
            }
            withTimeout(5_000L) { updates.receive() }

            dao.insertOpaqueFlow(opaqueFlow("opaque-live", "session", 2_000L))

            assertEquals("session", withTimeout(5_000L) { updates.receive() }.sessionId.value)
            collector.cancel()
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun `chronological cursor pages across HTTP and opaque rows without omission`() = runTest {
        val root = Files.createTempDirectory("knet-merged-history-query-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val dao = database.canonicalCaptureDao()
        try {
            dao.insertSession(CaptureSessionEntity("session", 1L, null, "ACTIVE", 0L))
            dao.insertConnection(connection("session"))
            dao.insertExchange(exchange("old-http", "session", 1_000L, "https", "HTTP/1.1", "/old"))
            dao.insertOpaqueFlow(opaqueFlow("middle-opaque", "session", 2_000L))
            dao.insertExchange(exchange("new-http", "session", 3_000L, "https", "HTTP/1.1", "/new"))
            val query = CanonicalTrafficQueryAdapter(
                sessionId = null,
                dao = dao,
                bodyStore = FileBodyStore(root.resolve("bodies")),
            )

            val observed = mutableListOf<String>()
            val observedHistorySequences = mutableListOf<Long>()
            var cursor: com.devuloopers.knet.application.contract.traffic.TrafficPageCursor? = null
            do {
                val page = query.query(TrafficPageQuery(limit = 1, cursor = cursor))
                observed += page.items.map { it.exchange.id.value }
                observed += page.opaqueItems.map { it.flow.id.value }
                observedHistorySequences += page.items.map { it.historySequence.value }
                observedHistorySequences += page.opaqueItems.map { it.historySequence.value }
                assertEquals(3L, page.totalCount)
                cursor = page.nextCursor
            } while (cursor != null)

            assertEquals(listOf("new-http", "middle-opaque", "old-http"), observed)
            assertEquals(listOf(3L, 2L, 1L), observedHistorySequences)
            assertEquals(
                listOf("middle-opaque"),
                query.query(
                    TrafficPageQuery(limit = 20, recordFilter = TrafficRecordFilter.PROTECTED),
                ).opaqueItems.map { it.flow.id.value },
            )
            assertEquals(
                listOf("new-http", "old-http"),
                query.query(
                    TrafficPageQuery(limit = 20, recordFilter = TrafficRecordFilter.DECRYPTED),
                ).items.map { it.exchange.id.value },
            )
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun `global query preserves retained sessions and applies typed store filters`() = runTest {
        val root = Files.createTempDirectory("knet-history-query-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val dao = database.canonicalCaptureDao()
        try {
            listOf("old-session", "new-session").forEachIndexed { index, sessionId ->
                dao.insertSession(
                    CaptureSessionEntity(
                        id = sessionId,
                        startedAtEpochMillis = index.toLong() + 1L,
                        endedAtEpochMillis = index.toLong() + 2L,
                        state = "CLOSED",
                        version = 1L,
                    ),
                )
                dao.insertConnection(connection(sessionId))
            }
            dao.insertExchange(exchange("old", "old-session", 1_000L, "http", "HTTP/1.1", "/legacy/find-me"))
            dao.insertExchange(
                exchange(
                    id = "new",
                    sessionId = "new-session",
                    timestamp = 2_000L,
                    scheme = "https",
                    protocol = "HTTP/1.1",
                    path = "/current",
                    responseProtocol = "HTTP/2",
                ),
            )

            val query = CanonicalTrafficQueryAdapter(
                sessionId = null,
                dao = dao,
                bodyStore = FileBodyStore(root.resolve("bodies")),
            )

            val newestPage = query.query(TrafficPageQuery(limit = 1))
            assertEquals(2L, newestPage.totalCount)
            assertEquals(listOf(2L), newestPage.items.map { item -> item.captureSequence.value })
            assertEquals(listOf(2L), newestPage.items.map { item -> item.historySequence.value })
            val olderPage = query.query(
                TrafficPageQuery(limit = 1, cursor = assertNotNull(newestPage.nextCursor)),
            )
            assertEquals(2L, olderPage.totalCount)
            assertEquals(listOf(1L), olderPage.items.map { item -> item.captureSequence.value })
            assertEquals(listOf(1L), olderPage.items.map { item -> item.historySequence.value })

            assertEquals(
                listOf("new", "old"),
                query.query(TrafficPageQuery(limit = 20)).items.map { item -> item.exchange.id.value },
            )
            val filteredOld = query.query(TrafficPageQuery(limit = 20, searchContains = "find-me"))
            assertEquals(listOf("old"), filteredOld.items.map { item -> item.exchange.id.value })
            assertEquals(listOf(1L), filteredOld.items.map { item -> item.historySequence.value })

            val filteredNew = query.query(
                TrafficPageQuery(
                    limit = 20,
                    schemes = setOf(HttpScheme.fromToken("https")),
                    protocols = setOf(ApplicationProtocol.fromToken("HTTP/2")),
                ),
            )
            assertEquals(listOf("new"), filteredNew.items.map { item -> item.exchange.id.value })
            assertEquals(listOf(2L), filteredNew.items.map { item -> item.historySequence.value })

            val facets = query.queryFacets(TrafficFacetQuery())
            assertEquals(2L, facets.totalCount)
            assertEquals(1L, facets.httpCount)
            assertEquals(1L, facets.httpsCount)

            val searchedFacets = query.queryFacets(TrafficFacetQuery(searchContains = "find-me"))
            assertEquals(1L, searchedFacets.totalCount)
            assertEquals(1L, searchedFacets.httpCount)
            assertEquals(0L, searchedFacets.httpsCount)

            val http2Facets = query.queryFacets(
                TrafficFacetQuery(protocols = setOf(ApplicationProtocol.fromToken("HTTP/2"))),
            )
            assertEquals(1L, http2Facets.totalCount)
            assertEquals(0L, http2Facets.httpCount)
            assertEquals(1L, http2Facets.httpsCount)
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun `message history is scoped bounded and ordered by capture sequence despite clock reversal`() = runTest {
        val root = Files.createTempDirectory("knet-message-context-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        try {
            val dao = database.canonicalCaptureDao()
            dao.insertSession(CaptureSessionEntity("session", 1, null, "ACTIVE", 0))
            dao.insertConnection(connection("session"))
            for (id in listOf("one", "other")) dao.insertExchange(exchange(id, "session", 1, "https", "HTTP/1.1", "/ws"))
            fun message(id: String, sequence: Long, owner: String = "one", direction: String = "CLIENT_TO_SERVER",
                kind: String = "text") = com.devuloopers.knet.storage.capture.entity.DuplexMessageEntity(
                id, "session", "connection-session", owner, null, sequence, sequence, direction, "websocket", kind,
                10_000 - sequence, null, 0, false, null, null, "COMPLETE", null)
            listOf(message("old", 1), message("foreign", 2, "other"), message("binary", 3, kind = "binary"),
                message("server-complete", 4, direction = "SERVER_TO_CLIENT"), message("new", 5),
                message("selected", 6), message("future", 7)).forEach { dao.insertDuplexMessage(it) }
            val adapter = DesktopTrafficQueryAdapter(dao, FileBodyStore(root.resolve("bodies")))
            val query = com.devuloopers.knet.application.contract.traffic.ProtocolMessageContextQuery(
                com.devuloopers.knet.traffic.id.ExchangeId("one"), com.devuloopers.knet.traffic.id.ProtocolMessageId("selected"),
                com.devuloopers.knet.traffic.model.message.MessageProtocolId.WEBSOCKET, null,
                com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.TEXT, limit = 2)
            assertEquals(listOf("new", "server-complete"), adapter.queryContext(query).map { it.id.value })
            assertEquals(listOf("new", "old"), adapter.queryContext(query.copy(direction =
                com.devuloopers.knet.traffic.model.TrafficDirection.CLIENT_TO_SERVER)).map { it.id.value })
            assertEquals(emptyList(), adapter.queryContext(query.copy(exchangeId = com.devuloopers.knet.traffic.id.ExchangeId("other"))))
            assertEquals(emptyList(), adapter.queryContext(query.copy(beforeMessageId = com.devuloopers.knet.traffic.id.ProtocolMessageId("deleted"))))
        } finally { database.close(); root.deleteRecursively() }
    }

    @Test
    fun `device isolation precedes mixed paging facets and clear across shared sessions`() = runTest {
        val root = Files.createTempDirectory("knet-multi-source-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        try {
            val dao = database.canonicalCaptureDao()
            dao.insertSession(CaptureSessionEntity("shared", 1, 2, "CLOSED", 1))
            val sources = listOf("a", "b", "manual", "legacy")
            sources.forEach { device ->
                dao.insertConnection(connection("shared").copy(id = device,
                    ingressKind = if (device in listOf("a", "b")) "LAN_PAIRED_DEVICE" else "WIFI_LAN_CLIENT",
                    clientIdentity = if (device == "legacy") null else device, downstreamHost = "192.0.2.1"))
                repeat(3) { index ->
                    dao.insertExchange(exchange("$device-$index", "shared", 100L + index, "https", "HTTP/2", "/same")
                        .copy(connectionId = device, connectionSequence = index.toLong() + 1))
                }
                dao.insertOpaqueFlow(opaqueFlow("$device-tls", "shared", 102).copy(connectionId = device))
            }
            val bodyStore = FileBodyStore(root.resolve("bodies"))
            val query = CanonicalTrafficQueryAdapter(null, dao, bodyStore)
            fun source(device: String) = com.devuloopers.knet.application.contract.traffic.TrafficSource.Device(
                com.devuloopers.knet.identity.RegisteredDeviceId(device))
            for (direction in com.devuloopers.knet.application.contract.traffic.TrafficSortDirection.entries) {
                val records = mutableListOf<String>()
                var cursor: com.devuloopers.knet.application.contract.traffic.TrafficPageCursor? = null
                do {
                    val page = query.query(TrafficPageQuery(limit = 1, source = source("a"), cursor = cursor, direction = direction))
                    assertEquals(4, page.totalCount)
                    records += page.items.map { row -> row.exchange.id.value }
                    records += page.opaqueItems.map { row -> row.flow.id.value }
                    cursor = page.nextCursor
                    if (cursor != null) {
                        kotlin.test.assertFailsWith<IllegalArgumentException> {
                            query.query(TrafficPageQuery(limit = 1, source = source("b"), cursor = cursor, direction = direction))
                        }
                        kotlin.test.assertFailsWith<IllegalArgumentException> {
                            query.query(TrafficPageQuery(limit = 1, source = source("a"), cursor = cursor, direction = direction, searchContains = "same"))
                        }
                    }
                } while (cursor != null)
                assertEquals(setOf("a-0", "a-1", "a-2", "a-tls"), records.toSet())
                assertEquals(4, records.size)
            }
            assertEquals(4, query.queryFacets(TrafficFacetQuery(source = source("b"))).httpsCount)
            assertEquals(8, query.query(TrafficPageQuery(limit = 100,
                source = com.devuloopers.knet.application.contract.traffic.TrafficSource.Other)).totalCount)
            // Capturing into the replacement session is allowed while the drained history is cleared.
            dao.insertSession(CaptureSessionEntity("active", 3, null, "ACTIVE", 0))
            dao.insertConnection(connection("active").copy(ingressKind = "LAN_PAIRED_DEVICE", clientIdentity = "a"))
            dao.insertExchange(exchange("a-new", "active", 200, "http", "HTTP/1.1", "/new"))
            com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficMaintenanceAdapter(database)
                .clearSourceTraffic(source("a"))
            assertEquals(listOf("a-new"), query.query(TrafficPageQuery(limit = 100, source = source("a"))).items.map { row -> row.exchange.id.value })
            assertEquals(4, query.query(TrafficPageQuery(limit = 100, source = source("b"))).totalCount)
            assertEquals(8, query.query(TrafficPageQuery(limit = 100,
                source = com.devuloopers.knet.application.contract.traffic.TrafficSource.Other)).totalCount)
            assertNotNull(dao.getSession("shared"))
        } finally { database.close(); root.deleteRecursively() }
    }

    @Test
    fun `tab membership observes all retained HTTP and zero byte opaque traffic but not empty connections or spoofed identities`(): Unit = runBlocking {
        val root = Files.createTempDirectory("knet-tab-membership-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val updates = Channel<Set<com.devuloopers.knet.identity.RegisteredDeviceId>>(Channel.UNLIMITED)
        val dao = database.canonicalCaptureDao()
        val collector = launch(Dispatchers.Default) {
            com.devuloopers.knet.data.desktop.workspace.repository.DesktopDeviceTrafficHistoryAdapter(dao)
                .observeDeviceIdsWithTraffic().collect { identities -> updates.send(identities) }
        }
        suspend fun expect(vararg identities: String) {
            val expected = identities.map { com.devuloopers.knet.identity.RegisteredDeviceId(it) }.toSet()
            withTimeout(5_000) { while (updates.receive() != expected) { /* Await Room invalidation. */ } }
        }
        try {
            expect()
            val registry = com.devuloopers.knet.data.desktop.pairing.RoomRegisteredDeviceStore(database.registeredDeviceDao())
            for (id in listOf("a", "b", "empty", "spoof", "custom")) {
                registry.putRegisteredDevice(com.devuloopers.knet.identity.RegisteredDevice(
                    com.devuloopers.knet.identity.RegisteredDeviceId(id), id,
                    com.devuloopers.knet.identity.DeviceRegistrationKind.PAIRED_COMPANION, 0, 0))
            }
            dao.insertSession(CaptureSessionEntity("old", 1, 2, "CLOSED", 1))
            dao.insertSession(CaptureSessionEntity("live", 3, null, "ACTIVE", 0))
            dao.insertConnection(connection("old").copy(ingressKind = "LAN_PAIRED_DEVICE", clientIdentity = "a"))
            dao.insertConnection(connection("live").copy(ingressKind = "COMPANION_DIRECT", clientIdentity = "a"))
            dao.insertConnection(connection("old").copy(id = "b", ingressKind = "COMPANION_RELAY", clientIdentity = "b"))
            dao.insertConnection(connection("old").copy(id = "empty", ingressKind = "LAN_PAIRED_DEVICE", clientIdentity = "empty"))
            for ((id, kind, identity) in listOf(Triple("spoof", "WIFI_LAN_CLIENT", "spoof"), Triple("null", "COMPANION_DIRECT", null),
                Triple("blank", "COMPANION_DIRECT", " "), Triple("custom", "CUSTOM:LAN_PAIRED_DEVICE", "custom"))) {
                dao.insertConnection(connection("old").copy(id = id, ingressKind = kind, clientIdentity = identity))
                dao.insertExchange(exchange("$id-http", "old", 1, "https", "HTTP/1.1", "/").copy(connectionId = id))
            }
            dao.insertExchange(exchange("a-http", "old", 2, "https", "HTTP/1.1", "/hidden-by-filters"))
            expect("a")
            dao.insertOpaqueFlow(opaqueFlow("b-tls", "old", 3).copy(connectionId = "b", uploadedBytes = 0, downloadedBytes = 0))
            expect("a", "b")
            dao.insertExchange(exchange("a-live", "live", 4, "https", "HTTP/1.1", "/"))
            val bodyStore = FileBodyStore(root.resolve("bodies"))
            val maintenance = com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficMaintenanceAdapter(database)
            maintenance.clearSourceTraffic(com.devuloopers.knet.application.contract.traffic.TrafficSource.Device(
                com.devuloopers.knet.identity.RegisteredDeviceId("a")))
            // The live record still counts, even after all older sessions for this identity are cleared.
            assertEquals(setOf("a", "b"), com.devuloopers.knet.data.desktop.workspace.repository.DesktopDeviceTrafficHistoryAdapter(dao).observeDeviceIdsWithTraffic().first().map { identity -> identity.value }.toSet())
            dao.deleteSourceExchanges(listOf("a-live"))
            expect("b")
            maintenance.clearSourceTraffic(com.devuloopers.knet.application.contract.traffic.TrafficSource.Device(
                com.devuloopers.knet.identity.RegisteredDeviceId("b")))
            expect()
            assertNotNull(dao.getConnection("b"), "Transport metadata alone must not retain a tab")
            assertNotNull(registry.getRegisteredDevice(com.devuloopers.knet.identity.RegisteredDeviceId("b")),
                "Clearing a device's traffic must preserve its pairing identity")
            assertNotNull(dao.getExchange("spoof-http"), "Other traffic must remain untouched")
        } finally { collector.cancel(); database.close(); root.deleteRecursively() }
    }

    @Test
    fun `arrival count uses independent insertion sequences not timestamp display number or page size`() = runTest {
        val root = Files.createTempDirectory("knet-arrivals-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        try {
            val dao = database.canonicalCaptureDao()
            dao.insertSession(CaptureSessionEntity("session", 1, null, "ACTIVE", 0))
            dao.insertConnection(connection("session"))
            dao.insertExchange(exchange("old-http", "session", 2_000, "https", "HTTP/1.1", "/old"))
            dao.insertOpaqueFlow(opaqueFlow("old-tls", "session", 2_000).copy(state = "ACTIVE", completedAtEpochMillis = null))
            val query = CanonicalTrafficQueryAdapter(null, dao, FileBodyStore(root.resolve("bodies")))
            val baseline = assertNotNull(query.query(TrafficPageQuery(limit = 1)).arrivals)
            assertEquals(0L, baseline.count)
            assertEquals(1L, baseline.checkpoint.exchanges)
            assertEquals(1L, baseline.checkpoint.opaqueFlows)
            // New captures may have an older or identical wall-clock timestamp.
            dao.insertExchange(exchange("new-http", "session", 1_000, "https", "HTTP/2", "/new"))
            dao.insertOpaqueFlow(opaqueFlow("new-tls", "session", 2_000))
            val page = query.query(TrafficPageQuery(limit = 1, arrivalsAfter = baseline.checkpoint))
            assertEquals(1, page.items.size + page.opaqueItems.size)
            assertEquals(2L, assertNotNull(page.arrivals).count)
            // A lifecycle update on an already observed identity cannot become a new arrival.
            assertEquals(1, dao.terminateOpaqueFlow("old-tls", 3, "FAILED", 4_000, 10, 20, "test-failure"))
            assertEquals(2L, query.query(TrafficPageQuery(limit = 1, arrivalsAfter = baseline.checkpoint)).arrivals!!.count)
            assertEquals(0L, query.query(TrafficPageQuery(limit = 1, arrivalsAfter = page.arrivals!!.checkpoint)).arrivals!!.count)
        } finally { database.close(); root.deleteRecursively() }
    }

    @Test
    fun `arrival counts exceed bounded pages and frozen paging excludes new traffic`() = runTest {
        val root = Files.createTempDirectory("knet-arrivals-burst-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        try {
            val dao = database.canonicalCaptureDao()
            dao.insertSession(CaptureSessionEntity("session", 1, null, "ACTIVE", 0))
            dao.insertConnection(connection("session"))
            dao.insertExchange(exchange("anchor", "session", 1_000, "https", "HTTP/1.1", "/old"))
            val query = CanonicalTrafficQueryAdapter(null, dao, FileBodyStore(root.resolve("bodies")))
            val checkpoint = query.query(TrafficPageQuery(limit = 1)).arrivals!!.checkpoint
            dao.insertExchangeBatch((1..1_500).map { exchange("burst-$it", "session", 2_000L + it, "https", "HTTP/1.1", "/") })
            val frozen = query.query(TrafficPageQuery(limit = 1, arrivalsAfter = checkpoint, windowThrough = checkpoint))
            assertEquals(listOf("anchor"), frozen.items.map { it.exchange.id.value })
            assertEquals(1_500L, frozen.arrivals!!.count)
            assertEquals(1_501L, frozen.totalCount)
            val latest = query.query(TrafficPageQuery(limit = 1, arrivalsAfter = frozen.arrivals!!.checkpoint))
            assertEquals(listOf("burst-1500"), latest.items.map { it.exchange.id.value })
            assertEquals(0L, latest.arrivals!!.count)
        } finally { database.close(); root.deleteRecursively() }
    }

    @Test
    fun `arrival summary applies source search scheme method status version and opaque filters in storage`() = runTest {
        val root = Files.createTempDirectory("knet-arrivals-filters-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        try {
            val dao = database.canonicalCaptureDao()
            dao.insertSession(CaptureSessionEntity("session", 1, null, "ACTIVE", 0))
            dao.insertConnection(connection("session"))
            dao.insertConnection(connection("session").copy(id = "a", ingressKind = "COMPANION_DIRECT", clientIdentity = "a"))
            dao.insertConnection(connection("session").copy(id = "b", ingressKind = "COMPANION_RELAY", clientIdentity = "b"))
            val query = CanonicalTrafficQueryAdapter(null, dao, FileBodyStore(root.resolve("bodies")))
            val checkpoint = query.query(TrafficPageQuery(limit = 1)).arrivals!!.checkpoint
            dao.insertExchange(exchange("a-http", "session", 1, "https", "HTTP/2", "/100%_match").copy(connectionId = "a"))
            dao.insertExchange(exchange("b-http", "session", 2, "https", "HTTP/2", "/100%_match").copy(connectionId = "b", method = "POST"))
            dao.insertExchange(exchange("other-http", "session", 3, "http", "HTTP/1.1", "/100xxmatch"))
            dao.insertOpaqueFlow(opaqueFlow("a-tls", "session", 4).copy(connectionId = "a"))
            val source = com.devuloopers.knet.application.contract.traffic.TrafficSource.Device(com.devuloopers.knet.identity.RegisteredDeviceId("a"))
            val base = TrafficPageQuery(limit = 1, arrivalsAfter = checkpoint, source = source)
            assertEquals(2L, query.query(base).arrivals!!.count)
            assertEquals(1L, query.query(base.copy(searchContains = "%_", methods = setOf(com.devuloopers.knet.traffic.model.http.HttpMethod.fromToken("GET")),
                statuses = setOf(com.devuloopers.knet.traffic.model.http.HttpStatus(200)), schemes = setOf(HttpScheme.fromToken("https")),
                protocols = setOf(ApplicationProtocol.fromToken("HTTP/2")))).arrivals!!.count)
            assertEquals(0L, query.query(base.copy(methods = setOf(com.devuloopers.knet.traffic.model.http.HttpMethod.fromToken("POST")))).arrivals!!.count)
            assertEquals(1L, query.query(base.copy(recordFilter = TrafficRecordFilter.TLS_TUNNEL)).arrivals!!.count)
            assertEquals(1L, query.query(base.copy(recordFilter = TrafficRecordFilter.PROTECTED)).arrivals!!.count)
            assertEquals(1L, query.query(base.copy(source = com.devuloopers.knet.application.contract.traffic.TrafficSource.Other)).arrivals!!.count)
            assertEquals(0L, query.query(base.copy(schemes = setOf(HttpScheme.fromToken("http")))).arrivals!!.count)
        } finally { database.close(); root.deleteRecursively() }
    }

    @Test
    fun `deleting history does not turn a retained record update into a new arrival`() = runTest {
        val root = Files.createTempDirectory("knet-arrivals-delete-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        try {
            val dao = database.canonicalCaptureDao()
            dao.insertSession(CaptureSessionEntity("session", 1, null, "ACTIVE", 0))
            dao.insertConnection(connection("session"))
            dao.insertExchange(exchange("first", "session", 1, "https", "HTTP/1.1", "/"))
            val query = CanonicalTrafficQueryAdapter(null, dao, FileBodyStore(root.resolve("bodies")))
            val checkpoint = query.query(TrafficPageQuery(limit = 1)).arrivals!!.checkpoint
            dao.deleteSourceExchanges(listOf("first"))
            assertEquals(0L, query.query(TrafficPageQuery(limit = 1, arrivalsAfter = checkpoint)).arrivals!!.count)
            dao.insertExchange(exchange("after-clear", "session", 1, "https", "HTTP/1.1", "/"))
            val next = query.query(TrafficPageQuery(limit = 1, arrivalsAfter = checkpoint))
            assertEquals(1L, next.arrivals!!.count)
            kotlin.test.assertTrue(next.arrivals!!.checkpoint.exchanges > checkpoint.exchanges)
        } finally { database.close(); root.deleteRecursively() }
    }

    @Test
    fun `progressive read checkpoint counts nineteen then eighteen and preserves later backdated insertions`() = runTest {
        val root = Files.createTempDirectory("knet-progressive-read-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        try {
            val dao = database.canonicalCaptureDao()
            dao.insertSession(CaptureSessionEntity("session", 1, null, "ACTIVE", 0))
            dao.insertConnection(connection("session"))
            dao.insertExchange(exchange("old", "session", 100_000, "https", "HTTP/1.1", "/old"))
            val reader = CanonicalTrafficQueryAdapter(null, dao, FileBodyStore(root.resolve("bodies")))
            val initial = reader.query(TrafficPageQuery(limit = 10))
            val baseline = initial.arrivals!!.checkpoint
            dao.insertExchangeBatch((101..119).map { exchange("new-$it", "session", it * 1_000L, "https", "HTTP/1.1", "/$it") })
            val base = TrafficPageQuery(limit = 100, arrivalsAfter = baseline)
            val all = reader.query(base)
            assertEquals(19L, all.arrivals!!.count)
            val byId = all.items.associateBy { it.exchange.id.value }
            val first = com.devuloopers.knet.application.contract.traffic.TrafficSeenCheckpoint(byId.getValue("new-101").readPoint, all.arrivals!!.checkpoint)
            assertEquals(18L, reader.query(base.copy(seenThrough = first)).arrivals!!.count)
            val second = first.copy(position = byId.getValue("new-102").readPoint)
            assertEquals(17L, reader.query(base.copy(seenThrough = second)).arrivals!!.count)
            // Newer paging starts next to the old head; its latest metadata must still identify 119.
            val adjacent = reader.query(base.copy(limit = 1, direction = com.devuloopers.knet.application.contract.traffic.TrafficSortDirection.OLDEST_FIRST,
                newerThan = initial.items.single().readPoint))
            assertEquals(listOf("new-101"), adjacent.items.map { it.exchange.id.value })
            assertEquals(byId.getValue("new-119").readPoint, adjacent.latest)
            dao.deleteSourceExchanges(listOf("new-101"))
            assertEquals(17L, reader.query(base.copy(seenThrough = second)).arrivals!!.count)
            dao.insertExchange(exchange("later-backdated", "session", 101_500, "https", "HTTP/1.1", "/late"))
            val backdated = reader.query(base.copy(seenThrough = second))
            assertEquals(18L, backdated.arrivals!!.count, "A later insertion cannot be lost behind an earlier viewed timestamp")
            assertEquals(0L, reader.query(base.copy(arrivalsAfter = backdated.arrivals!!.checkpoint)).arrivals!!.count)
            val older = reader.query(base.copy(limit = 1, olderThan = byId.getValue("new-103").readPoint))
            assertEquals("new-102", older.items.single().exchange.id.value)
        } finally { database.close(); root.deleteRecursively() }
    }

    @Test
    fun `mixed equal time HTTP and opaque read positions use one namespaced chronological order and source filters`() = runTest {
        val root = Files.createTempDirectory("knet-progressive-mixed-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        try {
            val dao = database.canonicalCaptureDao()
            dao.insertSession(CaptureSessionEntity("session", 1, null, "ACTIVE", 0))
            dao.insertConnection(connection("session").copy(ingressKind = "COMPANION_DIRECT", clientIdentity = "a"))
            dao.insertConnection(connection("session").copy(id = "b", ingressKind = "COMPANION_RELAY", clientIdentity = "b"))
            val reader = CanonicalTrafficQueryAdapter(null, dao, FileBodyStore(root.resolve("bodies")))
            val baseline = reader.query(TrafficPageQuery(limit = 1)).arrivals!!.checkpoint
            for (id in listOf("a", "b")) {
                dao.insertExchange(exchange(id, "session", 1_000, "https", "HTTP/1.1", "/"))
                dao.insertOpaqueFlow(opaqueFlow(id, "session", 1_000))
            }
            dao.insertExchange(exchange("foreign", "session", 2_000, "https", "HTTP/1.1", "/").copy(connectionId = "b"))
            val source = com.devuloopers.knet.application.contract.traffic.TrafficSource.Device(com.devuloopers.knet.identity.RegisteredDeviceId("a"))
            val base = TrafficPageQuery(limit = 10, source = source, arrivalsAfter = baseline)
            val all = reader.query(base)
            assertEquals(4L, all.arrivals!!.count)
            val httpB = all.items.single { it.exchange.id.value == "b" }.readPoint
            val opaqueA = all.opaqueItems.single { it.flow.id.value == "a" }.readPoint
            val seen = com.devuloopers.knet.application.contract.traffic.TrafficSeenCheckpoint(httpB, all.arrivals!!.checkpoint)
            assertEquals(2L, reader.query(base.copy(seenThrough = seen)).arrivals!!.count)
            assertEquals(1L, reader.query(base.copy(seenThrough = seen.copy(position = opaqueA))).arrivals!!.count)
            assertEquals(0L, reader.query(base.copy(seenThrough = seen, methods = setOf(com.devuloopers.knet.traffic.model.http.HttpMethod.fromToken("GET")))).arrivals!!.count)
            assertEquals(2L, reader.query(base.copy(seenThrough = seen, recordFilter = TrafficRecordFilter.TLS_TUNNEL)).arrivals!!.count)
            val next = reader.query(base.copy(limit = 1, newerThan = httpB, direction = com.devuloopers.knet.application.contract.traffic.TrafficSortDirection.OLDEST_FIRST))
            assertEquals("a", next.opaqueItems.single().flow.id.value)
            assertEquals(all.latest, next.latest)
        } finally { database.close(); root.deleteRecursively() }
    }

    @Test
    fun `adjacent newer pages span fifteen hundred records without gaps independently of the UI row cap`() = runTest {
        val root = Files.createTempDirectory("knet-newer-paging-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        try {
            val dao = database.canonicalCaptureDao()
            dao.insertSession(CaptureSessionEntity("session", 1, null, "ACTIVE", 0))
            dao.insertConnection(connection("session"))
            dao.insertExchange(exchange("old", "session", 1_000, "https", "HTTP/1.1", "/"))
            val reader = CanonicalTrafficQueryAdapter(null, dao, FileBodyStore(root.resolve("bodies")))
            val old = reader.query(TrafficPageQuery(limit = 1))
            dao.insertExchangeBatch((1..1_500).map { exchange("new-$it", "session", 2_000L + it, "https", "HTTP/1.1", "/") })
            var anchor = old.items.single().readPoint
            val ids = mutableListOf<String>()
            repeat(8) {
                val page = reader.query(TrafficPageQuery(limit = 200, newerThan = anchor,
                    direction = com.devuloopers.knet.application.contract.traffic.TrafficSortDirection.OLDEST_FIRST,
                    arrivalsAfter = old.arrivals!!.checkpoint))
                ids += page.items.map { it.exchange.id.value }
                assertEquals(1_500L, page.arrivals!!.count)
                assertEquals("new-1500", (page.latest!!.recordId as com.devuloopers.knet.application.contract.traffic.TrafficRecordId.Exchange).id.value)
                anchor = page.items.last().readPoint
            }
            assertEquals((1..1_500).map { "new-$it" }, ids)
        } finally { database.close(); root.deleteRecursively() }
    }

    private fun connection(sessionId: String): TrafficConnectionEntity = TrafficConnectionEntity(
        id = "connection-$sessionId",
        sessionId = sessionId,
        sequenceVersion = 1L,
        openedAtEpochMillis = 1L,
        closedAtEpochMillis = 2L,
        ingressKind = "LOCAL",
        clientIdentity = null,
        downstreamHost = null,
        downstreamPort = null,
        listenerHost = "127.0.0.1",
        listenerPort = 8080,
        transportProtocol = "tcp",
        receivedBytes = 0L,
        sentBytes = 0L,
        state = "CLOSED",
        terminalErrorCode = null,
    )

    private fun exchange(
        id: String,
        sessionId: String,
        timestamp: Long,
        scheme: String,
        protocol: String,
        path: String,
        responseProtocol: String = protocol,
    ): CanonicalExchangeEntity = CanonicalExchangeEntity(
        id = id,
        sessionId = sessionId,
        connectionId = "connection-$sessionId",
        streamId = null,
        connectionSequence = 1L,
        version = 2L,
        state = "COMPLETED",
        startedAtEpochMillis = timestamp,
        completedAtEpochMillis = timestamp + 1L,
        method = "GET",
        scheme = scheme,
        host = "api.example",
        port = null,
        pathAndQuery = path,
        protocol = protocol,
        requestHeadersEncoded = "H1:0:",
        requestBodyId = null,
        responseProtocol = responseProtocol,
        responseStatusCode = 200,
        responseReasonPhrase = "OK",
        responseHeadersEncoded = "H1:0:",
        responseBodyId = null,
        timingDnsMillis = null,
        timingConnectMillis = null,
        timingTlsMillis = null,
        timingFirstByteMillis = null,
        timingDownloadMillis = null,
        timingTotalMillis = 1L,
        terminalErrorCode = null,
    )

    private fun opaqueFlow(id: String, sessionId: String, timestamp: Long): OpaqueFlowEntity = OpaqueFlowEntity(
        id = id,
        sessionId = sessionId,
        connectionId = "connection-$sessionId",
        connectionSequence = 2L,
        version = 1L,
        state = "COMPLETED",
        startedAtEpochMillis = timestamp,
        completedAtEpochMillis = timestamp + 1L,
        destinationHost = "protected.example",
        destinationPort = 443,
        serverName = "protected.example",
        transport = "TCP",
        security = "TLS",
        sourceApplicationId = null,
        policyRuleId = "protected-rule",
        uploadedBytes = 10L,
        downloadedBytes = 20L,
        terminalErrorCode = null,
    )
}
