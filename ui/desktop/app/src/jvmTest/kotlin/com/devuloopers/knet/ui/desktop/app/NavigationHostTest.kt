package com.devuloopers.knet.ui.desktop.app

import com.devuloopers.knet.ui.desktop.app.navigation.NavigationHost
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Unit tests verifying NavigationHost definition.
 */
class NavigationHostTest {

    @Test
    fun `verify navigation host package reference`() {
        // Assert that the reference to the NavigationHost function exists
        val functionRef = ::NavigationHost
        assertNotNull(functionRef)
    }
}
