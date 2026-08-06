package com.devuloopers.knet.ui.desktop.apistudio.model

/**
 * Data DTO representing current request editor fields.
 */
public data class RequestEditorState(
    val url: String = "https://api.example.com/v1/users",
    val method: String = "GET",
    val queryParams: List<Pair<String, String>> = emptyList(),
    val headers: List<Pair<String, String>> = emptyList(),
    val cookies: List<Pair<String, String>> = emptyList(),

    val authType: String = "No Auth",
    val authToken: String = "",
    val bodyType: String = "None",
    val bodyPayload: String = "",
    val preRequestScript: String = "",
    val testScript: String = "",
    val activeSubTab: String = "BODY"
)
