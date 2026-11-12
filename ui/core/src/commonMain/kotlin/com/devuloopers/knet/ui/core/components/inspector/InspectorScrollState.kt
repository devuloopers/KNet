package com.devuloopers.knet.ui.core.components.inspector

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Retains the measured viewport across inspector selections while resetting its offset without animation.
 *
 * Replacing ScrollState on each identity discards viewport measurements: an unmeasured state's initial
 * maximum offset is not evidence of overflow. A transient scrollbar can otherwise change header/body height.
 * A null identity preserves the normal saveable scroll behavior of callers that do not select captures.
 */
@Composable
internal fun rememberInspectorScrollState(contentIdentity: String?): ScrollState {
    val state = rememberScrollState()
    LaunchedEffect(contentIdentity) {
        if (contentIdentity != null) state.scrollTo(0)
    }
    return state
}
