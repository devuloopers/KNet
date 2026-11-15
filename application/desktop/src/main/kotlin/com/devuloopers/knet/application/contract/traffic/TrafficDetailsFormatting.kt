package com.devuloopers.knet.application.contract.traffic

import com.devuloopers.knet.domain.graphql.GraphQLHttpDocument

/** Bounded protocol/JSON formatting implemented outside the application layer by existing formatters. */
public interface TrafficDetailsFormatting {
    /** Formats strict JSON containers within existing parser/depth/output limits; other text is unchanged. */
    public fun json(text: String): String

    /** Derives GraphQL request sections without modifying the captured request or learning new hashes. */
    public fun graphQL(method: String, url: String, contentType: String, body: ByteArray?, complete: Boolean): GraphQLHttpDocument?
}
