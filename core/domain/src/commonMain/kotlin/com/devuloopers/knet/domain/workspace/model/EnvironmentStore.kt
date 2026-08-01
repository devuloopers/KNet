package com.devuloopers.knet.domain.workspace.model

/**
 * In-memory thread-safe environment store for workspace & script variable substitution.
 */
class EnvironmentStore(
    val variables: MutableMap<String, String> = mutableMapOf()
) {
    fun get(key: String): String? = variables[key]
    fun set(key: String, value: String) { variables[key] = value }
    fun clear() { variables.clear() }
}
