package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object TestFixtures {

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

    fun createBreakpointRule(
        id: String = "rule-1",
        name: String = "Mock Auth Token",
        phase: BreakpointPhase = BreakpointPhase.BOTH,
        urlPattern: String = "*auth*",
        method: HttpMethod? = null,
        enabled: Boolean = true,
        protocolCriteria: ProtocolMatchCriteria = ProtocolMatchCriteria.HttpDefault
    ): BreakpointRule = BreakpointRule(
        id = id,
        name = name,
        phase = phase,
        urlPattern = urlPattern,
        method = method,
        enabled = enabled,
        protocolCriteria = protocolCriteria
    )
}

class FakeRulesRepository : RulesRepository {

    private val _rules = MutableStateFlow<List<BreakpointRule>>(emptyList())
    override val rulesFlow: Flow<List<BreakpointRule>> = _rules.asStateFlow()

    private val _isGlobalInterceptionEnabled = MutableStateFlow(true)
    override val isGlobalInterceptionEnabled: Flow<Boolean> = _isGlobalInterceptionEnabled.asStateFlow()

    override suspend fun toggleGlobalInterception(enabled: Boolean) {
        _isGlobalInterceptionEnabled.value = enabled
    }

    fun setRules(rules: List<BreakpointRule>) {
        _rules.value = rules
    }

    override suspend fun saveRule(rule: BreakpointRule) {
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
