package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorContribution
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorInput
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorStrategy
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.traffic.model.http.HttpMethod

/** Adds a stable RPC badge and method identity without changing the shared descriptor resolver. */
class GrpcRequestDescriptorStrategy : RequestDescriptorStrategy {
    override val priority: Int = 300

    override fun describe(request: RequestDescriptorInput): RequestDescriptorContribution? {
        if (request.transportMethod != HttpMethod.POST) return null
        val contentType = request.headers.firstOrNull { header ->
            header.name.value.equals("content-type", ignoreCase = true)
        }?.value
        val hinted = request.semanticKindHint == RequestKindId.GRPC
        if (!hinted && !GrpcProtocol.isNativeContentType(contentType)) return null
        val method = GrpcMethodIdentity.fromUrl(request.absoluteUrl) ?: return null
        return RequestDescriptorContribution(
            kind = RequestKindId.GRPC,
            badgeLabel = "RPC",
            suggestedName = "${method.serviceName}/${method.methodName}",
        )
    }
}
