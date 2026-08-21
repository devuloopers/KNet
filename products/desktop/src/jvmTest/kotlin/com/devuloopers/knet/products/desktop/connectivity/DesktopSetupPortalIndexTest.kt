package com.devuloopers.knet.products.desktop.connectivity

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopSetupPortalIndexTest {
    @Test
    fun `loads packaged setup portal index`() {
        val document = DesktopSetupPortalIndex.render()

        assertTrue(document.isNotBlank())
        assertContains(document, "KNet Setup")
        assertContains(document, "/knet-ca.crt")
        assertSame(document, DesktopSetupPortalIndex.render())
    }
}
