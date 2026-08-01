package com.devuloopers.knet.ui.desktop.apistudio.response

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.desktop.apistudio.component.ResponseSummary
import com.devuloopers.knet.ui.desktop.apistudio.model.ResponsePresentation

/**
 * Response status bar header displaying HTTP status badge and summary metrics.
 *
 * @param presentation Response presentation model.
 * @param modifier Layout modifier.
 */
@Composable
public fun ResponseStatusBar(
    presentation: ResponsePresentation,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ResponseSummary(presentation = presentation)
    }
}
