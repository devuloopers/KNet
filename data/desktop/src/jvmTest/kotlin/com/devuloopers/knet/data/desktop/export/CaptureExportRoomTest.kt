package com.devuloopers.knet.data.desktop.export

import com.devuloopers.knet.application.contract.export.*
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.data.desktop.capture.CanonicalSessionWriter
import com.devuloopers.knet.data.desktop.traffic.repository.*
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.storage.device.entity.RegisteredDeviceEntity
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.*

/** Actual SQLite writer/query/export/clear integration, preserving authenticated device scope at identical hosts. */
class CaptureExportRoomTest {
    @Test fun `Room snapshot exports only chosen identity and survives source clear without touching sibling`() = runBlocking {
        withTimeout(30_000) {
            val root = Files.createTempDirectory("knet-room-export").toFile()
            val database = DatabaseFactory.create(root.resolve("capture.db"))
            val store = FileBodyStore(root.resolve("bodies"))
            val studio = DesktopStudioExportRecorder(root.resolve("studio"))
            try {
                val session = CaptureSessionId("capture")
                val writer = CanonicalSessionWriter.open(session, 1000, database.canonicalCaptureDao(), store, store,
                    CaptureIngressLimits(128, 1_048_576, 1_048_576, 16))
                for (device in listOf("device-A", "device-B")) {
                    database.registeredDeviceDao().upsertRegisteredDevice(RegisteredDeviceEntity(device, "Same model", "COMPANION", 1000, 1000, null))
                    val connection = ConnectionId("connection-$device"); val exchange = ExchangeId("exchange-$device")
                    writer.tryPublish(CaptureEvent.ConnectionOpened(session, connection, 0, 1000,
                        IngressContext(IngressKind.CompanionDirect, ClientIdentity(device)), localListener = TrafficEndpoint("127.0.0.1", 8080), transportProtocol = "tcp"))
                    writer.tryPublish(CaptureEvent.ExchangeStarted(session, connection, 1, 1001, exchange, 1,
                        request = RequestHead(HttpMethod.GET, RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("same.test", 443), "/same"), ApplicationProtocol.fromToken("HTTP/1.1"), emptyList())))
                    writer.tryPublish(CaptureEvent.ResponseObserved(session, connection, 2, 1002, exchange, 2,
                        response = ResponseHead(ApplicationProtocol.fromToken("HTTP/1.1"), HttpStatus(200), "OK", emptyList())))
                    writer.tryPublish(CaptureEvent.ExchangeTerminated(session, connection, 3, 1003, exchange, 3, ExchangeTerminalOutcome.Completed))
                }
                writer.flush(); writer.close()
                val query = DesktopTrafficQueryAdapter(database.canonicalCaptureDao(), store)
                val repository = DesktopCaptureExportRepository(database, query, query, store, studio, root.resolve("exports"))
                val source = TrafficSource.Device(RegisteredDeviceId("device-A"))
                val prepared = repository.prepare(CaptureExportSource.Traffic(TrafficPageQuery(limit = 128, source = source)))
                assertEquals(1L, prepared.records)
                DesktopTrafficMaintenanceAdapter(database).clearSourceTraffic(source)
                assertEquals(0L, query.query(TrafficPageQuery(limit = 128, source = source)).totalCount)
                assertEquals(1L, query.query(TrafficPageQuery(limit = 128, source = TrafficSource.Device(RegisteredDeviceId("device-B")))).totalCount)
                val target = root.resolve("selected.knetcapture")
                repository.write(prepared, CaptureExportOptions(), target.path) {}
                ZipFile(target).use { zip ->
                    val record = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("records.jsonl")).bufferedReader().readLine()).jsonObject
                    assertEquals("exchange-device-A", record["id"]!!.jsonPrimitive.content)
                    assertEquals("device-A", record["source"]!!.jsonObject["deviceId"]!!.jsonPrimitive.content)
                    assertEquals("Same model", record["source"]!!.jsonObject["deviceName"]!!.jsonPrimitive.content)
                }
                repository.release(prepared)
            } finally { studio.close(); studio.awaitCleanup(); database.close(); root.deleteRecursively() }
        }
    }
}
