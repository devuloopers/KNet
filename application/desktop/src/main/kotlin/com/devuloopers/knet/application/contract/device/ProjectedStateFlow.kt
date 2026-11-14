package com.devuloopers.knet.application.contract.device

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*

/**
 * Read-only pure projection with a current synchronous value and no owned coroutine or replayed payload.
 * Each consumer owns its collection; making a workspace-bound adapter cannot leak a product-scope job.
 * [project] must be deterministic and must not perform IO or mutate its input.
 */
@OptIn(InternalCoroutinesApi::class)
public class ProjectedStateFlow<Input, Output>(
    private val source: StateFlow<Input>,
    private val project: (Input) -> Output,
) : StateFlow<Output> {
    override val value: Output get() = project(source.value)
    override val replayCache: List<Output> get() = listOf(value)
    override suspend fun collect(collector: FlowCollector<Output>): Nothing {
        source.map(project).distinctUntilChanged().collect(collector)
        awaitCancellation()
    }
}
