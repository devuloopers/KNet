package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.ui.desktop.certificate.model.ClientCertificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying list management operations on collections of [ClientCertificate].
 */
public class ClientCertificateListTest {

    /**
     * Verifies filtering of enabled/disabled client certificates in a list.
     *
     * Design Intent: UI panels filter client certificates for validation and list presentation.
     */
    @Test
    public fun testClientCertificateListFiltering() {
        val certs = listOf(
            ClientCertificate("alias1", "subject1", "host1", "exp1", enabled = true),
            ClientCertificate("alias2", "subject2", "host2", "exp2", enabled = false),
            ClientCertificate("alias3", "subject3", "host3", "exp3", enabled = true)
        )

        val enabledCerts = certs.filter { it.enabled }
        assertEquals(2, enabledCerts.size)
        assertTrue(enabledCerts.any { it.alias == "alias1" })
        assertTrue(enabledCerts.any { it.alias == "alias3" })
    }
}
