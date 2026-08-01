package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.ui.core.table.KeyValuePair

/**
 * Data DTO representing current request editor fields.
 */
public data class RequestEditorState(
    val url: String = "https://api.example.com/v1/users",
    val method: String = "GET",
    val queryParams: List<KeyValuePair> = emptyList(),
    val headers: List<KeyValuePair> = emptyList(),
    val cookies: List<KeyValuePair> = emptyList(),
    val authType: String = "No Auth",
    val authToken: String = "",
    val bodyType: String = "None",
    val bodyPayload: String = "",
    val preRequestScript: String = "",
    val testScript: String = ""
)
