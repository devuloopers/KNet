package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.scripting.model.ScriptPhase
import com.devuloopers.knet.ui.desktop.apistudio.response.ResponseSubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.AuthState
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyState
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry

/** Default authored values for a newly created API Studio document. */
object RequestEditorDefaults {
    /** Default editable headers for a newly authored API Studio request. */
    val DEFAULT_HEADERS: List<KeyValueEntry> = listOf(
        KeyValueEntry("default-content-type", "Content-Type", "application/json"),
        KeyValueEntry("default-accept", "Accept", "*/*"),
        KeyValueEntry("default-accept-encoding", "Accept-Encoding", "gzip, deflate, br"),
        KeyValueEntry("default-connection", "Connection", "keep-alive"),
        KeyValueEntry("default-user-agent", "User-Agent", "KNet/1.0.0")
    )
}

/**
 * Presentation projection of the active authored request and editor-only selection state.
 *
 * @property url Current request target URL string.
 * @property method Strongly typed HTTP method.
 * @property queryParams Ordered request query-parameter rows including disabled rows.
 * @property headers Ordered request header rows including disabled rows.
 * @property cookies Ordered request cookie rows including disabled rows.
 * @property automaticHeaderIds Identifiers of transport-generated header rows retained across edits.
 * @property authState Strongly typed authentication configuration.
 * @property bodyState Strongly typed request body including raw and structured authoring state.
 * @property preRequestScript Script code executed before request execution.
 * @property testScript Script code executed after response receipt.
 * @property scriptLanguage Target scripting language engine enum.
 * @property activeSubTab Strongly typed active request sub-tab selection.
 * @property activeScriptPhase Strongly typed active script editing phase.
 * @property activeResponseSubTab Strongly typed active response inspector sub-tab.
 */
data class RequestEditorState(
    val url: String = "",
    val method: HttpMethod = HttpMethod.GET,
    val queryParams: List<KeyValueEntry> = emptyList(),
    val headers: List<KeyValueEntry> = RequestEditorDefaults.DEFAULT_HEADERS,
    val cookies: List<KeyValueEntry> = emptyList(),
    val automaticHeaderIds: Set<String> = emptySet(),

    val authState: AuthState = AuthState(),
    val bodyState: RequestBodyState = RequestBodyState(mode = RequestBodyMode.NONE),
    val preRequestScript: String = "",
    val testScript: String = "",
    val scriptLanguage: ScriptLanguage = ScriptLanguage.JAVASCRIPT,
    val activeSubTab: InspectorSubTab = InspectorSubTab.BODY,
    val activeScriptPhase: ScriptPhase = ScriptPhase.PRE_REQUEST,
    val activeResponseSubTab: ResponseSubTab = ResponseSubTab.BODY
)
