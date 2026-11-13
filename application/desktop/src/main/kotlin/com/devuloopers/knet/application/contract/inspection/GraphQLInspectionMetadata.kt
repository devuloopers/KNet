package com.devuloopers.knet.application.contract.inspection

/** Stable annotation metadata shared by the GraphQL producer and protocol-neutral presentation adapters. */
public object GraphQLInspectionMetadata {
    /** Persisted GraphQL document kind; existing annotations keep their wire shape. */
    public const val DOCUMENT_KIND: String = "graphql"

    /** Legacy display-field identity for the request target already displayed by the inspector header. */
    public const val REQUEST_TARGET_FIELD: String = "Request target"
    /** Validated APQ SHA-256 identity, shared by stored and current derived metadata. */
    public const val PERSISTED_QUERY_HASH_FIELD: String = "Persisted query SHA-256"

    /** Provenance of the document visible at inspection time. */
    public const val QUERY_DOCUMENT_SOURCE_FIELD: String = "Query document source"

}
