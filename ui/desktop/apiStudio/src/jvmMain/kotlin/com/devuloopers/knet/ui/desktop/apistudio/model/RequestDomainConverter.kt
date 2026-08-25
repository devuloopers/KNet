package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.ApiRequestBodyField
import com.devuloopers.knet.domain.collection.model.ApiRequestScripts
import com.devuloopers.knet.domain.collection.model.RequestCookie
import com.devuloopers.knet.domain.collection.model.RequestHeader
import com.devuloopers.knet.domain.collection.model.RequestQueryParameter
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.clientNetwork.model.RawBodyFormat
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.ui.desktop.httppanel.model.toApiRequestAuth
import com.devuloopers.knet.ui.desktop.httppanel.model.toAuthState
import com.devuloopers.knet.ui.desktop.httppanel.model.RawSubFormat
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyState
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry

/**
 * Extension converters building domain [SavedApiRequest] instances from presentation state DTOs.
 */
object RequestDomainConverter {

    /**
     * Converts a [RequestEditorState] presentation model into a domain [SavedApiRequest] entity.
     *
     * @param id Target request identifier string.
     * @param name Target request title name string.
     * @param nameOrigin Whether the title is generated or explicitly user-owned.
     * @return Formatted domain [SavedApiRequest] entity.
     */
    fun RequestEditorState.toDomainSavedRequest(
        id: String,
        name: String,
        nameOrigin: RequestNameOrigin
    ): SavedApiRequest {
        return SavedApiRequest(
            id = id,
            name = name,
            nameOrigin = nameOrigin,
            method = method,
            httpVersionPreference = httpVersionPreference,
            url = this.url,
            queryParameters = queryParams.map {
                RequestQueryParameter(it.key, it.value, it.enabled)
            },
            headers = headers.map {
                RequestHeader(
                    key = it.key,
                    value = it.value,
                    isEnabled = it.enabled,
                    isAuto = it.id in automaticHeaderIds
                )
            },
            cookies = cookies.map { RequestCookie(it.key, it.value, it.enabled) },
            body = bodyState.toDomainBody(),
            scripts = ApiRequestScripts(
                preRequest = this.preRequestScript,
                test = this.testScript,
                language = scriptLanguage
            ),
            auth = this.authState.toApiRequestAuth()
        )
    }

    /**
     * Reconstructs a complete editor document from a persisted domain request in one operation.
     *
     * @param previousState Current editor state whose view-only tab selections are retained.
     * @return Fully hydrated editor state suitable for one atomic ViewModel publication.
     */
    fun SavedApiRequest.toEditorState(
        previousState: RequestEditorState
    ): RequestEditorState {
        val bodyMode = body.type.toEditorMode()
        val rawSubFormat = when (body.type) {
            RequestBodyType.XML -> RawSubFormat.XML
            else -> body.rawFormat.toEditorFormat()
        }
        val authState = auth.toAuthState()
        return RequestEditorState(
            url = url,
            method = method,
            httpVersionPreference = httpVersionPreference,
            queryParams = (queryParameters.ifEmpty {
                com.devuloopers.knet.domain.util.UrlQueryStringParser.parseQueryParams(url).map { (name, value) ->
                    RequestQueryParameter(name, value)
                }
            }).mapIndexed { index, parameter ->
                KeyValueEntry(
                    id = "query-$index",
                    key = parameter.name,
                    value = parameter.value,
                    enabled = parameter.isEnabled
                )
            },
            headers = headers.mapIndexed { index, header ->
                KeyValueEntry(
                    id = "header-$index",
                    key = header.key,
                    value = header.value,
                    enabled = header.isEnabled
                )
            },
            cookies = cookies.mapIndexed { index, cookie ->
                KeyValueEntry(
                    id = "cookie-$index",
                    key = cookie.name,
                    value = cookie.value,
                    enabled = cookie.isEnabled
                )
            },
            automaticHeaderIds = headers.mapIndexedNotNull { index, header ->
                "header-$index".takeIf { header.isAuto }
            }.toSet(),
            authState = authState,
            bodyState = RequestBodyState(
                mode = bodyMode,
                rawSubFormat = rawSubFormat,
                payloadText = body.content,
                formDataEntries = body.formDataFields.map { it.toKeyValueEntry() },
                urlEncodedEntries = body.urlEncodedFields.map { it.toKeyValueEntry() }
            ),
            preRequestScript = scripts.preRequest,
            testScript = scripts.test,
            scriptLanguage = scripts.language,
            activeSubTab = previousState.activeSubTab,
            activeScriptPhase = previousState.activeScriptPhase,
            activeResponseSubTab = previousState.activeResponseSubTab
        )
    }

    private fun RequestBodyState.toDomainBody(): ApiRequestBody = ApiRequestBody(
        content = payloadText,
        type = mode.toDomainType(),
        rawFormat = rawSubFormat.toDomainFormat(),
        formDataFields = formDataEntries.map { it.toDomainBodyField() },
        urlEncodedFields = urlEncodedEntries.map { it.toDomainBodyField() }
    )

    private fun KeyValueEntry.toDomainBodyField(): ApiRequestBodyField = ApiRequestBodyField(
        id = id,
        key = key,
        value = value,
        isEnabled = enabled
    )

    private fun ApiRequestBodyField.toKeyValueEntry(): KeyValueEntry = KeyValueEntry(
        id = id,
        key = key,
        value = value,
        enabled = isEnabled
    )

    private fun RequestBodyMode.toDomainType(): RequestBodyType = when (this) {
        RequestBodyMode.NONE -> RequestBodyType.NONE
        RequestBodyMode.JSON -> RequestBodyType.JSON
        RequestBodyMode.FORM_DATA -> RequestBodyType.FORM_DATA
        RequestBodyMode.X_WWW_FORM_URLENCODED -> RequestBodyType.X_WWW_FORM_URLENCODED
        RequestBodyMode.RAW -> RequestBodyType.RAW_TEXT
        RequestBodyMode.GRAPHQL -> RequestBodyType.GRAPHQL
    }

    private fun RequestBodyType.toEditorMode(): RequestBodyMode = when (this) {
        RequestBodyType.NONE -> RequestBodyMode.NONE
        RequestBodyType.JSON -> RequestBodyMode.JSON
        RequestBodyType.XML,
        RequestBodyType.RAW_TEXT -> RequestBodyMode.RAW
        RequestBodyType.FORM_DATA,
        RequestBodyType.MULTIPART -> RequestBodyMode.FORM_DATA
        RequestBodyType.X_WWW_FORM_URLENCODED -> RequestBodyMode.X_WWW_FORM_URLENCODED
        RequestBodyType.GRAPHQL -> RequestBodyMode.GRAPHQL
    }

    private fun RawSubFormat.toDomainFormat(): RawBodyFormat = when (this) {
        RawSubFormat.TEXT -> RawBodyFormat.TEXT
        RawSubFormat.JSON -> RawBodyFormat.JSON
        RawSubFormat.XML -> RawBodyFormat.XML
        RawSubFormat.HTML -> RawBodyFormat.HTML
        RawSubFormat.JAVASCRIPT -> RawBodyFormat.JAVASCRIPT
    }

    private fun RawBodyFormat.toEditorFormat(): RawSubFormat = when (this) {
        RawBodyFormat.TEXT -> RawSubFormat.TEXT
        RawBodyFormat.JSON -> RawSubFormat.JSON
        RawBodyFormat.XML -> RawSubFormat.XML
        RawBodyFormat.HTML -> RawSubFormat.HTML
        RawBodyFormat.JAVASCRIPT -> RawSubFormat.JAVASCRIPT
    }
}
