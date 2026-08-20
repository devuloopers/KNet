package com.devuloopers.knet.domain.apistudio.usecase

import com.devuloopers.knet.domain.apistudio.descriptor.RequestDescriptor
import com.devuloopers.knet.domain.apistudio.descriptor.RequestDescriptorStrategy
import com.devuloopers.knet.domain.apistudio.descriptor.RequestKindId
import com.devuloopers.knet.domain.collection.model.SavedApiRequest

/**
 * Resolves generated naming and semantic badge metadata through ordered protocol contributions.
 *
 * The first recognized contribution owns kind/badge identity. If it cannot provide a name, later contributions
 * may provide only the fallback name; this keeps anonymous GraphQL identified as `GQL` while using its HTTP path.
 */
class DescribeRequestUseCase(
    strategies: List<RequestDescriptorStrategy>
) {

    private val strategies = strategies.toList()

    /** Resolves one complete, stable descriptor for [request]. */
    fun execute(request: SavedApiRequest): RequestDescriptor {
        var resolvedKind: RequestKindId? = null
        var resolvedBadge: String? = null
        var resolvedName: String? = null

        for (strategy in strategies) {
            val contribution = try {
                strategy.describe(request)
            } catch (_: Exception) {
                null
            } ?: continue

            if (resolvedKind == null) {
                resolvedKind = contribution.kind
                resolvedBadge = contribution.badgeLabel.trim().takeIf { it.isNotEmpty() }
            }
            if (resolvedName == null) {
                resolvedName = contribution.suggestedName?.trim()?.takeIf { it.isNotEmpty() }
            }
            if (resolvedKind != null && resolvedName != null) break
        }

        return RequestDescriptor(
            suggestedName = resolvedName ?: UNTITLED_REQUEST,
            kind = resolvedKind ?: RequestKindId.HTTP,
            badgeLabel = resolvedBadge ?: request.method.token,
            transportMethod = request.method
        )
    }

    companion object {
        /** Stable fallback for an authored request without a meaningful target yet. */
        const val UNTITLED_REQUEST: String = "Untitled Request"
    }
}
