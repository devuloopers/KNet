package com.devuloopers.knet.domain.request.usecase

import com.devuloopers.knet.domain.request.descriptor.RequestDescriptor
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorBody
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorInput
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorStrategy
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.absoluteUrl
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName

/**
 * Resolves generated naming and semantic badge metadata through ordered protocol contributions.
 *
 * The first recognized contribution owns kind/badge identity. If it cannot provide a name, later contributions
 * may provide only the fallback name; this keeps anonymous GraphQL identified as `GQL` while using its HTTP path.
 */
class DescribeRequestUseCase(
    strategies: List<RequestDescriptorStrategy>
) {

    private val strategies = strategies.sortedByDescending(RequestDescriptorStrategy::priority)

    /** Resolves one complete, stable descriptor for [request]. */
    fun execute(request: SavedApiRequest): RequestDescriptor = execute(request.toDescriptorInput())

    /** Resolves one complete, stable descriptor for a canonical cross-feature [request]. */
    fun execute(request: RequestDescriptorInput): RequestDescriptor {
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
            if (resolvedName != null) break
        }

        return RequestDescriptor(
            suggestedName = resolvedName ?: UNTITLED_REQUEST,
            kind = resolvedKind ?: RequestKindId.HTTP,
            badgeLabel = resolvedBadge ?: request.transportMethod.token,
            transportMethod = request.transportMethod
        )
    }

    /**
     * Builds and resolves a descriptor for captured or pending canonical request metadata.
     *
     * @param request Canonical request head and target.
     * @param body Optional bounded request body preview.
     * @param bodyComplete Whether [body] represents the complete observed request body.
     * @param semanticKindHint Optional kind already established by a protocol extension or semantic inspector.
     */
    fun execute(
        request: HttpRequestSnapshot,
        body: RequestDescriptorBody? = null,
        bodyComplete: Boolean = body != null,
        semanticKindHint: RequestKindId? = null,
    ): RequestDescriptor = execute(
        RequestDescriptorInput(
            transportMethod = request.head.method,
            absoluteUrl = request.absoluteUrl(),
            headers = request.head.headers,
            body = body,
            bodyComplete = bodyComplete,
            semanticKindHint = semanticKindHint,
        ),
    )

    companion object {
        /** Stable fallback for an authored request without a meaningful target yet. */
        const val UNTITLED_REQUEST: String = "Untitled Request"
    }
}

private fun SavedApiRequest.toDescriptorInput(): RequestDescriptorInput {
    val bodyBytes = body.content.takeIf(String::isNotEmpty)?.encodeToByteArray()
    return RequestDescriptorInput(
        transportMethod = method,
        absoluteUrl = url,
        headers = headers.asSequence()
            .filter { it.isEnabled && it.key.isNotBlank() }
            .map { HeaderField(HeaderName(it.key), it.value) }
            .toList(),
        body = bodyBytes?.copyOf(minOf(bodyBytes.size, RequestDescriptorBody.MAXIMUM_BYTES))
            ?.let(::RequestDescriptorBody),
        bodyComplete = bodyBytes == null || bodyBytes.size <= RequestDescriptorBody.MAXIMUM_BYTES,
        semanticKindHint = if (body.type == RequestBodyType.GRAPHQL) RequestKindId.GRAPHQL else null,
    )
}
