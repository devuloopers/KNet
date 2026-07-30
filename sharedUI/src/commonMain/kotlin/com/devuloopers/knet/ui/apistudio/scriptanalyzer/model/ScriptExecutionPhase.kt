package com.devuloopers.knet.ui.apistudio.scriptanalyzer.model

/**
 * Enumeration representing the execution phase of a script in API Studio.
 */
enum class ScriptExecutionPhase {
    /**
     * Script executed before dispatching the HTTP network request.
     */
    PRE_REQUEST,

    /**
     * Script executed after receiving the HTTP network response.
     */
    POST_RESPONSE
}
