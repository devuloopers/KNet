package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.ui.desktop.certificate.client.ClientIdentityImportCapabilities
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientIdentityImportCapabilitiesTest {

    @Test
    fun `accepts every supported identity extension case insensitively`() {
        listOf("p12", "pfx", "pem", "crt", "cer", "key", "jks", "keystore").forEach { extension ->
            assertTrue(ClientIdentityImportCapabilities.supports("client.$extension"))
            assertTrue(ClientIdentityImportCapabilities.supports("client.${extension.uppercase()}"))
        }
    }

    @Test
    fun `rejects files without a supported identity extension`() {
        assertFalse(ClientIdentityImportCapabilities.supports("client.txt"))
        assertFalse(ClientIdentityImportCapabilities.supports("client"))
        assertFalse(ClientIdentityImportCapabilities.supports("client.p12.txt"))
    }
}
