package com.devuloopers.knet.engine.proxy.ssl

import com.devuloopers.knet.engine.certificate.ssl.KNetTrustManagerProvider
import javax.net.ssl.TrustManagerFactory

/**
 * Manages configurable upstream SSL certificate trust strategies for KNet Proxy Engine.
 *
 * Delegates to [KNetTrustManagerProvider] to maintain a unified trust policy across
 * Netty's proxy engine and Ktor's client engine.
 */
object ProxyTrustManager {

    /**
     * Returns a [TrustManagerFactory] according to [strictValidation].
     *
     * @param strictValidation If `true`, verifies server certificates against the unified CA trust store
     * (system public CAs + KNet Root CA). If `false`, trusts all upstream certificates for local debugging.
     */
    fun getTrustManagerFactory(strictValidation: Boolean): TrustManagerFactory {
        return KNetTrustManagerProvider.getTrustManagerFactory(strictValidation)
    }
}
