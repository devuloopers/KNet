package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.engine.formatter.model.BodyFormat

/** Whether the prepared request can open the existing GraphQL Query viewer without parsing in a composable. */
enum class GraphQLQueryAvailability(val description: String) {
    AVAILABLE("Open the captured query in its request source: Body or Params > Query."),
    PREPARING("The captured request query is still being prepared."),
    UNAVAILABLE("A GraphQL query is not available in this captured request's Query viewer. Inspect the original request body or URL parameters for retained data."),
}

/** Reads only an already-resolved format; raw payloads and other exchanges are never inspected here. */
val PayloadInspectionSpec.graphQLQueryAvailability: GraphQLQueryAvailability
    get() = when {
        isPreparing -> GraphQLQueryAvailability.PREPARING
        (resolvedFormat as? BodyFormat.GraphQL)?.queryText?.isNotBlank() == true -> GraphQLQueryAvailability.AVAILABLE
        else -> GraphQLQueryAvailability.UNAVAILABLE
    }
