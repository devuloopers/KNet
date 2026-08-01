package com.devuloopers.knet.ui.desktop.scripting.model

/**
 * Reusable automation scripting snippet model.
 */
public data class ScriptSnippet(
    val id: String,
    val title: String,
    val description: String,
    val codeJs: String,
    val codeKotlin: String
)
