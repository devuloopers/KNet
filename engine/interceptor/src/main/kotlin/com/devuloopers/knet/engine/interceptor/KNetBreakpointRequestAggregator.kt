package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.application.contract.breakpoint.BreakpointGate
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.engine.proxy.pipeline.SelectiveHttpObjectAggregator
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest

/**
 * Selects request aggregation using application rule facts while keeping the proxy aggregator
 * independent from breakpoint, protocol, persistence, and UI types.
 *
 * @param breakpointGate Application-owned rule prefilter and editable-body limit provider.
 */
class KNetBreakpointRequestAggregator(
    breakpointGate: BreakpointGate,
) : SelectiveHttpObjectAggregator(
    maximumContentBytes = breakpointGate.requirements.value.maxEditableBodyBytes,
    shouldAggregate = { context, message ->
        message is HttpRequest && message.method() != HttpMethod.CONNECT &&
            mapBreakpointRequest(context, message).request.let { request ->
                breakpointGate.mayIntercept(request, BreakpointPhase.REQUEST) ||
                    breakpointGate.mayIntercept(request, BreakpointPhase.RESPONSE)
            }
    }
)
