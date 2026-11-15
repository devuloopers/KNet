package com.devuloopers.knet.website

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.devuloopers.knet.application.contract.device.DevicePresenceStatus
import com.devuloopers.knet.application.contract.device.DeviceWorkspaceEntry
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.identity.*
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.ui.core.components.split.HorizontalSplitPane
import com.devuloopers.knet.ui.core.components.split.SplitPaneDividerStyle
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.app.navigation.DesktopDestination
import com.devuloopers.knet.ui.desktop.app.navigation.NavigationRail
import com.devuloopers.knet.ui.desktop.app.workspace.DeviceWorkspaceState
import com.devuloopers.knet.ui.desktop.app.workspace.DeviceWorkspaceTabs
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.ui.desktop.traffic.filter.*
import com.devuloopers.knet.ui.desktop.traffic.inspector.TrafficInspectorPanel
import com.devuloopers.knet.ui.desktop.traffic.model.*
import com.devuloopers.knet.ui.desktop.traffic.table.TrafficTable
import com.devuloopers.knet.ui.desktop.traffic.toolbar.*
import java.awt.EventQueue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders real, network-free product components; source artwork contains only authored demo data. */
@OptIn(ExperimentalComposeUiApi::class)
class WebsiteProductPreviewTest {
    /** Emits a fixed-size image without opening the user's desktop app or reading its traffic database. */
    @Test
    fun renderWebsiteArtwork() = EventQueue.invokeAndWait {
        val endpoints = listOf(
            Triple("GET", "/v1/catalog", 128L), Triple("POST", "/v1/session", 242L),
            Triple("GET", "/v1/products/1042", 85L), Triple("GET", "/v1/cart", 36L),
            Triple("POST", "/v1/cart/items", 114L), Triple("GET", "/v1/account", 67L),
            Triple("GET", "/v1/recommendations", 94L), Triple("PATCH", "/v1/preferences", 152L),
            Triple("GET", "/v1/search?q=headphones", 71L), Triple("GET", "/v1/categories", 42L),
        )
        val exchanges = endpoints.mapIndexed { index, (method, path, duration) ->
            HttpExchangeSnapshot(ExchangeId("website-demo-$index"),
                request = HttpRequestSnapshot(RequestHead(HttpMethod.fromToken(method), RequestTarget.Absolute(
                    HttpScheme.fromToken("https"), Authority("api.example.com", 443), path),
                    ApplicationProtocol.fromToken("HTTP/2"), emptyList())),
                response = HttpResponseSnapshot(ResponseHead(ApplicationProtocol.fromToken("HTTP/2"), HttpStatus(200), "OK",
                    listOf(HeaderField(HeaderName("Content-Type"), "application/json")))),
                state = ExchangeState.COMPLETED, startedAtEpochMillis = 1_788_084_000_000L - index * 2_000,
                timings = ExchangeTimings(totalMillis = duration))
        }
        val rows = exchanges.mapIndexed { index, exchange ->
            TrafficRowUiState(sequenceNumber = (endpoints.size - index).toLong(), transactionId = exchange.id.value,
                method = endpoints[index].first, scheme = HttpScheme.fromToken("https"), host = "api.example.com",
                path = endpoints[index].second, status = 200, statusText = "OK", protocol = ApplicationProtocol.fromToken("HTTP/2"),
                timestamp = exchange.startedAtEpochMillis, formattedTimestamp = "09:41:${(35 - index).toString().padStart(2, '0')}",
                formattedTime = "${endpoints[index].third} ms", transferredBytes = 428, responseBytes = 428,
                formattedSize = "428 B", dateGroup = "Demo", contentType = "application/json", timings = exchange.timings)
        }
        val selected = rows.first()
        val payload = """{
  "data": {
    "products": [
      {
        "id": "1042",
        "name": "Wireless headphones",
        "price": 129.00,
        "inStock": true
      },
      {
        "id": "1043",
        "name": "Studio speakers",
        "price": 249.00,
        "inStock": true
      }
    ],
    "total": 2
  }
}"""
        val devices = listOf("Pixel 8" to "8A41D2", "iPhone 15" to "F72C10").map { (name, id) ->
            DeviceWorkspaceEntry(RegisteredDevice(RegisteredDeviceId(id), name,
                DeviceRegistrationKind.PAIRED_COMPANION, 1, 1), DevicePresenceStatus.CONNECTED)
        }
        ImageComposeScene(1600, 960, Density(1.2f), content = { KNetTheme {
            Column(Modifier.fillMaxSize().background(KNetTheme.colors.background)) {
                DeviceWorkspaceTabs(DeviceWorkspaceState(devices = devices, source = TrafficSource.Device(devices.first().device.id)), {},
                    modifier = Modifier.fillMaxWidth().height(46.dp))
                Row(Modifier.weight(1f)) {
                    NavigationRail(DesktopDestination.Traffic, {}, Modifier.width(64.dp))
                    Column(Modifier.weight(1f)) {
                        TrafficToolbar(TrafficToolbarState(exportAvailable = true, selectedCount = 1),
                            TrafficToolbarActions(onImport = {}, onExport = {}))
                        TrafficFilterBar(TrafficFilterBarState(totalCount = rows.size.toLong(), httpsCount = rows.size.toLong()), TrafficFilterBarActions())
                        HorizontalSplitPane(.59f, {}, modifier = Modifier.weight(1f), dividerStyle = SplitPaneDividerStyle.SINGLE_LINE,
                            firstPane = { pane -> TrafficTable(rows, selected.transactionId, false, {}, "4.2 KB", rows.size.toLong(), modifier = pane) },
                            secondPane = { pane -> TrafficInspectorPanel(selected, InspectorTab.RESPONSE,
                                preparedState = InspectorPreparedState(selected.transactionId, exchanges.first(),
                                    responsePayloadSpec = PayloadInspectionSpec.fromBytes(payload.encodeToByteArray(), listOf("Content-Type" to "application/json")),
                                    loadState = InspectorLoadState.READY), onTabSelected = {}, modifier = pane) })
                    }
                }
            }
        } }).use { scene ->
            repeat(6) { scene.render((it + 1L) * 1_000_000_000).close() }
            val output = File(System.getProperty("knet.website.artwork"))
            output.parentFile.mkdirs()
            scene.render(7_000_000_000).use { image -> image.encodeToData()!!.use { data -> output.writeBytes(data.bytes) } }
            assertTrue(output.length() > 10_000, "The real product render must not be empty")
        }
    }
}
