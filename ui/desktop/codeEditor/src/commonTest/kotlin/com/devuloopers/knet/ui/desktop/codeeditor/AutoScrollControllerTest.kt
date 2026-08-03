package com.devuloopers.knet.ui.desktop.codeeditor

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.AutoScrollController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying [AutoScrollController] velocity calculations and threshold bounds.
 */
class AutoScrollControllerTest {

    private val testScope = CoroutineScope(Dispatchers.Default)
    private val controller = AutoScrollController(testScope)

    @Test
    fun testSafeCenterZoneReturnsZeroVelocity() {
        val containerHeight = 500f
        val threshold = 40f
        val safeMouseY = 250f // Exactly in middle

        val velocity = controller.calculateVelocity(safeMouseY, containerHeight, threshold)
        assertEquals(0f, velocity, "Safe center zone should return 0 velocity")
    }

    @Test
    fun testBottomBoundaryReturnsPositiveScrollDownVelocity() {
        val containerHeight = 500f
        val threshold = 40f
        val bottomMouseY = 480f // Past bottom threshold (460px)

        val velocity = controller.calculateVelocity(bottomMouseY, containerHeight, threshold)
        assertTrue(velocity > 0f, "Bottom boundary should return positive scroll-down velocity")
    }

    @Test
    fun testTopBoundaryReturnsNegativeScrollUpVelocity() {
        val containerHeight = 500f
        val threshold = 40f
        val topMouseY = 10f // Inside top threshold (0..40px)

        val velocity = controller.calculateVelocity(topMouseY, containerHeight, threshold)
        assertTrue(velocity < 0f, "Top boundary should return negative scroll-up velocity")
    }

    @Test
    fun testVelocityAcceleratesFartherOutsideBoundary() {
        val containerHeight = 500f
        val threshold = 40f

        val nearVelocity = controller.calculateVelocity(470f, containerHeight, threshold)
        val farVelocity = controller.calculateVelocity(520f, containerHeight, threshold)

        assertTrue(farVelocity > nearVelocity, "Velocity should accelerate as mouse moves farther past boundary")
    }
}
