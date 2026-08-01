package com.devuloopers.knet.ui.desktop.inspector.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.desktop.inspector.viewmodel.InspectorViewModel

/**
 * Outer inspector widget container.
 */
@Composable
public fun InspectorWidget(
    viewModel: InspectorViewModel,
    modifier: Modifier = Modifier
) {
    InspectorPanel(viewModel = viewModel, modifier = modifier)
}
