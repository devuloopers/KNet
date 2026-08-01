package com.devuloopers.knet.ui.desktop.apistudio.model

/**
 * Data DTO for active HTTP request tabs in API Studio.
 */
public data class RequestTab(
    val id: String,
    val title: String,
    val method: String = "GET",
    val isDirty: Boolean = false
)
