package com.devuloopers.knet.engine.proxy.pool

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.EventLoopGroup
import io.netty.channel.pool.AbstractChannelPoolMap
import io.netty.channel.pool.ChannelHealthChecker
import io.netty.channel.pool.ChannelPoolHandler
import io.netty.channel.pool.SimpleChannelPool
import io.netty.channel.socket.nio.NioSocketChannel
import java.net.InetSocketAddress

/**
 * Manages reusable upstream TCP Netty socket channels mapped to remote server addresses.
 */
class ProxyConnectionPoolManager(
    private val eventLoopGroup: EventLoopGroup,
    private val channelInitializerHandler: ChannelPoolHandler
) {

    private val poolMap = object : AbstractChannelPoolMap<InetSocketAddress, SimpleChannelPool>() {
        override fun newPool(key: InetSocketAddress): SimpleChannelPool {
            val bootstrap = Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel::class.java)
                .remoteAddress(key)

            return SimpleChannelPool(
                bootstrap,
                channelInitializerHandler,
                ChannelHealthChecker.ACTIVE
            )
        }
    }

    /**
     * Acquires an active or new [Channel] for the given [address].
     */
    fun acquire(address: InetSocketAddress) = poolMap.get(address).acquire()

    /**
     * Releases an active [channel] back to the pool for [address].
     */
    fun release(address: InetSocketAddress, channel: Channel) = poolMap.get(address).release(channel)

    /**
     * Closes all active channel pools and releases underlying socket resources.
     */
    fun close() {
        poolMap.close()
    }
}
