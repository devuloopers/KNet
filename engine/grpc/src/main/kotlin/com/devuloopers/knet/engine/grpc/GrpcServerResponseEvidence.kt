package com.devuloopers.knet.engine.grpc

import io.grpc.*
import java.util.concurrent.atomic.AtomicBoolean

/** Transport proof for headers/trailers, including trailers-only server errors without response messages. */
internal class GrpcServerResponseEvidence : ClientStreamTracer.Factory() {
    private val received = AtomicBoolean(false)
    val present: Boolean get() = received.get()
    override fun newClientStreamTracer(info: ClientStreamTracer.StreamInfo, headers: Metadata): ClientStreamTracer =
        object : ClientStreamTracer() {
            override fun inboundHeaders() { received.set(true) }
            override fun inboundTrailers(trailers: Metadata) { received.set(true) }
        }
}
