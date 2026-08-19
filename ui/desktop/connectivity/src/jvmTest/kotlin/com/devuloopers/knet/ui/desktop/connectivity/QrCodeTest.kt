package com.devuloopers.knet.ui.desktop.connectivity

import com.devuloopers.knet.ui.desktop.connectivity.components.encodeQrCode
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
}
