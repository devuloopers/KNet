package com.devuloopers.knet.ui.desktop.apistudio.model

/**
 * Data DTO representing current request editor fields.
 */
public data class RequestEditorState(
    val url: String = "",
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
    val scriptLanguage: String = "JAVASCRIPT",
    val activeSubTab: String = "BODY",
    val activeScriptPhase: String = "PRE_REQUEST",
    val activeResponseSubTab: String = "BODY",
    val linkedUnsavedId: String? = null
)

