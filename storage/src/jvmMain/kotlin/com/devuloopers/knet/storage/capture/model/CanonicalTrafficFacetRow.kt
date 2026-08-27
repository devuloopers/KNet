package com.devuloopers.knet.storage.capture.model

/** Room projection for one aggregate Traffic facet query. */
data class CanonicalTrafficFacetRow(
    val totalCount: Long,
    val httpCount: Long,
    val httpsCount: Long,
)
