package com.devuloopers.knet.ui.desktop.scripting.model

import com.devuloopers.knet.ui.core.table.KeyValuePair

/**
 * UI presentation model exposing variables and network scope to automation script context.
 */
public data class ExecutionContext(
    val requests: List<KeyValuePair> = emptyList(),
    val responses: List<KeyValuePair> = emptyList(),
    val environment: List<KeyValuePair> = emptyList(),
    val globals: List<KeyValuePair> = emptyList(),
    val variables: List<KeyValuePair> = emptyList()
)
