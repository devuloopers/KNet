package com.devuloopers.knet.engine.certificate.migration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.certificate.LeafCertificateGenerator
import com.devuloopers.knet.engine.certificate.TrustStoreInstaller
import com.devuloopers.knet.engine.certificate.util.assertIsRootCa
import kotlin.test.Test
import kotlin.test.assertNotNull

class MigrationRegressionTest {

    @Test
    fun testPublicApiContractsIntact() {
        val ca = CertificateAuthority.generate("Migration CA", "Migration Org")
        assertIsRootCa(ca)

        val leaf = LeafCertificateGenerator.generate("migration.test", ca)
        assertNotNull(leaf.keyPair)
        assertNotNull(leaf.certificate)

        val cache = CertificateCache(maxEntries = 500)
        assertNotNull(cache.get("migration.test", ca))

        val result = TrustStoreInstaller.install(ca.certificate)
        assertNotNull(result)
    }
}
