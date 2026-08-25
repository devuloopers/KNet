package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorContribution
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorInput
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorStrategy
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import java.net.URI

/** Adds a stable WebSocket badge and endpoint name to common authored and captured request lists. */
class WebSocketRequestDescriptorStrategy : RequestDescriptorStrategy {
    override val priority: Int = 350

    override fun describe(request: RequestDescriptorInput): RequestDescriptorContribution? {
        val upgrade = request.headers.firstOrNull { header ->
            header.name.value.equals("upgrade", ignoreCase = true)
        }?.value
        val hinted = request.semanticKindHint == RequestKindId.WEBSOCKET
        val websocketScheme = request.absoluteUrl.startsWith("ws://", ignoreCase = true) ||
            request.absoluteUrl.startsWith("wss://", ignoreCase = true)
        if (!hinted && !websocketScheme && !upgrade.equals("websocket", ignoreCase = true)) return null
        val endpointName = runCatching { URI.create(request.absoluteUrl).rawPath }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: "WebSocket"
        return RequestDescriptorContribution(
            kind = RequestKindId.WEBSOCKET,
            badgeLabel = "WS",
            suggestedName = endpointName,
        )
    }
}
