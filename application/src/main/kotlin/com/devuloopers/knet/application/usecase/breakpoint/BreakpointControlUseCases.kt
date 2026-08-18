package com.devuloopers.knet.application.usecase.breakpoint

import com.devuloopers.knet.application.port.breakpoint.BreakpointControlPort
import com.devuloopers.knet.application.port.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.port.breakpoint.BreakpointRequestEdit
import com.devuloopers.knet.application.port.breakpoint.BreakpointResponseEdit
import com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint
import kotlinx.coroutines.flow.StateFlow

/** Observes immutable pending breakpoint candidates. */
public class ObservePendingBreakpointsUseCase(
    private val control: BreakpointControlPort,
) {
    public fun execute(): StateFlow<List<PendingBreakpoint>> = control.pendingBreakpoints
}

/** Resolves one pending breakpoint using canonical request/response edits. */
public class ResolveBreakpointUseCase(
    private val control: BreakpointControlPort,
) {
    public suspend fun resumeRequest(pendingId: String, edit: BreakpointRequestEdit): Boolean =
        control.resolve(pendingId, BreakpointDecision.Resume(requestEdit = edit))

    public suspend fun resumeResponse(pendingId: String, edit: BreakpointResponseEdit): Boolean =
        control.resolve(pendingId, BreakpointDecision.Resume(responseEdit = edit))

    public suspend fun drop(pendingId: String): Boolean =
        control.resolve(pendingId, BreakpointDecision.Drop)
}

/** Drops pending breakpoints matching one logical request. */
public class DropMatchingBreakpointsUseCase(
    private val control: BreakpointControlPort,
) {
    public suspend fun execute(url: String, method: String): Int = control.dropMatching(url, method)
}

/** Drops every pending breakpoint and returns the number resolved. */
public class ClearPendingBreakpointsUseCase(
    private val control: BreakpointControlPort,
) {
    public suspend fun execute(): Int = control.clear()
}
