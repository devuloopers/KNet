package com.devuloopers.knet.domain.apistudio.descriptor

import com.devuloopers.knet.domain.collection.model.SavedApiRequest

/**
 * Optional protocol-aware descriptor contribution for one canonical API Studio request.
 *
 * Implementations return `null` when they do not recognize the request. They must not mutate or persist it.
 */
fun interface RequestDescriptorStrategy {
    /** Returns semantic descriptor metadata when this strategy recognizes [request], otherwise `null`. */
    fun describe(request: SavedApiRequest): RequestDescriptorContribution?
}
