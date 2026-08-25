package com.devuloopers.knet.ui.desktop.apistudio.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import kotlin.math.roundToInt

/**
 * Deterministic vertical layout for streamed-protocol authoring surfaces.
 *
 * Controls may consume up to [controlsMaximumHeightFraction], the toolbar keeps its natural height, and the editor
 * receives the exact remaining height. All three slots are measured during one layout pass, avoiding the transient
 * weighted-editor geometry produced by nested constraint-aware layouts.
 */
@Composable
fun ApiStudioAuthoringEditorLayout(
    controls: @Composable () -> Unit,
    toolbar: @Composable () -> Unit,
    editor: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    controlsMaximumHeightFraction: Float = DEFAULT_CONTROLS_MAXIMUM_HEIGHT_FRACTION,
) {
    require(controlsMaximumHeightFraction in 0f..1f) {
        "Controls maximum height fraction must be between zero and one."
    }

    Layout(
        modifier = modifier,
        content = {
            Box { controls() }
            Box { toolbar() }
            Box { editor() }
        },
    ) { measurables, constraints ->
        val layoutWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val layoutHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        val fixedWidthConstraints = constraints.copy(
            minWidth = layoutWidth,
            maxWidth = layoutWidth,
            minHeight = 0,
            maxHeight = layoutHeight,
        )

        val toolbarPlaceable = measurables[TOOLBAR_SLOT].measure(fixedWidthConstraints)
        val maximumControlsHeight = (layoutHeight * controlsMaximumHeightFraction).roundToInt()
        val controlsPlaceable = measurables[CONTROLS_SLOT].measure(
            fixedWidthConstraints.copy(maxHeight = maximumControlsHeight),
        )
        val editorHeight = (layoutHeight - controlsPlaceable.height - toolbarPlaceable.height).coerceAtLeast(0)
        val editorPlaceable = measurables[EDITOR_SLOT].measure(
            fixedWidthConstraints.copy(minHeight = editorHeight, maxHeight = editorHeight),
        )

        layout(width = layoutWidth, height = layoutHeight) {
            controlsPlaceable.placeRelative(x = 0, y = 0)
            toolbarPlaceable.placeRelative(x = 0, y = controlsPlaceable.height)
            editorPlaceable.placeRelative(
                x = 0,
                y = controlsPlaceable.height + toolbarPlaceable.height,
            )
        }
    }
}

private const val CONTROLS_SLOT = 0
private const val TOOLBAR_SLOT = 1
private const val EDITOR_SLOT = 2
private const val DEFAULT_CONTROLS_MAXIMUM_HEIGHT_FRACTION = 0.45f
