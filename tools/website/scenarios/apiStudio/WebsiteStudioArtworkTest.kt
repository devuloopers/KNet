package com.devuloopers.knet.ui.desktop.apistudio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.ui.core.components.tabs.*
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.*
import com.devuloopers.knet.ui.desktop.apistudio.editor.RequestUrlBar
import com.devuloopers.knet.ui.desktop.apistudio.layout.ApiStudioSplitWorkspace
import com.devuloopers.knet.ui.desktop.apistudio.model.*
import com.devuloopers.knet.ui.desktop.apistudio.response.ResponseInspectorView
import com.devuloopers.knet.ui.desktop.httppanel.editor.RequestEditorPanel
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.components.ResponseSummaryHeader
import com.devuloopers.knet.ui.desktop.httppanel.export.CaptureExportButton
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.website.*
import kotlin.test.Test

/** Actual Studio collections, URL editor, resizable workspace and response inspector with authored state. */
class WebsiteStudioArtworkTest {
    /** No client or scripting engine is initialized to display this successful example response. */
    @Test fun renderStudio() {
        val document = SavedApiRequest("website-catalog", "Get catalog", method = HttpMethod.GET, url = "https://api.example.com/v1/catalog")
        val request = SidebarRequestItem(document.id, document.name, document = document,
            descriptor = SidebarRequestDescriptor(RequestKindId.HTTP, "GET", HttpMethod.GET))
        renderArtwork(ArtworkScene.API_STUDIO, listOf("Collections", "Get catalog", "Send", "200")) {
            Row(Modifier.fillMaxSize()) {
                CollectionsSidebar(CollectionsState(collections = listOf(SidebarFolderItem("website", name = "Store API", requests = listOf(request))),
                    isLoading = false), selectedRequestId = request.id, canSaveActiveRequest = false, modifier = Modifier.width(230.dp))
                Column(Modifier.weight(1f)) {
                    KNetTabRow { listOf("HTTP", "WebSocket", "GraphQL WS", "gRPC").forEach { KNetTab(it, it == "HTTP", {}) } }
                    ApiStudioSplitWorkspace(.5f, {}, authoringPane = { pane ->
                        Column(pane.fillMaxSize()) {
                        RequestUrlBar(HttpMethod.GET, document.url, HttpVersionPreference.AUTO, {}, {}, {}, {})
                        RequestEditorPanel(queryParams = listOf(KeyValueEntry("limit", "limit", "20"),
                            KeyValueEntry("category", "category", "headphones")), activeSubTab = InspectorSubTab.PARAMS, modifier = Modifier.weight(1f))
                        }
                    }, resultPane = { pane ->
                        ResponseInspectorView(ResponseInspectorState(statusCode = 200, statusText = "OK", durationMs = 128,
                            sizeBytes = sampleResponse.length.toLong(), headers = mapOf("Content-Type" to "application/json"),
                            responseBody = sampleResponse, protocol = ApplicationProtocol.fromToken("HTTP/2")),
                            modifier = pane, exportAction = { CaptureExportButton(true) {} })
                    }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
