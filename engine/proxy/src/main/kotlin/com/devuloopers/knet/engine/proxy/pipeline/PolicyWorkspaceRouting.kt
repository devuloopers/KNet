package com.devuloopers.knet.engine.proxy.pipeline

import com.devuloopers.knet.domain.policy.PolicyWorkspace
import io.netty.channel.Channel

/** Resolves authenticated parent attribution even when a multiplexed child has no copied attribute. */
fun policyWorkspace(channel: Channel): PolicyWorkspace {
    var current: Channel? = channel
    while (current != null) {
        val client = current.attr(ProxyChannelAttributes.TLS_FALLBACK_CLIENT).get()
        if (client != null) return PolicyWorkspace.fromClient(client)
        current = current.parent()
    }
    return PolicyWorkspace.Other
}
