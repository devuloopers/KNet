package com.devuloopers.knet.engine.certificate.installer

import com.devuloopers.knet.engine.certificate.InstallationResult
import com.devuloopers.knet.engine.certificate.TrustStoreInstaller
import com.devuloopers.knet.engine.certificate.util.TestCertificateFactory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrustStoreInstallerTest {

    private val ca = TestCertificateFactory.createTestCa()

    @Test
    fun testTrustStoreInstallerInvocation() {
        val result = TrustStoreInstaller.install(ca.certificate)
        assertNotNull(result, "InstallationResult must not be null")
        when (result) {
            is InstallationResult.Success -> {
                // Succeeded on host OS
                assertTrue(true)
            }
            is InstallationResult.Failure -> {
                // Failed gracefully with suggested command instructions
                assertNotNull(result.message)
                assertNotNull(result.suggestedCommand)
                assertTrue(result.message.isNotBlank())
            }
        }
    }
}
