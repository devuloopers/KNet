package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for FeedToolbar intents in `:ui:desktop:traffic`.
 */
class FeedToolbarTest {

    @Test
    fun `PauseFeed intent exists`() {
        val intent: TrafficIntent = TrafficIntent.PauseFeed
        assertEquals(TrafficIntent.PauseFeed, intent)
    }
}
