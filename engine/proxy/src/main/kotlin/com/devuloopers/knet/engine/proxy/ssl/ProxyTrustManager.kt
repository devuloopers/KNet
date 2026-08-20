package com.devuloopers.knet.engine.proxy.ssl

import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

/**
 * Manages configurable upstream SSL certificate trust strategies for KNet Proxy Engine.
 *
 * Strict mode uses the host JVM's public trust roots. The interception Root CA is deliberately not
 * inserted into upstream trust because it authenticates KNet to downstream clients, not origin servers.
 */
object ProxyTrustManager {

    /**
     * Returns a [TrustManagerFactory] according to [strictValidation].
     *
     * @param strictValidation If `true`, verifies origin certificates against the host JVM trust roots.
     * If `false`, trusts all upstream certificates for an explicitly insecure local-debugging session.
     */
    fun getTrustManagerFactory(strictValidation: Boolean): TrustManagerFactory {
        if (!strictValidation) return InsecureTrustManagerFactory.INSTANCE
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(null as KeyStore?)
        }
    }
}
