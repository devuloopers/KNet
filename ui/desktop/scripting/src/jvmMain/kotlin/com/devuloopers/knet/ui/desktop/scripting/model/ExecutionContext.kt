package com.devuloopers.knet.ui.desktop.scripting.model

import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry

/**
 * UI presentation model exposing variables and network scope to automation script context.
 */
public data class ExecutionContext(
    val requests: List<KeyValueEntry> = emptyList(),
    val responses: List<KeyValueEntry> = emptyList(),
    val environment: List<KeyValueEntry> = emptyList(),
    val globals: List<KeyValueEntry> = emptyList(),
    val variables: List<KeyValueEntry> = emptyList()
)
