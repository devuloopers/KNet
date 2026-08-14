package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.clientNetwork.model.HttpTimings
import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object TestFixtures {

    fun createHttpRequest(
        id: String = "req-test-1",
        method: String = "GET",
        url: String = "https://api.knet.dev/users",
        protocol: String = "HTTP/1.1",
        headers: List<Pair<String, String>> = listOf("Accept" to "application/json"),
        body: ByteArray? = null,
        timestamp: Long = 1000L
    ): HttpRequest = HttpRequest(
        id = id,
        method = method,
        url = url,
        protocol = protocol,
        headers = headers,
        body = body,
        timestamp = timestamp
    )

    fun createHttpResponse(
        statusCode: Int = 200,
        statusText: String = "OK",
        headers: List<Pair<String, String>> = listOf("Content-Type" to "application/json"),
        body: ByteArray? = "{\"status\":\"success\"}".encodeToByteArray(),
        timestamp: Long = 1200L
    ): HttpResponse = HttpResponse(
        statusCode = statusCode,
        statusText = statusText,
        headers = headers,
        body = body,
        timestamp = timestamp
    )

    fun createHttpTransaction(
        id: String = "tx-test-1",
        request: HttpRequest = createHttpRequest(),
        response: HttpResponse? = createHttpResponse(),
        requestBodyPath: String? = null,
        responseBodyPath: String? = null,
        durationMs: Long = 200L,
        timestamp: Long = 1000L,
        timings: HttpTimings = HttpTimings(dnsMs = 10, tcpMs = 20, tlsMs = 30, ttfbMs = 100, downloadMs = 40)
    ): HttpTransaction = HttpTransaction(
        id = id,
        request = request,
        response = response,
        requestBodyPath = requestBodyPath,
        responseBodyPath = responseBodyPath,
        durationMs = durationMs,
        timestamp = timestamp,
        timings = timings
    )

    fun createSavedApiRequest(
        id: String = "req-saved-1",
        name: String = "Get Users",
        method: HttpMethod = HttpMethod.GET,
        url: String = "https://api.knet.dev/v1/users",
        body: ApiRequestBody = ApiRequestBody()
    ): SavedApiRequest = SavedApiRequest(
        id = id,
        name = name,
        method = method,
        url = url,
        body = body
    )

    fun createCollection(
        id: String = "col-1",
        name: String = "KNet Core API",
        folders: List<CollectionFolder> = listOf(
            CollectionFolder(
                id = "folder-1",
                name = "Users",
                requests = listOf(createSavedApiRequest())
            )
        )
    ): ApiCollection = ApiCollection(
        id = id,
        name = name,
        folders = folders
    )

    fun createRuleModel(
        id: String = "rule-1",
        name: String = "Mock Auth Token",
        type: com.devuloopers.knet.domain.rules.model.BreakpointPhase = com.devuloopers.knet.domain.rules.model.BreakpointPhase.BOTH,
        condition: String = "url CONTAINS 'auth'",
        action: String = "Set Header",
        enabled: Boolean = true,
        protocolCriteria: com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria = com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.HttpDefault
    ): RuleModel = RuleModel(
        id = id,
        name = name,
        type = type,
        condition = condition,
        action = action,
        enabled = enabled,
        protocolCriteria = protocolCriteria
    )
}

class FakeRulesRepository : RulesRepository {

    private val _rules = MutableStateFlow<List<RuleModel>>(emptyList())
    override val rulesFlow: Flow<List<RuleModel>> = _rules.asStateFlow()

    private val _isGlobalInterceptionEnabled = MutableStateFlow(true)
    override val isGlobalInterceptionEnabled: Flow<Boolean> = _isGlobalInterceptionEnabled.asStateFlow()

    override suspend fun toggleGlobalInterception(enabled: Boolean) {
        _isGlobalInterceptionEnabled.value = enabled
    }

    fun setRules(rules: List<RuleModel>) {
        _rules.value = rules
    }

    override suspend fun saveRule(rule: RuleModel) {
        val current = _rules.value.toMutableList()
        val index = current.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            current[index] = rule
        } else {
            current.add(rule)
        }
        _rules.value = current
    }

    override suspend fun toggleRule(ruleId: String, enabled: Boolean) {
        val current = _rules.value.map {
            if (it.id == ruleId) it.copy(enabled = enabled) else it
        }
        _rules.value = current
    }

    override suspend fun deleteRule(ruleId: String) {
        _rules.value = _rules.value.filterNot { it.id == ruleId }
    }
}
