package com.devuloopers.knet.ui.apistudio.handler

/**
 * Data configuration model representing all parameters required to execute an API suite.
 * Decouples execution setup from execution pipeline logic.
 *
 * @property scope Target [SuiteExecutionScope] specifying the execution boundary.
 * @property selectedCollectionIds List of unique collection identifiers selected for execution.
 * @property continueOnFailure Whether execution should continue after individual request failures.
 * @property stopOnFirstFailure Whether execution should halt immediately upon the first request or assertion failure.
 */
data class SuiteExecutionConfig(
    val scope: SuiteExecutionScope = SuiteExecutionScope.CurrentRequest,
    val selectedCollectionIds: List<String> = emptyList(),
    val continueOnFailure: Boolean = true,
    val stopOnFirstFailure: Boolean = false
)
