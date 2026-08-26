package com.devuloopers.knet.application.usecase.breakpoint

import com.devuloopers.knet.application.contract.breakpoint.BreakpointControl
import com.devuloopers.knet.application.contract.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.contract.breakpoint.BreakpointRequestEdit
import com.devuloopers.knet.application.contract.breakpoint.BreakpointResponseEdit
import com.devuloopers.knet.application.contract.breakpoint.PendingBreakpoint
import com.devuloopers.knet.application.contract.breakpoint.PendingProtocolMessageBreakpoint
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointControl
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointDecision
import com.devuloopers.knet.application.contract.breakpoint.BreakpointBody
import kotlinx.coroutines.flow.StateFlow

/** Observes immutable pending breakpoint candidates. */
public class ObservePendingBreakpointsUseCase(
    private val control: BreakpointControl,
) {
    public fun execute(): StateFlow<List<PendingBreakpoint>> = control.pendingBreakpoints
}

/** Resolves one pending breakpoint using canonical request/response edits. */
public class ResolveBreakpointUseCase(
    private val control: BreakpointControl,
) {
    public suspend fun resumeRequest(pendingId: String, edit: BreakpointRequestEdit): Boolean =
        control.resolve(pendingId, BreakpointDecision.ResumeRequest(edit))

    public suspend fun resumeResponse(pendingId: String, edit: BreakpointResponseEdit): Boolean =
        control.resolve(pendingId, BreakpointDecision.ResumeResponse(edit))

    public suspend fun continueUnchanged(pendingId: String): Boolean =
        control.resolve(pendingId, BreakpointDecision.ContinueUnchanged)

    public suspend fun drop(pendingId: String): Boolean =
        control.resolve(pendingId, BreakpointDecision.Drop)
}

/** Drops pending breakpoints matching one logical request. */
public class DropMatchingBreakpointsUseCase(
    private val control: BreakpointControl,
) {
    public suspend fun execute(url: String, method: String): Int = control.dropMatching(url, method)
}

/** Drops every pending breakpoint and returns the number resolved. */
public class ClearPendingBreakpointsUseCase(
    private val control: BreakpointControl,
) {
    public suspend fun execute(): Int = control.clear()
}

/** Observes pending framed-message breakpoints independently from HTTP exchange pauses. */
public class ObservePendingProtocolMessageBreakpointsUseCase(
    private val control: ProtocolMessageBreakpointControl,
) {
    public fun execute(): StateFlow<List<PendingProtocolMessageBreakpoint>> = control.pendingProtocolMessages
}

/** Resolves one pending framed-message interception. */
public class ResolveProtocolMessageBreakpointUseCase(
    private val control: ProtocolMessageBreakpointControl,
) {
    public suspend fun continueUnchanged(pendingId: String): Boolean =
        control.resolveProtocolMessage(pendingId, ProtocolMessageBreakpointDecision.ContinueUnchanged)

    public suspend fun replace(pendingId: String, body: ByteArray): Boolean =
        control.resolveProtocolMessage(
            pendingId,
            ProtocolMessageBreakpointDecision.Replace(BreakpointBody(body)),
        )

    public suspend fun dropStream(pendingId: String): Boolean =
        control.resolveProtocolMessage(pendingId, ProtocolMessageBreakpointDecision.DropStream)
}
