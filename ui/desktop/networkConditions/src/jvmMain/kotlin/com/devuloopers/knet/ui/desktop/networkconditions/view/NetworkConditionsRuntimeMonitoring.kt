package com.devuloopers.knet.ui.desktop.networkconditions.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.devuloopers.knet.ui.desktop.networkconditions.viewmodel.NetworkConditionsViewModel

/**
 * Owns one telemetry subscription across the shell's Traffic and Network Conditions destinations.
 * Keeping [active] true during their handoff preserves the sampler/history; leaving both stops monitoring.
 * @param viewModel Shared feature owner, also used by global controls and the Overview graph.
 * @param active Whether either throughput consumer is visible in the workspace.
 */
@Composable
fun NetworkConditionsRuntimeMonitoring(viewModel: NetworkConditionsViewModel, active: Boolean) {
    DisposableEffect(viewModel, active) {
        if (active) viewModel.startRuntimeMonitoring()
        onDispose { if (active) viewModel.stopRuntimeMonitoring() }
    }
}
