package com.devuloopers.knet.ui.desktop.breakpointmanager

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.devuloopers.knet.application.contract.breakpoint.*
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.ui.desktop.breakpointmanager.components.*
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.ResolvedInterceptPayload
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.ui.core.components.layout.KNetScreenLayout
import com.devuloopers.knet.website.*
import kotlin.test.Test

/** Real paused-request drawer, with authored bytes and no breakpoint-control implementation. */
class WebsiteInterceptArtworkTest {
    /** Captures the actual queue/editor/forward controls after its entrance animation settles. */
    @Test fun renderIntercept() {
        val payload = """{ "productId": "1042", "quantity": 2 }"""
        val headers = listOf(HeaderField(HeaderName("Content-Type"), "application/json"))
        val event = PendingBreakpoint("website-pending", "website-rule", BreakpointCandidate(
            exchangeId = ExchangeId("website-cart"), phase = BreakpointPhase.REQUEST,
            request = HttpRequestSnapshot(RequestHead(HttpMethod.POST, RequestTarget.Absolute(HttpScheme.fromToken("https"),
                Authority("api.example.com", 443), "/v1/cart/items"), ApplicationProtocol.fromToken("HTTP/1.1"), headers)),
            requestBody = BreakpointBody(payload.encodeToByteArray()), startedAtEpochMillis = 1_788_084_000_000L))
        renderArtwork(ArtworkScene.INTERCEPTS, listOf("FORWARD REQUEST")) {
            Box(Modifier.fillMaxSize()) {
                KNetScreenLayout(title = "Intercepts", subtitle = "Pixel 8 · Pause matching requests and responses.") {
                }
                LiveInterceptDrawer(LiveInterceptDrawerState(listOf(event), event, true,
                    mapOf(event.id to ResolvedInterceptPayload(event.id,
                        PayloadInspectionSpec.fromBytes(payload.encodeToByteArray(), listOf("Content-Type" to "application/json")),
                        PayloadInspectionSpec.EMPTY)), emptyMap()),
                    LiveInterceptDrawerActions({}, {}, {}, {}, {}, {}, {}, {}, {}))
            }
        }
    }
}
