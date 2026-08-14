package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.engine.script.api.ScriptLanguage
import com.devuloopers.knet.ui.desktop.apistudio.response.ResponseSubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyState

public typealias RequestSubTab = com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab

/**
 * Data DTO representing current request editor fields.
 *
 * @property url Current request target URL string.
 * @property method HTTP method string (GET, POST, PUT, etc.).
 * @property queryParams List of request query parameter key-value pairs.
 * @property headers List of request header key-value pairs.
 * @property cookies List of request cookie key-value pairs.
 * @property authState Strongly-typed authentication state configuration.
 * @property authType Display label string of authentication type.
 * @property authToken Credential token string.
 * @property bodyType Request body mode representation string.
 * @property bodyPayload Raw text payload of the request body.
 * @property preRequestScript Script code executed before request execution.
 * @property testScript Script code executed after response receipt.
 * @property scriptLanguage Target scripting language engine enum.
 * @property activeSubTab Strongly-typed active request sub-tab selection.
 * @property activeScriptPhase Strongly-typed active script editing phase.
 * @property activeResponseSubTab Strongly-typed active response inspector sub-tab.
 * @property linkedUnsavedId Linked unsaved draft session ID, or null if saved collection request.
 */
public object RequestEditorDefaults {
    public val DEFAULT_HEADERS: List<Pair<String, String>> = listOf(
        "Content-Type" to "application/json",
        "Accept" to "*/*",
        "Accept-Encoding" to "gzip, deflate, br",
        "Connection" to "keep-alive",
        "User-Agent" to "KNet/1.0.0"
    )
}

public data class RequestEditorState(
    val url: String = "",
    val method: String = "GET",
    val queryParams: List<Pair<String, String>> = emptyList(),
    val headers: List<Pair<String, String>> = RequestEditorDefaults.DEFAULT_HEADERS,
    val cookies: List<Pair<String, String>> = emptyList(),

    val authState: AuthState = AuthState(),
    val bodyState: RequestBodyState = RequestBodyState(mode = RequestBodyMode.NONE),
    val authType: String = "No Auth",
    val authToken: String = "",
    val preRequestScript: String = "",
    val testScript: String = "",
    val scriptLanguage: ScriptLanguage = ScriptLanguage.JAVASCRIPT,
    val activeSubTab: RequestSubTab = RequestSubTab.BODY,
    val activeScriptPhase: ScriptPhase = ScriptPhase.PRE_REQUEST,
    val activeResponseSubTab: ResponseSubTab = ResponseSubTab.BODY,
    val linkedUnsavedId: String? = null,
    val sessionType: SessionType = SessionType.UNSAVED_DRAFT
) {
    /** Raw text payload string derived directly from [bodyState] SSOT. */
    val bodyPayload: String get() = bodyState.payloadText

    /** User-facing display label string derived directly from [bodyState] SSOT mode. */
    val bodyType: String get() = if (bodyState.mode == RequestBodyMode.NONE) "None" else bodyState.mode.name
}
