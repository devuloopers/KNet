package com.devuloopers.knet.companion.connectivity.certificate

import java.security.cert.X509Certificate

/** Result of checking Android's current trusted-credential store for one exact root identity. */
internal sealed interface AndroidTrustedCertificateLookupResult {
    data object Present : AndroidTrustedCertificateLookupResult

    data object Absent : AndroidTrustedCertificateLookupResult

    data object Unavailable : AndroidTrustedCertificateLookupResult
}

/** Android boundary for a fresh exact-certificate lookup in the current CA store. */
internal fun interface AndroidTrustedCertificateStore {
    suspend fun lookup(rootCertificate: X509Certificate): AndroidTrustedCertificateLookupResult
}
