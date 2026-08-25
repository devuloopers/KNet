package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.application.port.traffic.ProtocolMessagePayloadDecoder
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePayloadDecoderId
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePayloadInput
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePresentation
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.message.MessageProtocolId

/** Descriptor-backed gRPC decoder plugged into the protocol-neutral presentation registry. */
class GrpcProtocolMessageDecoder(
    private val descriptors: GrpcDescriptorRegistry,
) : ProtocolMessagePayloadDecoder {
    override val decoderId: ProtocolMessagePayloadDecoderId = ProtocolMessagePayloadDecoderId("grpc-protobuf")
    override val protocolId: MessageProtocolId = MessageProtocolId.GRPC
    override val priority: Int = 100

    override fun decode(input: ProtocolMessagePayloadInput): ProtocolMessagePresentation? {
        val identity = GrpcMethodIdentity.fromTarget(input.parentExchange.request.head.target) ?: return null
        val direction = when (input.message.direction) {
            TrafficDirection.CLIENT_TO_SERVER -> GrpcPayloadDirection.REQUEST
            TrafficDirection.SERVER_TO_CLIENT -> GrpcPayloadDirection.RESPONSE
        }
        return when (val decoded = descriptors.decode(
            identity = identity,
            direction = direction,
            payload = input.payload,
            compressed = input.message.compressed,
            compressionEncoding = input.message.compressionEncoding,
        )) {
            is GrpcPayloadDecodeResult.DecodedJson -> ProtocolMessagePresentation(
                title = "${identity.serviceName}/${identity.methodName}",
                contentType = "application/json",
                text = decoded.json,
                schemaName = decoded.messageType,
            )
            is GrpcPayloadDecodeResult.Unavailable -> null
        }
    }
}
