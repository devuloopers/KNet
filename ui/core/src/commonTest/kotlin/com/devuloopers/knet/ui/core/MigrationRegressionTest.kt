package com.devuloopers.knet.ui.core

import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetTypography
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Migration regression tests for `:ui:core`.
 *
 * Verifies public API availability for presentation tokens and composables.
 */
class MigrationRegressionTest {

    @Test
    fun `public design system symbols remain available`() {
        assertNotNull(KNetColors.BackgroundDark)
        assertNotNull(KNetTypography.Title)
    }
}
