package com.devuloopers.knet.engine.certificate.util

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.LeafCertificate
import com.devuloopers.knet.engine.certificate.LeafCertificateGenerator

/**
 * Reusable test factory creating Root CA and Leaf certificate instances for unit & integration testing.
 */
object TestCertificateFactory {

    /**
     * Generates a fast Root CA instance for testing.
     */
    fun createTestCa(
        commonName: String = "Test KNet CA",
        org: String = "Test Org",
        validityDays: Int = 30
    ): CertificateAuthority {
        return CertificateAuthority.generate(commonName, org, validityDays)
    }

    /**
     * Generates a signed leaf certificate for testing.
     */
    fun createTestLeaf(
        hostname: String = "api.github.com",
        ca: CertificateAuthority = createTestCa(),
        validityDays: Int = 15
    ): LeafCertificate {
        return LeafCertificateGenerator.generate(hostname, ca, validityDays)
    }
}
