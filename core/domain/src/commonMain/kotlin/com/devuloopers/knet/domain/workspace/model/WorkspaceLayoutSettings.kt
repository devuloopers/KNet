package com.devuloopers.knet.domain.workspace.model

/**
 * Immutable domain model representing persisted workspace layout settings.
 *
 * @property isTrafficFeedVisible Whether Live Traffic Feed panel is open.
 * @property isInspectorVisible Whether Middle Inspector panel is open.
 * @property isRulesConsoleVisible Whether Breakpoint Rules tray is open.
 * @property isQuickReplayVisible Whether Replay Controller tray is open.
 * @property isNotesTagsVisible Whether Notes & Tags sidebar is open.
 * @property trafficFeedWidthDp Width of traffic feed panel in Dp.
 * @property sidebarWidthDp Width of right sidebar panel in Dp.
 * @property bottomTrayHeightDp Height of bottom tray panel in Dp.
 */
data class WorkspaceLayoutSettings(
    val isTrafficFeedVisible: Boolean = true,
    val isInspectorVisible: Boolean = true,
    val isRulesConsoleVisible: Boolean = false,
    val isQuickReplayVisible: Boolean = false,
    val isNotesTagsVisible: Boolean = false,
    val trafficFeedWidthDp: Float = 600f,
    val sidebarWidthDp: Float = 260f,
    val bottomTrayHeightDp: Float = 180f,
    val activeRequestSubTab: String = "BODY",
    val activeScriptPhase: String = "PRE_REQUEST",
    val activeResponseSubTab: String = "BODY",
    val activeSessionId: String = "",
    val scriptLanguage: String = "JAVASCRIPT",
    val proxyPort: Int = 8080,
    val autoClearTrafficOnStartup: Boolean = false,
    val theme: String = "DARK",
    val maxPayloadMb: Int = 10,
    val apiStudioTimeoutSeconds: Int = 60,
    val liveInterceptionTimeoutSeconds: Int = 60
)
