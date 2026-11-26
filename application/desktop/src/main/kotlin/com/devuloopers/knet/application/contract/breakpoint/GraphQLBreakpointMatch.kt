package com.devuloopers.knet.application.contract.breakpoint

import com.devuloopers.knet.domain.graphql.GraphQLQueryHash
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId

/** User-selected scope of a GraphQL HTTP breakpoint. [CHOOSE] represents an unsaved draft. */
public enum class GraphQLBreakpointScope(public val token: String, public val label: String) {
    /** A draft that requires a deliberate scope choice before it can be saved. */
    CHOOSE("choose", "Choose match scope"),
    /** One APQ document hash, optionally narrowed by the request's explicit operation selector. */
    EXACT("exact", "Exact persisted query"),
    /** A name verified from the request or resolved GraphQL document. */
    OPERATION("operation", "Operation name"),
    /** Any recognized GraphQL request satisfying the transport filters. */
    ENDPOINT("endpoint", "All operations on this endpoint"),
}

/** GraphQL editor field identities shared by the rule editor and its protocol extension. */
public object GraphQLBreakpointMatch {
    /** GraphQL HTTP breakpoint protocol identity. */
    public val protocolId: BreakpointProtocolId = BreakpointProtocolId("graphql")
    /** Scope selection field. */
    public val scopeField: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("match-scope")
    /** Verified operation name editor field. */
    public val operationNameField: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("operation-name")
    /** Persisted SHA-256 editor field. */
    public val hashField: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("persisted-sha256")
    /** Explicit operation selector from the wire request, used only alongside an exact hash. */
    public val requestOperationField: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("request-operation-name")

    /** Parses a user-entered [value] as a canonical SHA-256, ignoring case and outer whitespace. */
    public fun parseHash(value: String): GraphQLQueryHash? =
        runCatching { GraphQLQueryHash(value.trim().lowercase()) }.getOrNull()

    /** Returns whether [value] is a bounded GraphQL operation name. */
    public fun isOperationName(value: String): Boolean = value.length <= 256 && operationName.matches(value)

    private val operationName = Regex("^[_A-Za-z][_0-9A-Za-z]*$")
}
