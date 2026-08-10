package com.devuloopers.knet.engine.proxy.tls

import javax.net.ssl.KeyManagerFactory

/**
 * Functional strategy interface providing KeyManagerFactory resolution for outbound mTLS proxy connections.
 *
 * Decouples the Netty proxy engine from certificate storage and domain rule evaluation implementations.
 */
public fun interface KeyManagerProvider {

    /**
     * Resolves an initialized [KeyManagerFactory] containing the client keypair for the specified target host.
     *
     * @param host The target hostname (e.g. "client.badssl.com").
     * @return Initialized [KeyManagerFactory] if an active mTLS domain rule matches, or null if no rule applies.
     */
    public fun getKeyManagerFactory(host: String): KeyManagerFactory?
}
