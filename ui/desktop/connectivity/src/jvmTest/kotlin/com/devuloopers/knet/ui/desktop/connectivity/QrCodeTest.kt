package com.devuloopers.knet.ui.desktop.connectivity

import com.devuloopers.knet.ui.desktop.connectivity.components.encodeQrCode
import com.devuloopers.knet.ui.desktop.connectivity.components.formatRemainingTime
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QrCodeTest {
    @Test
    fun `stable setup URL produces a square QR matrix with quiet zone and finder pattern`() {
        val matrix = encodeQrCode("http://192.0.2.10:8181/setup")

        assertTrue(matrix.width >= 33)
        assertTrue(matrix.width == matrix.height)
        assertFalse(matrix.isDark(0, 0))
        assertTrue((0 until 8).any { x -> (0 until 8).any { y -> matrix.isDark(x + 2, y + 2) } })
    }

    @Test
    fun `invitation countdown stays stable at minute boundaries`() {
        assertEquals("5:00", formatRemainingTime(300L))
        assertEquals("0:09", formatRemainingTime(9L))
        assertEquals("0:00", formatRemainingTime(-1L))
    }
}
