package com.devuloopers.knet.engine.proxy.inspection

import io.netty.buffer.ByteBuf

/** Callback-scoped [ByteBuf] view that never transfers or retains reference-counted ownership. */
internal class NettyPayloadSlice(private val source: ByteBuf) : ProxyPayloadSlice {
    override val size: Int = source.readableBytes()

    override fun copyTo(
        destination: ByteArray,
        destinationOffset: Int,
        sourceOffset: Int,
        length: Int,
    ) {
        require(sourceOffset >= 0 && length >= 0 && sourceOffset + length <= size) {
            "Payload source range is outside the borrowed slice."
        }
        require(destinationOffset >= 0 && destinationOffset + length <= destination.size) {
            "Payload destination range is outside the supplied array."
        }
        source.getBytes(source.readerIndex() + sourceOffset, destination, destinationOffset, length)
    }
}
