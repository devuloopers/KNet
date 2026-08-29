@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.devuloopers.knet.companion.packettunnel.tunnel

import com.devuloopers.knet.companion.packettunnel.hev.hev_socks5_tunnel_main_from_str
import com.devuloopers.knet.companion.packettunnel.hev.hev_socks5_tunnel_quit
import com.devuloopers.knet.companion.packettunnel.hev.knet_find_packet_tunnel_file_descriptor
import com.devuloopers.knet.companion.packettunnel.options.TunnelFailure
import com.devuloopers.knet.companion.packettunnel.options.TunnelException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSLock
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create

internal class HevTunnelEngine {
    private val queue = dispatch_queue_create("com.devuloopers.knet.companion.tun", null)
    private val lock = NSLock()
    private var running: Boolean = false

    fun start(socksPort: UShort, stopped: (TunnelException?) -> Unit) {
        val descriptor = knet_find_packet_tunnel_file_descriptor()
        if (descriptor < 0) throw TunnelFailure.UNABLE_TO_OPEN_TUN.exception()

        lock.lock()
        if (running) {
            lock.unlock()
            return
        }
        running = true
        lock.unlock()

        val config = configuration(socksPort)
        dispatch_async(queue) {
            val bytes = config.encodeToByteArray()
            val result = bytes.usePinned { pinned ->
                hev_socks5_tunnel_main_from_str(
                    pinned.addressOf(0).reinterpret(),
                    bytes.size.toUInt(),
                    descriptor,
                )
            }
            lock.lock()
            val wasRunning = running
            running = false
            lock.unlock()
            stopped(if (wasRunning && result != 0) TunnelFailure.TUNNEL_ENGINE_STOPPED.exception() else null)
        }
    }

    fun stop() {
        lock.lock()
        val shouldStop = running
        running = false
        lock.unlock()
        if (shouldStop) hev_socks5_tunnel_quit()
    }

    private fun configuration(socksPort: UShort): String = """
        tunnel:
          name: tun0
          mtu: 1500
          multi-queue: false
          ipv4: 198.18.0.1
          ipv6: 'fd00::1'
          icmp: 'off'
        socks5:
          address: 127.0.0.1
          port: $socksPort
          udp: 'udp'
        misc:
          task-stack-size: 86016
          tcp-buffer-size: 65536
          max-session-count: 512
          connect-timeout: 10000
          tcp-read-write-timeout: 300000
          udp-read-write-timeout: 60000
          log-file: stderr
          log-level: warn
    """.trimIndent()
}
