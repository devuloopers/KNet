package com.devuloopers.knet.engine.proxy.ssl

import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

/**
 * Manages configurable upstream SSL certificate trust strategies for KNet Proxy Engine.
 */
object ProxyTrustManager {

    /**
     * Returns a [TrustManagerFactory] according to [strictValidation].
     *
     * @param strictValidation If `true`, verifies server certificates against JVM CA trust store.
     * If `false`, trusts all upstream certificates for local debugging.
     */
    fun getTrustManagerFactory(strictValidation: Boolean): TrustManagerFactory {
        return if (strictValidation) {
            val defaultFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            defaultFactory.init(null as KeyStore?)
            defaultFactory
        } else {
            InsecureTrustManagerFactory.INSTANCE
        }
    }
}
