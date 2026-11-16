package com.devuloopers.knet.ui.desktop.networkconditions.view

import com.devuloopers.knet.website.*
import com.devuloopers.knet.ui.desktop.networkconditions.model.*
import com.devuloopers.knet.domain.networkconditions.*
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot
import kotlin.test.Test

/** Real condition workspace supplied only with deterministic, authored telemetry. */
class WebsiteNetworkArtworkTest {
    /** Captures the same profile toolbar, tabs and Canvas graph used by the desktop product. */
    @Test fun renderNetworkConditions() {
        val profile = NetworkConditionBuiltIns.all.first { it.name == "Streaming 100 kbps" }
        val state = NetworkConditionsState(workspaceName = "Pixel 8",
            configuration = NetworkConditionConfiguration(enabled = true, globalProfileId = profile.id),
            throughput = NetworkThroughputHistory(
                LongArray(60) { 64_000L + (it * 7919L % 29_000L) },
                LongArray(60) { 12_000L + (it * 3501L % 18_000L) }, BooleanArray(60) { true }, 60),
            runtime = NetworkConditionRuntimeSnapshot(activeFlows = 4, queuedBytes = 2048,
                downloadedBytes = 1_048_576, uploadedBytes = 262_144))
        renderArtwork(ArtworkScene.NETWORK_CONDITIONS, listOf("Overview", "Streaming 100 kbps", "Actual throughput")) {
            NetworkConditionsWorkspace(state, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {})
        }
    }
}
