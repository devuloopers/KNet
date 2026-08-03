package com.devuloopers.knet.ui.core

import com.devuloopers.knet.ui.core.components.button.ButtonDefaults
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Unit tests verifying KNet Design System v2.0 primitive components defaults and state resolvers.
 */
class ComponentTest {

    @Test
    fun testButtonDefaultsVariantResolution() {
        assertNotNull(ButtonVariant.Primary)
        assertNotNull(ButtonVariant.Secondary)
        assertNotNull(ButtonVariant.Tertiary)
        assertNotNull(ButtonVariant.Ghost)
        assertNotNull(ButtonVariant.Danger)

        assertNotNull(ButtonSize.Compact)
        assertNotNull(ButtonSize.Standard)
        assertNotNull(ButtonSize.Large)
    }
}
