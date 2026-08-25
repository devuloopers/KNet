package com.devuloopers.knet.ui.desktop.apistudio.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.components.split.HorizontalSplitPane

/**
 * Standard API Studio authoring/result workspace used to the right of the shared Collections pane.
 *
 * Protocol features own the content of both panes. This component owns only their common resizable geometry,
 * keeping response inspectors and streamed timelines full-height without teaching the shell protocol semantics.
 */
@Composable
fun ApiStudioSplitWorkspace(
    authoringRatio: Float,
    onAuthoringRatioChange: (Float) -> Unit,
    authoringPane: @Composable (Modifier) -> Unit,
    resultPane: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalSplitPane(
        splitRatio = authoringRatio,
        onSplitRatioChange = onAuthoringRatioChange,
        firstPane = authoringPane,
        secondPane = resultPane,
        modifier = modifier,
        minSplitRatio = MINIMUM_AUTHORING_RATIO,
        maxSplitRatio = MAXIMUM_AUTHORING_RATIO,
    )
}

private const val MINIMUM_AUTHORING_RATIO = 0.2f
private const val MAXIMUM_AUTHORING_RATIO = 0.8f
