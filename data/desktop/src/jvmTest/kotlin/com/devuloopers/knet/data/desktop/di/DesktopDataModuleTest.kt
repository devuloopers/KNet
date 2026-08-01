package com.devuloopers.knet.data.desktop.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying [DesktopDataModule] layer definitions and module aggregation.
 */
class DesktopDataModuleTest {

    @Test
    fun testDesktopDataModuleExposesSubModules() {
        assertTrue(DesktopDataModule.all.isNotEmpty(), "DesktopDataModule.all must not be empty")
        assertEquals(3, DesktopDataModule.all.size, "DesktopDataModule.all must contain datasource, runtime, and repositories modules")
    }
}
