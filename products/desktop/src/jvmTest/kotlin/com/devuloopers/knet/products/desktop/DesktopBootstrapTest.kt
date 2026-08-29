package com.devuloopers.knet.products.desktop

import com.devuloopers.knet.products.desktop.bootstrap.DesktopBootstrap
import com.devuloopers.knet.products.desktop.config.DesktopConfiguration
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Test Suite for [DesktopBootstrap] Composition Root.
 * Verifies startup orchestrator initialization and environment loading.
 */
class DesktopBootstrapTest {

    @BeforeTest
    fun setUp() {
        stopKoin()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testDesktopBootstrapObjectIsNotNull() {
        assertNotNull(DesktopBootstrap)
    }

    @Test
    fun testDesktopConfigurationLoadsSuccessfully() {
        val config = DesktopConfiguration.load()
        assertNotNull(config)
        assertNotNull(config.environment)
    }

    @Test
    fun testConfigureWindowsRenderingPipelineSetsDefaultOpenGLProperties() {
        configureWindowsRenderingPipeline(osName = "Windows 11", preferredRenderApi = "OPENGL")
        kotlin.test.assertEquals("OPENGL", System.getProperty("skiko.renderApi"))
        kotlin.test.assertEquals("false", System.getProperty("skiko.vsync.enabled"))
        kotlin.test.assertEquals("true", System.getProperty("sun.java2d.opengl"))
        kotlin.test.assertEquals("false", System.getProperty("sun.java2d.d3d"))
        kotlin.test.assertEquals("true", System.getProperty("sun.java2d.noddraw"))
    }

    @Test
    fun testConfigureWindowsRenderingPipelineSetsDirectXPropertiesWhenSpecified() {
        configureWindowsRenderingPipeline(osName = "Windows 11", preferredRenderApi = "DIRECTX")
        kotlin.test.assertEquals("DIRECTX", System.getProperty("skiko.renderApi"))
        kotlin.test.assertEquals("false", System.getProperty("skiko.vsync.enabled"))
        kotlin.test.assertEquals("true", System.getProperty("sun.java2d.d3d"))
        kotlin.test.assertEquals("false", System.getProperty("sun.java2d.opengl"))
        kotlin.test.assertEquals("true", System.getProperty("sun.java2d.noddraw"))
    }

    @Test
    fun testEnableSynchronousMetalWindowResizeSetsPropertyOnMac() {
        enableSynchronousMetalWindowResize(osName = "Mac OS X")
        kotlin.test.assertEquals("true", System.getProperty("skiko.rendering.macos.metalSynchronousLiveResize"))
    }

    @Test
    fun testConfigurePlatformRenderingAppliesApplicationName() {
        configurePlatformRendering(osName = "Windows 10")
        kotlin.test.assertEquals(
            com.devuloopers.knet.domain.config.AppMetadata.APP_NAME,
            System.getProperty("apple.awt.application.name")
        )
    }
}
