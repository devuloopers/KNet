package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.ApiRequestScripts
import com.devuloopers.knet.domain.collection.model.RequestHeader
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem
import com.devuloopers.knet.ui.desktop.httppanel.model.toApiRequestAuth

/**
 * Extension converters building domain [SavedApiRequest] instances from presentation state DTOs.
 */
object RequestDomainConverter {

    /**
     * Converts a [RequestEditorState] presentation model into a domain [SavedApiRequest] entity.
     *
     * @param id Target request identifier string.
     * @param name Target request title name string.
     * @return Formatted domain [SavedApiRequest] entity.
     */
    fun RequestEditorState.toDomainSavedRequest(id: String, name: String): SavedApiRequest {
        return SavedApiRequest(
            id = id,
            name = name,
            method = HttpMethod.fromToken(this.method),
            url = this.url,
            headers = this.headers.map { RequestHeader(it.first, it.second) },
            body = ApiRequestBody(content = this.bodyPayload, type = this.bodyType),
            scripts = ApiRequestScripts(preRequest = this.preRequestScript, test = this.testScript),
            auth = this.authState.toApiRequestAuth()
        )
    }

    /**
     * Converts a [SidebarRequestItem] presentation model into a domain [SavedApiRequest] entity.
     *
     * @param overrideName Optional replacement title name string.
     * @return Formatted domain [SavedApiRequest] entity.
     */
    fun SidebarRequestItem.toDomainSavedRequest(overrideName: String? = null): SavedApiRequest {
        return SavedApiRequest(
            id = this.id,
            name = overrideName?.trim() ?: this.name,
            method = HttpMethod.fromToken(this.method),
            url = this.url,
            headers = this.headers.map { RequestHeader(it.first, it.second) },
            body = ApiRequestBody(content = this.bodyPayload, type = this.bodyType),
            scripts = ApiRequestScripts(preRequest = this.preRequestScript, test = this.testScript),
            auth = this.authState.toApiRequestAuth()
        )
    }
}
