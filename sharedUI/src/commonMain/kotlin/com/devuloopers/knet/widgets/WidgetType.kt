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
    /** Selected request metadata, status, target host, and Forward/Drop controls. */
    TRANSACTION_OVERVIEW("Overview"),
    /** Selected request URL query parameter tree map. */
    REQUEST_TREE("Query Parameters"),
    /** Selected request payload body JSON text. */
    REQUEST_BODY("Request Body"),
    /** Selected response payload body JSON text. */
    RESPONSE_BODY("Response Body"),
    /** Execution duration timeline gauges (DNS, Connection, Download). */
    TIMINGS("Connection Timings"),
    /** Active rules manager table (Breakpoints, Rewrites, Drops). */
    RULES_CONSOLE("Breakpoint Rules"),
    /** Batch trigger replay count inputs. */
    QUICK_REPLAY("Replay Controller"),
    /** Custom tags and notes text area. */
    NOTES_TAGS("Notes & Tags"),
    /** Transaction inspector panel (Overview, Headers, Request Body, Response Body, Timings). */
    INSPECTOR("Inspector")
}
