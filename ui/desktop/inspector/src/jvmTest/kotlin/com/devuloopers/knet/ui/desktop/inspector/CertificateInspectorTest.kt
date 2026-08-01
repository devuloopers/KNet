package com.devuloopers.knet.ui.desktop.inspector

import com.devuloopers.knet.ui.desktop.inspector.model.InspectorTab
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for Certificate inspection tab in `:ui:desktop:inspector`.
 */
class CertificateInspectorTest {

    @Test
    fun `TLS enum value exists`() {
        assertEquals("TLS", InspectorTab.TLS.name)
    }
}
