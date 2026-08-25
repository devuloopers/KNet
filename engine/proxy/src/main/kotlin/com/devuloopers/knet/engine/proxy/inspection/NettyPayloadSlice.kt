package com.devuloopers.knet.engine.proxy.inspection

import io.netty.buffer.ByteBuf

/** Callback-scoped [ByteBuf] view that never transfers or retains reference-counted ownership. */
internal class NettyPayloadSlice(private val source: ByteBuf) : ProxyPayloadSlice {
    override val size: Int = source.readableBytes()

    override fun indexOf(value: Byte, startIndex: Int): Int {
        require(startIndex in 0..size) { "Payload search start is outside the borrowed slice." }
        if (startIndex == size) return -1
        val absoluteStart = source.readerIndex() + startIndex
        val absoluteMatch = source.indexOf(absoluteStart, source.readerIndex() + size, value)
        return if (absoluteMatch < 0) -1 else absoluteMatch - source.readerIndex()
    }

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
