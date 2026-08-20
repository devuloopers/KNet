package com.devuloopers.knet.domain.request.descriptor

/**
 * Optional protocol-aware descriptor contribution for one canonical request presentation input.
 *
 * Implementations return `null` when they do not recognize the request. They must not mutate or persist it.
 */
fun interface RequestDescriptorStrategy {
    /** Higher-priority semantic strategies run before the terminal HTTP fallback. */
    val priority: Int
        get() = 0

    /** Returns semantic descriptor metadata when this strategy recognizes [request], otherwise `null`. */
    fun describe(request: RequestDescriptorInput): RequestDescriptorContribution?
}
