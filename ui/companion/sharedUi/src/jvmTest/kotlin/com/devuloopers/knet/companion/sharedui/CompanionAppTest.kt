package com.devuloopers.knet.companion.sharedui

import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.network_ready
import com.devuloopers.knet.companion.sharedui.generated.resources.network_unavailable
import com.devuloopers.knet.companion.sharedui.generated.resources.network_unknown
import com.devuloopers.knet.companion.sharedui.generated.resources.paired_desktop_count_one
import com.devuloopers.knet.companion.sharedui.generated.resources.paired_desktop_count_other
import kotlin.test.Test
import kotlin.test.assertEquals

class CompanionAppTest {
    @Test
    fun `network states select stable shared resources`() {
        assertEquals(Res.string.network_ready, CompanionNetworkState.Available(metered = false).statusResource())
        assertEquals(Res.string.network_unavailable, CompanionNetworkState.Unavailable.statusResource())
        assertEquals(Res.string.network_unknown, CompanionNetworkState.Unknown.statusResource())
    }

    @Test
    fun `empty registrations select plural count resource`() {
        assertEquals(
            Res.string.paired_desktop_count_other,
            pairedDesktopCountResource(count = 0),
        )
    }

    @Test
    fun `single registration selects singular count resource`() {
        assertEquals(
            Res.string.paired_desktop_count_one,
            pairedDesktopCountResource(count = 1),
        )
    }
}
