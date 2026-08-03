package com.devuloopers.knet.engine.proxy.dns

import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.resolver.dns.DnsAddressResolverGroup
import io.netty.resolver.dns.DnsServerAddressStreamProviders

/**
 * Provides native Netty asynchronous UDP DNS address resolution on Netty EventLoop threads.
 */
object NettyDnsResolver {

    /**
     * Shared [DnsAddressResolverGroup] using system default DNS servers and NIO datagram sockets.
     */
    val resolverGroup: DnsAddressResolverGroup by lazy {
        DnsAddressResolverGroup(
            NioDatagramChannel::class.java,
            DnsServerAddressStreamProviders.platformDefault()
        )
    }

    /**
     * Gracefully closes all background DNS channel resources.
     */
    fun close() {
        resolverGroup.close()
    }
}
