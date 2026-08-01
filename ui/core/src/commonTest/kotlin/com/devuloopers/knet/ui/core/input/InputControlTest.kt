package com.devuloopers.knet.ui.core.input

import com.devuloopers.knet.ui.core.theme.KNetDimensions
import com.devuloopers.knet.ui.core.theme.KNetShapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests verifying input control shape and dimension tokens in `:ui:core`.
 */
class InputControlTest {

    @Test
    fun `input control dimension tokens are valid`() {
        assertEquals(28.0f, KNetDimensions.InputFieldHeight.value)
        assertNotNull(KNetShapes.Medium)
        assertNotNull(KNetShapes.Small)
    }
}
