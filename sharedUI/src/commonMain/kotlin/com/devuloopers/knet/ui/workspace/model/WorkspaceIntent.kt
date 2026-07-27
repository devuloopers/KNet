package com.devuloopers.knet.ui.workspace.model

import com.devuloopers.knet.widgets.WidgetType

/**
 * Sealed interface representing user intents for workspace layout configuration.
 */
sealed interface WorkspaceIntent {
    /** Toggle visibility of a grid widget. */
    data class ToggleWidget(val widget: WidgetType) : WorkspaceIntent

    /** Update panel width of the live traffic feed. */
    data class UpdateTrafficFeedWidth(val widthDp: Float) : WorkspaceIntent

    /** Update panel width of the right sidebar. */
    data class UpdateSidebarWidth(val widthDp: Float) : WorkspaceIntent

    /** Update panel height of the bottom tray. */
    data class UpdateBottomTrayHeight(val heightDp: Float) : WorkspaceIntent
}
