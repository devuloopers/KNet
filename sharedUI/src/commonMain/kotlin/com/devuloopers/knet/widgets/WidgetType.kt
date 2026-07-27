package com.devuloopers.knet.widgets

/**
 * Lists the available layout widgets in KNet's dynamic grid.
 *
 * Mapped to support dynamic insertion, removal, and sizing configuration.
 *
 * @property title The display header title of the widget.
 */
enum class WidgetType(val title: String) {
    /** The chronological list of HTTP requests. */
    TRAFFIC_FEED("Live Traffic Feed"),
    /** Unified transaction inspector panel (Overview, Headers, Request, Response, Timeline). */
    INSPECTOR("Inspector"),
    /** Active rules manager table (Breakpoints, Rewrites, Drops). */
    RULES_CONSOLE("Breakpoint Rules"),
    /** Batch trigger replay count inputs. */
    QUICK_REPLAY("Replay Controller"),
    /** Custom tags and notes text area. */
    NOTES_TAGS("Notes & Tags")
}

