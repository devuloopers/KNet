package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.application.contract.inspection.GraphQLInspectionMetadata
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.traffic.inspection.InspectionDocument
import com.devuloopers.knet.traffic.inspection.InspectionField

/**
 * Current compact persisted-query metadata, without query source, parsing or formatter dependencies for consumers.
 * Captured annotations stay immutable; owners can use this projection for current overview/cache identity.
 */
val PayloadInspectionSpec.persistedQueryDocument: InspectionDocument?
    get() {
        val format = resolvedFormat as? BodyFormat.GraphQL ?: return null
        val metadata = format.persistedQuery ?: return null
        return InspectionDocument(GraphQLInspectionMetadata.DOCUMENT_KIND,
            if (format.operationType.isBlank()) "GraphQL persisted request" else
                "GraphQL ${format.operationType}" + format.operationName?.let { ": $it" }.orEmpty(),
            fields = listOfNotNull(
                format.operationType.takeIf(String::isNotBlank)?.let { InspectionField("Operation type", it) },
                format.operationName?.let { InspectionField("Operation name", it) },
                InspectionField("Source", format.source.label),
                InspectionField(GraphQLInspectionMetadata.PERSISTED_QUERY_HASH_FIELD, metadata.hash.value),
                InspectionField(GraphQLInspectionMetadata.QUERY_DOCUMENT_SOURCE_FIELD, format.queryProvenance.label),
                format.diagnostic?.let { InspectionField("Inspection", it.description) },
            ))
    }
