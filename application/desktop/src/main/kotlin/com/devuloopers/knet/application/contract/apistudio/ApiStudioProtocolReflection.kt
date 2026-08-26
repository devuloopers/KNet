package com.devuloopers.knet.application.contract.apistudio

import com.devuloopers.knet.domain.request.descriptor.RequestKindId

/** Protocol-neutral target for schema discovery from a running service. */
public data class ApiStudioProtocolReflectionTarget(
    public val host: String,
    public val port: Int,
    public val useTls: Boolean,
    public val deadlineMillis: Long,
    public val route: ApiStudioProtocolRoute = ApiStudioProtocolRoute.Direct,
) {
    init {
        require(host.isNotBlank()) { "Reflection target host must not be blank." }
        require(port in 1..65_535) { "Reflection target port is invalid." }
        require(deadlineMillis in 1L..120_000L) { "Reflection deadline is invalid." }
    }
}

/** Imported schema and safe summary returned by a protocol reflection contribution. */
public data class ApiStudioProtocolReflectionResult(
    public val source: ApiStudioProtocolSchemaSource,
    public val summary: ApiStudioProtocolSchemaImport,
)

/** Additive schema-discovery boundary implemented by protocols that support reflection. */
public interface ApiStudioProtocolReflection {
    public val kind: RequestKindId

    public suspend fun reflect(target: ApiStudioProtocolReflectionTarget): Result<ApiStudioProtocolReflectionResult>
}

/** Immutable reflection registry assembled at the product composition root. */
public class ApiStudioProtocolReflectionRegistry(
    contributions: List<ApiStudioProtocolReflection> = emptyList(),
) {
    private val byKind = contributions.associateBy(ApiStudioProtocolReflection::kind).also { indexed ->
        require(indexed.size == contributions.size) { "API Studio reflection kinds must be unique." }
    }

    public suspend fun reflect(
        kind: RequestKindId,
        target: ApiStudioProtocolReflectionTarget,
    ): Result<ApiStudioProtocolReflectionResult> = contribution(kind).reflect(target)

    private fun contribution(kind: RequestKindId): ApiStudioProtocolReflection =
        requireNotNull(byKind[kind]) { "No API Studio reflection contribution is registered for '${kind.value}'." }
}
