package com.devuloopers.knet.ui.desktop.apistudio.model

/**
 * Data DTO for active HTTP request tabs in API Studio.
 *
 * @property id Unique tab identifier string.
 * @property title Tab display title string.
 * @property method HTTP method string (GET, POST, etc.).
 * @property isDirty True if the tab contains unsaved changes.
 */
public data class RequestTab(
    val id: String,
    val title: String,
    val method: String = "GET",
    val isDirty: Boolean = false
)
