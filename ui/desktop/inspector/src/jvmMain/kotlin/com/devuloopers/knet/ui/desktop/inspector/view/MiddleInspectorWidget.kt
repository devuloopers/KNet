package com.devuloopers.knet.ui.desktop.inspector.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.desktop.inspector.viewmodel.InspectorViewModel

/**
 * 2-pane detailed transaction inspector view widget.
 */
@Composable
public fun MiddleInspectorWidget(
    viewModel: InspectorViewModel,
    modifier: Modifier = Modifier
) {
    InspectorPanel(viewModel = viewModel, modifier = modifier)
}
